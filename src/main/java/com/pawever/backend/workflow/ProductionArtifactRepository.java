package com.pawever.backend.workflow;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionArtifactRepository extends JpaRepository<ProductionArtifact, String> {
  List<ProductionArtifact> findByOrderNumberOrderByIdAsc(String number);

  @org.springframework.data.jpa.repository.Query(
      """
select a from ProductionArtifact a left join GoodsSurveyFulfillment o on a.orderNumber=o.orderNumber
where (a.confirmed=false and a.expiresAt <= :now) or o.id is null
   or o.lifecycleOrderStatus in ('CANCELED','EXPIRED') or o.deleteAfter <= :now
order by a.id
""")
  List<ProductionArtifact> purgeCandidates(
      java.time.Instant now, org.springframework.data.domain.Pageable page);
}
