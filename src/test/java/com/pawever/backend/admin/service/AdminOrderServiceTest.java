package com.pawever.backend.admin.service;

import com.pawever.backend.admin.dto.AdminOrderDetail;
import com.pawever.backend.admin.dto.AdminOrderListResponse;
import com.pawever.backend.admin.entity.AdminAccessLog;
import com.pawever.backend.admin.entity.AdminRole;
import com.pawever.backend.admin.repository.AdminAccessLogRepository;
import com.pawever.backend.admin.security.AdminPrincipal;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.goodssurvey.entity.GoodsOrderPricing;
import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
import com.pawever.backend.goodssurvey.entity.GoodsDeliveryMethod;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhoto;
import com.pawever.backend.goodssurvey.repository.GoodsOrderStatusChangeRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyPhotoRepository;
import com.pawever.backend.goodssurvey.service.GoodsOrderService;
import com.pawever.backend.goodssurvey.service.GoodsSurveyPhotoStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 역할에 따라 무엇이 보이고 무엇을 할 수 있는지 고정한다.
 *
 * 화면이 알아서 가리게 두면 화면을 거치지 않고 요청을 보내는 것으로 넘어간다.
 * 그래서 서버가 막는지를 본다.
 */
@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T09:00:00Z");
    private static final AdminPrincipal ADMIN = new AdminPrincipal(1L, AdminRole.ADMIN);
    private static final AdminPrincipal PRODUCTION = new AdminPrincipal(2L, AdminRole.PRODUCTION);

    @Mock private GoodsSurveyFulfillmentRepository fulfillmentRepository;
    @Mock private GoodsSurveyPhotoRepository photoRepository;
    @Mock private GoodsOrderStatusChangeRepository statusChangeRepository;
    @Mock private AdminAccessLogRepository accessLogRepository;
    @Mock private GoodsSurveyPhotoStorage photoStorage;
    @Mock private GoodsOrderService orderService;
    @Mock private com.pawever.backend.payment.client.TossPaymentsClient tossClient;

    private AdminOrderService service;

    @BeforeEach
    void setUp() {
        service = new AdminOrderService(
                fulfillmentRepository,
                photoRepository,
                statusChangeRepository,
                accessLogRepository,
                photoStorage,
                orderService,
                tossClient,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        lenient().when(statusChangeRepository.findByResponseIdOrderByChangedAtAsc(anyString()))
                .thenReturn(List.of());
        lenient().when(accessLogRepository.findByOrderNumberOrderByAccessedAtDesc(anyString()))
                .thenReturn(List.of());
        lenient().when(photoRepository.findByResponseId(anyString())).thenReturn(List.of());
    }

    @Test
    void 목록에서_보호자_이름과_연락처는_가려서_내려간다() {
        // 목록을 훑어보는 데 전체 값이 필요하지 않다. 화면을 켜 두는 것만으로
        // 어깨너머와 화면 공유에 흘러간다.
        when(fulfillmentRepository.findByStatusInOrderByCreatedAtDesc(any()))
                .thenReturn(List.of(order("PE-2026-000001", GoodsOrderStatus.PAYMENT_COMPLETED)));

        AdminOrderListResponse response = service.list(ADMIN, Set.of(), null, null, 0, 20);

        var summary = response.orders().get(0);
        assertThat(summary.guardianNameMasked()).isEqualTo("김***");
        assertThat(summary.phoneMasked()).isEqualTo("010-12**-56**");
        // 반려견 이름은 가리지 않는다. 제작 화면에서 부르는 이름이다.
        assertThat(summary.petName()).isEqualTo("몽이");
    }

    @Test
    void 제작팀은_보호자_이름으로_찾을_수_없다() {
        // 목록에서 이름을 가리고 상세에서 비워 내려도, 검색이 이름에 걸리면
        // 이름을 넣어 보고 결과 수로 확인할 수 있다. 값을 못 보게 하는 것과
        // 값을 못 알아내게 하는 것은 다르다.
        when(fulfillmentRepository.findByStatusInOrderByCreatedAtDesc(any()))
                .thenReturn(List.of(order("PE-2026-000001", GoodsOrderStatus.IN_PRODUCTION)));

        AdminOrderListResponse response =
                service.list(PRODUCTION, Set.of(), "김포에버", null, 0, 20);

        assertThat(response.orders()).isEmpty();
        assertThat(response.totalCount()).isZero();
    }

    @Test
    void 관리자는_보호자_이름으로_찾을_수_있다() {
        when(fulfillmentRepository.findByStatusInOrderByCreatedAtDesc(any()))
                .thenReturn(List.of(order("PE-2026-000001", GoodsOrderStatus.IN_PRODUCTION)));

        AdminOrderListResponse response =
                service.list(ADMIN, Set.of(), "김포에버", null, 0, 20);

        assertThat(response.orders()).hasSize(1);
    }

    @Test
    void 제작팀도_주문번호와_반려동물_이름으로는_찾을_수_있다() {
        // 제작에 필요한 값이다. 이것까지 막으면 목록을 눈으로 훑는 수밖에 없다.
        when(fulfillmentRepository.findByStatusInOrderByCreatedAtDesc(any()))
                .thenReturn(List.of(order("PE-2026-000001", GoodsOrderStatus.IN_PRODUCTION)));

        assertThat(service.list(PRODUCTION, Set.of(), "몽이", null, 0, 20).orders()).hasSize(1);
        assertThat(service.list(PRODUCTION, Set.of(), "PE-2026-000001", null, 0, 20).orders())
                .hasSize(1);
    }

    @Test
    void 굿즈_코드를_사람이_읽는_이름으로_바꿔_내려준다() {
        // 화면에 backplate 라고 떠 있으면 담당자가 매번 무엇인지 되물어야 한다.
        when(fulfillmentRepository.findByStatusInOrderByCreatedAtDesc(any()))
                .thenReturn(List.of(order("PE-2026-000001", GoodsOrderStatus.LEGACY_FREE)));

        var summary = service.list(ADMIN, Set.of(), null, null, 0, 20).orders().get(0);

        assertThat(summary.goodsType()).isEqualTo("figure");
        assertThat(summary.goodsTypeLabel()).isEqualTo("3D 전신 피규어");
    }

    @Test
    void 제작팀도_1차_체험단을_볼_수_있다() {
        // 돈은 받지 않았지만 만들어 보내야 하는 물건이다. 안 보이면 100건을
        // 제작 화면에서 찾을 방법이 없다.
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000100"))
                .thenReturn(Optional.of(order("PE-2026-000100", GoodsOrderStatus.LEGACY_FREE)));

        assertThat(service.detail(PRODUCTION, "PE-2026-000100").orderNumber())
                .isEqualTo("PE-2026-000100");
    }

    @Test
    void 일차_체험단을_결제_완료로_바꿀_수_없다() {
        // 결제라는 것이 없던 주문이다. 결제 완료로 바꾸면 받지도 않은 돈이
        // 매출로 잡힌다.
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000100"))
                .thenReturn(Optional.of(order("PE-2026-000100", GoodsOrderStatus.LEGACY_FREE)));

        assertThatThrownBy(() -> service.changeStatus(
                ADMIN, "PE-2026-000100", GoodsOrderStatus.PAYMENT_COMPLETED, null))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 관리자가_결제_완료로_바꾸면_결제_시각이_남는다() {
        // 무통장 입금이라 은행에서 시각이 넘어오지 않는다. 사람이 통장을 보고
        // 누르는 그 시각이 우리가 가진 유일한 시각이다.
        //
        // 이 칸이 비면 화면만 어긋나는 게 아니다. 취소 버튼이 이 값으로
        // 열리므로, 돈은 받아 두고 환불할 자리가 화면에서 사라진다.
        GoodsSurveyFulfillment order =
                order("PE-2026-000123", GoodsOrderStatus.PAYMENT_PENDING);
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000123"))
                .thenReturn(Optional.of(order));

        service.changeStatus(
                ADMIN, "PE-2026-000123", GoodsOrderStatus.PAYMENT_COMPLETED, "통장 확인");

        assertThat(order.getStatus()).isEqualTo(GoodsOrderStatus.PAYMENT_COMPLETED);
        assertThat(order.getPaidAt()).isEqualTo(NOW);
        // 어떻게 받았는지도 남긴다. 나중에 PG 를 붙이면 카드와 섞인다.
        assertThat(order.getPaymentMethod()).isEqualTo("MANUAL");
    }

    @Test
    void 이미_결제된_건은_다시_눌러도_시각이_덮이지_않는다() {
        // 두 번 누르거나, 나중에 PG 웹훅이 같은 건을 다시 보낼 수 있다.
        // 그때마다 시각이 덮이면 실제로 받은 때를 잃는다.
        GoodsSurveyFulfillment order =
                order("PE-2026-000123", GoodsOrderStatus.PAYMENT_PENDING);
        order.markPaid(NOW.minusSeconds(3600), null, "MANUAL");
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000123"))
                .thenReturn(Optional.of(order));

        service.changeStatus(
                ADMIN, "PE-2026-000123", GoodsOrderStatus.PAYMENT_COMPLETED, null);

        assertThat(order.getPaidAt()).isEqualTo(NOW.minusSeconds(3600));
    }

    @Test
    void 결제_완료가_아닌_상태는_결제_시각을_건드리지_않는다() {
        // 제작 중·만료·실패로 옮기는 것은 돈을 받은 일과 무관하다.
        GoodsSurveyFulfillment order =
                order("PE-2026-000123", GoodsOrderStatus.PAYMENT_PENDING);
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000123"))
                .thenReturn(Optional.of(order));

        service.changeStatus(
                ADMIN, "PE-2026-000123", GoodsOrderStatus.PAYMENT_EXPIRED, null);

        assertThat(order.getStatus()).isEqualTo(GoodsOrderStatus.PAYMENT_EXPIRED);
        assertThat(order.getPaidAt()).isNull();
    }

    @Test
    void 일차_체험단을_제작_중으로는_바꿀_수_있다() {
        GoodsSurveyFulfillment order = order("PE-2026-000100", GoodsOrderStatus.LEGACY_FREE);
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000100"))
                .thenReturn(Optional.of(order));

        service.changeStatus(ADMIN, "PE-2026-000100", GoodsOrderStatus.IN_PRODUCTION, "착수");

        assertThat(order.getStatus()).isEqualTo(GoodsOrderStatus.IN_PRODUCTION);
    }

    @Test
    void 일차_체험단에도_송장을_넣을_수_있다() {
        // 무료로 드린 것도 보내야 한다. 송장을 못 넣으면 100건이 발송 완료로
        // 넘어가지 못한다.
        GoodsSurveyFulfillment order = order("PE-2026-000100", GoodsOrderStatus.LEGACY_FREE);
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000100"))
                .thenReturn(Optional.of(order));

        service.registerTracking(ADMIN, "PE-2026-000100", "CJ대한통운", "123456789");

        assertThat(order.getStatus()).isEqualTo(GoodsOrderStatus.SHIPPED);
    }

    @Test
    void 제작팀에게는_결제_전_주문이_보이지_않는다() {
        // 돈을 받지 않은 주문이 제작 대기열에 섞이면 만들지 않아도 될 것을 만든다.
        //
        // 1차 체험단은 예외다. 결제는 없었지만 실제로 만들어 보내야 하는
        // 물건이라 제작 화면에 있어야 한다. 결제를 기다리다 말았거나 실패한
        // 주문과는 다르다.
        service.list(PRODUCTION, Set.of(), null, null, 0, 20);

        verify(fulfillmentRepository).findByStatusInOrderByCreatedAtDesc(
                argThat(statuses ->
                        !statuses.contains(GoodsOrderStatus.PAYMENT_PENDING)
                                && !statuses.contains(GoodsOrderStatus.PAYMENT_EXPIRED)
                                && !statuses.contains(GoodsOrderStatus.PAYMENT_FAILED)
                                && statuses.contains(GoodsOrderStatus.LEGACY_FREE)
                                && statuses.contains(GoodsOrderStatus.PAYMENT_COMPLETED))
        );
    }

    @Test
    void 제작팀이_결제_대기_상태를_지정해_요청해도_넓혀지지_않는다() {
        // 화면이 보내는 값을 그대로 믿으면 요청을 고쳐 볼 수 없는 것을 본다.
        service.list(PRODUCTION, Set.of(GoodsOrderStatus.PAYMENT_PENDING), null, null, 0, 20);

        verify(fulfillmentRepository, never()).findByStatusInOrderByCreatedAtDesc(any());
    }

    @Test
    void 관리자가_상세를_열면_주소_열람이_이력에_남는다() {
        // 요구서 8장: 사진 다운로드·주문 취소·주소 전체 조회는 담당자·시각·
        // 주문번호를 이력으로 남긴다. 남기지 않으면 정보가 밖으로 나갔을 때
        // 누구를 거쳐 나갔는지 알 방법이 없다.
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000001"))
                .thenReturn(Optional.of(order("PE-2026-000001", GoodsOrderStatus.PAYMENT_COMPLETED)));

        service.detail(ADMIN, "PE-2026-000001");

        ArgumentCaptor<AdminAccessLog> captured = ArgumentCaptor.forClass(AdminAccessLog.class);
        verify(accessLogRepository).save(captured.capture());
        assertThat(captured.getValue().getAction()).isEqualTo("ADDRESS_VIEW");
        assertThat(captured.getValue().getOrderNumber()).isEqualTo("PE-2026-000001");
        assertThat(captured.getValue().getAdminAccountId()).isEqualTo(1L);
    }

    @Test
    void 제작팀_상세는_주소_열람_이력을_남기지_않는다() {
        // 주소가 내려가지 않으니 남길 것도 없다. 남기면 이력이 부풀어서
        // 실제로 주소를 본 기록을 찾기 어려워진다.
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000001"))
                .thenReturn(Optional.of(order("PE-2026-000001", GoodsOrderStatus.IN_PRODUCTION)));

        service.detail(PRODUCTION, "PE-2026-000001");

        verify(accessLogRepository, never()).save(any());
    }

    @Test
    void 상세_조회는_읽기_전용_트랜잭션이_아니다() throws Exception {
        // 이력을 남기므로 쓰기가 필요하다. readOnly 로 되돌리면 화면은 그대로
        // 동작하고 이력만 조용히 사라진다. 그때는 아무 시험도 깨지지 않는다.
        org.springframework.transaction.annotation.Transactional annotation =
                AdminOrderService.class
                        .getMethod("detail", AdminPrincipal.class, String.class)
                        .getAnnotation(org.springframework.transaction.annotation.Transactional.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.readOnly()).isFalse();
    }

    @Test
    void 올리지_않은_사진_자리도_비어_있는_채로_내려간다() {
        // 요구서: 선택 사진이 없으면 관리자 화면에 반드시 "미기입"으로 표시한다.
        // 화면이 그렇게 그리려면 빈 자리도 함께 내려와야 한다.
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000001"))
                .thenReturn(Optional.of(order("PE-2026-000001", GoodsOrderStatus.PAYMENT_COMPLETED)));

        AdminOrderDetail detail = service.detail(ADMIN, "PE-2026-000001");

        assertThat(detail.photos()).hasSize(5);
        assertThat(detail.photos()).extracting(AdminOrderDetail.Photo::slot)
                .containsExactly(1, 2, 3, 4, 5);
        assertThat(detail.photos()).allMatch(photo -> !photo.filled());
    }

    @Test
    void 제작팀_상세에는_연락처와_주소가_담기지_않는다() {
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000001"))
                .thenReturn(Optional.of(order("PE-2026-000001", GoodsOrderStatus.IN_PRODUCTION)));

        AdminOrderDetail detail = service.detail(PRODUCTION, "PE-2026-000001");

        assertThat(detail.shipping()).isNull();
        assertThat(detail.payment()).isNull();
        assertThat(detail.marketing()).isNull();
        // 만드는 데 필요한 것은 남는다.
        assertThat(detail.petName()).isEqualTo("몽이");
        assertThat(detail.photos()).hasSize(5);
    }

    @Test
    void 관리자_상세에는_주소와_결제가_담긴다() {
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000001"))
                .thenReturn(Optional.of(order("PE-2026-000001", GoodsOrderStatus.PAYMENT_COMPLETED)));

        AdminOrderDetail detail = service.detail(ADMIN, "PE-2026-000001");

        assertThat(detail.shipping()).isNotNull();
        assertThat(detail.shipping().phone()).isEqualTo("01012345678");
        assertThat(detail.payment()).isNotNull();
    }

    @Test
    void 제작팀이_결제_전_주문을_직접_열면_없는_것과_같게_답한다() {
        // 못 본다고 알려주면 어떤 주문이 있는지는 알게 된다.
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000009"))
                .thenReturn(Optional.of(order("PE-2026-000009", GoodsOrderStatus.PAYMENT_PENDING)));

        assertThatThrownBy(() -> service.detail(PRODUCTION, "PE-2026-000009"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("찾을 수 없습니다");
    }

    @Test
    void 사진_슬롯은_다섯_자리로_채우고_빈_자리를_표시한다() {
        // 요구서 3-1: 사진 2~5 가 없으면 관리자에 "미기입" 으로 보여준다.
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000001"))
                .thenReturn(Optional.of(order("PE-2026-000001", GoodsOrderStatus.PAYMENT_COMPLETED)));
        when(photoRepository.findByResponseId("resp-1"))
                .thenReturn(List.of(photo("photo-1"), photo("photo-2")));

        AdminOrderDetail detail = service.detail(ADMIN, "PE-2026-000001");

        assertThat(detail.photos()).hasSize(5);
        assertThat(detail.photos().get(0).filled()).isTrue();
        assertThat(detail.photos().get(1).filled()).isTrue();
        assertThat(detail.photos().get(2).filled()).isFalse();
        assertThat(detail.photos().get(4).filled()).isFalse();
    }

    @Test
    void 사진_링크를_내주면_누가_가져갔는지_남긴다() {
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000001"))
                .thenReturn(Optional.of(order("PE-2026-000001", GoodsOrderStatus.IN_PRODUCTION)));
        when(photoRepository.findByResponseId("resp-1")).thenReturn(List.of(photo("photo-1")));
        when(photoStorage.presignDownload(any(), any(Duration.class), any(Instant.class)))
                .thenReturn(new GoodsSurveyPhotoStorage.PresignedDownload(
                        "https://example.com/photo", NOW.plusSeconds(300)));

        var response = service.photoLinks(PRODUCTION, "PE-2026-000001");

        assertThat(response.photos()).hasSize(1);
        verify(accessLogRepository).save(any(AdminAccessLog.class));
    }

    @Test
    void 제작팀은_발송_완료로_바꿀_수_없다() {
        // 송장을 넣고 보내는 일은 관리자가 한다.
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000001"))
                .thenReturn(Optional.of(order("PE-2026-000001", GoodsOrderStatus.IN_PRODUCTION)));

        assertThatThrownBy(() ->
                service.changeStatus(PRODUCTION, "PE-2026-000001", GoodsOrderStatus.SHIPPED, null))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 제작팀은_제작_중으로는_바꿀_수_있다() {
        GoodsSurveyFulfillment order = order("PE-2026-000001", GoodsOrderStatus.PAYMENT_COMPLETED);
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000001"))
                .thenReturn(Optional.of(order));

        service.changeStatus(PRODUCTION, "PE-2026-000001", GoodsOrderStatus.IN_PRODUCTION, "착수");

        assertThat(order.getStatus()).isEqualTo(GoodsOrderStatus.IN_PRODUCTION);
        verify(orderService).recordManualChange(
                any(), any(), any(), any(), any());
    }

    @Test
    void 상태_변경으로는_취소할_수_없다() {
        // 결제 취소가 성공해야 취소로 넘어간다. 상태만 바꾸면 돈은 돌려주지 않고
        // 취소된 것으로 보인다.
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000001"))
                .thenReturn(Optional.of(order("PE-2026-000001", GoodsOrderStatus.PAYMENT_COMPLETED)));

        assertThatThrownBy(() ->
                service.changeStatus(ADMIN, "PE-2026-000001", GoodsOrderStatus.CANCELED, null))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 송장을_넣으면_발송_완료로_넘어간다() {
        GoodsSurveyFulfillment order = order("PE-2026-000001", GoodsOrderStatus.IN_PRODUCTION);
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000001"))
                .thenReturn(Optional.of(order));

        service.registerTracking(ADMIN, "PE-2026-000001", "CJ대한통운", "123456789");

        assertThat(order.getStatus()).isEqualTo(GoodsOrderStatus.SHIPPED);
        assertThat(order.getTrackingNumber()).isEqualTo("123456789");
    }

    @Test
    void 제작팀은_송장을_넣을_수_없다() {
        assertThatThrownBy(() ->
                service.registerTracking(PRODUCTION, "PE-2026-000001", "CJ대한통운", "123456789"))
                .isInstanceOf(CustomException.class);
        // 주문을 찾아보기도 전에 막는다. 볼 수 없는 사람에게 존재 여부를
        // 알려 주지 않는 편이 낫다.
        verify(fulfillmentRepository, never()).findByOrderNumber(any());
    }

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }

    @Test
    void 사진을_한_번에_받으면_자리_번호가_파일_이름에_남는다() {
        // 압축을 풀면 순서가 섞인다. 제작 화면에서 부르는 번호와 맞아야
        // 어느 사진인지 알 수 있다.
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000001"))
                .thenReturn(Optional.of(order("PE-2026-000001", GoodsOrderStatus.IN_PRODUCTION)));
        when(photoRepository.findByResponseId(anyString()))
                .thenReturn(List.of(photo("a"), photo("b")));
        when(photoStorage.download(anyString())).thenReturn("사진".getBytes());

        var archive = service.photoArchive(ADMIN, "PE-2026-000001");

        assertThat(archive.fileName()).isEqualTo("PE-2026-000001_photos.zip");
        List<String> names = new ArrayList<>();
        try (var zip = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(archive.bytes()))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                names.add(entry.getName());
            }
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
        assertThat(names)
                .containsExactly("PE-2026-000001_1.jpg", "PE-2026-000001_2.jpg");
    }

    @Test
    void 사진을_한_번에_받아도_이력이_남는다() {
        // 파일이 실제로 나가는 것은 이쪽이다. 여기서 빠뜨리면 이력이 반쪽이 된다.
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000001"))
                .thenReturn(Optional.of(order("PE-2026-000001", GoodsOrderStatus.IN_PRODUCTION)));
        when(photoRepository.findByResponseId(anyString())).thenReturn(List.of(photo("a")));
        when(photoStorage.download(anyString())).thenReturn("사진".getBytes());

        service.photoArchive(PRODUCTION, "PE-2026-000001");

        ArgumentCaptor<AdminAccessLog> captured = ArgumentCaptor.forClass(AdminAccessLog.class);
        verify(accessLogRepository).save(captured.capture());
        assertThat(captured.getValue().getAction()).isEqualTo("PHOTO_DOWNLOAD");
    }

    @Test
    void 제작팀이_결제_전_주문의_사진을_한_번에_받으려_해도_막힌다() {
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000009"))
                .thenReturn(Optional.of(order("PE-2026-000009", GoodsOrderStatus.PAYMENT_PENDING)));

        assertThatThrownBy(() -> service.photoArchive(PRODUCTION, "PE-2026-000009"))
                .isInstanceOf(CustomException.class);
        verify(photoStorage, never()).download(anyString());
    }

    @Test
    void 제출일로_목록을_좁힌다() {
        // 담당자가 고르는 날짜는 한국 날짜다. UTC 자정으로 자르면 하루가 밀린다.
        when(fulfillmentRepository.findByStatusInOrderByCreatedAtDesc(any()))
                .thenReturn(List.of(order("PE-2026-000001", GoodsOrderStatus.PAYMENT_COMPLETED)));

        var 포함 = new AdminOrderService.OrderFilter(
                null, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 20), null);
        var 이후 = new AdminOrderService.OrderFilter(
                null, LocalDate.of(2026, 8, 21), null, null);

        assertThat(service.list(ADMIN, Set.of(), null, 포함, 0, 20).orders()).hasSize(1);
        assertThat(service.list(ADMIN, Set.of(), null, 이후, 0, 20).orders()).isEmpty();
    }

    @Test
    void 굿즈_종류로_목록을_좁힌다() {
        when(fulfillmentRepository.findByStatusInOrderByCreatedAtDesc(any()))
                .thenReturn(List.of(order("PE-2026-000001", GoodsOrderStatus.PAYMENT_COMPLETED)));

        var 맞음 = new AdminOrderService.OrderFilter("figure", null, null, null);
        var 다름 = new AdminOrderService.OrderFilter("acrylic", null, null, null);

        assertThat(service.list(ADMIN, Set.of(), null, 맞음, 0, 20).orders()).hasSize(1);
        assertThat(service.list(ADMIN, Set.of(), null, 다름, 0, 20).orders()).isEmpty();
    }

    @Test
    void 사진_수로_목록을_좁힌다() {
        // 사진이 덜 온 주문을 찾을 때 쓴다.
        when(fulfillmentRepository.findByStatusInOrderByCreatedAtDesc(any()))
                .thenReturn(List.of(order("PE-2026-000001", GoodsOrderStatus.PAYMENT_COMPLETED)));
        when(photoRepository.findByResponseId(anyString()))
                .thenReturn(List.of(photo("a"), photo("b")));

        var 두장이상 = new AdminOrderService.OrderFilter(null, null, null, 2);
        var 세장이상 = new AdminOrderService.OrderFilter(null, null, null, 3);

        assertThat(service.list(ADMIN, Set.of(), null, 두장이상, 0, 20).orders()).hasSize(1);
        assertThat(service.list(ADMIN, Set.of(), null, 세장이상, 0, 20).orders()).isEmpty();
    }

    @Test
    void 결제_취소가_성공해야_주문이_취소된다() {
        GoodsSurveyFulfillment order = paidOrder(GoodsOrderStatus.PAYMENT_COMPLETED);
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000101"))
                .thenReturn(Optional.of(order));

        service.cancel(ADMIN, "PE-2026-000101", "고객 요청");

        assertThat(order.getStatus()).isEqualTo(GoodsOrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo("고객 요청");
    }

    @Test
    void 결제_취소가_실패하면_취소_처리_실패로_남긴다() {
        // 상태를 먼저 바꾸면 돈은 그대로 두고 취소된 것으로 읽힌다.
        // 사람이 확인해야 하는 건이라 조용히 지나가게 두지 않는다.
        GoodsSurveyFulfillment order = paidOrder(GoodsOrderStatus.IN_PRODUCTION);
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000101"))
                .thenReturn(Optional.of(order));
        when(tossClient.cancel(anyString(), anyString(), anyString()))
                .thenThrow(new CustomException(ErrorCode.PAYMENT_CANCEL_FAILED));

        assertThatThrownBy(() -> service.cancel(ADMIN, "PE-2026-000101", "제작 불가"))
                .isInstanceOf(CustomException.class);

        assertThat(order.getStatus()).isEqualTo(GoodsOrderStatus.CANCEL_FAILED);
        assertThat(order.getCancelReason()).isEqualTo("제작 불가");
    }

    @Test
    void 취소할_수_없는_상태면_토스를_부르지도_않는다() {
        // 결제 대기·발송 완료·이미 취소된 건이다. 부르면 엉뚱한 취소가 나간다.
        GoodsSurveyFulfillment order = paidOrder(GoodsOrderStatus.SHIPPED);
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000101"))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(ADMIN, "PE-2026-000101", "고객 요청"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_NOT_CANCELABLE);

        verify(tossClient, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void 결제한_적_없는_주문은_취소하지_않는다() {
        // 1차 체험단처럼 결제 번호가 없는 건이다. 돌려줄 돈이 없다.
        GoodsSurveyFulfillment order = order("PE-2026-000100", GoodsOrderStatus.IN_PRODUCTION);
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000100"))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(ADMIN, "PE-2026-000100", "고객 요청"))
                .isInstanceOf(CustomException.class);

        verify(tossClient, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void 제작팀은_취소할_수_없다() {
        assertThatThrownBy(() -> service.cancel(PRODUCTION, "PE-2026-000101", "고객 요청"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(tossClient, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void 같은_주문을_두_번_눌러도_이중_환불이_되지_않게_주문번호를_멱등키로_보낸다() {
        // 토스가 같은 멱등 키에 앞의 결과를 그대로 돌려준다.
        GoodsSurveyFulfillment order = paidOrder(GoodsOrderStatus.PAYMENT_COMPLETED);
        when(fulfillmentRepository.findByOrderNumber("PE-2026-000101"))
                .thenReturn(Optional.of(order));

        service.cancel(ADMIN, "PE-2026-000101", "고객 요청");

        verify(tossClient).cancel("toss-pay-1", "고객 요청", "PE-2026-000101");
    }

    /** 결제까지 끝난 주문. */
    private GoodsSurveyFulfillment paidOrder(GoodsOrderStatus status) {
        GoodsSurveyFulfillment fulfillment = order("PE-2026-000101", GoodsOrderStatus.PAYMENT_PENDING);
        fulfillment.markPaid(NOW, "toss-pay-1", "간편결제");
        fulfillment.changeStatus(status);
        return fulfillment;
    }

    private GoodsSurveyFulfillment order(String orderNumber, GoodsOrderStatus status) {
        GoodsSurveyFulfillment fulfillment = GoodsSurveyFulfillment.create(
                "resp-1",
                "idem-1",
                "conv-1",
                "{}",
                "figure",
                null,
                "몽이",
                "김포에버",
                "01012345678",
                "phone-hash",
                GoodsDeliveryMethod.SHIPPING,
                "01234",
                "서울특별시 노원구 공릉로 232",
                "101호",
                "2026-07-23",
                NOW,
                true,
                orderNumber,
                GoodsOrderPricing.discounted(29_900, 5_000, "설문 참여 할인", 3_000),
                false,
                "marketing-v1",
                30,
                1825
        );
        fulfillment.changeStatus(status);
        return fulfillment;
    }

    private GoodsSurveyPhoto photo(String id) {
        return GoodsSurveyPhoto.pending(
                id, "resp-1", "client-" + id, "goods/" + id + ".jpg",
                "image/jpeg", 1024L, NOW.plusSeconds(600));
    }
}
