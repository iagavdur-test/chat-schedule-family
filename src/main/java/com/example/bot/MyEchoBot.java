package com.example.bot;

import com.example.bot.events.EventEntity;
import com.example.bot.events.EventRepository;
import com.example.bot.events.enums.RepeatType;
import com.example.bot.users.UserEntity;
import com.example.bot.users.UserRepository;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MyEchoBot implements LongPollingSingleThreadUpdateConsumer {
    private final TelegramClient telegramClient;
    private final UserRepository userRepository;
    private final EventRepository eventRepository; // Добавили репозиторий событий
    private final long adminId;

    public MyEchoBot(TelegramClient telegramClient, UserRepository userRepository,
                     EventRepository eventRepository, long adminId) {
        this.telegramClient = telegramClient;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.adminId = adminId;
    }

    @Override
    public void consume(Update update) {
        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            int messageId = update.getCallbackQuery().getMessage().getMessageId();

            if (callbackData.startsWith("delete_")) {
                long eventId = Long.parseLong(callbackData.substring(7));

                if (eventRepository.existsById(eventId)) {
                    eventRepository.deleteById(eventId);

                    DeleteMessage deleteMessage = DeleteMessage.builder()
                            .chatId(chatId)
                            .messageId(messageId)
                            .build();
                    try {
                        telegramClient.execute(deleteMessage);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    sendText(chatId, "🗑️ Событие успешно удалено из вашего календаря!");
                } else {
                    sendText(chatId, "❌ Это событие уже было удалено ранее.");
                }
            }
            return;
        }

        if (update.hasMessage() && update.getMessage().hasText()) {

            if (!update.getMessage().isUserMessage()) {
                return;
            }

            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            var telegramUser = update.getMessage().getFrom();
            long userId = telegramUser.getId();

            boolean defaultAccess = (userId == adminId);
            UserEntity user = userRepository.findById(userId)
                    .orElseGet(() -> userRepository.save(new UserEntity(
                            userId, telegramUser.getFirstName(),
                            telegramUser.getUserName() != null ? telegramUser.getUserName() : "",
                            0, defaultAccess
                    )));

            if (!user.isHasAccess()) {
                sendText(chatId, "⛔ Доступ ограничен. Запрос отправлен администратору.");
                return;
            }

            switch (messageText) {
                case "/start", "ℹ️ Справка" -> {
                    String helpText = "🤖 *Добро пожаловать в Календарь-Бот!*\n\n" +
                            "Используйте кнопки меню для управления:\n" +
                            "• Нажмите *📅 Мои планы на сегодня*, чтобы увидеть список задач.\n" +
                            "• Нажмите *➕ Добавить событие*, чтобы получить шаблон для ввода.";
                    sendText(chatId, helpText);
                    return;
                }
                case "📅 Мои планы на сегодня" -> {
                    handleShowTodayPlans(chatId);
                    return;
                }
                case "📅 Планы на неделю" -> {
                    handleShowWeeklyPlans(chatId);
                    return;
                }
                case "➕ Добавить событие" -> {
                    String templateText = "📋 *Шаблон для добавления события:*\n\n" +
                            "Скопируйте текст ниже, замените данные на свои и отправьте боту:\n\n" +
                            "`/add 06.09.2026 15:00 ONCE Важная встреча ; Обсудить разработку` \n\n" +
                            "_Типы повторений: ONCE (один раз), WEEKLY (каждую неделю), YEARLY (каждый год). Если время не нужно, укажите вместо него дефис `-`._";
                    sendText(chatId, templateText);
                    return;
                }
            }

            if (messageText.startsWith("/add ")) {
                handleAddEventCommand(chatId, messageText);
                return;
            }

            user.setMessageCount(user.getMessageCount() + 1);
            userRepository.save(user);
            sendText(chatId, String.format("Привет, %s! Вы можете использовать нижнее меню для работы со своими задачами.", user.getFirstName()));
        }
    }

    private void handleShowTodayPlans(long chatId) {
        java.time.LocalDate today = java.time.LocalDate.now();
        Iterable<EventEntity> allEvents = eventRepository.findAll();
        boolean hasEvents = false;

        for (EventEntity event : allEvents) {
            boolean isToday = switch (event.getRepeatType()) {
                case ONCE -> event.getEventDate().isEqual(today);
                case WEEKLY -> event.getEventDate().getDayOfWeek() == today.getDayOfWeek();
                case YEARLY -> event.getEventDate().getDayOfMonth() == today.getDayOfMonth()
                        && event.getEventDate().getMonth() == today.getMonth();
            };

            if (isToday) {
                hasEvents = true;
                String timeText = event.getEventTime() != null ? " 🕒 " + event.getEventTime() : "";
                String prefix = event.getRepeatType() == RepeatType.YEARLY ? "🎂" : "📌";

                String eventText = String.format("%s *%s*%s\n📝 %s",
                        prefix, event.getTitle(), timeText, event.getDescription());

                InlineKeyboardButton deleteButton = InlineKeyboardButton.builder()
                        .text("❌ Удалить событие")
                        .callbackData("delete_" + event.getId())
                        .build();

                InlineKeyboardRow row = new InlineKeyboardRow(deleteButton);

                InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                        .keyboard(Collections.singletonList(row))
                        .build();

                SendMessage message = SendMessage.builder()
                        .chatId(chatId)
                        .text(eventText)
                        .parseMode("Markdown")
                        .replyMarkup(markup)
                        .build();
                try {
                    telegramClient.execute(message);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        if (!hasEvents) {
            sendText(chatId, "📅 *На сегодня планов нет!* Отличный повод отдохнуть. 😎");
        }
    }

    private void handleAddEventCommand(long chatId, String text) {
        try {
            String rawParams = text.substring(5).trim();
            String[] parts = rawParams.split("\\s+", 5);
            if (parts.length < 4) {
                sendText(chatId, "❌ Ошибка! Неверный формат.\nИспользуйте:\n`/add ДД.ММ.ГГГГ ЧЧ:ММ ТИП Название ; Описание`\n\n*Пример без времени:* `/add 05.09.2026 - YEARLY День рождения ; Подарить подарок` ");
                return;
            }
            LocalDate date = LocalDate.parse(parts[0], DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            LocalTime time = null;
            if (!parts[1].equals("-")) {
                time = LocalTime.parse(parts[1], DateTimeFormatter.ofPattern("HH:mm"));
            }
            RepeatType repeatType = RepeatType.valueOf(parts[2].toUpperCase());
            String remainingText = parts[3] + (parts.length == 5 ? " " + parts[4] : "");
            String title = remainingText;
            String description = "";
            if (remainingText.contains(";")) {
                String[] titleAndDesc = remainingText.split(";", 2);
                title = titleAndDesc[0].trim();
                description = titleAndDesc[1].trim();
            }
            EventEntity event = EventEntity.builder()
                    .title(title)
                    .eventDate(date)
                    .eventTime(time)
                    .repeatType(repeatType)
                    .description(description)
                    .build();
            eventRepository.save(event);

            sendText(chatId, "✅ Событие успешно добавлено в ваш календарь!");

        } catch (Exception e) {
            sendText(chatId, "❌ Ошибка разбора команды. Проверьте правильность даты (ДД.ММ.ГГГГ), времени (ЧЧ:ММ) и типа (ONCE/WEEKLY/YEARLY).");
        }
    }

    private void sendText(long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(createMainKeyboard()) // Подключаем наши нижние кнопки
                .build();
        try {
            telegramClient.execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ReplyKeyboardMarkup createMainKeyboard() {
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("📅 Мои планы на сегодня"));
        row1.add(new KeyboardButton("📅 Планы на неделю"));
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("➕ Добавить событие"));
        row2.add(new KeyboardButton("ℹ️ Справка"));
        keyboard.add(row1);
        keyboard.add(row2);
        return ReplyKeyboardMarkup.builder()
                .keyboard(keyboard)
                .selective(true)
                .resizeKeyboard(true)   // Кнопки будут компактными
                .oneTimeKeyboard(false)  // Клавиатура не скроется после нажатия
                .build();
    }

    private void handleShowWeeklyPlans(long chatId) {
        LocalDate today = LocalDate.now();
        Iterable<EventEntity> allEvents = eventRepository.findAll();
        StringBuilder messageBuilder = new StringBuilder("🗓️ *Ваши планы на ближайшую неделю:*\n\n");
        boolean hasAnyEvents = false;
        for (int i = 0; i < 7; i++) {
            LocalDate currentDay = today.plusDays(i);
            List<EventEntity> dayEvents = new ArrayList<>();
            for (EventEntity event : allEvents) {
                boolean isMatch = switch (event.getRepeatType()) {
                    case ONCE -> event.getEventDate().isEqual(currentDay);
                    case WEEKLY -> event.getEventDate().getDayOfWeek() == currentDay.getDayOfWeek();
                    case YEARLY -> event.getEventDate().getDayOfMonth() == currentDay.getDayOfMonth()
                            && event.getEventDate().getMonth() == currentDay.getMonth();
                };
                if (isMatch) dayEvents.add(event);
            }
            if (!dayEvents.isEmpty()) {
                hasAnyEvents = true;
                String dayName = switch (currentDay.getDayOfWeek()) {
                    case MONDAY -> "Понедельник";
                    case TUESDAY -> "Вторник";
                    case WEDNESDAY -> "Среда";
                    case THURSDAY -> "Четверг";
                    case FRIDAY -> "Пятница";
                    case SATURDAY -> "Суббота";
                    case SUNDAY -> "Воскресенье";
                };
                messageBuilder.append(String.format("🔹 *%s (%02d.%02d):*\n",
                        dayName, currentDay.getDayOfMonth(), currentDay.getMonthValue()));
                for (EventEntity event : dayEvents) {
                    String timeText = event.getEventTime() != null ? " 🕒 " + event.getEventTime() : "";
                    String prefix = event.getRepeatType() == RepeatType.YEARLY ? "🎂" : "▫️";
                    messageBuilder.append(String.format("  %s %s%s — _%s_\n",
                            prefix, event.getTitle(), timeText, event.getDescription()));
                }
                messageBuilder.append("\n");
            }
        }
        if (!hasAnyEvents) {
            sendText(chatId, "📅 *На ближайшую неделю планов нет!* Полная свобода. 😎");
            return;
        }
        sendText(chatId, messageBuilder.toString());
    }
}
