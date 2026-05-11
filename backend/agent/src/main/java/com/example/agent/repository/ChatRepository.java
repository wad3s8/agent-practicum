package com.example.agent.repository;

import com.example.agent.entity.Chat;
import com.example.agent.entity.User;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRepository extends CrudRepository<Chat, Long> {
    List<Chat> findByUserOrderByPinnedDescCreatedAtDesc(User user);
    Optional<Chat> findByIdAndUser(Long id, User user);
}
