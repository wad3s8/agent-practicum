package com.example.agent.repository;

import com.example.agent.entity.User;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface UserRepository extends CrudRepository<User, Long> {
    Optional<User> findByLogin(String login);
    boolean existsByLogin(String login);
}
