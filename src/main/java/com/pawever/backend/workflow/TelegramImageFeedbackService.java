package com.pawever.backend.workflow;

import static com.pawever.backend.admin.entity.PermissionKey.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class TelegramImageFeedbackService {
  private final TelegramImageFeedbackRepository drafts;
  private final ImageGenerationService images;
  private final WorkflowService workflow;
  private final StaffPermissions access;
  private final ImageTelegramClient telegram;
  private final Clock clock;
  private static final Map<String,String> DIRECTIONS=labels("all","전체","front","정면","left","왼쪽","back","뒷면","right","오른쪽");
  private static final Map<String,String> REGIONS=labels("face","얼굴","eyes_nose","눈·코","ears","귀","markings","무늬·색상","tail","꼬리","tail_origin","꼬리 시작점 오류","tail_visibility","꼬리 좌우 중복 노출","tail_consistency","꼬리 방향별 불일치","paws","발·다리","proportions","비율","pose","자세","framing","구도·잘림","other","기타");
  private static Map<String,String> labels(String... entries) {
    var result=new LinkedHashMap<String,String>();for(int i=0;i<entries.length;i+=2)result.put(entries[i],entries[i+1]);return Collections.unmodifiableMap(result);
  }
  private WorkflowException bad(String text) {return new WorkflowException(409,"INVALID_IMAGE_FEEDBACK",text);}
  private Map<String,Object> current(String job) {
    var detail=images.detail(job);
    if(!Boolean.TRUE.equals(detail.get("canReview")))throw bad("이미 처리됐거나 이전 후보입니다. 최신 검수 메시지를 사용해 주세요.");
    return detail;
  }
  private TelegramImageFeedback draft(String session,String chat) {
    var d=drafts.findBySessionId(session).orElseThrow(()->bad("종료된 수정 버튼입니다. 최신 검수 메시지를 사용해 주세요."));
    if(!d.getChatId().equals(chat))throw bad("수정 요청을 시작한 검수방에서 진행해 주세요.");
    access.read(images.detail(d.getJobId()).get("orderNumber").toString());
    return d;
  }
  private void owner(TelegramImageFeedback d) {
    if(!d.getAccountId().equals(access.current().getId()))throw bad("다른 담당자가 수정 작성 중입니다. 작성자가 완료하거나 취소해 주세요.");
  }
  private void editable(TelegramImageFeedback d) {
    owner(d);
    if(!d.getStatus().equals("OPEN")||!d.getExpiresAt().isAfter(clock.instant()))throw bad("종료·만료된 수정 요청입니다. 취소 후 다시 시작해 주세요.");
    current(d.getJobId());
  }
  @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public String begin(String job,String chat,Long message,Long user,String original,String key) {
    access.require(REVIEW_IMAGES);
    var result=workflow.command("telegram-feedback-open:"+job,key,Map.of("chat",chat,"message",message),()->{
      current(job);
      var d=drafts.findById(job).orElseGet(()->TelegramImageFeedback.create(job));
      if("OPEN".equals(d.getStatus())&&d.getExpiresAt().isAfter(clock.instant()))owner(d);
      else d.open(access.current().getId(),user,chat,message,original,clock.instant().plus(Duration.ofHours(24)));
      drafts.saveAndFlush(d);return Map.of("sessionId",d.getSessionId());
    });
    var d=draft(result.get("sessionId").toString(),chat);render(d);return "방향·부위를 선택하거나 메모를 적은 뒤 재생성을 눌러 주세요.";
  }
  @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public String action(String session,String action,String chat,String key) {
    access.require(REVIEW_IMAGES);
    var result=workflow.command("telegram-feedback:"+session,key,Map.of("action",action),()->{
      var d=draft(session,chat);
      if(action.equals("cancel")) {
        if(!d.getAccountId().equals(access.current().getId()))access.require(OVERRIDE_WORKFLOW);
        if(!d.getStatus().equals("OPEN"))throw bad("이미 종료된 수정 요청입니다.");
        d.cancel();drafts.flush();return Map.of("status","CANCELED");
      }
      owner(d);
      if(action.equals("generate")&&d.getStatus().equals("SUBMITTED"))return Map.of("status","SUBMITTED","jobId",d.getSubmittedJobId());
      editable(d);
      if(action.startsWith("d:")||action.startsWith("r:")) {
        boolean direction=action.startsWith("d:");String value=action.substring(2);
        if(!(direction?DIRECTIONS:REGIONS).containsKey(value))throw bad("올바른 수정 항목을 선택해 주세요.");
        d.toggle(direction,value);
      } else if(action.equals("note")) {
        if(d.getPromptMessageId()==null)d.prompt(telegram.feedbackPrompt(chat,d.getTelegramUserId(),access.current().getName(),identity(d)));
      } else if(action.equals("generate")) {
        if(d.getRegions().isBlank()&&d.getNote().isBlank())throw bad("수정 부위를 하나 이상 선택하거나 메모를 입력해 주세요.");
        String note="수정 방향: "+selected(d,d.getDirections(),DIRECTIONS)+". 바로잡을 문제·부위: "+selected(d,d.getRegions(),REGIONS)+". "+d.getNote()+"\n"+tailInstructions(d)+"그 외 특징과 이전에 반영된 수정은 유지하세요.";
        if(note.length()>1000)throw bad("수정 메모를 조금 줄여 주세요.");
        var next=images.regenerate(d.getJobId(),"tg-feedback:"+session,Map.of("direction",d.selected(d.getDirections()).size()==1?d.getDirections():"all","region",d.selected(d.getRegions()).size()==1?(d.getRegions().startsWith("tail_")?"tail":d.getRegions()):"other","note",note));
        d.submit(next.get("id").toString());
      } else throw bad("알 수 없는 수정 버튼입니다.");
      drafts.flush();return Map.of("status",d.getStatus());
    });
    var d=draft(session,chat);
    if(result.get("status").equals("CANCELED")) {
      boolean active=Boolean.TRUE.equals(images.detail(d.getJobId()).get("canReview"));
      telegram.editReview(chat,d.getControlMessageId(),d.getOriginalText(),active?telegram.reviewButtons(d.getJobId()):List.of());
      return "수정 작성을 취소했습니다. 이미지 생성 비용은 발생하지 않았습니다.";
    }
    if(d.getStatus().equals("SUBMITTED")) {
      telegram.editReview(chat,d.getControlMessageId(),d.getOriginalText()+"\n\n재생성 접수 완료 · 새 결과는 이 검수방으로 전송됩니다.",List.of());
      return "재생성을 접수했습니다. 같은 요청은 중복 생성하지 않습니다.";
    }
    render(d);return action.equals("note")?"봇이 보낸 메모 요청 메시지에 답장해 주세요.":"수정 항목을 저장했습니다. 재생성을 눌러야 생성됩니다.";
  }
  public boolean isMemoReply(String chat,Long prompt) {return drafts.findByChatIdAndPromptMessageId(chat,prompt).isPresent();}
  @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public String memo(String chat,Long prompt,Long user,String note,long update,String key) {
    access.require(REVIEW_IMAGES);
    var result=workflow.command("telegram-feedback-memo",key,Map.of("chat",chat,"prompt",prompt,"note",note),()->{
      var d=drafts.findByChatIdAndPromptMessageId(chat,prompt).orElseThrow(()->bad("메모 요청을 찾을 수 없습니다."));
      editable(d);
      if(!d.getTelegramUserId().equals(user))throw bad("수정을 시작한 담당자만 메모를 입력할 수 있습니다.");
      String text=note.trim();if(text.isEmpty()||text.length()>600)throw bad("수정 내용을 텍스트 1~600자로 입력해 주세요.");
      if(update>d.getLastNoteUpdate())d.note(text,update);
      drafts.flush();return Map.of("sessionId",d.getSessionId());
    });
    render(draft(result.get("sessionId").toString(),chat));return "수정 메모를 저장했습니다. 항목·메모 확인 후 재생성을 눌러 주세요.";
  }
  private String tailInstructions(TelegramImageFeedback d) {
    var selected=d.selected(d.getRegions());var text=new StringBuilder();
    if(selected.contains("tail_origin"))text.append("꼬리 뿌리는 낮은 골반 뒤 중앙 한 곳. 등·옆구리의 잘못된 뿌리 흔적 제거. ");
    if(selected.contains("tail_visibility"))text.append("몸 반대편 꼬리는 몸·뒷다리에 가려짐. 좌우에 완전한 꼬리를 복제하거나 투과시키지 말 것. ");
    if(selected.contains("tail_consistency"))text.append("정면·좌우·뒷면 모두 하나의 꼬리 경로·감는 쪽·끝 위치·무늬 고정. 카메라만 회전. ");
    return text.toString();
  }
  private String identity(TelegramImageFeedback d) {
    var detail=images.detail(d.getJobId());return detail.get("petName")+" · "+detail.get("orderNumber")+" · 후보 "+d.getJobId().substring(0,8);
  }
  private String selected(TelegramImageFeedback d,String csv,Map<String,String> labels) {
    return String.join(", ",d.selected(csv).stream().map(labels::get).toList());
  }
  private Map<String,String> button(TelegramImageFeedback d,String text,String action) {return Map.of("text",text,"callback_data","fb:"+d.getSessionId()+":"+action);}
  private void render(TelegramImageFeedback d) {
    var buttons=new ArrayList<List<Map<String,String>>>();
    buttons.add(DIRECTIONS.entrySet().stream().map(e->button(d,(d.selected(d.getDirections()).contains(e.getKey())?"✅ ":"")+e.getValue(),"d:"+e.getKey())).toList());
    var regionButtons=REGIONS.entrySet().stream().filter(e->!e.getKey().startsWith("tail_")).map(e->button(d,(d.selected(d.getRegions()).contains(e.getKey())?"✅ ":"")+e.getValue(),"r:"+e.getKey())).toList();
    for(int i=0;i<regionButtons.size();i+=3)buttons.add(regionButtons.subList(i,Math.min(i+3,regionButtons.size())));
    REGIONS.entrySet().stream().filter(e->e.getKey().startsWith("tail_")).forEach(e->buttons.add(List.of(button(d,(d.selected(d.getRegions()).contains(e.getKey())?"✅ ":"")+e.getValue(),"r:"+e.getKey()))));
    buttons.add(List.of(button(d,"메모 입력·변경","note")));
    buttons.add(List.of(button(d,"재생성 (API 비용 발생)","generate"),button(d,"취소","cancel")));
    telegram.editReview(d.getChatId(),d.getControlMessageId(),d.getOriginalText()+"\n\n수정 작성: "+access.current().getName()+"\n방향: "+selected(d,d.getDirections(),DIRECTIONS)+"\n부위: "+selected(d,d.getRegions(),REGIONS)+"\n메모: "+(d.getNote().isBlank()?"없음":d.getNote())+"\n여러 항목 선택 가능 · 재생성을 누를 때만 API를 호출합니다. 작성은 24시간 동안 유지됩니다.",buttons);
  }
}
