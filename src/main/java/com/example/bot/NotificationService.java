package com.example.bot;

import com.example.bot.events.EventEntity;
import com.example.bot.events.EventRepository;
import com.example.bot.events.enums.RepeatType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class NotificationService {

    private final EventRepository eventRepository;
    private final TelegramClient telegramClient;

    private final long groupId;

    public NotificationService(EventRepository eventRepository, TelegramClient telegramClient, @Value("${bot.group.id}") long groupId) {
        this.eventRepository = eventRepository;
        this.telegramClient = telegramClient;
        this.groupId = groupId;
    }

    @Scheduled(cron = "0 30 6 * * *", zone = "Europe/Minsk")
    public void sendDailyDigest() {
        LocalDate today = LocalDate.now();
        List<EventEntity> todayEvents = getEventsForDate(today);
        if (todayEvents.isEmpty()) return;

        StringBuilder messageBuilder = new StringBuilder("📅 *Планы на сегодня:*\n\n");
        buildEventListText(messageBuilder, todayEvents);
        broadcastMessage(messageBuilder.toString());
    }

    @Scheduled(cron = "0 30 21 * * *", zone = "Europe/Minsk")
    public void sendTomorrowDigest() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<EventEntity> tomorrowEvents = getEventsForDate(tomorrow);

        if (tomorrowEvents.isEmpty()) {
            return;
        }

        StringBuilder messageBuilder = new StringBuilder("🌙 *Планы на завтра:*\n\n");
        buildEventListText(messageBuilder, tomorrowEvents);
        broadcastMessage(messageBuilder.toString());
    }


    @Scheduled(cron = "0 * * * * *", zone = "Europe/Minsk")
    public void checkTimelyNotifications() {
        LocalDate today = LocalDate.now();
        List<EventEntity> todayEvents = getEventsForDate(today);
        LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);

        for (EventEntity event : todayEvents) {
            if (event.getEventTime() != null) {
                LocalTime eventTime = event.getEventTime().truncatedTo(ChronoUnit.MINUTES);
                long minutesUntilEvent = ChronoUnit.MINUTES.between(now, eventTime);

                if (minutesUntilEvent == 120 || minutesUntilEvent == 60 || minutesUntilEvent == 30 || minutesUntilEvent == 15) {
                    String timePhrase = switch ((int) minutesUntilEvent) {
                        case 120 -> "через 2 часа";
                        case 60 -> "через 1 час";
                        case 30 -> "через 30 минут";
                        case 15 -> "через 15 минут";
                        default -> "";
                    };

                    String alertText = String.format("⏰ *Напоминание!*\nДо события осталось всего %s (начнется в %s):\n\n*Название:* %s\n*Описание:* %s",
                            timePhrase, eventTime, event.getTitle(), event.getDescription());
                    broadcastMessage(alertText);
                }
            }
        }
    }


    private List<EventEntity> getEventsForDate(LocalDate targetDate) {
        Iterable<EventEntity> allEvents = eventRepository.findAll();
        List<EventEntity> filteredEvents = new ArrayList<>();

        for (EventEntity event : allEvents) {
            boolean isMatch = switch (event.getRepeatType()) {
                case ONCE -> event.getEventDate().isEqual(targetDate);
                case WEEKLY -> event.getEventDate().getDayOfWeek() == targetDate.getDayOfWeek();
                case YEARLY -> event.getEventDate().getDayOfMonth() == targetDate.getDayOfMonth()
                        && event.getEventDate().getMonth() == targetDate.getMonth();
            };
            if (isMatch) filteredEvents.add(event);
        }
        return filteredEvents;
    }


    private void buildEventListText(StringBuilder messageBuilder, List<EventEntity> events) {
        for (EventEntity event : events) {
            String timeText = event.getEventTime() != null ? " 🕒 " + event.getEventTime() : "";
            String prefix = event.getRepeatType() == RepeatType.YEARLY ? "🎂" : "📌";
            messageBuilder.append(String.format("%s *%s*%s\n📝 %s\n\n",
                    prefix, event.getTitle(), timeText, event.getDescription()));
        }
    }


    private void broadcastMessage(String text) {
        SendMessage message = SendMessage.builder()
                .chatId(groupId) // Отправляем СТРОГО на ID нашей группы
                .text(text)
                .parseMode("Markdown")
                .build();
        try {
            telegramClient.execute(message);
            log.info("Notification published to the group");
        } catch (Exception e) {
            log.error("Failed to publish notification to the group\n{}",e.getMessage());
        }
    }
}
