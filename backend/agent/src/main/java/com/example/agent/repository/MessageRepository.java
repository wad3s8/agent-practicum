package com.example.agent.repository;

import com.example.agent.entity.Chat;
import com.example.agent.entity.Message;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends CrudRepository<Message, Long> {
    List<Message> findByChatOrderByCreatedAtAsc(Chat chat);
}
