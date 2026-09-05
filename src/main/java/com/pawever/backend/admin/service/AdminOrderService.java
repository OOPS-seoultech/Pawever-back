package com.pawever.backend.admin.service;

import com.pawever.backend.admin.dto.AdminOrderDetail;
import com.pawever.backend.admin.dto.AdminOrderListResponse;
import com.pawever.backend.admin.dto.AdminOrderSummary;
import com.pawever.backend.admin.dto.AdminPhotoDownloadResponse;
import com.pawever.backend.admin.entity.AdminAccessLog;
import com.pawever.backend.admin.entity.AdminRole;
import com.pawever.backend.admin.repository.AdminAccessLogRepository;
import com.pawever.backend.admin.security.AdminPrincipal;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.goodssurvey.config.GoodsSurveyProperties;
import com.pawever.backend.goodssurvey.entity.GoodsDeliveryMethod;
import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
import com.pawever.backend.goodssurvey.entity.GoodsOrderStatusChange;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhoto;
import com.pawever.backend.goodssurvey.entity.GoodsTypeNames;
import com.pawever.backend.payment.client.TossPaymentsClient;
import com.pawever.backend.goodssurvey.repository.GoodsOrderStatusChangeRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyPhotoRepository;
import com.pawever.backend.goodssurvey.service.GoodsOrderService;
import com.pawever.backend.goodssurvey.service.GoodsSurveyPhotoStorage;
import com.pawever.backend.goodssurvey.service.GoodsSurveyRetentionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 관리자 화면이 부르는 주문 조회와 수정.
 *
 * 무엇을 볼 수 있는지를 여기서 정한다. 화면이 알아서 가리게 두면, 화면을
 * 거치지 않고 요청을 보내는 것으로 넘어간다.
 *
 * 이름과 연락처는 암호화해 저장한다. 그래서 검색어로 데이터베이스를 훑을 수
 * 없고, 상태와 기간으로 좁힌 뒤 그 안에서 찾는다. 주문이 수만 건이 되면
 * 이 방식으로는 버티지 못하므로 그때는 검색용 값을 따로 두어야 한다.
 */
@Service
@RequiredArgsConstructor
public class AdminOrderService {

    /** 담당자에게 내주는 사진 링크가 열려 있는 시간. */
    private static final Duration PHOTO_LINK_TTL = Duration.ofMinutes(5);

    private static final int MAX_PHOTO_SLOTS = 5;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 제작팀 계정에 보이는 상태. */
    private static final Set<GoodsOrderStatus> PRODUCTION_VISIBLE =
            EnumSet.copyOf(Arrays.stream(GoodsOrderStatus.values())
                    .filter(GoodsOrderStatus::isVisibleToProduction)
                    .toList());

    /**
     * 한 요청에 묶을 수 있는 최대 건수.
     *
     * 한 번에 다 보내면 트랜잭션이 오래 열려 있고, 실패했을 때 어디까지
     * 됐는지도 크다. 1차 체험단 100건이 한 번에 들어가고도 남는다.
     */
    private static final int MAX_BULK_SIZE = 500;

    /** 제작팀이 스스로 바꿀 수 있는 상태. 발송과 취소는 관리자만 한다. */
    private static final Set<GoodsOrderStatus> PRODUCTION_SETTABLE =
            EnumSet.of(GoodsOrderStatus.IN_PRODUCTION);

    /**
     * 수령 완료로 갈 수 있는 상태.
     *
     * 돈이 확인된 뒤라야 한다. 결제 전 주문을 건넨 것으로 적으면 받지 않은
     * 물건값이 없어진다. 제작 중을 거치지 않고 바로 건네는 일도 있어서 결제
     * 완료에서도 열어 둔다.
     */
    private static final Set<GoodsOrderStatus> PICKUP_COMPLETABLE =
            EnumSet.of(GoodsOrderStatus.PAYMENT_COMPLETED, GoodsOrderStatus.IN_PRODUCTION);

