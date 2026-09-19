package com.pawever.backend.workflow;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsCaseAccessGrantRepository extends JpaRepository<AsCaseAccessGrant, Long> {
  List<AsCaseAccessGrant> findByAsCaseIdOrderByIdDesc(Long asCaseId);

  List<AsCaseAccessGrant> findByAsCaseIdAndRecipientIdOrderByIdDesc(Long asCaseId, Long recipientId);
}
