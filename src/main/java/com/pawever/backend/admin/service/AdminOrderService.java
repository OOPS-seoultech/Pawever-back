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
     * 1차 체험단에서 갈 수 있는 상태.
     *
     * 결제가 없던 주문이라 결제 관련 상태로는 가지 않는다. 발송 완료는 여기
     * 없다 — 송장을 등록해야 넘어간다.
     */
    private static final Set<GoodsOrderStatus> LEGACY_FREE_SETTABLE =
            EnumSet.of(GoodsOrderStatus.IN_PRODUCTION);

    /** 제작팀이 스스로 바꿀 수 있는 상태. 발송과 취소는 관리자만 한다. */
    private static final Set<GoodsOrderStatus> PRODUCTION_SETTABLE =
            EnumSet.of(GoodsOrderStatus.IN_PRODUCTION);

    private final GoodsSurveyFulfillmentRepository fulfillmentRepository;
    private final GoodsSurveyPhotoRepository photoRepository;
    private final GoodsOrderStatusChangeRepository statusChangeRepository;
    private final AdminAccessLogRepository accessLogRepository;
    private final GoodsSurveyPhotoStorage photoStorage;
    private final GoodsOrderService orderService;
    private final TossPaymentsClient tossClient;
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
            return new AdminOrderListResponse(List.of(), 0, page, size, emptySummary());
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

        return new AdminOrderListResponse(orders, matched.size(), page, size, summarize(found));
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
                        fulfillment.getCancelReason())
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
     * 사진 다운로드 링크를 내준다.
     *
     * 누가 언제 어느 주문의 사진을 가져갔는지 남긴다. 남기지 않으면 사진이
     * 밖으로 나갔을 때 누구를 거쳐 나갔는지 알 방법이 없다.
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
                "PHOTO_DOWNLOAD",
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
        // 취소는 결제 취소가 성공해야 넘어간다. 상태만 바꾸면 돈은 돌려주지 않고
        // 취소된 것으로 보인다.
        if (next == GoodsOrderStatus.CANCELED || next == GoodsOrderStatus.CANCEL_FAILED) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        // 1차 체험단은 결제라는 것이 없던 주문이다. 결제 완료로 바꾸면 받지도
        // 않은 돈이 매출로 잡히고, 결제 만료·실패로 바꾸면 없던 결제가 실패한
        // 것이 된다. 만들어 보내는 흐름으로만 나간다.
        if (fulfillment.getStatus() == GoodsOrderStatus.LEGACY_FREE
                && !LEGACY_FREE_SETTABLE.contains(next)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        GoodsOrderStatus before = fulfillment.getStatus();
        if (next == GoodsOrderStatus.PAYMENT_COMPLETED) {
            // 상태만 옮기면 결제 시각이 비어 있는 채로 결제 완료가 된다.
            //
            // 그 칸은 보기 좋으라고 있는 것이 아니다. 결제 승인과 웹훅이
            // "이미 받은 건인지"를 이 값으로 판단하고, 관리자 화면의 취소
            // 버튼도 이 값이 있어야 열린다. 비어 있으면 돈은 받아 두고
            // 환불할 자리가 화면에서 사라진다.
            //
            // 무통장 입금이라 은행에서 시각이 넘어오지 않는다. 사람이 통장을
            // 보고 누르는 이 시각이 우리가 가진 유일한 시각이다. 이미 값이
            // 있으면 markPaid 가 아무것도 하지 않으므로 두 번 눌러도, 나중에
            // PG 웹훅이 겹쳐도 처음 받은 때가 덮이지 않는다.
            fulfillment.markPaid(clock.instant(), null, "MANUAL");
        } else {
            fulfillment.changeStatus(next);
        }
        orderService.recordManualChange(
                fulfillment.getResponseId(),
                before,
                next,
                String.valueOf(principal.accountId()),
                memo
        );
    }

    /** 송장을 넣으면 발송 완료로 넘어간다. 관리자만 한다. */
    /**
     * 주문을 취소한다.
     *
     * 상태를 먼저 바꾸지 않는다. 결제 취소가 실패했는데 주문만 취소로 보이면
     * 돈은 그대로 두고 취소된 것으로 읽힌다. 토스가 취소를 받아들인 뒤에만
     * 취소로 옮긴다.
     *
     * 실패하면 취소 처리 실패로 남긴다. 사람이 확인해야 하는 건이라 조용히
     * 지나가게 두지 않는다.
     *
     * 멱등 키에 주문번호를 쓴다. 같은 주문을 두 번 눌러도 토스가 앞의 결과를
     * 그대로 돌려주므로 이중 환불이 되지 않는다.
     */
    @Transactional
    public void cancel(AdminPrincipal principal, String orderNumber, String reason) {
        requireAdmin(principal);
        GoodsSurveyFulfillment fulfillment = findVisible(principal, orderNumber);

        if (!fulfillment.getStatus().isCancelable()) {
            throw new CustomException(ErrorCode.PAYMENT_NOT_CANCELABLE);
        }
        if (fulfillment.getPaymentKey() == null || fulfillment.getPaymentKey().isBlank()) {
            // 결제 없이 만들어진 주문이다. 돌려줄 돈이 없으므로 취소할 것도 없다.
            throw new CustomException(ErrorCode.PAYMENT_NOT_CANCELABLE);
        }

        GoodsOrderStatus before = fulfillment.getStatus();
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

        fulfillment.cancel(GoodsOrderStatus.CANCELED, reason);
        orderService.recordManualChange(
                fulfillment.getResponseId(),
                before,
                GoodsOrderStatus.CANCELED,
                String.valueOf(principal.accountId()),
                reason
        );
        // 요구서 8장: 주문 취소도 이력으로 남긴다.
        accessLogRepository.save(AdminAccessLog.of(
                principal.accountId(), "ORDER_CANCEL", orderNumber, clock.instant()));
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
        orderService.recordManualChange(
                fulfillment.getResponseId(),
                before,
                GoodsOrderStatus.SHIPPED,
                String.valueOf(principal.accountId()),
                company + " " + number
        );
    }

    private AdminOrderDetail.Shipping shippingOf(GoodsSurveyFulfillment fulfillment) {
        return new AdminOrderDetail.Shipping(
                fulfillment.getGuardianName(),
                fulfillment.getPhone(),
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
                fulfillment.getTrackingNumber()
        );
    }

    /** 접수 시각. 저장은 하나로 하고 화면에서 날짜와 시간을 나눠 보여준다. */
    private Instant submittedAt(GoodsSurveyFulfillment fulfillment) {
        return fulfillment.getCreatedAt() == null
                ? fulfillment.getPrivacyConsentedAt()
                : fulfillment.getCreatedAt().atZone(KST).toInstant();
    }

    private AdminOrderListResponse.Summary summarize(List<GoodsSurveyFulfillment> all) {
        return new AdminOrderListResponse.Summary(
                count(all, GoodsOrderStatus.PAYMENT_COMPLETED),
                count(all, GoodsOrderStatus.IN_PRODUCTION),
                // 만들어 두고 아직 안 보낸 것. 이것이 오늘 챙겨야 할 수다.
                count(all, GoodsOrderStatus.IN_PRODUCTION)
                        + count(all, GoodsOrderStatus.PAYMENT_COMPLETED)
        );
    }

    private long count(List<GoodsSurveyFulfillment> all, GoodsOrderStatus status) {
        return all.stream().filter(item -> item.getStatus() == status).count();
    }

    private AdminOrderListResponse.Summary emptySummary() {
        return new AdminOrderListResponse.Summary(0, 0, 0);
    }
}
