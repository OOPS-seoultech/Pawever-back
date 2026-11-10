package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity @Table(name = "image_generation_jobs") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageGenerationJob {
  @Id private String id;
  @Version private long version;
  @Column(nullable=false,length=20) private String orderNumber;
  private int petIndex;
  private Long taskId;
  private Long requestedBy;
  @Column(nullable=false,unique=true,length=160) private String requestKey;
  @Column(length=36) private String previousId;
  @Column(nullable=false,length=30) private String status;
  @Column(nullable=false,length=80) private String promptVersion;
  @Column(length=2000) private String feedback;
  @Column(length=200) private String errorCode;
  @Column(length=500) private String sheetKey;
  @Column(length=180) private String artifactIds;
  @Column(columnDefinition="LONGTEXT") private String usageJson;
  @Column(length=150) private String requestId;
  private Instant createdAt;
  private Instant startedAt;
  private Instant finishedAt;
  private Long reviewedBy;
  private Instant reviewedAt;
  @Column(length=30) private String telegramStatus;
  private Long controlMessageId;

  public static ImageGenerationJob create(String number,int pet,Long task,Long actor,String key,String previous,String feedback,Instant at) {
    var j=new ImageGenerationJob(); j.id=UUID.randomUUID().toString(); j.orderNumber=number;
    j.petIndex=pet;j.taskId=task;j.requestedBy=actor;j.requestKey=key;j.previousId=previous;
    j.feedback=feedback;j.status="QUEUED";j.promptVersion="pet-four-view-api-direct-v1";j.createdAt=at;
    j.telegramStatus="NOT_SENT";return j;
  }
  public void start(Instant at) { status="RUNNING";startedAt=at; }
  public void finish(String sheet,String ids,String usage,String request,Instant at) {
    sheetKey=sheet;artifactIds=ids;usageJson=usage;requestId=request;finishedAt=at;status="REVIEW";
  }
  public void retainSheet(String key,String usage,String request) {sheetKey=key;usageJson=usage;requestId=request;}
  public void requeueSave() {status="QUEUED";errorCode=null;}
  public void fail(String code,Instant at) { status="FAILED";errorCode=code;finishedAt=at; }
  public void unknown(Instant at) { status="UNKNOWN";errorCode="RESULT_UNKNOWN";finishedAt=at; }
  public void supersede() { if (!status.equals("APPROVED")) status="SUPERSEDED"; }
  public void approve(Long actor,Instant at) { status="APPROVED";reviewedBy=actor;reviewedAt=at; }
  public void notified(boolean accepted,Long messageId) { telegramStatus=accepted?"ACCEPTED":"FAILED";controlMessageId=messageId; }
}
