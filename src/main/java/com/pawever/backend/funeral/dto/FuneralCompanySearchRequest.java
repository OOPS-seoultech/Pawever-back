package com.pawever.backend.funeral.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장례업체를 거리순으로 찾을 때 보내는 값.
 *
 * 위치를 주소가 아니라 본문에 담는다. 주소에 실으면 값이 URL 안에 들어가고,
 * URL 은 남기기 쉬운 자리다 — 중간 프록시, 접속 기록, 오류 추적 도구가
 * 기본으로 주소 전체를 적는다. 본문은 그렇게 새지 않는다.
 *
 * 좌표는 저장하지 않는다. 정렬에 한 번 쓰고 버린다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FuneralCompanySearchRequest {

    @NotNull(message = "반려동물 ID는 필수입니다.")
    private Long petId;

    /** 없으면 서울역 기준으로 정렬한다. 위치를 주지 않아도 목록은 보여야 한다. */
    @DecimalMin(value = "-90.0", message = "위도 범위를 벗어났습니다.")
    @DecimalMax(value = "90.0", message = "위도 범위를 벗어났습니다.")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "경도 범위를 벗어났습니다.")
    @DecimalMax(value = "180.0", message = "경도 범위를 벗어났습니다.")
    private Double longitude;
}
