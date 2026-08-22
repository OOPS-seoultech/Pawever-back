package com.pawever.backend.funeral.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 위치가 주소에 실리지 않는지 본다.
 *
 * 주소에 실으면 값이 URL 안에 들어간다. URL 은 남기기 쉬운 자리다 — 중간
 * 프록시, 접속 기록, 오류 추적 도구가 기본으로 주소 전체를 적는다. 우리
 * 서버 로그는 경로만 남기게 고쳤지만, 우리가 통제하지 못하는 구간이 남는다.
 *
 * 값이 아니라 배선을 본다. 요청 하나를 보내 보는 시험은 그 요청만 지키고,
 * 다음에 누가 다른 자리에 좌표를 붙이면 아무도 모른다.
 */
class FuneralControllerLocationTest {

    private static final Set<String> LOCATION_PARAMS = Set.of("latitude", "longitude");

    /**
     * 아직 주소로 받는 통로.
     *
     * 이미 배포된 앱이 부르고 있어 지우지 못한다. 지우면 업데이트하지 않은
     * 사용자가 장례업체를 찾지 못한다. 앱이 POST 로 넘어오고 옛 버전이
     * 빠지면 이 목록과 해당 메서드를 함께 지운다.
     *
     * 여기에 이름을 더하는 것은 새 통로를 여는 것과 같다. 더하기 전에
     * 왜 본문으로 받을 수 없는지부터 답해야 한다.
     */
    private static final Set<String> GRANDFATHERED = Set.of(
            "getFuneralCompanyList",
            "getSavedFuneralCompanies",
            "getBlockedFuneralCompanies"
    );

    @Test
    void 새로_만든_통로는_위치를_주소로_받지_않는다() {
        List<String> offenders = locationInQueryString().stream()
                .filter(name -> !GRANDFATHERED.contains(name))
                .toList();

        assertThat(offenders)
                .as("위치는 요청 본문으로 받는다. 주소에 실으면 URL 에 좌표가 남는다")
                .isEmpty();
    }

    @Test
    void 검사기가_실제로_주소_매개변수를_찾아낸다() {
        // 이 시험이 없으면 위 시험은 아무것도 지키지 못한다. 컴파일러가
        // 매개변수 이름을 남기지 않으면 이름이 arg0 으로 보여 하나도 걸리지
        // 않고, 그래도 통과한다. 지금 걸려야 할 것이 걸리는지부터 본다.
        assertThat(locationInQueryString())
                .as("옛 GET 세 개는 아직 위치를 주소로 받는다. 이게 안 걸리면 검사기가 고장 난 것이다")
                .containsExactlyInAnyOrderElementsOf(GRANDFATHERED);
    }

    @Test
    void 본문으로_받는_통로가_실제로_있다() {
        // 위 시험만 있으면 통로를 아예 만들지 않아도 통과한다.
        List<String> byBody = List.of(
                "searchFuneralCompanies",
                "searchSavedFuneralCompanies",
                "searchBlockedFuneralCompanies"
        );

        List<String> declared = List.of(FuneralController.class.getDeclaredMethods()).stream()
                .map(Method::getName)
                .toList();

        assertThat(declared).containsAll(byBody);
    }

    /** 옛 통로가 사라지면 예외 목록도 같이 지우도록 알린다. */
    @Test
    void 예외_목록에_이제_없는_메서드가_남아_있지_않다() {
        Set<String> declared = List.of(FuneralController.class.getDeclaredMethods()).stream()
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(declared)
                .as("옛 통로를 지웠다면 GRANDFATHERED 목록에서도 지운다")
                .containsAll(GRANDFATHERED);
    }

    private List<String> locationInQueryString() {
        return List.of(FuneralController.class.getDeclaredMethods()).stream()
                .filter(this::hasLocationRequestParam)
                .map(Method::getName)
                .toList();
    }

    private boolean hasLocationRequestParam(Method method) {
        for (Parameter parameter : method.getParameters()) {
            RequestParam annotation = parameter.getAnnotation(RequestParam.class);
            if (annotation == null) {
                continue;
            }
            // 이름을 따로 적지 않으면 매개변수 이름이 그대로 쿼리 이름이 된다.
            String name = annotation.value().isBlank()
                    ? parameter.getName()
                    : annotation.value();
            if (LOCATION_PARAMS.contains(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
