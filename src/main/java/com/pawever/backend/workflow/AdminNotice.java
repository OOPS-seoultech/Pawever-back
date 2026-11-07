package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "admin_notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminNotice {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Version private long version;
  @Column(nullable = false, length = 200) private String title;
  @Column(nullable = false, columnDefinition = "TEXT") private String body;
  @Column(unique = true, length = 60) private String guideKey;
  private Long authorId;
  @Column(nullable = false) private Instant createdAt;
  @Column(nullable = false) private Instant updatedAt;

  public static AdminNotice create(String title, String body, Long author, Instant at) {
    var notice = new AdminNotice();
    notice.title = title; notice.body = body; notice.authorId = author;
    notice.createdAt = at; notice.updatedAt = at;
    return notice;
  }

  public void edit(String title, String body, Instant at) {
    this.title = title; this.body = body; updatedAt = at;
  }
}
