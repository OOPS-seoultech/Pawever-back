package com.pawever.backend.workflow;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.pawever.backend.admin.entity.*;
import com.pawever.backend.admin.repository.AdminAccountRepository;
import com.pawever.backend.admin.security.AdminPrincipal;
import com.pawever.backend.goodssurvey.entity.*;
import com.pawever.backend.goodssurvey.repository.*;
import com.pawever.backend.goodssurvey.service.GoodsSurveyPhotoStorage;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
@SpringBootTest(properties={"admin.jwt-secret=workflow-test-secret-at-least-thirty-two-characters","figurine.image.enabled=true","figurine.image.api-key=test","figurine.image.worker-enabled=false","figurine.image.register-webhooks=false"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@ActiveProfiles("test")
class ImageGenerationIntegrationTest {
 @Autowired org.springframework.test.web.servlet.MockMvc mvc;
 @Autowired AdminAccountRepository accounts;
 @Autowired GoodsSurveyFulfillmentRepository orders;
 @Autowired GoodsSurveyCampaignRepository campaigns;
 @Autowired GoodsSurveyResponseRepository responses;
 @Autowired GoodsSurveyPhotoRepository photos;
 @Autowired WorkflowService workflow;
 @Autowired ImageGenerationService images;
 @Autowired ImageGenerationJobRepository jobs;
 @Autowired ProductionArtifactRepository artifacts;
 @Autowired ImageTelegramService linking;
 @Autowired StaffTelegramLinkRepository links;
 @Autowired TelegramImageFeedbackService feedback;
 @Autowired TelegramImageFeedbackRepository drafts;
 @MockitoBean OpenAiImageClient api;
 @MockitoBean ImageTelegramClient telegram;
 @MockitoBean GoodsSurveyPhotoStorage storage;
 String number;AdminAccount actor;
 @AfterEach void clear(){drafts.deleteAll(drafts.findAll().stream().filter(d->jobs.findById(d.getJobId()).map(j->j.getOrderNumber().equals(number)).orElse(false)).toList());links.findById(actor.getId()).ifPresent(links::delete);jobs.deleteAll(jobs.findAll().stream().filter(j->j.getOrderNumber().equals(number)).toList());SecurityContextHolder.clearContext();}
  @BeforeEach
  void setup() {
    var a=AdminAccount.invite(UUID.randomUUID()+"@example.test","제작자",AdminRole.OWNER,"test",Instant.now().plusSeconds(3600));
    a.acceptInvite("unused");a.approve(Set.of(),Instant.now());accounts.saveAndFlush(a);actor=a;
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AdminPrincipal(a.getId(),a.getRole()),null,List.of()));
    number = "PE-" + UUID.randomUUID().toString().substring(0, 12);
    String campaignId = UUID.randomUUID().toString(), responseId = UUID.randomUUID().toString();
    campaigns.saveAndFlush(
        GoodsSurveyCampaign.create(
            campaignId,
            100,
            0,
            Instant.now().minusSeconds(3600),
            Instant.now().plusSeconds(3600),
            true,
            true));
    responses.saveAndFlush(
        GoodsSurveyResponse.draft(
            responseId, campaignId, "v1", UUID.randomUUID().toString(), "figure", "{}"));
    var o =
        GoodsSurveyFulfillment.create(
            responseId,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "{}",
            "figure",
            null,
            "초코",
            "보호자",
            "01012345678",
            UUID.randomUUID().toString(),
            GoodsDeliveryMethod.PICKUP,
            null,
            null,
            null,
            "v1",
            Instant.now(),
            false,
            number,
            GoodsOrderPricing.listPrice(18900, 0),
            false,
            false,
            "v1",
            10080,
            1825);
    orders.saveAndFlush(o);
    for (int i = 0; i < 3; i++) {
      var p =
          GoodsSurveyPhoto.pending(
              UUID.randomUUID().toString(),
              o.getResponseId(),
              "photo-" + i,
              "test/" + UUID.randomUUID(),
              "image/png",
              10,
              Instant.now().plusSeconds(600));
      p.confirm(10, Instant.now());
      photos.saveAndFlush(p);
    }
  }


 Map<String,Object> confirm(){var o=orders.findByOrderNumber(number).orElseThrow();return workflow.confirm(number,UUID.randomUUID().toString(),Map.of("version",o.getVersion(),"amount",18900));}
 ImageGenerationJob latest(){return jobs.findFirstByOrderNumberAndPetIndexOrderByCreatedAtDesc(number,0).orElseThrow();}
 void output() throws Exception {
   byte[] sheet=OpenAiImageClientTest.sheet();
   when(storage.download(anyString())).thenReturn(sheet);
   when(storage.presignDownload(anyString(),any(),any())).thenAnswer(i->new GoodsSurveyPhotoStorage.PresignedDownload("https://test.invalid/file",i.getArgument(2)));
   when(api.generate(anyList(),anyString())).thenReturn(new OpenAiImageClient.Result(sheet,OpenAiImageClient.split(sheet),"{}","req-test"));
 }
 @Test void paymentQueuesOnceAndApprovalPublishesExactlyFourBeforeManualModeling() throws Exception {
   assertThat(jobs.findByOrderNumberAndPetIndexOrderByCreatedAtAsc(number,0)).isEmpty();
   var row=confirm();var j=latest();assertThat(j.getStatus()).isEqualTo("QUEUED");
   assertThatThrownBy(this::confirm).isInstanceOf(WorkflowException.class);
   assertThat(row.get("allowedActions").toString()).doesNotContain("START_TASK");
   output();assertThat(images.runOne()).isTrue();assertThat(latest().getStatus()).isEqualTo("REVIEW");
   assertThat(artifacts.findByOrderNumberOrderByIdAsc(number).stream().filter(a->a.getKind().equals("MULTIVIEW")).allMatch(a->"READY".equals(a.getMultiviewState()))).isTrue();
   images.approve(j.getId(),"approve-test",Map.of());images.approve(j.getId(),"approve-test",Map.of());
   assertThat(latest().getStatus()).isEqualTo("APPROVED");
   assertThat(artifacts.findByOrderNumberOrderByIdAsc(number).stream().filter(a->a.getKind().equals("MULTIVIEW")&&a.getMultiviewState()==null)).hasSize(4);
   verify(api,times(1)).generate(anyList(),anyString());
 }
 @Test void feedbackEditsPreviousSheetAndOldButtonsCannotApproveNewVersion() throws Exception {
   confirm();output();images.runOne();String first=latest().getId();
   images.regenerate(first,"tail-fix",Map.of("direction","back","region","tail","note","꼬리 뿌리를 골반 중앙으로"));
   assertThatThrownBy(()->images.approve(first,"old-button",Map.of())).isInstanceOf(WorkflowException.class);
   images.runOne();
   var prompt=org.mockito.ArgumentCaptor.forClass(String.class);verify(api,times(2)).generate(anyList(),prompt.capture());
   assertThat(prompt.getValue()).contains("current 2x2 candidate to EDIT","꼬리 뿌리를 골반 중앙으로","TAIL GEOMETRY AND OCCLUSION","Anatomical left and","correct opaque-body occlusion");
   assertThat(latest().getPreviousId()).isEqualTo(first);
   assertThat(latest().getPromptVersion()).isEqualTo("pet-four-view-api-tail-consistency-v2");
 }
 @Test void unknownApiResultPreservesPaymentAndNeverAutomaticallyBillsAgain() throws Exception {
   confirm();output();when(api.generate(anyList(),anyString())).thenThrow(new WorkflowException(502,"IMAGE_RESULT_UNKNOWN","unknown"));
   images.runOne();images.runOne();assertThat(latest().getStatus()).isEqualTo("UNKNOWN");
   assertThat(orders.findByOrderNumber(number).orElseThrow().paymentStatus()).isEqualTo("CONFIRMED");
   verify(api,times(1)).generate(anyList(),anyString());
 }
 @Test void savedSheetCanBeRetriedWithoutAnotherPaidApiCall() throws Exception {
   confirm();output();var uploads=new java.util.concurrent.atomic.AtomicInteger();
   doAnswer(i->{if(uploads.incrementAndGet()==2)throw new IllegalStateException("save failed");return null;}).when(storage).upload(anyString(),anyString(),any());
   images.runOne();assertThat(latest().getStatus()).isEqualTo("FAILED");assertThat(latest().getSheetKey()).isNotNull();
   doNothing().when(storage).upload(anyString(),anyString(),any());
   images.retrySave(latest().getId(),"save-retry",Map.of());images.runOne();assertThat(latest().getStatus()).isEqualTo("REVIEW");
   verify(api,times(1)).generate(anyList(),anyString());
 }
 @Test void telegramLinkIsOneUseExpiresAndChecksActiveAccount() {
   when(telegram.reviewBotName()).thenReturn("pawever_test_bot");
   String url=(String)linking.createLink().get("url");String code=url.substring(url.indexOf("start=")+6);
   assertThat(linking.connect(code,123L)).isTrue();assertThat(linking.connect(code,123L)).isFalse();
   assertThat(linking.linkStatus().get("connected")).isEqualTo(true);
   linking.unlink();var link=StaffTelegramLink.create(actor.getId());link.code(ImageTelegramClient.hash("expired"),Instant.now().minusSeconds(1));links.saveAndFlush(link);
   assertThat(linking.connect("expired",123L)).isFalse();
 }
 @Test void paymentButtonRequiresLinkedStaffAndDuplicateClicksQueueOnlyOnce() throws Exception {
   when(telegram.chat("payment")).thenReturn("-100test");
   var json=new com.fasterxml.jackson.databind.ObjectMapper();
   String raw="{\"update_id\":1,\"callback_query\":{\"id\":\"cb1\",\"from\":{\"id\":777},\"message\":{\"message_id\":1,\"chat\":{\"id\":\"-100test\"}},\"data\":\"pay:"+number+"\"}}";
   linking.update("payment",json.readTree(raw));assertThat(orders.findByOrderNumber(number).orElseThrow().paymentStatus()).isEqualTo("PENDING");
   var linked=StaffTelegramLink.create(actor.getId());linked.connect(777L);links.saveAndFlush(linked);
   linking.update("payment",json.readTree(raw));linking.update("payment",json.readTree(raw.replace("cb1","cb2")));
   assertThat(jobs.findByOrderNumberAndPetIndexOrderByCreatedAtAsc(number,0)).hasSize(1);
   verifyNoInteractions(api);
 }
 @Test void generatedCandidatesAndApprovalRemainSeparateForTwoPets() throws Exception {
   var o=orders.findByOrderNumber(number).orElseThrow();o.recordPetCount(2);orders.saveAndFlush(o);
   var photo=GoodsSurveyPhoto.pending(UUID.randomUUID().toString(),o.getResponseId(),"second-pet","test/second","image/png",10,Instant.now().plusSeconds(600));photo.confirm(10,Instant.now());photo.assignToPet(1);photos.saveAndFlush(photo);
   confirm();output();images.runOne();images.runOne();
   var first=jobs.findFirstByOrderNumberAndPetIndexOrderByCreatedAtDesc(number,0).orElseThrow();
   var second=jobs.findFirstByOrderNumberAndPetIndexOrderByCreatedAtDesc(number,1).orElseThrow();
   images.approve(first.getId(),"pet-one-only",Map.of());
   assertThat(jobs.findById(second.getId()).orElseThrow().getStatus()).isEqualTo("REVIEW");
   var references=org.mockito.ArgumentCaptor.forClass(java.util.List.class);verify(api,times(2)).generate(references.capture(),anyString());
   assertThat(references.getAllValues()).extracting(List::size).containsExactlyInAnyOrder(4,2);
 }

 @Test void webhookBindsActualTelegramJsonBeforeAuthenticatingIt() throws Exception {
   String path="/api/telegram/image-workflow/payment";
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).contentType("application/json").content("{}"))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
   when(telegram.authorized("payment","test-header")).thenReturn(true);
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("X-Telegram-Bot-Api-Secret-Token","test-header").contentType("application/json").content("{\"update_id\":1}"))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.ok").value(true));
 }

 @Test void testMessagesAreIdempotentAndTestCallbacksNeverMutateOrdersOrCallImageApi() throws Exception {
   when(telegram.sendTest("payment",actor.getName())).thenReturn(Map.of("accepted",true,"messageId",10,"title","PAWEVER"));
   linking.sendTest("payment","test-room-send",Map.of());linking.sendTest("payment","test-room-send",Map.of());
   verify(telegram,times(1)).sendTest("payment",actor.getName());
   when(telegram.chat("payment")).thenReturn("-100test");
   var linked=StaffTelegramLink.create(actor.getId());linked.connect(777L);links.saveAndFlush(linked);
   String raw="{\"update_id\":99,\"callback_query\":{\"id\":\"test-cb\",\"from\":{\"id\":777},\"message\":{\"message_id\":10,\"chat\":{\"id\":\"-100test\"}},\"data\":\"test:payment\"}}";
   linking.update("payment",new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw));
   verify(telegram).answer(eq("payment"),eq("test-cb"),contains("연결 테스트 성공"));
   verify(telegram,never()).clear(anyString(),anyString(),anyLong());
   assertThat(orders.findByOrderNumber(number).orElseThrow().paymentStatus()).isEqualTo("PENDING");
   assertThat(jobs.findByOrderNumberAndPetIndexOrderByCreatedAtAsc(number,0)).isEmpty();verifyNoInteractions(api);
   actor.changeRole(AdminRole.PRODUCTION);actor.setWorkRoles(Set.of(WorkRole.MODELING));accounts.saveAndFlush(actor);
   assertThatThrownBy(()->linking.sendTest("payment","forbidden-test",Map.of())).isInstanceOf(WorkflowException.class);
   linking.update("payment",new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw));
   assertThat(orders.findByOrderNumber(number).orElseThrow().paymentStatus()).isEqualTo("PENDING");
 }

 @Test void nativeTelegramFeedbackSelectsMultiplePartsStoresMemoAndRegeneratesOnce() throws Exception {
   confirm();output();images.runOne();String first=latest().getId();
   feedback.begin(first,"-review",20L,777L,"검수 대기","draft-open");
   var d=drafts.findById(first).orElseThrow();String session=d.getSessionId();
   feedback.action(session,"d:back","-review","select-back");
   feedback.action(session,"d:left","-review","select-left");
   feedback.action(session,"r:tail","-review","select-tail");
   feedback.action(session,"r:tail_origin","-review","select-tail-origin");
   feedback.action(session,"r:tail_visibility","-review","select-tail-visibility");
   feedback.action(session,"r:tail_consistency","-review","select-tail-consistency");
   feedback.action(session,"r:paws","-review","select-paws");
   feedback.action(session,"r:paws","-review","select-paws");
   when(telegram.feedbackPrompt(anyString(),anyLong(),anyString(),anyString())).thenReturn(100L);
   feedback.action(session,"note","-review","memo-prompt");
   feedback.memo("-review",100L,777L,"꼬리 뿌리는 골반 중앙, 발은 몸에 붙여 주세요",20,"memo-save");
   feedback.memo("-review",100L,777L,"늦게 도착한 이전 메모",19,"memo-older");
   d=drafts.findById(first).orElseThrow();
   assertThat(d.getDirections()).isEqualTo("back,left");assertThat(d.getRegions()).contains("tail","paws");
   assertThat(d.getNote()).contains("골반 중앙").doesNotContain("이전 메모");
   assertThat(jobs.findByOrderNumberAndPetIndexOrderByCreatedAtAsc(number,0)).hasSize(1);
   verify(api,times(1)).generate(anyList(),anyString());
   feedback.action(session,"generate","-review","submit-feedback");
   feedback.action(session,"generate","-review","submit-feedback-again");
   assertThat(jobs.findByOrderNumberAndPetIndexOrderByCreatedAtAsc(number,0)).hasSize(2);
   assertThat(latest().getFeedback()).contains("뒷면, 왼쪽","꼬리 시작점 오류","꼬리 좌우 중복 노출","꼬리 방향별 불일치","골반 중앙","카메라만 회전","복제하거나 투과시키지");
   verify(api,times(1)).generate(anyList(),anyString());
   images.runOne();String second=latest().getId();assertThat(second).isNotEqualTo(first);
   feedback.begin(second,"-review",21L,777L,"재검수 대기","second-draft");
   feedback.action(drafts.findById(second).orElseThrow().getSessionId(),"cancel","-review","cancel-second");
   images.approve(second,"approve-latest",Map.of());assertThat(latest().getStatus()).isEqualTo("APPROVED");
   verify(api,times(2)).generate(anyList(),anyString());
 }
 @Test void feedbackRejectsStaleCandidatesWrongStaffWrongRoomAndEmptyRequest() throws Exception {
   confirm();output();images.runOne();String first=latest().getId();
   feedback.begin(first,"-review",20L,777L,"検수 대기","open-protected");
   String session=drafts.findById(first).orElseThrow().getSessionId();
   assertThatThrownBy(()->feedback.action(session,"generate","-review","empty-request")).isInstanceOf(WorkflowException.class);
   assertThatThrownBy(()->feedback.action(session,"r:tail","-other","wrong-room")).isInstanceOf(WorkflowException.class);
   var other=AdminAccount.invite(UUID.randomUUID()+"@example.test","다른 담당자",AdminRole.OWNER,"test",Instant.now().plusSeconds(3600));
   other.acceptInvite("unused");other.approve(Set.of(),Instant.now());accounts.saveAndFlush(other);
   SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AdminPrincipal(other.getId(),other.getRole()),null,List.of()));
   assertThatThrownBy(()->feedback.action(session,"r:tail","-review","other-staff")).isInstanceOf(WorkflowException.class);
   SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AdminPrincipal(actor.getId(),actor.getRole()),null,List.of()));
   feedback.action(session,"cancel","-review","cancel-draft");
   feedback.begin(first,"-review",20L,777L,"검수 대기","reopen-draft");
   assertThat(drafts.findById(first).orElseThrow().getSessionId()).isNotEqualTo(session);
   assertThatThrownBy(()->feedback.action(session,"r:tail","-review","old-draft-button")).isInstanceOf(WorkflowException.class);
   images.approve(first,"web-approved",Map.of());
   String fresh=drafts.findById(first).orElseThrow().getSessionId();
   assertThatThrownBy(()->feedback.action(fresh,"r:tail","-review","after-web-approval")).isInstanceOf(WorkflowException.class);
   verify(api,times(1)).generate(anyList(),anyString());
 }
 @Test void telegramMemoWebhookOnlyUsesLinkedStaffRepliesToTheirOwnPrompt() throws Exception {
   confirm();output();images.runOne();String first=latest().getId();
   var link=StaffTelegramLink.create(actor.getId());link.connect(777L);links.saveAndFlush(link);
   when(telegram.chat("review")).thenReturn("-review");
   when(telegram.feedbackPrompt(anyString(),anyLong(),anyString(),anyString())).thenReturn(100L);
   var json=new com.fasterxml.jackson.databind.ObjectMapper();
   linking.update("review",json.valueToTree(Map.of("update_id",50,"callback_query",Map.of("id","cb","from",Map.of("id",777),"message",Map.of("message_id",20,"chat",Map.of("id","-review"),"text","검수 대기"),"data","changes:"+first))));
   String session=drafts.findById(first).orElseThrow().getSessionId();
   linking.update("review",json.valueToTree(Map.of("update_id",51,"callback_query",Map.of("id","cb2","from",Map.of("id",777),"message",Map.of("message_id",20,"chat",Map.of("id","-review")),"data","fb:"+session+":note"))));
   linking.update("review",json.valueToTree(Map.of("update_id",52,"message",Map.of("message_id",101,"from",Map.of("id",888),"chat",Map.of("id","-review"),"reply_to_message",Map.of("message_id",100),"text","남의 메모"))));
   assertThat(drafts.findById(first).orElseThrow().getNote()).isEmpty();
   linking.update("review",json.valueToTree(Map.of("update_id",53,"message",Map.of("message_id",102,"from",Map.of("id",777),"chat",Map.of("id","-review"),"reply_to_message",Map.of("message_id",100),"text","눈 주변 무늬를 사진에 맞춰 주세요"))));
   assertThat(drafts.findById(first).orElseThrow().getNote()).contains("눈 주변");
   verify(api,times(1)).generate(anyList(),anyString());
 }
 @Test void successiveCorrectionsKeepEarlierPetInstructions() throws Exception {
   confirm();output();images.runOne();
   images.regenerate(latest().getId(),"history-tail",Map.of("direction","back","region","tail","note","TAIL_ROOT_CENTER"));images.runOne();
   images.regenerate(latest().getId(),"history-paws",Map.of("direction","front","region","paws","note","PAWS_SUPPORTED"));images.runOne();
   var prompt=org.mockito.ArgumentCaptor.forClass(String.class);verify(api,times(3)).generate(anyList(),prompt.capture());
   assertThat(prompt.getValue()).contains("TAIL_ROOT_CENTER","PAWS_SUPPORTED","newest correction conflicts");
 }

}
