package com.pawever.backend.workflow;

import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhotoStatus;
import com.pawever.backend.goodssurvey.repository.*;
import com.pawever.backend.goodssurvey.service.GoodsSurveyPhotoStorage;
import com.pawever.backend.workflow.event.ModelReviewRequestedEvent;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 같은 아이의 현재 제출 자료만 운영 검수방으로 전달한다. */
@Service
@RequiredArgsConstructor
public class ModelReviewMedia {
  private final ProductionTaskRepository tasks;
  private final GoodsSurveyFulfillmentRepository orders;
  private final GoodsSurveyPhotoRepository photos;
  private final GoodsOrderPetRepository pets;
  private final GoodsSurveyPhotoStorage storage;
  private final WorkflowService workflow;

  public record Photo(String caption, byte[] bytes) {}
  public record Album(String title, List<Photo> photos) {}

  @Transactional(readOnly = true)
  public Album load(ModelReviewRequestedEvent event) {
    var review = tasks.findById(event.reviewTaskId()).orElse(null);
    var order = orders.findByOrderNumber(event.orderNumber()).orElse(null);
    if (review == null || order == null || !review.getOrderNumber().equals(event.orderNumber())
        || review.getStage() != ProductionStage.MODEL_REVIEW || !review.getStatus().equals("WAITING")
        || !workflow.active(order) || !workflow.paid(order)) return null;
    var source = review.getMaterialsFromTaskId() == null
        ? tasks.findByOrderNumberOrderByIdAsc(event.orderNumber()).stream()
            .filter(t -> t.getPetIndex() == review.getPetIndex() && t.getId() < review.getId()
                && t.getStage() == ProductionStage.MODELING && t.getStatus().equals("COMPLETED"))
            .reduce((a, b) -> b).orElse(null)
        : tasks.findById(review.getMaterialsFromTaskId()).orElse(null);
    var name = pets.findByOrderNumberOrderByPetIndexAsc(event.orderNumber()).stream()
        .filter(p -> p.getPetIndex() == review.getPetIndex()).map(p -> p.getPetName()).findFirst().orElse(order.getPetName());
    var result = new ArrayList<Photo>();
    var originals = photos.findByResponseId(order.getResponseId()).stream()
        .filter(p -> p.getStatus() == GoodsSurveyPhotoStatus.CONFIRMED
            && Objects.equals(p.getPetIndex() == null ? 0 : p.getPetIndex(), review.getPetIndex()))
        .sorted(Comparator.comparing(p -> p.getClientFileId())).limit(5).toList();
    for (int i = 0; i < originals.size(); i++)
      result.add(new Photo(name + " · 고객 사진 " + (i + 1), jpeg(storage.download(originals.get(i).getObjectKey()))));
    if (source != null) {
      var views = workflow.currentMultiviews(source.getId(), event.orderNumber()).stream().limit(4).toList();
      for (int i = 0; i < views.size(); i++)
        result.add(new Photo(name + " · 4면도 " + (i + 1), jpeg(storage.download(views.get(i).getObjectKey()))));
    }
    return new Album(event.orderNumber() + " · " + name + " · 검수 대기", result);
  }

  static byte[] jpeg(byte[] source) {
    try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new IOException("지원하지 않는 이미지입니다.");
      var reader = readers.next();
      try {
        reader.setInput(input, true, true);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < 1 || height < 1 || (long) width * height > 100_000_000) throw new IOException("이미지 크기가 큽니다.");
        var param = reader.getDefaultReadParam();
        int sample = Math.max(1, Math.max(width, height) / 1600);
        param.setSourceSubsampling(sample, sample, 0, 0);
        var image = reader.read(0, param);
        double scale = Math.min(1d, 1600d / Math.max(image.getWidth(), image.getHeight()));
        int w = Math.max(1, (int) (image.getWidth() * scale)), h = Math.max(1, (int) (image.getHeight() * scale));
        // Telegram accepts photos with an aspect ratio up to 20:1.
        var canvas = new BufferedImage(Math.max(w, (h + 19) / 20), Math.max(h, (w + 19) / 20), BufferedImage.TYPE_INT_RGB);
        var graphics = canvas.createGraphics();
        try {
          graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
          graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
          graphics.drawImage(image, (canvas.getWidth() - w) / 2, (canvas.getHeight() - h) / 2, w, h, null);
        } finally { graphics.dispose(); image.flush(); }
        var output = new ByteArrayOutputStream();
        if (!ImageIO.write(canvas, "jpg", output)) throw new IOException("이미지를 변환하지 못했습니다.");
        canvas.flush();
        return output.toByteArray();
      } finally { reader.dispose(); }
    } catch (IOException error) { throw new WorkflowException(422, "REVIEW_PHOTO_FAILED", "검수 알림 사진을 변환하지 못했습니다."); }
  }
}
