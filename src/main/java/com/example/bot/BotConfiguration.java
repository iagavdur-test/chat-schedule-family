package com.example.bot;

import com.example.bot.events.EventRepository;
import com.example.bot.users.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;


@Slf4j
@Configuration
public class BotConfiguration {
    @Value("${bot.token}")
    private String botToken;

    @Value("${bot.admin.id}")
    private long adminId;

    @Bean
    public TelegramClient telegramClient() {
        return new OkHttpTelegramClient(botToken);
    }

    @Bean
    public TelegramBotsLongPollingApplication telegramBotsApplication(TelegramClient telegramClient,
                                                                      UserRepository userRepository,
                                                                      EventRepository eventRepository) {
        TelegramBotsLongPollingApplication api = new TelegramBotsLongPollingApplication();
        try {
            log.info("Starting bot application registration process...");
            api.registerBot(botToken, new MyEchoBot(telegramClient, userRepository, eventRepository, adminId));
            log.info("Bot application bot has been successfully registered and started.");
        } catch (TelegramApiException e) {
            log.error("Failed to register bot application due to an error: {}", e.getMessage(), e);
        }
        return api;
    }
}
