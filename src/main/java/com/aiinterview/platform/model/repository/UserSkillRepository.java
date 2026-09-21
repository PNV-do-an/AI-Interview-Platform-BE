package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.UserSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {

    List<UserSkill> findByCvId(Long cvId);

    void deleteByCvId(Long cvId);
}