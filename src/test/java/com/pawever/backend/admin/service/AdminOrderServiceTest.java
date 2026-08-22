package com.pawever.backend.admin.service;

import com.pawever.backend.admin.dto.AdminOrderDetail;
import com.pawever.backend.admin.dto.AdminOrderListResponse;
import com.pawever.backend.admin.entity.AdminAccessLog;
import com.pawever.backend.admin.entity.AdminRole;
import com.pawever.backend.admin.repository.AdminAccessLogRepository;
import com.pawever.backend.admin.security.AdminPrincipal;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.goodssurvey.entity.GoodsOrderPricing;
import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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

        AdminOrderListResponse response = service.list(ADMIN, Set.of(), null, 0, 20);

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
                service.list(PRODUCTION, Set.of(), "김포에버", 0, 20);

        assertThat(response.orders()).isEmpty();
        assertThat(response.totalCount()).isZero();
    }

    @Test
    void 관리자는_보호자_이름으로_찾을_수_있다() {
        when(fulfillmentRepository.findByStatusInOrderByCreatedAtDesc(any()))
                .thenReturn(List.of(order("PE-2026-000001", GoodsOrderStatus.IN_PRODUCTION)));

        AdminOrderListResponse response =
                service.list(ADMIN, Set.of(), "김포에버", 0, 20);

        assertThat(response.orders()).hasSize(1);
    }

    @Test
    void 제작팀도_주문번호와_반려동물_이름으로는_찾을_수_있다() {
        // 제작에 필요한 값이다. 이것까지 막으면 목록을 눈으로 훑는 수밖에 없다.
        when(fulfillmentRepository.findByStatusInOrderByCreatedAtDesc(any()))
                .thenReturn(List.of(order("PE-2026-000001", GoodsOrderStatus.IN_PRODUCTION)));

        assertThat(service.list(PRODUCTION, Set.of(), "몽이", 0, 20).orders()).hasSize(1);
        assertThat(service.list(PRODUCTION, Set.of(), "PE-2026-000001", 0, 20).orders())
                .hasSize(1);
    }

    @Test
    void 제작팀에게는_결제_전_주문이_보이지_않는다() {
        // 돈을 받지 않은 주문이 제작 대기열에 섞이면 만들지 않아도 될 것을 만든다.
        service.list(PRODUCTION, Set.of(), null, 0, 20);

        verify(fulfillmentRepository).findByStatusInOrderByCreatedAtDesc(
                argThat(statuses ->
                        !statuses.contains(GoodsOrderStatus.PAYMENT_PENDING)
                                && !statuses.contains(GoodsOrderStatus.PAYMENT_EXPIRED)
                                && !statuses.contains(GoodsOrderStatus.LEGACY_FREE)
                                && statuses.contains(GoodsOrderStatus.PAYMENT_COMPLETED))
        );
    }

    @Test
    void 제작팀이_결제_대기_상태를_지정해_요청해도_넓혀지지_않는다() {
        // 화면이 보내는 값을 그대로 믿으면 요청을 고쳐 볼 수 없는 것을 본다.
        service.list(PRODUCTION, Set.of(GoodsOrderStatus.PAYMENT_PENDING), null, 0, 20);

        verify(fulfillmentRepository, never()).findByStatusInOrderByCreatedAtDesc(any());
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
                "01234",
                "서울특별시 노원구 공릉로 232",
                "101호",
                "2026-07-23",
                NOW,
                true,
                orderNumber,
                GoodsOrderPricing.discounted(29_900, 5_000, "설문 참여 할인"),
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
