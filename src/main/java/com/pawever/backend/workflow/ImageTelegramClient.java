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
    var result=call("review","sendMessage",Map.of("chat_id",chat("review"),"text",number+" · 4면도 이미지 검수\n최신 후보를 승인하거나 수정할 부분을 입력해 주세요.",
        "reply_markup",Map.of("inline_keyboard",List.of(List.of(Map.of("text","검수 완료","callback_data","approve:"+id),Map.of("text","수정 요청","url",url)),List.of(Map.of("text","웹에서 비교","url",url))))));
    return result.path("message_id").asLong();
  }
  void answer(String kind,String id,String text){try{call(kind,"answerCallbackQuery",Map.of("callback_query_id",id,"text",text,"show_alert",true));}catch(RuntimeException ignored){}}
  void clear(String kind,String chat,Long message){if(message==null)return;try{call(kind,"editMessageReplyMarkup",Map.of("chat_id",chat,"message_id",message,"reply_markup",Map.of("inline_keyboard",List.of())));}catch(RuntimeException ignored){}}
  void text(String kind,String chat,String text){call(kind,"sendMessage",Map.of("chat_id",chat,"text",text));}
}
