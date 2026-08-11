package com.wuxiaozhi.repository;

import com.wuxiaozhi.entity.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {
    List<AiConversation> findByUserIdAndToolCodeOrderByUpdatedAtDesc(Long userId, String toolCode);

    Optional<AiConversation> findByIdAndUserId(Long id, Long userId);
}