    private final GoodsSurveyFulfillmentRepository fulfillmentRepository;
    private final GoodsSurveyPhotoRepository photoRepository;
    private final GoodsOrderStatusChangeRepository statusChangeRepository;
    private final AdminAccessLogRepository accessLogRepository;
    private final GoodsSurveyPhotoStorage photoStorage;
    private final GoodsOrderService orderService;
    private final TossPaymentsClient tossClient;
    private final GoodsSurveyRetentionService retentionService;
    private final GoodsSurveyProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminOrderListResponse list(
            AdminPrincipal principal,
            Set<GoodsOrderStatus> requestedStatuses,
            String query,
            OrderFilter filter,
            int page,
            int size
    ) {
        Set<GoodsOrderStatus> visible = visibleStatuses(principal, requestedStatuses);
        if (visible.isEmpty()) {
            return new AdminOrderListResponse(List.of(), 0, page, size, summarize());
        }

        List<GoodsSurveyFulfillment> found =
                fulfillmentRepository.findByStatusInOrderByCreatedAtDesc(visible);
        List<GoodsSurveyFulfillment> matched = found.stream()
                .filter(fulfillment -> matches(fulfillment, query, principal.role()))
                .filter(fulfillment -> filter == null || filter.accepts(this, fulfillment))
                .toList();

        int from = Math.min(page * size, matched.size());
        int to = Math.min(from + size, matched.size());
        List<AdminOrderSummary> orders = matched.subList(from, to).stream()
                .map(this::toSummary)
                .toList();

        return new AdminOrderListResponse(orders, matched.size(), page, size, summarize());
    }

    /**
     * 주문 하나를 연다.
     *
     * 읽기 전용이 아니다. 주소 전체를 열어 본 일을 이력에 남기기 때문이다.
     * readOnly 로 되돌리면 이력만 조용히 사라지고 화면은 그대로 동작한다.
     */
    @Transactional
    public AdminOrderDetail detail(AdminPrincipal principal, String orderNumber) {
        GoodsSurveyFulfillment fulfillment = findVisible(principal, orderNumber);
        List<GoodsSurveyPhoto> photos =
                photoRepository.findByResponseId(fulfillment.getResponseId());

        boolean canSeeShipping = principal.role() == AdminRole.ADMIN;
        if (canSeeShipping) {
            // 제작팀에게는 주소가 내려가지 않으니 남길 것도 없다.
            accessLogRepository.save(AdminAccessLog.of(
                    principal.accountId(),
                    "ADDRESS_VIEW",
                    orderNumber,
                    clock.instant()
            ));
        }
        return new AdminOrderDetail(
                fulfillment.getOrderNumber(),
                submittedAt(fulfillment),
                fulfillment.getStatus(),
                fulfillment.getStatus().label(),
                fulfillment.getGoodsType(),
                GoodsTypeNames.of(fulfillment.getGoodsType()),
                fulfillment.getPetName(),
                new AdminOrderDetail.Pricing(
                        fulfillment.getListPriceKrw(),
                        fulfillment.getDiscountAmountKrw(),
                        fulfillment.getPromotionName(),
                        fulfillment.getShippingFeeKrw(),
                        fulfillment.getPaymentAmountKrw()
                ),
                canSeeShipping
                        ? new AdminOrderDetail.Payment(
                        fulfillment.getPaymentMethod(),
                        fulfillment.getPaidAt(),
                        fulfillment.getPaymentExpiresAt(),
                        fulfillment.getCancelReason(),
                        hasPaymentKey(fulfillment))
                        : null,
                canSeeShipping ? shippingOf(fulfillment) : null,
                photoSlots(photos),
                canSeeShipping
                        ? new AdminOrderDetail.Marketing(
                        fulfillment.isMarketingConsent(),
                        fulfillment.getMarketingConsentedAt(),
                        fulfillment.getMarketingConsentVersion())
                        : null,
                statusHistory(fulfillment.getResponseId()),
                canSeeShipping ? accessLogs(orderNumber) : List.of()
        );
    }

