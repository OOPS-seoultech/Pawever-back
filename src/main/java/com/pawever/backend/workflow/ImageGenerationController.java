package com.pawever.backend.workflow;
import com.pawever.backend.global.common.ApiResponse;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class ImageGenerationController {
  private final ImageGenerationService images;
  private final ImageTelegramService telegram;
  private final ImageTelegramClient client;
  @GetMapping("/api/admin/orders/{number}/image-generation")
  public Object list(@PathVariable String number,@RequestParam(defaultValue="0") int petIndex){return ApiResponse.ok(images.list(number,petIndex));}
  @GetMapping("/api/production/image-jobs/{id}")
  public Object detail(@PathVariable String id){return ApiResponse.ok(images.detail(id));}
  @PostMapping("/api/production/image-jobs/{id}/approve")
  public Object approve(@PathVariable String id,@RequestHeader("Idempotency-Key")String key,@RequestBody Map<String,Object> body){return ApiResponse.ok(images.approve(id,key,body));}
  @PostMapping("/api/production/image-jobs/{id}/regenerate")
  public Object regenerate(@PathVariable String id,@RequestHeader("Idempotency-Key")String key,@RequestBody Map<String,Object> body){return ApiResponse.ok(images.regenerate(id,key,body));}
  @PostMapping("/api/production/image-jobs/{id}/retry-save")
  public Object retrySave(@PathVariable String id,@RequestHeader("Idempotency-Key")String key,@RequestBody Map<String,Object> body){return ApiResponse.ok(images.retrySave(id,key,body));}
  @PostMapping("/api/production/image-jobs/{id}/notify")
  public Object notifyReview(@PathVariable String id,@RequestHeader("Idempotency-Key")String key,@RequestBody Map<String,Object> body){return ApiResponse.ok(images.resend(id,key,body));}
  @GetMapping("/api/admin/image-generation/status")
  public Object status(@RequestParam(defaultValue="false")boolean probe){return ApiResponse.ok(images.status(probe));}
  @GetMapping("/api/admin/telegram/link")
  public Object link(){return ApiResponse.ok(telegram.linkStatus());}
  @PostMapping("/api/admin/telegram/link")
  public Object createLink(){return ApiResponse.ok(telegram.createLink());}
  @DeleteMapping("/api/admin/telegram/link")
  public Object unlink(){return ApiResponse.ok(telegram.unlink());}
  @PostMapping("/api/telegram/image-workflow/{kind}")
  public Object webhook(@PathVariable String kind,@RequestHeader(value="X-Telegram-Bot-Api-Secret-Token",required=false)String secret,@RequestBody Map<String,Object> body){
    if(!client.authorized(kind,secret))throw new WorkflowException(403,"FORBIDDEN","웹훅 인증이 필요합니다.");
    telegram.update(kind,new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(body));return Map.of("ok",true);
  }
}
