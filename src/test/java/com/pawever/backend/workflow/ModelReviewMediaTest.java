package com.pawever.backend.workflow;

import com.pawever.backend.goodssurvey.entity.*;
import com.pawever.backend.goodssurvey.repository.*;
import com.pawever.backend.goodssurvey.service.GoodsSurveyPhotoStorage;
import com.pawever.backend.workflow.event.ModelReviewRequestedEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModelReviewMediaTest {
  static byte[] image() throws IOException {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(1800, 900, BufferedImage.TYPE_INT_RGB), "png", bytes);
    return bytes.toByteArray();
  }

  @Test
  void photoPreviewIsJpegAndBoundedWithoutChangingOriginalBytes() throws Exception {
    byte[] original = image(), before = original.clone();
    byte[] preview = ModelReviewMedia.jpeg(original);
    var decoded = ImageIO.read(new ByteArrayInputStream(preview));
    assertThat(Math.max(decoded.getWidth(), decoded.getHeight())).isEqualTo(1600);
    assertThat(preview[0]).isEqualTo((byte) 0xff);
    assertThat(original).isEqualTo(before);
    assertThatThrownBy(() -> ModelReviewMedia.jpeg(new byte[] {1,2,3})).isInstanceOf(WorkflowException.class);
  }

  @Test
  void albumContainsOnlySelectedPetsFivePhotosAndFourCurrentViews() throws Exception {
    var tasks = mock(ProductionTaskRepository.class);
    var orders = mock(GoodsSurveyFulfillmentRepository.class);
    var photos = mock(GoodsSurveyPhotoRepository.class);
    var pets = mock(GoodsOrderPetRepository.class);
    var storage = mock(GoodsSurveyPhotoStorage.class);
    var workflow = mock(WorkflowService.class);
    var accounts = mock(com.pawever.backend.admin.repository.AdminAccountRepository.class);
    var worker = mock(com.pawever.backend.admin.entity.AdminAccount.class);
    when(worker.getName()).thenReturn("제작 담당 <가&나>");
    when(accounts.findById(9L)).thenReturn(Optional.of(worker));
    var order = mock(GoodsSurveyFulfillment.class);
    when(order.getGuardianName()).thenReturn("반려인 이름");
    when(orders.findByOrderNumber("TEST-1")).thenReturn(Optional.of(order));
    when(order.getResponseId()).thenReturn("response"); when(order.getPetName()).thenReturn("선택한 아이");
    when(workflow.active(order)).thenReturn(true); when(workflow.paid(order)).thenReturn(true);
    var source = ProductionTask.create("TEST-1", 1, ProductionStage.MODELING, 9L, 1);
    var review = ProductionTask.create("TEST-1", 1, ProductionStage.MODEL_REVIEW, 44L, 1);
    org.springframework.test.util.ReflectionTestUtils.setField(source, "id", 1L);
    org.springframework.test.util.ReflectionTestUtils.setField(review, "id", 2L);
    source.complete(Instant.now());
    when(tasks.findById(2L)).thenReturn(Optional.of(review));
    when(tasks.findByOrderNumberOrderByIdAsc("TEST-1")).thenReturn(List.of(source, review));
    var originals = new ArrayList<GoodsSurveyPhoto>();
    for (int i = 0; i < 7; i++) {
      var photo = GoodsSurveyPhoto.pending("p" + i, "response", "client" + i, "photo/" + i, "image/png", 10, Instant.now());
      photo.assignToPet(i == 0 ? 0 : 1); photo.confirm(10, Instant.now()); originals.add(photo);
    }
    when(photos.findByResponseId("response")).thenReturn(originals);
    var views = new ArrayList<ProductionArtifact>();
    for (int i = 0; i < 4; i++) views.add(ProductionArtifact.pending("v" + i, "TEST-1", 1L, 9L, "MULTIVIEW", "view.png", "image/png", 10, Instant.now()));
    when(workflow.currentMultiviews(1L, "TEST-1")).thenReturn(views);
    when(storage.download(anyString())).thenReturn(image());
    var service = new ModelReviewMedia(tasks, orders, photos, pets, storage, workflow, mock(StaffPermissions.class), accounts, mock(com.pawever.backend.notification.telegram.TelegramOpsClient.class));
    var album = service.load(new ModelReviewRequestedEvent("TEST-1", 2L));
    assertThat(album.title()).contains("작업 담당자: 제작 담당 <가&나>", "반려동물: 선택한 아이", "반려인: 반려인 이름", "주문번호: TEST-1");
    verify(accounts, never()).findById(44L);
    assertThat(album.photos()).hasSize(9);
    assertThat(album.photos().stream().filter(p -> p.caption().contains("고객 사진"))).hasSize(5);
    assertThat(album.photos().stream().filter(p -> p.caption().contains("4면도"))).hasSize(4);
    verify(storage, never()).download("photo/0");
    when(accounts.findById(9L)).thenReturn(Optional.empty());
    when(order.getGuardianName()).thenReturn(null);
    var missingNames = service.load(new ModelReviewRequestedEvent("TEST-1", 2L));
    assertThat(missingNames.title()).contains("작업 담당자: 미확인", "반려인: 미등록").doesNotContain("null");
  }
}
