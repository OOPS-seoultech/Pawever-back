package com.pawever.backend.workflow;

import com.fasterxml.jackson.databind.*;
import com.pawever.backend.goodssurvey.event.GoodsOrderSubmittedEvent;
import com.pawever.backend.notification.telegram.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component @RequiredArgsConstructor
public class ImageTelegramClient {
  private final RestTemplate restTemplate;
  private final TelegramProperties payment;
  private final TelegramOpsProperties ops;
  private final TelegramOpsClient albums;
  private final ImageGenerationProperties config;
  private final Map<String,String> hooks=new ConcurrentHashMap<>();
  private final ObjectMapper json=new ObjectMapper();
  private String token(String kind){return kind.equals("payment")?payment.getBotToken():ops.getBotToken();}
  String chat(String kind){return kind.equals("payment")?payment.getChatId():ops.getChatId();}
  private String base(String kind){return kind.equals("payment")?payment.getBaseUrl():ops.getBaseUrl();}
  static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
  String secret(String kind){return hash("pawever-image-workflow-v1:"+token(kind));}
  boolean authorized(String kind,String header){return Set.of("payment","review").contains(kind)&&config.ready()&&!token(kind).isBlank()&&header!=null&&MessageDigest.isEqual(secret(kind).getBytes(StandardCharsets.UTF_8),header.getBytes(StandardCharsets.UTF_8));}
  JsonNode call(String kind,String method,Map<String,Object> body){
    if(token(kind).isBlank())throw new WorkflowException(503,"TELEGRAM_NOT_CONFIGURED","텔레그램 봇 설정이 없습니다.");
    try{
      var response=restTemplate.postForEntity(base(kind)+"/bot"+token(kind)+"/"+method,body,String.class);
      var result=json.readTree(response.getBody());
      if(!result.path("ok").asBoolean())throw new WorkflowException(502,"TELEGRAM_REJECTED","텔레그램 요청이 거절됐습니다.");
      return result.path("result");
    }catch(RestClientException|java.io.IOException e){throw new WorkflowException(502,"TELEGRAM_UNAVAILABLE","텔레그램 연결을 확인해 주세요.");}
  }
  public Map<String,Object> room(String kind) {
    var room=call(kind,"getChat",Map.of("chat_id",chat(kind)));
    String title=room.path("title").asText();
    // Telegram labels a private bot conversation with the bot name, not the recipient name.
    if(room.path("type").asText().equals("private"))title=call(kind,"getMe",Map.of()).path("first_name").asText();
    return Map.of("kind",kind,"title",title,"type",room.path("type").asText());
  }
  public Map<String,Object> sendTest(String kind,String actor) {
    var room=room(kind);
    String title=kind.equals("payment")?"입금 확인·4면도 생성":"검수 완료";
    String second=kind.equals("payment")?"관리자에서 보기":"수정 요청";
    String url=config.getPublicWebUrl()+(kind.equals("payment")?"/admin/operations":"/admin/telegram?test=review");
    String text="[PAW-EVER 연결 테스트 · 실제 주문 아님]\n대상 방: "+room.get("title")+"\n요청자: "+actor+
        "\n반려동물: 테스트 아이 / 반려인: 테스트 보호자\n첫 버튼은 계정·권한 연결만 확인합니다. 두 번째 버튼은 관리자 안내 화면을 엽니다.\n실제 입금·제작 단계 변경, 고객 문자, 유료 이미지 생성은 없습니다.";
    var result=call(kind,"sendMessage",Map.of("chat_id",chat(kind),"text",text,
        "reply_markup",Map.of("inline_keyboard",List.of(List.of(Map.of("text",title,"callback_data","test:"+kind),Map.of("text",second,"url",url))))));
    return Map.of("kind",kind,"title",room.get("title"),"messageId",result.path("message_id").asLong(),"accepted",true);
  }
  public String reviewBotName(){return call("review","getMe",Map.of()).path("username").asText();}
  public Map<String,String> webhookStatus(){return Map.copyOf(hooks);}
  @EventListener(ApplicationReadyEvent.class)
  public void register(){
    if(!config.ready()||!config.isRegisterWebhooks())return;
    for(String kind:List.of("payment","review")) {
      try{
        String url=config.getPublicApiUrl()+"/api/telegram/image-workflow/"+kind;
        String existing=call(kind,"getWebhookInfo",Map.of()).path("url").asText();
        if(!existing.isBlank()&&!existing.equals(url)){hooks.put(kind,"CONFLICT_EXISTING_WEBHOOK");continue;}
        call(kind,"setWebhook",Map.of("url",url,"secret_token",secret(kind),"allowed_updates",List.of("message","callback_query"),"drop_pending_updates",false));
        hooks.put(kind,"CONNECTED");
      }catch(RuntimeException e){hooks.put(kind,"FAILED");}
    }
  }
  public boolean order(GoodsOrderSubmittedEvent event){
    try{
      call("payment","sendMessage",Map.of("chat_id",chat("payment"),"text",TelegramMessage.goodsOrderSubmitted(event),"parse_mode","HTML","disable_web_page_preview",true,
          "reply_markup",Map.of("inline_keyboard",List.of(List.of(Map.of("text","입금 확인·4면도 생성","callback_data","pay:"+event.orderNumber())),List.of(Map.of("text","관리자에서 보기","url",config.getPublicWebUrl()+"/admin/orders/"+event.orderNumber()))))));return true;
    }catch(RuntimeException e){return false;}
  }
  public Long review(String id,String number,int pet,ModelReviewMedia.Album album){
    if(!albums.sendAlbum(album))return null;
    String url=config.getPublicWebUrl()+"/admin/image-review/"+id;
    var result=call("review","sendMessage",Map.of("chat_id",chat("review"),"text",album.title()+"\n후보: "+id.substring(0,8)+"\n최신 후보를 승인하거나 수정할 부분을 입력해 주세요.",
        "reply_markup",Map.of("inline_keyboard",List.of(List.of(Map.of("text","검수 완료","callback_data","approve:"+id),Map.of("text","수정 요청","url",url)),List.of(Map.of("text","웹에서 비교","url",url))))));
    return result.path("message_id").asLong();
  }
  void answer(String kind,String id,String text){try{call(kind,"answerCallbackQuery",Map.of("callback_query_id",id,"text",text,"show_alert",true));}catch(RuntimeException ignored){}}
  void clear(String kind,String chat,Long message){if(message==null)return;try{call(kind,"editMessageReplyMarkup",Map.of("chat_id",chat,"message_id",message,"reply_markup",Map.of("inline_keyboard",List.of())));}catch(RuntimeException ignored){}}
  void text(String kind,String chat,String text){call(kind,"sendMessage",Map.of("chat_id",chat,"text",text));}
}
