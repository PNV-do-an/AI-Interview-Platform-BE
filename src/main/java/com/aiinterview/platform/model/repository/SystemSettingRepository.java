package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {

    java.util.Optional<SystemSetting> findBySettingKey(String settingKey);

    List<SystemSetting> findBySettingKeyIn(List<String> keys);
}
