package com.pawever.backend.workflow;

import static com.pawever.backend.admin.entity.PermissionKey.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.pawever.backend.admin.security.AdminPrincipal;
import com.pawever.backend.admin.repository.AdminAccountRepository;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ImageTelegramService {
  private final StaffTelegramLinkRepository links;
  private final AdminAccountRepository accounts;
  private final StaffPermissions access;
  private final WorkflowService workflow;
  private final ImageGenerationService images;
  private final ImageTelegramClient telegram;
  private final TelegramImageFeedbackService feedback;
  private final Clock clock;
  private final org.springframework.transaction.PlatformTransactionManager transactions;

  public List<Map<String,Object>> rooms() {
    access.require(MANAGE_OPERATION_SETTINGS);
    return List.of(telegram.room("payment"),telegram.room("review"));
  }
  @Transactional
  public Map<String,Object> sendTest(String kind,String key,Map<String,Object> body) {
    access.require(MANAGE_OPERATION_SETTINGS);
    if(!Set.of("payment","review").contains(kind))throw new WorkflowException(400,"INVALID_INPUT","테스트 방을 확인해 주세요.");
    return workflow.command("telegram-test:"+kind,key,body,()->telegram.sendTest(kind,access.current().getName()));
  }
  @Transactional(readOnly=true)
  public Map<String,Object> linkStatus(){return Map.of("connected",links.findById(access.current().getId()).map(l->l.getTelegramUserId()!=null).orElse(false));}
  @Transactional
  public Map<String,Object> createLink(){
    var actor=access.current();String code=UUID.randomUUID().toString().replace("-","");
    var link=links.findById(actor.getId()).orElseGet(()->StaffTelegramLink.create(actor.getId()));
    if(link.getTelegramUserId()!=null)throw new WorkflowException(400,"ALREADY_LINKED","이미 연결된 계정입니다.");
    String name=telegram.reviewBotName();if(name.isBlank())throw new WorkflowException(502,"TELEGRAM_UNAVAILABLE","봇 이름을 확인하지 못했습니다.");
    var expiry=clock.instant().plus(Duration.ofMinutes(10));link.code(ImageTelegramClient.hash(code),expiry);links.saveAndFlush(link);
    return Map.of("url","https://t.me/"+name+"?start="+code,"expiresAt",expiry);
  }
  @Transactional
  public Map<String,Object> unlink(){links.deleteById(access.current().getId());return Map.of("connected",false);}
  @Transactional
  public boolean connect(String code,Long telegramUser){
    return new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status -> {
    var link=links.findByCodeHash(ImageTelegramClient.hash(code)).orElse(null);
    if(link==null||link.getExpiresAt()==null||!link.getExpiresAt().isAfter(clock.instant())||accounts.findById(link.getAccountId()).filter(a->a.canSignIn()).isEmpty())return false;
    var existing=links.findByTelegramUserId(telegramUser).filter(l->!l.getAccountId().equals(link.getAccountId()));
    if(existing.isPresent()) {
      if(accounts.findById(existing.get().getAccountId()).filter(a->a.canSignIn()).isPresent())return false;
      links.delete(existing.get());links.flush();
    }
    link.connect(telegramUser);links.flush();return true;
    });
  }
  public void update(String kind,JsonNode update){
    var message=update.path("message");
    if(kind.equals("review")&&message.path("chat").path("type").asText().equals("private")&&message.path("from").path("id").asLong()==message.path("chat").path("id").asLong()){
      String text=message.path("text").asText();if(text.startsWith("/start ")){
        boolean accepted=connect(text.substring(7).trim(),message.path("from").path("id").asLong());
        telegram.text(kind,message.path("chat").path("id").asText(),accepted?"PAW-EVER 관리자 계정과 연결했습니다. 기존 주문방·검수방의 버튼을 사용할 수 있습니다.":"연결 코드가 만료됐거나 사용할 수 없습니다. 관리자 사이트에서 새 링크를 발급해 주세요.");
        return;
      }
    }
    String key="tg:"+kind+":"+update.path("update_id").asLong();
    if(kind.equals("review")&&message.has("reply_to_message")) {
      String chat=message.path("chat").path("id").asText();Long prompt=message.path("reply_to_message").path("message_id").asLong();
      if(!chat.equals(telegram.chat(kind))||!feedback.isMemoReply(chat,prompt))return;
      var previous=SecurityContextHolder.getContext();String result;
      try {
        long user=message.path("from").path("id").asLong();authenticate(user);
        result=feedback.memo(chat,prompt,user,message.path("text").asText(),update.path("update_id").asLong(),key);
      }catch(WorkflowException e){result=e.getMessage();}
      catch(RuntimeException e){result="메모 저장 결과를 확인하지 못했습니다. 다시 시도해 주세요.";}
      finally{SecurityContextHolder.setContext(previous);}
      telegram.feedbackReply(chat,message.path("message_id").asLong(),result);return;
    }
    var callback=update.path("callback_query");if(callback.isMissingNode())return;
    String callbackId=callback.path("id").asText();
    String chat=callback.path("message").path("chat").path("id").asText();
    String result="처리할 수 없는 버튼입니다.";
    var previous=SecurityContextHolder.getContext();
    try{
      if(!chat.equals(telegram.chat(kind)))throw new WorkflowException(403,"FORBIDDEN","등록된 운영 채팅방에서만 사용할 수 있습니다.");
      long userId=callback.path("from").path("id").asLong();
      authenticate(userId);
      String data=callback.path("data").asText();
      if(kind.equals("review")&&data.startsWith("changes:")) {
        result=feedback.begin(data.substring(8),chat,callback.path("message").path("message_id").asLong(),userId,callback.path("message").path("text").asText(),key);return;
      }
      if(kind.equals("review")&&data.startsWith("fb:")) {
        String[] parts=data.split(":",3);if(parts.length!=3)throw new WorkflowException(400,"INVALID_INPUT","수정 버튼을 확인해 주세요.");
        result=feedback.action(parts[1],parts[2],chat,key);return;
      }
      if(data.equals("test:"+kind)) {
        access.require(kind.equals("payment")?CONFIRM_PAYMENT:REVIEW_IMAGES);
        result="연결 테스트 성공 · 실제 주문 변경이나 유료 이미지 생성은 없습니다.";
        return;
      }
      if(kind.equals("payment")&&data.startsWith("pay:")){
        access.require(CONFIRM_PAYMENT);String number=data.substring(4);var row=workflow.detail(number,0);
        if(row.get("paymentStatus").equals("CONFIRMED")){result="이미 입금 확인된 주문입니다. 중복 생성하지 않았습니다.";}
        else {workflow.confirm(number,key,Map.of("version",row.get("version"),"amount",row.get("expectedAmount"),"memo","Telegram에서 실제 입금 확인","petIndex",0));result="입금 확인 완료 · 아이별 4면도 생성을 등록했습니다.";}
        telegram.confirmedOrderLink(chat,callback.path("message").path("message_id").asLong(),number);return;
      }else if(kind.equals("review")&&data.startsWith("approve:")){
        images.approve(data.substring(8),key,Map.of());result="4면도 검수를 완료했습니다. 웹사이트에 반영했습니다.";
      }
      telegram.clear(kind,chat,callback.path("message").path("message_id").asLong());
    }catch(WorkflowException e){result=e.getMessage();}
    catch(RuntimeException e){result="처리 결과를 확인하지 못했습니다. 관리자 사이트에서 확인해 주세요.";}
    finally{SecurityContextHolder.setContext(previous);telegram.answer(kind,callbackId,result);}
  }
  private void authenticate(long user) {
    var link=links.findByTelegramUserId(user).orElseThrow(()->new WorkflowException(403,"TELEGRAM_NOT_LINKED","관리자 사이트의 텔레그램 연결을 먼저 진행해 주세요."));
    var account=accounts.findById(link.getAccountId()).filter(a->a.canSignIn()).orElseThrow(()->new WorkflowException(403,"FORBIDDEN","사용 중지된 계정입니다."));
    var context=SecurityContextHolder.createEmptyContext();
    context.setAuthentication(new UsernamePasswordAuthenticationToken(new AdminPrincipal(account.getId(),account.getRole()),null,List.of(new SimpleGrantedAuthority(account.getRole().authority()))));
    SecurityContextHolder.setContext(context);
  }
}
