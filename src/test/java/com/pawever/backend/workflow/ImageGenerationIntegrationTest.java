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
@ActiveProfiles("test")
class ImageGenerationIntegrationTest {
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
 @MockitoBean OpenAiImageClient api;
 @MockitoBean ImageTelegramClient telegram;
 @MockitoBean GoodsSurveyPhotoStorage storage;
 String number;AdminAccount actor;
 @AfterEach void clear(){jobs.deleteAll(jobs.findAll().stream().filter(j->j.getOrderNumber().equals(number)).toList());SecurityContextHolder.clearContext();}
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
   assertThat(prompt.getValue()).contains("current 2x2 candidate to EDIT","꼬리 뿌리를 골반 중앙으로");
   assertThat(latest().getPreviousId()).isEqualTo(first);
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

}
