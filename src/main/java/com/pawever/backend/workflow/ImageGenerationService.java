package com.pawever.backend.workflow;

import static com.pawever.backend.admin.entity.PermissionKey.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawever.backend.admin.repository.AdminAccountRepository;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhotoStatus;
import com.pawever.backend.goodssurvey.repository.*;
import com.pawever.backend.goodssurvey.service.GoodsSurveyPhotoStorage;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service @RequiredArgsConstructor
public class ImageGenerationService {
  private final ImageGenerationJobRepository jobs;
  private final ProductionTaskRepository tasks;
  private final ProductionArtifactRepository artifacts;
  private final GoodsSurveyFulfillmentRepository orders;
  private final GoodsSurveyPhotoRepository photos;
  private final GoodsOrderPetRepository pets;
  private final GoodsSurveyPhotoStorage storage;
  private final WorkflowService workflow;
  private final StaffPermissions access;
  private final AdminAccountRepository accounts;
  private final OpenAiImageClient api;
  private final ImageGenerationProperties config;
  private final ImageTelegramClient telegram;
  private final PlatformTransactionManager transactions;
  private final Clock clock;
  private static final List<String> VIEWS=List.of("front","left","back","right");
  private <T>T tx(Supplier<T> action){return new TransactionTemplate(transactions).execute(s->action.get());}
  private WorkflowException bad(String text){return new WorkflowException(400,"INVALID_IMAGE_ACTION",text);}
  private ImageGenerationJob job(String id){return jobs.findById(id).orElseThrow(()->new WorkflowException(404,"NOT_FOUND","이미지 작업을 찾을 수 없습니다."));}
  private boolean current(ImageGenerationJob j){return jobs.findFirstByOrderNumberAndPetIndexOrderByCreatedAtDesc(j.getOrderNumber(),j.getPetIndex()).map(x->x.getId().equals(j.getId())).orElse(false);}
  private void editable(ImageGenerationJob j){
    var o=orders.findByOrderNumber(j.getOrderNumber()).orElseThrow();
    var t=workflow.currentTask(j.getOrderNumber(),j.getPetIndex());
    if(!workflow.active(o)||!workflow.paid(o)||t==null||!t.getId().equals(j.getTaskId())||t.getStage()!=ProductionStage.MODELING||!t.getStatus().equals("WAITING"))
      throw bad("현재 모델링 시작 전인 아이만 이미지 검수·수정을 진행할 수 있습니다.");
  }
  @Transactional(readOnly=true)
  public List<Map<String,Object>> list(String number,int pet){
    access.read(number);access.require(VIEW_CUSTOMER_PHOTOS);access.require(VIEW_PRODUCTION_FILES);
    return jobs.findByOrderNumberAndPetIndexOrderByCreatedAtAsc(number,pet).stream().map(this::view).toList();
  }
  @Transactional(readOnly=true)
  public Map<String,Object> detail(String id){var j=job(id);access.read(j.getOrderNumber());access.require(VIEW_CUSTOMER_PHOTOS);access.require(VIEW_PRODUCTION_FILES);return view(j);}
  private Map<String,Object> view(ImageGenerationJob j){
    var result=new LinkedHashMap<String,Object>();
    result.put("id",j.getId());result.put("version",j.getVersion());result.put("orderNumber",j.getOrderNumber());result.put("petIndex",j.getPetIndex());
    result.put("petName",petName(j));result.put("status",j.getStatus());result.put("promptVersion",j.getPromptVersion());
    result.put("feedback",j.getFeedback());result.put("errorCode",j.getErrorCode());result.put("createdAt",j.getCreatedAt());
    result.put("telegramStatus",j.getTelegramStatus());result.put("current",current(j));
    result.put("requestedBy",accountName(j.getRequestedBy()));result.put("reviewedBy",accountName(j.getReviewedBy()));result.put("reviewedAt",j.getReviewedAt());
    boolean canEdit;try{editable(j);canEdit=current(j);}catch(RuntimeException e){canEdit=false;}
    result.put("canRegenerate",access.has(REVIEW_IMAGES)&&canEdit&&Set.of("REVIEW","APPROVED","FAILED","UNKNOWN").contains(j.getStatus()));
    result.put("canRetrySave",access.has(REVIEW_IMAGES)&&canEdit&&j.getStatus().equals("FAILED")&&j.getSheetKey()!=null);
    var files=new ArrayList<Map<String,Object>>();
    if(j.getArtifactIds()!=null) for(var id:j.getArtifactIds().split(",")) {
      var a=artifacts.findById(id).orElse(null);if(a==null)continue;var expires=clock.instant().plusSeconds(300);
      var link=storage.presignDownload(a.getObjectKey(),Duration.ofMinutes(5),expires);
      files.add(Map.of("id",id,"url",link.url(),"expiresAt",expires,"fileName",a.getFileName(),"label",a.getFileName()));
    }
    result.put("canReview",access.has(REVIEW_IMAGES)&&canEdit&&j.getStatus().equals("REVIEW")&&files.size()==4);
    result.put("images",files);return result;
  }
  private String accountName(Long id){return id==null?null:accounts.findById(id).map(a->a.getName()).orElse("삭제된 담당자");}
  private String petName(ImageGenerationJob j){return pets.findByOrderNumberOrderByPetIndexAsc(j.getOrderNumber()).stream().filter(p->p.getPetIndex()==j.getPetIndex()).map(p->p.getPetName()).findFirst().orElse("반려동물");}
  @Transactional
  public Map<String,Object> regenerate(String id,String key,Map<String,Object> body){
    access.require(REVIEW_IMAGES);var initial=job(id);access.read(initial.getOrderNumber());
    return workflow.command("image-regenerate:"+id,key,body,()->{
      workflow.locked(initial.getOrderNumber());var j=job(id);editable(j);if(!current(j)||!Set.of("REVIEW","APPROVED","FAILED","UNKNOWN").contains(j.getStatus()))throw bad("최신 결과에서 수정 요청해 주세요.");
      if(!config.ready())throw bad("이미지 API 설정을 확인해 주세요.");
      String direction=Objects.toString(body.get("direction"),"");String region=Objects.toString(body.get("region"),"");String note=Objects.toString(body.get("note"),"").trim();
      if(!Set.of("all","front","left","back","right").contains(direction)||!Set.of("face","eyes_nose","ears","markings","tail","paws","proportions","pose","framing","other").contains(region)||note.isBlank()||note.length()>1000)
        throw bad("방향·수정 부위를 선택하고 수정 내용을 1~1000자로 적어 주세요.");
      String feedback="View: "+direction+"; Region: "+region+"; Correction: "+note;
      var next=ImageGenerationJob.create(j.getOrderNumber(),j.getPetIndex(),j.getTaskId(),access.current().getId(),"manual:"+access.current().getId()+":"+key,j.getSheetKey()==null?j.getPreviousId():j.getId(),feedback,clock.instant());
      j.supersede();jobs.saveAndFlush(next);workflow.audit(j.getOrderNumber(),"REGENERATE_IMAGES",j.getId(),next.getId(),"아이 "+(j.getPetIndex()+1));
      return view(next);
    });
  }
  @Transactional
  public Map<String,Object> approve(String id,String key,Map<String,Object> body){
    access.require(REVIEW_IMAGES);var initial=job(id);access.read(initial.getOrderNumber());
    return workflow.command("approve-images:"+id,key,body,()->{
      workflow.locked(initial.getOrderNumber());var j=job(id);if(j.getStatus().equals("APPROVED")&&current(j))return view(j);
      editable(j);if(!current(j)||!j.getStatus().equals("REVIEW"))throw bad("이미 처리됐거나 이전 버전입니다. 최신 이미지를 확인해 주세요.");
      workflow.publishGeneratedViews(j);
      j.approve(access.current().getId(),clock.instant());jobs.flush();workflow.audit(j.getOrderNumber(),"APPROVE_IMAGES",j.getId(),"APPROVED","아이 "+(j.getPetIndex()+1));
      return view(j);
    });
  }
  @Transactional
  public Map<String,Object> retrySave(String id,String key,Map<String,Object> body){
    access.require(REVIEW_IMAGES);var initial=job(id);access.read(initial.getOrderNumber());
    return workflow.command("retry-image-save:"+id,key,body,()->{
      workflow.locked(initial.getOrderNumber());var j=job(id);editable(j);if(!current(j)||!j.getStatus().equals("FAILED")||j.getSheetKey()==null)throw bad("저장된 생성 결과가 없습니다. 수정 요청으로 다시 생성해 주세요.");
      j.requeueSave();jobs.flush();return view(j);
    });
  }
  @Transactional
  public Map<String,Object> resend(String id,String key,Map<String,Object> body){
    access.require(REVIEW_IMAGES);var initial=job(id);access.read(initial.getOrderNumber());
    return workflow.command("notify-images:"+id,key,body,()->{
      var j=job(id);editable(j);if(!current(j)||!j.getStatus().equals("REVIEW"))throw bad("최신 검수 대기 후보만 다시 보낼 수 있습니다.");
      notifyReview(id);return view(j);
    });
  }
  public Map<String,Object> status(boolean probe){
    access.require(MANAGE_OPERATION_SETTINGS);
    return Map.of("enabled",config.isEnabled(),"keyConfigured",!config.getApiKey().isBlank(),"model",config.getModel(),"connection",probe?api.connectionTest():false,"webhooks",telegram.webhookStatus());
  }
  public boolean runOne(){
    String id=tx(()->{
      jobs.findByStatusAndStartedAtBefore("RUNNING",clock.instant().minus(Duration.ofMinutes(10))).forEach(j->j.unknown(clock.instant()));
      var pending=jobs.findFirstByStatusOrderByCreatedAtAsc("QUEUED");if(pending.isEmpty())return null;
      var j=pending.get();try{editable(j);}catch(WorkflowException e){j.fail("ORDER_NOT_EDITABLE",clock.instant());return null;}
      j.start(clock.instant());jobs.flush();return j.getId();
    });
    if(id==null)return false;
    try {
      var snapshot=tx(()->job(id));
      OpenAiImageClient.Result result;
      if(snapshot.getSheetKey()!=null){byte[] sheet=storage.download(snapshot.getSheetKey());result=new OpenAiImageClient.Result(sheet,OpenAiImageClient.split(sheet),snapshot.getUsageJson(),snapshot.getRequestId());}
      else {
        record Source(String key,String type){}
        var sources=tx(()->{
          var order=orders.findByOrderNumber(snapshot.getOrderNumber()).orElseThrow();
          return photos.findByResponseId(order.getResponseId()).stream().filter(p->p.getStatus()==GoodsSurveyPhotoStatus.CONFIRMED&&Objects.equals(p.getPetIndex()==null?0:p.getPetIndex(),snapshot.getPetIndex()))
              .sorted(Comparator.comparing(p->p.getClientFileId())).limit(5).map(p->new Source(p.getObjectKey(),p.getContentType())).toList();
        });
        if(sources.isEmpty())throw bad("사진을 추가해 주세요.");
        var inputs=new ArrayList<OpenAiImageClient.Input>();
        String priorKey=snapshot.getPreviousId()==null?null:tx(()->job(snapshot.getPreviousId()).getSheetKey());
        if(priorKey!=null)inputs.add(new OpenAiImageClient.Input("previous.png",storage.download(priorKey),"image/png"));
        int start=inputs.size()+1;
        for(var source:sources)inputs.add(new OpenAiImageClient.Input("source-"+inputs.size()+extension(source.type()),storage.download(source.key()),source.type()));
        inputs.add(new OpenAiImageClient.Input("style.png",resource("image-generation/style-reference.png"),"image/png"));
        String prompt=new String(resource("image-generation/direct-four-view-v1.txt"),StandardCharsets.UTF_8)
            .replace("{{target_photo_roles}}","Inputs "+start+" through "+(inputs.size()-1)+" show only the target pet; infer visible individual features from these photographs.")
            .replace("{{style_reference_input_index}}",String.valueOf(inputs.size()))
            .replace("{{identity_lock}}","Extract and preserve species, face, ears, muzzle, coat colors, characteristic asymmetric markings ONLY from target photos.")
            .replace("{{tail_lock}}","Preserve observed length, curl and markings. Do not invent a breed-default tail or an extra attachment. Use the specified supported construction for unseen parts.")
            .replace("{{confirmed_anatomy_exceptions}}","None separately confirmed. Preserve any explicitly confirmed exception described in the correction; occlusion alone is not proof of absence.")
            .replace("{{approved_unknown_region_policy}}","Use the least-assumptive reconstruction for hidden non-critical regions; do not mirror known asymmetry. Human approval is mandatory.");
        if(priorKey!=null)prompt="INPUT 1 is the current 2x2 candidate to EDIT. Preserve its identity and unchanged features. Other inputs are original evidence and the style reference. Apply only this correction consistently to affected views: "+snapshot.getFeedback()+"\n"+prompt;
        else if(snapshot.getFeedback()!=null)prompt+="\nAdditional human instruction: "+snapshot.getFeedback();
        result=api.generate(inputs,prompt);
        var sheetArtifact=ProductionArtifact.pending(id+"-sheet",snapshot.getOrderNumber(),snapshot.getTaskId(),snapshot.getRequestedBy(),"IMAGE_SHEET","sheet.png","image/png",result.sheet().length,clock.instant().plus(Duration.ofDays(3650)));
        sheetArtifact.generatedCandidate();
        tx(()->{artifacts.saveAndFlush(sheetArtifact);return null;});
        storage.upload(sheetArtifact.getObjectKey(),"image/png",result.sheet());
        var completed=result;tx(()->{job(id).retainSheet(sheetArtifact.getObjectKey(),completed.usage(),completed.requestId());return null;});
      }
      var prepared=new ArrayList<ProductionArtifact>();
      for(int i=0;i<4;i++){
        String artifactId=UUID.nameUUIDFromBytes((id+":"+VIEWS.get(i)).getBytes(StandardCharsets.UTF_8)).toString();
        var a=ProductionArtifact.pending(artifactId,snapshot.getOrderNumber(),snapshot.getTaskId(),snapshot.getRequestedBy(),"MULTIVIEW",VIEWS.get(i)+"_"+id.substring(0,8)+".png","image/png",result.views().get(i).length,clock.instant().plus(Duration.ofDays(3650)));
        a.generatedCandidate();prepared.add(a);
      }
      tx(()->{artifacts.saveAllAndFlush(prepared);return null;});
      for(int i=0;i<4;i++)storage.upload(prepared.get(i).getObjectKey(),"image/png",result.views().get(i));
      var completed=result;
      tx(()->{var j=job(id);editable(j);if(!j.getStatus().equals("RUNNING")||!current(j))throw bad("이미 종료된 이미지 생성입니다.");j.finish(j.getSheetKey(),String.join(",",prepared.stream().map(a->a.getId()).toList()),completed.usage(),completed.requestId(),clock.instant());jobs.flush();return null;});
      notifyReview(id);
    }catch(Exception e){tx(()->{var j=job(id);if(j.getStatus().equals("RUNNING")){if(e instanceof WorkflowException w&&w.getCode().equals("IMAGE_RESULT_UNKNOWN"))j.unknown(clock.instant());else j.fail(e instanceof WorkflowException w?w.getCode():"OUTPUT_SAVE_FAILED",clock.instant());}return null;});}
    return true;
  }
  private byte[] resource(String name) throws java.io.IOException {try(var input=new ClassPathResource(name).getInputStream()){return input.readAllBytes();}}
  private String extension(String type){return type.equals("image/png")?".png":type.equals("image/webp")?".webp":".jpg";}
  public void notifyReview(String id){
    var j=tx(()->job(id));
    if(!j.getStatus().equals("REVIEW"))return;
    try {
      var album=tx(()->{
        editable(j);var order=orders.findByOrderNumber(j.getOrderNumber()).orElseThrow();var pictures=new ArrayList<ModelReviewMedia.Photo>();
        var originals=photos.findByResponseId(order.getResponseId()).stream().filter(p->p.getStatus()==GoodsSurveyPhotoStatus.CONFIRMED&&Objects.equals(p.getPetIndex()==null?0:p.getPetIndex(),j.getPetIndex())).sorted(Comparator.comparing(p->p.getClientFileId())).limit(5).toList();
        for(int i=0;i<originals.size();i++)pictures.add(new ModelReviewMedia.Photo("고객 사진 "+(i+1),ModelReviewMedia.jpeg(storage.download(originals.get(i).getObjectKey()))));
        for(var a:j.getArtifactIds().split(",")){var file=artifacts.findById(a).orElseThrow();pictures.add(new ModelReviewMedia.Photo(file.getFileName(),ModelReviewMedia.jpeg(storage.download(file.getObjectKey()))));}
        String title="4면도 이미지 검수 대기\n주문번호: "+j.getOrderNumber()+"\n반려동물: "+petName(j)+"\n반려인: "+order.getGuardianName()+"\n생성: AI 자동\n요청자: "+accountName(j.getRequestedBy())+"\n담당자: "+accountName(tasks.findById(j.getTaskId()).orElseThrow().getAssigneeId());
        return new ModelReviewMedia.Album(title,pictures);
      });
      var message=telegram.review(j.getId(),j.getOrderNumber(),j.getPetIndex(),album);
      tx(()->{job(id).notified(message!=null,message);return null;});
    }catch(RuntimeException e){tx(()->{job(id).notified(false,null);return null;});}
  }
}
