package com.wuxiaozhi.repository;

import com.wuxiaozhi.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
    List<ChatMessage> findTop3BySessionIdAndRoleOrderByCreatedAtAsc(Long sessionId, String role);
}
