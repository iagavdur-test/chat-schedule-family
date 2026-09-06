package com.example.bot;

import com.example.bot.events.EventRepository;
import com.example.bot.users.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;


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
            api.registerBot(botToken, new MyEchoBot(telegramClient, userRepository, eventRepository, adminId));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
        return api;
    }
}
