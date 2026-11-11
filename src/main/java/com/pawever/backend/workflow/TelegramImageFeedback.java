package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity @Table(name="telegram_image_feedback") @Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class TelegramImageFeedback {
  @Id @Column(length=36) private String jobId;
  @Version private long version;
  @Column(nullable=false,unique=true,length=36) private String sessionId;
  private Long accountId;
  private Long telegramUserId;
  private String chatId;
  private Long controlMessageId;
  @Column(length=4096) private String originalText;
  @Column(length=80) private String directions;
  @Column(length=180) private String regions;
  @Column(length=800) private String note;
  private Long promptMessageId;
  private long lastNoteUpdate;
  @Column(length=20) private String status;
  @Column(length=36) private String submittedJobId;
  private Instant expiresAt;

  static TelegramImageFeedback create(String jobId) {var d=new TelegramImageFeedback();d.jobId=jobId;return d;}
  void open(Long actor,Long user,String chat,Long message,String text,Instant expiry) {
    sessionId=UUID.randomUUID().toString();accountId=actor;telegramUserId=user;chatId=chat;
    controlMessageId=message;originalText=text;directions="all";regions="";note="";
    promptMessageId=null;lastNoteUpdate=0;submittedJobId=null;status="OPEN";expiresAt=expiry;
  }
  Set<String> selected(String value) {return new LinkedHashSet<>(value==null||value.isBlank()?List.of():List.of(value.split(",")));}
  void toggle(boolean direction,String value) {
    var selected=selected(direction?directions:regions);
    if(direction&&value.equals("all"))selected.clear();
    else if(direction)selected.remove("all");
    if(!selected.remove(value))selected.add(value);
    if(direction&&selected.isEmpty())selected.add("all");
    if(direction)directions=String.join(",",selected);else regions=String.join(",",selected);
  }
  void prompt(Long message) {promptMessageId=message;}
  void note(String value,long update) {note=value;lastNoteUpdate=update;}
  void cancel() {status="CANCELED";}
  void submit(String id) {status="SUBMITTED";submittedJobId=id;}
}
