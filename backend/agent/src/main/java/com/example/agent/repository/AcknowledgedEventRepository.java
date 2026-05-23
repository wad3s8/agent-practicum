package com.example.agent.repository;

import com.example.agent.entity.AcknowledgedEvent;
import com.example.agent.entity.User;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AcknowledgedEventRepository extends CrudRepository<AcknowledgedEvent, Long> {
    List<AcknowledgedEvent> findByUser(User user);
    Optional<AcknowledgedEvent> findByUserAndJiraIssueKey(User user, String jiraIssueKey);
}
