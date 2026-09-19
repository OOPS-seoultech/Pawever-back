package com.pawever.backend.workflow;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsCaseAccessGrantAssetRepository extends JpaRepository<AsCaseAccessGrantAsset, Long> {
  List<AsCaseAccessGrantAsset> findByGrantIdOrderByIdAsc(Long grantId);

  List<AsCaseAccessGrantAsset> findByGrantIdAndAssetId(Long grantId, String assetId);
}
