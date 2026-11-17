package com.wuxiaozhi.repository;

import com.wuxiaozhi.entity.ExpertConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExpertConversationRepository extends JpaRepository<ExpertConversation, String> {
    List<ExpertConversation> findByUserIdAndExpertIdOrderByUpdatedAtDesc(Long userId, String expertId);
}
