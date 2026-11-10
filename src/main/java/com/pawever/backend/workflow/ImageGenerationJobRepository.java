package com.pawever.backend.workflow;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ImageGenerationJobRepository extends JpaRepository<ImageGenerationJob,String> {
  Optional<ImageGenerationJob> findByRequestKey(String key);
  List<ImageGenerationJob> findByOrderNumberAndPetIndexOrderByCreatedAtAsc(String number,int pet);
  Optional<ImageGenerationJob> findFirstByOrderNumberAndPetIndexOrderByCreatedAtDesc(String number,int pet);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<ImageGenerationJob> findFirstByStatusOrderByCreatedAtAsc(String status);
  List<ImageGenerationJob> findByStatusAndStartedAtBefore(String status,Instant at);
}
