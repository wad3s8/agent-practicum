package com.example.agent.repository;

import com.example.agent.entity.TaskComplexity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskComplexityRepository extends CrudRepository<TaskComplexity, String> {
    List<TaskComplexity> findByJiraIssueKeyIn(List<String> keys);
}
