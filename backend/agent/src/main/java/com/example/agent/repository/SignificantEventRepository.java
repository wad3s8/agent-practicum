package com.example.agent.repository;

import com.example.agent.entity.SignificantEvent;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SignificantEventRepository extends CrudRepository<SignificantEvent, String> {
    List<SignificantEvent> findAll();
    List<SignificantEvent> findByTeamKeyIn(List<String> teamKeys);
    void deleteByTeamKeyIn(List<String> teamKeys);
}
