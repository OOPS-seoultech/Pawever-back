package com.pawever.backend.workflow;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramImageFeedbackRepository extends JpaRepository<TelegramImageFeedback,String> {
  Optional<TelegramImageFeedback> findBySessionId(String sessionId);
  Optional<TelegramImageFeedback> findByChatIdAndPromptMessageId(String chatId,Long promptMessageId);
}
