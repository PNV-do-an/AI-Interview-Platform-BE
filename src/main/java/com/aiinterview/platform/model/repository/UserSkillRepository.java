package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.UserSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {

    List<UserSkill> findByCvId(Long cvId);

    @Modifying
    @Query("delete from UserSkill s where s.cv.id = :cvId")
    void deleteByCvId(@Param("cvId") Long cvId);
}