package com.axedu.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.axedu.entity.ChatSetting;

public interface ChatSettingRepository extends JpaRepository<ChatSetting, String> {
}