    /**
     * 사진을 화면에 띄울 링크를 내준다.
     *
     * 누가 언제 어느 주문의 사진을 봤는지 남긴다. 개인정보처리시스템의
     * 접속기록이라 화면 편의로 뺄 수 있는 것이 아니다.
     *
     * 파일로 가져가는 것(photoArchive)과 이름을 나눈다. 상세를 열면 사진이
     * 바로 보이므로 이 기록은 훑어본 것까지 포함한다. 한 이름으로 남기면
     * 훑어본 것과 실제로 가져간 것이 섞여, 사진이 밖으로 나갔을 때 누구를
     * 봐야 하는지 알 수 없어진다.
     */
    @Transactional
    public AdminPhotoDownloadResponse photoLinks(AdminPrincipal principal, String orderNumber) {
        GoodsSurveyFulfillment fulfillment = findVisible(principal, orderNumber);
        List<GoodsSurveyPhoto> photos =
                photoRepository.findByResponseId(fulfillment.getResponseId());

        Instant now = clock.instant();
        Instant expiresAt = now.plus(PHOTO_LINK_TTL);
        List<AdminPhotoDownloadResponse.Item> items = new ArrayList<>();
        for (int index = 0; index < photos.size(); index++) {
            var link = photoStorage.presignDownload(
                    photos.get(index).getObjectKey(),
                    PHOTO_LINK_TTL,
                    expiresAt
            );
            items.add(new AdminPhotoDownloadResponse.Item(index + 1, link.url(), link.expiresAt()));
        }

        accessLogRepository.save(AdminAccessLog.of(
                principal.accountId(),
                "PHOTO_VIEW",
                orderNumber,
                now
        ));
        return new AdminPhotoDownloadResponse(items);
    }

