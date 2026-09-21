package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.StudyProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudyProgramRepository extends JpaRepository<StudyProgram, Long> {

    List<StudyProgram> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<StudyProgram> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserId(Long userId);
}