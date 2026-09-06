package com.example.bot.events;

import org.springframework.data.repository.CrudRepository;

import java.time.LocalDate;
import java.util.List;

public interface EventRepository extends CrudRepository<EventEntity, Long> {
    List<EventEntity> findAllByEventDate(LocalDate date);
}
