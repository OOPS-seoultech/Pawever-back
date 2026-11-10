package com.pawever.backend.workflow;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
@Entity @Table(name="staff_telegram_links") @Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class StaffTelegramLink {
  @Id private Long accountId;
  @Version private long version;
  @Column(unique=true) private Long telegramUserId;
  @Column(unique=true,length=64) private String codeHash;
  private Instant expiresAt;
  public static StaffTelegramLink create(Long account) {var l=new StaffTelegramLink();l.accountId=account;return l;}
  public void code(String hash,Instant expiry) {codeHash=hash;expiresAt=expiry;}
  public void connect(Long user) {telegramUserId=user;codeHash=null;expiresAt=null;}
}
