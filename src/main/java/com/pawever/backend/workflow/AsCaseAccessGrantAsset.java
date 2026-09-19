package com.pawever.backend.workflow;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "as_case_access_grant_assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AsCaseAccessGrantAsset {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long grantId;

  @Column(nullable = false, length = 30)
  private String assetType;

  @Column(nullable = false, length = 80)
  private String assetId;

  public static AsCaseAccessGrantAsset of(Long grantId, String assetType, String assetId) {
    var value = new AsCaseAccessGrantAsset();
    value.grantId = grantId;
    value.assetType = assetType;
    value.assetId = assetId;
    return value;
  }
}
