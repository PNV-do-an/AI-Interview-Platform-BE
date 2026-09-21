package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.StudyTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudyTopicRepository extends JpaRepository<StudyTopic, Long> {

    List<StudyTopic> findByProgramIdOrderByTopicOrderAsc(Long programId);
}