    /**
     * 사진을 한 번에 내려받는다.
     *
     * 다섯 장을 한 장씩 누르면 다섯 번 눌러야 하고, 그때마다 만료 5분짜리
     * 링크를 새로 받아야 한다. 제작에 넘길 때는 통째로 받는 편이 낫다.
     *
     * 링크를 내주는 것과 같은 이력을 남긴다. 파일이 실제로 나가는 것은
     * 이쪽이므로 여기서 빠뜨리면 이력이 반쪽이 된다.
     */
    @Transactional
    public PhotoArchive photoArchive(AdminPrincipal principal, String orderNumber) {
        GoodsSurveyFulfillment fulfillment = findVisible(principal, orderNumber);
        List<GoodsSurveyPhoto> photos =
                photoRepository.findByResponseId(fulfillment.getResponseId());
        if (photos.isEmpty()) {
            throw new CustomException(ErrorCode.SURVEY_INVALID_STATE);
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (int index = 0; index < photos.size(); index++) {
                GoodsSurveyPhoto photo = photos.get(index);
                // 자리 번호를 파일 이름에 넣는다. 압축을 풀면 순서가 섞이는데,
                // 제작 화면에서 부르는 번호와 맞아야 어느 사진인지 알 수 있다.
                zip.putNextEntry(new ZipEntry(
                        orderNumber + "_" + (index + 1) + extensionOf(photo.getContentType())));
                zip.write(photoStorage.download(photo.getObjectKey()));
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }

        accessLogRepository.save(AdminAccessLog.of(
                principal.accountId(),
                "PHOTO_DOWNLOAD",
                orderNumber,
                clock.instant()
        ));
        return new PhotoArchive(orderNumber + "_photos.zip", buffer.toByteArray());
    }

    /** 압축 파일 하나. */
    public record PhotoArchive(String fileName, byte[] bytes) {
    }

    private String extensionOf(String contentType) {
        if (contentType == null) {
            return "";
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/heic" -> ".heic";
            // 모르는 형식은 확장자 없이 둔다. 잘못 붙이면 열리지 않는다.
            default -> "";
        };
    }

    /**
     * 상태를 손으로 옮긴다.
     *
     * 어디서 어디로 갈 수 있는지는 {@link GoodsOrderStatus#canManuallyBecome}
     * 이 정한다. 여기서는 그 표를 따르고, 역할과 결제 시각만 더 본다.
     */
    @Transactional
    public void changeStatus(
            AdminPrincipal principal,
            String orderNumber,
            GoodsOrderStatus next,
            String memo
    ) {
        GoodsSurveyFulfillment fulfillment = findVisible(principal, orderNumber);
        if (principal.role() == AdminRole.PRODUCTION && !PRODUCTION_SETTABLE.contains(next)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        GoodsOrderStatus before = fulfillment.getStatus();
        if (next == before) {
            // 두 번 누른 것이다. "결제 완료 → 결제 완료" 가 이력에 남으면 읽는
            // 사람이 무슨 일이 있었는지 찾게 된다.
            return;
        }
        if (!before.canManuallyBecome(next)) {
            throw new CustomException(ErrorCode.ORDER_STATUS_TRANSITION_NOT_ALLOWED);
        }

        if (next == GoodsOrderStatus.PAYMENT_COMPLETED && before == GoodsOrderStatus.PAYMENT_PENDING) {
            // 상태만 옮기면 결제 시각이 비어 있는 채로 결제 완료가 된다.
            //
            // 그 칸은 보기 좋으라고 있는 것이 아니다. 결제 승인과 웹훅이
            // "이미 받은 건인지"를 이 값으로 판단하고, 관리자 화면의 취소
            // 버튼도 이 값이 있어야 열린다. 비어 있으면 돈은 받아 두고
            // 환불할 자리가 화면에서 사라진다.
            //
            // 무통장 입금이라 은행에서 시각이 넘어오지 않는다. 사람이 통장을
            // 보고 누르는 이 시각이 우리가 가진 유일한 시각이다. 이미 값이
            // 있으면 markPaid 가 아무것도 하지 않으므로 나중에 PG 웹훅이
            // 겹쳐도 처음 받은 때가 덮이지 않는다.
            fulfillment.markPaid(clock.instant(), null, "MANUAL");
        } else if (next == GoodsOrderStatus.PAYMENT_COMPLETED) {
            // 제작 중에서 되돌리는 길이다. 되돌린 것이지 새로 받은 것이 아니라
            // 결제 시각은 그대로 둔다. 결제가 없던 주문(1차 체험단이 제작 중으로
            // 넘어온 건)은 되돌릴 결제도 없다 — 옮기면 받지도 않은 돈이 지금
            // 받은 것으로 적힌다.
            if (fulfillment.getPaidAt() == null) {
                throw new CustomException(ErrorCode.ORDER_STATUS_TRANSITION_NOT_ALLOWED);
            }
            fulfillment.changeStatus(next);
        } else {
            fulfillment.changeStatus(next);
        }
        if (GoodsOrderStatus.releasesSlot().contains(next)) {
            // 만료·실패는 계약이 성립하지 않은 것이다. 시간이 지나 저절로 만료된
            // 건은 스케줄러가 사진을 지우는데, 사람이 같은 상태로 옮긴 건은
            // 아무도 지우지 않았다. 같은 상태면 같은 일이다.
            retentionService.discardVoidOrderData(fulfillment);
        }
        orderService.recordManualChange(
                fulfillment.getResponseId(),
                before,
                next,
                String.valueOf(principal.accountId()),
                memo
        );
    }

    /**
     * 주문을 취소한다. 관리자만 한다.
     *
     * 결제 대행사 키가 있는 주문은 상태를 먼저 바꾸지 않는다. 결제 취소가
     * 실패했는데 주문만 취소로 보이면 돈은 그대로 두고 취소된 것으로 읽힌다.
     * 토스가 취소를 받아들인 뒤에만 취소로 옮긴다. 실패하면 취소 처리 실패로
     * 남긴다 — 사람이 확인해야 하는 건이라 조용히 지나가게 두지 않는다.
     * 멱등 키에 주문번호를 쓰므로 같은 주문을 두 번 눌러도 이중 환불이 되지
     * 않는다.
     *
     * 계좌이체로 받은 주문(결제 키 없음)은 대행사를 부를 것이 없다. 환불은
     * 사람이 은행에서 먼저 하고, 여기서는 그 사실을 적는다. 이 길이 없으면
     * 지금 받는 주문은 하나도 취소할 수 없다 — 환불을 해 줘도 결제 완료로
     * 남아 제작 대기열과 정원 한 자리를 계속 차지한다.
     *
     * 결제 자체가 없던 주문(1차 체험단이 제작 중으로 넘어온 건)은 돌려줄
     * 돈이 없으므로 취소할 것도 없다.
     */
    @Transactional
    public void cancel(AdminPrincipal principal, String orderNumber, String reason) {
        requireAdmin(principal);
        GoodsSurveyFulfillment fulfillment = findVisible(principal, orderNumber);

        if (!fulfillment.getStatus().isCancelable()) {
            throw new CustomException(ErrorCode.PAYMENT_NOT_CANCELABLE);
        }
        if (fulfillment.getPaidAt() == null) {
            throw new CustomException(ErrorCode.PAYMENT_NOT_CANCELABLE);
        }

        GoodsOrderStatus before = fulfillment.getStatus();
        if (!hasPaymentKey(fulfillment)) {
            markCanceled(principal, fulfillment, before, reason);
            return;
        }
        try {
            tossClient.cancel(fulfillment.getPaymentKey(), reason, orderNumber);
        } catch (RuntimeException exception) {
            fulfillment.cancel(GoodsOrderStatus.CANCEL_FAILED, reason);
            orderService.recordManualChange(
                    fulfillment.getResponseId(),
                    before,
                    GoodsOrderStatus.CANCEL_FAILED,
                    String.valueOf(principal.accountId()),
                    reason
            );
            accessLogRepository.save(AdminAccessLog.of(
                    principal.accountId(), "ORDER_CANCEL_FAILED", orderNumber, clock.instant()));
            throw exception;
        }

        markCanceled(principal, fulfillment, before, reason);
    }

    private void markCanceled(
            AdminPrincipal principal,
            GoodsSurveyFulfillment fulfillment,
            GoodsOrderStatus before,
            String reason
    ) {
        fulfillment.cancel(GoodsOrderStatus.CANCELED, reason);
        // 계약이 되돌려졌다. 사진을 들고 있을 근거가 없다.
        retentionService.discardVoidOrderData(fulfillment);
        orderService.recordManualChange(
                fulfillment.getResponseId(),
                before,
                GoodsOrderStatus.CANCELED,
                String.valueOf(principal.accountId()),
                reason
        );
        // 요구서 8장: 주문 취소도 이력으로 남긴다.
        accessLogRepository.save(AdminAccessLog.of(
                principal.accountId(), "ORDER_CANCEL", fulfillment.getOrderNumber(), clock.instant()));
    }

    /** 결제 대행사 키가 붙어 있는지. 없으면 계좌이체거나 결제가 없던 주문이다. */
    private static boolean hasPaymentKey(GoodsSurveyFulfillment fulfillment) {
        return fulfillment.getPaymentKey() != null && !fulfillment.getPaymentKey().isBlank();
    }

    /**
     * 고른 주문을 한 번에 제작 중으로 옮긴다.
     *
     * 제작은 낱개로 하는 일이 아니다. 모아서 만들고, 제작용 목록과 사진도
     * 묶음으로 내보낸다. 상태만 한 건씩 눌러야 하면 100건이면 100번 누른다.
     *
     * 옮길 수 없는 건은 건너뛰고 어느 것인지 돌려준다. 하나가 막혔다고
     * 전부 되돌리면, 방금 통과한 아흔아홉 건을 다시 눌러야 한다. 조용히
     * 넘어가도 안 된다 — 눌렀는데 안 바뀐 것을 화면만 보고는 모른다.
     *
     * 이미 제작 중인 건은 건너뛰되 실패로 세지 않는다. 두 번 눌렀거나 남이
     * 먼저 누른 것이고, 결과는 바라던 그대로다.
     *
     * 이력은 건마다 남는다. 묶어서 눌렀다고 한 줄로 합치면 어느 주문이
     * 언제 제작에 들어갔는지 알 수 없다.
     */
    @Transactional
    public BulkResult startProduction(AdminPrincipal principal, List<String> orderNumbers) {
        if (orderNumbers.size() > MAX_BULK_SIZE) {
            throw new CustomException(ErrorCode.ORDER_BULK_TOO_MANY);
        }

        List<GoodsSurveyFulfillment> found =
                fulfillmentRepository.findByOrderNumberIn(orderNumbers);
        Map<String, GoodsSurveyFulfillment> byNumber = found.stream()
                .collect(Collectors.toMap(GoodsSurveyFulfillment::getOrderNumber, item -> item));

        int changed = 0;
        List<String> skipped = new ArrayList<>();
        for (String orderNumber : orderNumbers) {
            GoodsSurveyFulfillment fulfillment = byNumber.get(orderNumber);
            if (fulfillment == null) {
                skipped.add(orderNumber);
                continue;
            }
            if (principal.role() == AdminRole.PRODUCTION
                    && !fulfillment.getStatus().isVisibleToProduction()) {
                // 제작팀에게 없는 것과 같은 주문이다. 묶음이라고 뚫리지 않는다.
                skipped.add(orderNumber);
                continue;
            }
            GoodsOrderStatus before = fulfillment.getStatus();
            if (before == GoodsOrderStatus.IN_PRODUCTION) {
                continue;
            }
            if (!before.canManuallyBecome(GoodsOrderStatus.IN_PRODUCTION)) {
                skipped.add(orderNumber);
                continue;
            }
            fulfillment.changeStatus(GoodsOrderStatus.IN_PRODUCTION);
            orderService.recordManualChange(
                    fulfillment.getResponseId(),
                    before,
                    GoodsOrderStatus.IN_PRODUCTION,
                    String.valueOf(principal.accountId()),
                    "묶음 제작 시작"
            );
            changed++;
        }
        return new BulkResult(changed, List.copyOf(skipped));
    }

    /**
     * 묶음 처리 결과.
     *
     * @param changed 실제로 옮긴 건수
     * @param skipped 옮기지 못한 주문번호. 화면이 그대로 보여 준다
     */
    public record BulkResult(int changed, List<String> skipped) {
    }

    @Transactional
    public void registerTracking(
            AdminPrincipal principal,
            String orderNumber,
            String company,
            String number
    ) {
        requireAdmin(principal);
        GoodsSurveyFulfillment fulfillment = findVisible(principal, orderNumber);

        GoodsOrderStatus before = fulfillment.getStatus();
        fulfillment.registerTracking(company, number);
        fulfillment.changeStatus(GoodsOrderStatus.SHIPPED);
        if (fulfillment.getDeleteAfter() == null) {
            // 제작용 사진은 "배송 완료를 표시한 날"부터 90일 뒤에 지운다고
            // 고지했다. 여기서 표시하지 않으면 내부 API 를 건마다 따로 부르지
            // 않는 한 사진이 계약 기록과 함께 5년을 산다. 송장을 고쳐 다시
            // 넣어도 날짜는 밀리지 않는다 — 밀리면 고지한 기간보다 오래 갖는다.
            fulfillment.markDeliveryCompleted(
                    clock.instant(), properties.getPersonalDataRetentionDays());
        }
        orderService.recordManualChange(
                fulfillment.getResponseId(),
                before,
                GoodsOrderStatus.SHIPPED,
                String.valueOf(principal.accountId()),
                company + " " + number
        );
    }

    /**
     * 현장에서 건넨 주문을 끝낸다. 관리자만 한다.
     *
     * 송장을 받지 않는다. 현장 수령에는 택배사도 송장번호도 없고, 발송 완료
     * 하나만 끝으로 두면 이 주문들은 끝낼 길이 없다.
     *
     * 이 시각부터 사진 보유 기간(90일)을 센다. 고지한 파기 기준일이 "배송
     * 완료를 표시한 날"인데, 표시할 길이 없으면 기준일이 잡히지 않아 사진이
     * 계약 기록과 함께 5년을 산다.
     *
     * 이미 끝난 주문을 다시 눌러도 아무 일도 하지 않는다. 현장에서 같은
     * 버튼을 두 번 누르는 일은 흔하고, 두 번째가 오류로 끝나면 첫 번째도
     * 실패한 줄 알고 다른 것을 만진다.
     */
    @Transactional
    public void completePickup(AdminPrincipal principal, String orderNumber) {
        requireAdmin(principal);
        GoodsSurveyFulfillment fulfillment = findVisible(principal, orderNumber);

        if (fulfillment.getDeliveryMethod() != GoodsDeliveryMethod.PICKUP) {
            // 부쳐야 하는 물건이다. 송장 없이 끝내면 고객이 조회할 번호가 없다.
            throw new CustomException(ErrorCode.ORDER_NOT_PICKUP_COMPLETABLE);
        }
        if (fulfillment.getStatus() == GoodsOrderStatus.PICKED_UP) {
            return;
        }
        if (!PICKUP_COMPLETABLE.contains(fulfillment.getStatus())) {
            throw new CustomException(ErrorCode.ORDER_NOT_PICKUP_COMPLETABLE);
        }

        GoodsOrderStatus before = fulfillment.getStatus();
        Instant now = clock.instant();
        fulfillment.changeStatus(GoodsOrderStatus.PICKED_UP);
        if (fulfillment.getDeleteAfter() == null) {
            // 다시 찍어도 파기 예정일이 뒤로 밀리지 않게 한 번만 잡는다.
            fulfillment.markDeliveryCompleted(now, properties.getPersonalDataRetentionDays());
        }
        orderService.recordManualChange(
                fulfillment.getResponseId(),
                before,
                GoodsOrderStatus.PICKED_UP,
                String.valueOf(principal.accountId()),
                "현장 수령"
        );
    }

    private AdminOrderDetail.Shipping shippingOf(GoodsSurveyFulfillment fulfillment) {
        return new AdminOrderDetail.Shipping(
                fulfillment.getGuardianName(),
                fulfillment.getPhone(),
                fulfillment.getDeliveryMethod().name(),
                fulfillment.getPostalCode(),
                fulfillment.getAddress(),
                fulfillment.getAddressDetail(),
                fulfillment.getTrackingCompany(),
                fulfillment.getTrackingNumber()
        );
    }

    /** 사진 1~5 자리를 만든다. 비어 있으면 화면이 "미기입" 으로 보여준다. */
    private List<AdminOrderDetail.Photo> photoSlots(List<GoodsSurveyPhoto> photos) {
        List<AdminOrderDetail.Photo> slots = new ArrayList<>();
        for (int index = 0; index < MAX_PHOTO_SLOTS; index++) {
            if (index < photos.size()) {
                slots.add(new AdminOrderDetail.Photo(
                        index + 1, photos.get(index).getObjectKey(), true));
            } else {
                slots.add(new AdminOrderDetail.Photo(index + 1, null, false));
            }
        }
        return slots;
    }

    private List<AdminOrderDetail.StatusChange> statusHistory(String responseId) {
        return statusChangeRepository.findByResponseIdOrderByChangedAtAsc(responseId).stream()
                .map(change -> new AdminOrderDetail.StatusChange(
                        nameOf(change.getFromStatus()),
                        change.getToStatus().name(),
                        change.getChangedAt(),
                        change.getChangedBy(),
                        change.getMemo()
                ))
                .toList();
    }

    private List<AdminOrderDetail.AccessLog> accessLogs(String orderNumber) {
        return accessLogRepository.findByOrderNumberOrderByAccessedAtDesc(orderNumber).stream()
                .map(log -> new AdminOrderDetail.AccessLog(
                        log.getAction(), log.getAdminAccountId(), log.getAccessedAt()))
                .toList();
    }

    private String nameOf(GoodsOrderStatus status) {
        return status == null ? null : status.name();
    }

    private GoodsSurveyFulfillment findVisible(AdminPrincipal principal, String orderNumber) {
        GoodsSurveyFulfillment fulfillment = fulfillmentRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.SURVEY_RESPONSE_NOT_FOUND));
        if (principal.role() == AdminRole.PRODUCTION
                && !fulfillment.getStatus().isVisibleToProduction()) {
            // 없는 것과 같게 답한다. 못 본다고 알려주면 어떤 주문이 있는지는 알게 된다.
            throw new CustomException(ErrorCode.SURVEY_RESPONSE_NOT_FOUND);
        }
        return fulfillment;
    }

