package com.pawever.backend.goodssurvey.service;

import com.pawever.backend.goodssurvey.config.GoodsSurveyProperties;
import com.pawever.backend.goodssurvey.entity.GoodsOrderPricing;
import com.pawever.backend.goodssurvey.entity.GoodsOrderSequence;
import com.pawever.backend.goodssurvey.repository.GoodsOrderSequenceRepository;
import com.pawever.backend.goodssurvey.repository.GoodsOrderStatusChangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoodsOrderServiceTest {

    /** 한국 시각으로 2026-08-20. 주문번호의 연도가 여기서 나온다. */
    private static final Instant NOW = Instant.parse("2026-08-20T05:00:00Z");

    @Mock private GoodsOrderSequenceRepository sequenceRepository;
    @Mock private GoodsOrderStatusChangeRepository statusChangeRepository;

    private GoodsOrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new GoodsOrderService(
                sequenceRepository,
                statusChangeRepository,
                new GoodsSurveyProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void 주문번호는_연도와_여섯_자리_순번으로_만든다() {
        when(sequenceRepository.findByYearForUpdate(2026))
                .thenReturn(Optional.of(GoodsOrderSequence.startOf(2026)));

        assertThat(orderService.issueOrderNumber()).isEqualTo("PE-2026-000001");
    }

    @Test
    void 같은_해에는_번호가_하나씩_올라간다() {
        GoodsOrderSequence sequence = GoodsOrderSequence.startOf(2026);
        when(sequenceRepository.findByYearForUpdate(2026)).thenReturn(Optional.of(sequence));

        assertThat(orderService.issueOrderNumber()).isEqualTo("PE-2026-000001");
        assertThat(orderService.issueOrderNumber()).isEqualTo("PE-2026-000002");
        assertThat(orderService.issueOrderNumber()).isEqualTo("PE-2026-000003");
    }

    @Test
    void 그_해_채번기가_없으면_새로_만들어_1번부터_준다() {
        when(sequenceRepository.findByYearForUpdate(2026)).thenReturn(Optional.empty());
        lenient().when(sequenceRepository.save(any(GoodsOrderSequence.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(orderService.issueOrderNumber()).isEqualTo("PE-2026-000001");
    }

    @Test
    void 주문번호의_연도는_한국_시각을_따른다() {
        // UTC 2025-12-31 15:00 은 한국에서 이미 2026-01-01 이다. UTC 로 세면
        // 새해 첫 주문이 지난해 번호를 받고, 그 번호는 이미 나간 번호와 겹친다.
        GoodsOrderService koreanNewYear = new GoodsOrderService(
                sequenceRepository,
                statusChangeRepository,
                new GoodsSurveyProperties(),
                Clock.fixed(Instant.parse("2025-12-31T15:00:00Z"), ZoneOffset.UTC)
        );
        when(sequenceRepository.findByYearForUpdate(2026))
                .thenReturn(Optional.of(GoodsOrderSequence.startOf(2026)));

        assertThat(koreanNewYear.issueOrderNumber()).startsWith("PE-2026-");
    }

    @Test
    void 설문에_답하고_왔으면_할인가를_적용한다() {
        GoodsOrderPricing pricing = orderService.priceFor(true);

        assertThat(pricing.listPriceKrw()).isEqualTo(29_900);
        assertThat(pricing.discountAmountKrw()).isEqualTo(5_000);
        assertThat(pricing.paymentAmountKrw()).isEqualTo(24_900);
        assertThat(pricing.promotionName()).isEqualTo("설문 참여 할인");
    }

    @Test
    void 설문을_건너뛰었으면_정상가를_적용한다() {
        // 답하는 수고와 값의 차이가 이 서비스가 설문을 받는 이유다.
        // 여기서 깎아 주면 설문을 끝까지 답할 까닭이 없어진다.
        GoodsOrderPricing pricing = orderService.priceFor(false);

        assertThat(pricing.paymentAmountKrw()).isEqualTo(29_900);
        assertThat(pricing.discountAmountKrw()).isZero();
        assertThat(pricing.promotionName()).isNull();
    }

    @Test
    void 채번은_행을_잠그고_읽는다() {
        // 잠그지 않으면 동시에 신청한 두 사람이 같은 번호를 받고,
        // 주문번호 UNIQUE 에 걸려 한쪽은 저장에 실패한다.
        when(sequenceRepository.findByYearForUpdate(anyInt()))
                .thenReturn(Optional.of(GoodsOrderSequence.startOf(2026)));

        orderService.issueOrderNumber();

        // findByYearForUpdate 는 @Lock(PESSIMISTIC_WRITE) 가 걸린 메서드다.
        // 잠금 없는 findById 로 갈아타면 이 검증이 깨진다.
        org.mockito.Mockito.verify(sequenceRepository).findByYearForUpdate(2026);
        org.mockito.Mockito.verifyNoMoreInteractions(sequenceRepository);
    }
}
