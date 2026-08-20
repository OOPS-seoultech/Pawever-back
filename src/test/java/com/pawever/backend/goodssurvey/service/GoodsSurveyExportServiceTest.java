package com.pawever.backend.goodssurvey.service;

import com.pawever.backend.goodssurvey.config.GoodsSurveyProperties;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhoto;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhotoStatus;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponse;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyPhotoRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyResponseRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyStoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.Objects;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoodsSurveyExportServiceTest {

    @Mock private GoodsSurveyResponseRepository responseRepository;
    @Mock private GoodsSurveyFulfillmentRepository fulfillmentRepository;
    @Mock private GoodsSurveyStoryRepository storyRepository;
    @Mock private GoodsSurveyPhotoRepository photoRepository;
    @Mock private GoodsSurveyPhotoStorage photoStorage;

    private GoodsSurveyExportService service;

    @BeforeEach
    void setUp() {
        GoodsSurveyProperties properties = new GoodsSurveyProperties();
        properties.setCampaignId("goods-2026-07");
        service = new GoodsSurveyExportService(
                responseRepository,
                fulfillmentRepository,
                storyRepository,
                photoRepository,
                photoStorage,
                properties,
                new ObjectMapper()
        );
    }

    @Test
    void applicationListCarriesTheConsentRecordSoItCanBeShownLater() {
        givenThreeApplications();

        String csv = service.applicationsCsv();

        // 동의 없이는 신청이 성립하지 않지만, 그것만으로는 기록이 아니다.
        // 언제 어떤 문구에 동의했는지 남아 있어야 나중에 보여줄 수 있다.
        assertThat(csv).contains("개인정보동의버전,개인정보동의일시");
        assertThat(csv).contains("2026-07-23,2026-08-02T10:00:00Z");
    }

    @Test
    void productionListLeavesOutEverythingShippingOnly() {
        givenThreeApplications();

        String csv = service.productionCsv(1, 3);

        // 만드는 데 필요한 것만 있어야 한다.
        assertThat(csv).contains("번호,굿즈이름,굿즈종류,반려견이름,사진파일명,요청사항,응답ID");
        // 코드값만 보면 무엇을 만들어야 하는지 알 수 없다.
        assertThat(csv).contains("3D 얼굴 키링").contains("3D 전신 피규어");
        assertThat(csv).contains("몽이");
        // 보호자 이름·연락처·주소는 배송 단계에서 쓰인다. 제작에는 나가지 않는다.
        assertThat(csv).doesNotContain("보호자");
        assertThat(csv).doesNotContain("01012345678");
        assertThat(csv).doesNotContain("서울시 노원구");
    }

    @Test
    void creativeFallsBackToUtmTermBecauseThreadsPutsItThere() {
        // 인스타 광고는 utm_content에, 쓰레드 링크는 utm_term에 소재를 담아 왔다.
        // 한쪽만 읽으면 49건이 빈칸이 된다.
        lenient().when(responseRepository.findAll()).thenReturn(List.of(
                tracked("r-content", "{\"utm_source\":\"instagram\",\"utm_content\":\"va\"}"),
                tracked("r-term", "{\"utm_source\":\"thread\",\"utm_term\":\"bio_link\"}")
        ));
        lenient().when(fulfillmentRepository.findAll()).thenReturn(List.of());

        String csv = service.responsesCsv();

        assertThat(csv).contains("instagram,,,va");
        assertThat(csv).contains("thread,,,bio_link");
    }

    @Test
    void unknownGoodsTypeFallsBackToTheCodeInsteadOfAnEmptyCell() {
        // 굿즈가 늘었는데 이름을 안 더하면 빈칸이 되어 무엇인지 알 수 없다.
        lenient().when(responseRepository.findAll()).thenReturn(List.of(response("r9")));
        lenient().when(fulfillmentRepository.findAll()).thenReturn(List.of(
                fulfillment(9L, "r9", "hologram", "새봄", LocalDateTime.parse("2026-08-02T11:00:00"))
        ));
        lenient().when(photoRepository.findAll()).thenReturn(List.of());

        assertThat(service.productionCsv(1, 1)).contains("1,hologram,hologram,새봄");
    }

    @Test
    void selectsOnlyTheRequestedRangeInApplicationOrder() {
        givenThreeApplications();

        String csv = service.productionCsv(1, 2);

        assertThat(csv).contains("몽이").contains("루이");
        assertThat(csv).doesNotContain("제나");
    }

    @Test
    void namesPhotosSoTheOwnerIsObviousAfterUnzipping() {
        givenThreeApplications();
        when(photoStorage.download(any())).thenReturn(new byte[] {1, 2, 3});

        ByteArrayOutputStream target = new ByteArrayOutputStream();
        service.writePhotoArchive(1, 3, target);

        assertThat(entryNames(target.toByteArray())).containsExactly(
                "01_face_몽이_1.png",
                "02_figure_루이_1.jpg",
                "02_figure_루이_2.jpg",
                "03_figure_제_나_1.jpg"
        );
    }

    private List<String> entryNames(byte[] archive) {
        List<String> names = new ArrayList<>();
        try (ZipInputStream stream = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = stream.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        return names;
    }

    private void givenThreeApplications() {
        List<GoodsSurveyResponse> responses = List.of(
                response("r1"), response("r2"), response("r3")
        );
        lenient().when(responseRepository.findAll()).thenReturn(responses);

        // 신청 순서가 뒤섞여 들어와도 들어온 시각대로 번호를 매겨야 한다.
        lenient().when(fulfillmentRepository.findAll()).thenReturn(List.of(
                fulfillment(3L, "r3", "figure", "제 나", LocalDateTime.parse("2026-08-02T10:37:44")),
                fulfillment(1L, "r1", "face", "몽이", LocalDateTime.parse("2026-07-26T07:01:43")),
                fulfillment(2L, "r2", "figure", "루이", LocalDateTime.parse("2026-08-02T10:35:23"))
        ));

        lenient().when(photoRepository.findAll()).thenReturn(List.of(
                photo("p1", "r1", "key-1", "image/png"),
                photo("p2", "r2", "key-2", "image/jpeg"),
                photo("p3", "r2", "key-3", "image/jpeg"),
                photo("p4", "r3", "key-4", "image/jpeg")
        ));
    }

    private GoodsSurveyResponse tracked(String id, String touchJson) {
        String tracking = "{\"attribution\":{\"lastTouch\":" + touchJson
                + ",\"firstTouch\":" + touchJson + "},\"device\":{\"category\":\"mobile\"}}";
        return GoodsSurveyResponse.draft(
                id, "goods-2026-07", "2026-07-25-v2", "hash-" + id, "figure", tracking
        );
    }

    private GoodsSurveyResponse response(String id) {
        return GoodsSurveyResponse.draft(
                id, "goods-2026-07", "2026-07-25-v2", "hash-" + id, "figure", "{}"
        );
    }

    private GoodsSurveyFulfillment fulfillment(
            Long id,
            String responseId,
            String goodsType,
            String petName,
            LocalDateTime createdAt
    ) {
        GoodsSurveyFulfillment fulfillment = GoodsSurveyFulfillment.create(
                responseId,
                "idem-" + responseId,
                "conv-" + responseId,
                "{}",
                goodsType,
                null,
                petName,
                "보호자",
                "01012345678",
                "hash-" + responseId,
                "01234",
                "서울시 노원구",
                "",
                "2026-07-23",
                Instant.parse("2026-08-02T10:00:00Z"),
                true,
                23_900,
                1825
        );
        set(fulfillment, "id", id);
        set(fulfillment, "createdAt", createdAt);
        return fulfillment;
    }

    private GoodsSurveyPhoto photo(
            String id,
            String responseId,
            String objectKey,
            String contentType
    ) {
        GoodsSurveyPhoto photo = GoodsSurveyPhoto.pending(
                id, responseId, "client-" + id, objectKey, contentType, 100L,
                Instant.parse("2026-08-02T10:00:00Z")
        );
        set(photo, "status", GoodsSurveyPhotoStatus.CONFIRMED);
        return photo;
    }

    private void set(Object target, String fieldName, Object value) {
        try {
            Class<?> type = target.getClass();
            Field field = null;
            while (type != null && field == null) {
                try {
                    field = type.getDeclaredField(fieldName);
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                }
            }
            Objects.requireNonNull(field).setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