    private void requireAdmin(AdminPrincipal principal) {
        if (principal.role() != AdminRole.ADMIN) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    private Set<GoodsOrderStatus> visibleStatuses(
            AdminPrincipal principal,
            Set<GoodsOrderStatus> requested
    ) {
        Set<GoodsOrderStatus> allowed = principal.role() == AdminRole.ADMIN
                ? EnumSet.allOf(GoodsOrderStatus.class)
                : EnumSet.copyOf(PRODUCTION_VISIBLE);
        if (requested == null || requested.isEmpty()) {
            return allowed;
        }
        EnumSet<GoodsOrderStatus> narrowed = EnumSet.noneOf(GoodsOrderStatus.class);
        requested.stream().filter(allowed::contains).forEach(narrowed::add);
        return narrowed;
    }

    /**
     * 검색어와 맞는지 본다.
     *
     * 이름은 암호화해 저장해 데이터베이스에서 찾을 수 없다. 꺼내서 맞춰 본다.
     */
    /**
     * 검색이 닿는 범위.
     *
     * 제작팀에게는 보호자 이름을 걸지 않는다. 목록에서 가리고 상세에서 비워
     * 내려도, 검색이 이름에 걸리면 이름을 넣어 보고 결과 수로 확인할 수 있다.
     * 값을 못 보게 하는 것과 값을 못 알아내게 하는 것은 다르다.
     *
     * 주문번호와 반려동물 이름은 제작에 필요한 값이라 그대로 둔다.
     */
    /**
     * 목록을 좁히는 조건.
     *
     * 굿즈 종류·제출일·사진 수. 요구서 4-1 이 요구하는 값이다. 상태 필터와
     * 검색어와는 별개로 겹쳐 쓸 수 있다.
     *
     * 날짜는 한국 날짜로 받는다. 담당자가 화면에서 고르는 것은 UTC 자정이
     * 아니라 한국 자정이다.
     *
     * @param submittedFrom 이 날짜부터(포함). 없으면 제한 없음
     * @param submittedTo   이 날짜까지(포함). 없으면 제한 없음
     * @param minPhotoCount 이 장수 이상. 사진이 덜 온 주문을 찾을 때 쓴다
     */
    public record OrderFilter(
            String goodsType,
            LocalDate submittedFrom,
            LocalDate submittedTo,
            Integer minPhotoCount
    ) {

        boolean isEmpty() {
            return goodsType == null && submittedFrom == null
                    && submittedTo == null && minPhotoCount == null;
        }

        boolean accepts(AdminOrderService service, GoodsSurveyFulfillment fulfillment) {
            if (isEmpty()) {
                return true;
            }
            if (goodsType != null && !goodsType.isBlank()
                    && !goodsType.equalsIgnoreCase(fulfillment.getGoodsType())) {
                return false;
            }
            if (submittedFrom != null || submittedTo != null) {
                LocalDate submitted =
                        service.submittedAt(fulfillment).atZone(KST).toLocalDate();
                if (submittedFrom != null && submitted.isBefore(submittedFrom)) {
                    return false;
                }
                if (submittedTo != null && submitted.isAfter(submittedTo)) {
                    return false;
                }
            }
            if (minPhotoCount != null) {
                // 사진 수는 마지막에 본다. 여기서만 사진 표를 한 번 더 읽는다.
                int count = service.photoRepository
                        .findByResponseId(fulfillment.getResponseId()).size();
                return count >= minPhotoCount;
            }
            return true;
        }
    }

    private boolean matches(GoodsSurveyFulfillment fulfillment, String query, AdminRole role) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (contains(fulfillment.getOrderNumber(), needle)
                || contains(fulfillment.getPetName(), needle)) {
            return true;
        }
        return role == AdminRole.ADMIN && contains(fulfillment.getGuardianName(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private AdminOrderSummary toSummary(GoodsSurveyFulfillment fulfillment) {
        return new AdminOrderSummary(
                fulfillment.getOrderNumber(),
                submittedAt(fulfillment),
                fulfillment.getGoodsType(),
                GoodsTypeNames.of(fulfillment.getGoodsType()),
                fulfillment.getPetName(),
                PersonalDataMask.name(fulfillment.getGuardianName()),
                PersonalDataMask.phone(fulfillment.getPhone()),
                fulfillment.getStatus(),
                fulfillment.getStatus().label(),
                photoRepository.findByResponseId(fulfillment.getResponseId()).size(),
                fulfillment.getPaymentAmountKrw(),
                fulfillment.getPaidAt(),
                fulfillment.getDeliveryMethod().name(),
                fulfillment.getTrackingNumber()
        );
    }

    /** 접수 시각. 저장은 하나로 하고 화면에서 날짜와 시간을 나눠 보여준다. */
    private Instant submittedAt(GoodsSurveyFulfillment fulfillment) {
        return fulfillment.getCreatedAt() == null
                ? fulfillment.getPrivacyConsentedAt()
                : fulfillment.getCreatedAt().atZone(KST).toInstant();
    }

    /**
     * 목록 위 요약 카드.
     *
     * 화면이 건 필터·검색어와 무관하게 전체를 센다. 예전에는 목록을 뽑은
     * 결과로 셌는데, 그러면 제작 중 필터를 켜는 것만으로 결제 완료가 0 이
     * 되어 입금 확인할 것이 없다고 읽혔다.
     *
     * 두 상태 다 제작팀에게도 보이는 값이라 역할로 나누지 않는다.
     */
    private AdminOrderListResponse.Summary summarize() {
        long paymentCompleted =
                fulfillmentRepository.countByStatus(GoodsOrderStatus.PAYMENT_COMPLETED);
        long inProduction =
                fulfillmentRepository.countByStatus(GoodsOrderStatus.IN_PRODUCTION);
        return new AdminOrderListResponse.Summary(
                paymentCompleted,
                inProduction,
                // 만들어 두고 아직 안 보낸 것. 이것이 오늘 챙겨야 할 수다.
                inProduction + paymentCompleted
        );
    }
}
