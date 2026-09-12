package com.pawever.backend.workflow;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowCommandRepository extends JpaRepository<WorkflowCommand, Long> {
  Optional<WorkflowCommand> findByActorIdAndCommandKey(Long actor, String key);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      """
delete from WorkflowCommand c where c.orderNumber is not null and not exists
  (select o.id from GoodsSurveyFulfillment o where o.orderNumber=c.orderNumber
    and o.lifecycleOrderStatus not in ('CANCELED','EXPIRED') and (o.deleteAfter is null or o.deleteAfter > :now))
""")
  int purgeOrderSnapshots(java.time.Instant now);
}
