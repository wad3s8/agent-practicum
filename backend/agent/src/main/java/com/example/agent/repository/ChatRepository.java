package com.example.agent.repository;

import com.example.agent.entity.Chat;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatRepository extends CrudRepository<Chat, Long> {
}
