package com.pawever.backend.stats.controller;

import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.stats.config.AppStatsProperties;
import com.pawever.backend.stats.service.AppStatsExportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppStatsExportControllerTest {

    @Mock
    private AppStatsExportService exportService;

    @Test
    void exportIsClosedWhenNoTokenIsConfigured() {
        // 토큰을 넣지 않은 서버에서 통로가 열려 있으면 누구나 규모를 들여다볼 수 있다.
        AppStatsExportController controller = controllerWithToken("");

        assertThatThrownBy(() -> controller.summary("아무거나"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(exportService, never()).summaryCsv();
    }

    @Test
    void wrongTokenIsRejected() {
        AppStatsExportController controller = controllerWithToken("올바른토큰");

        assertThatThrownBy(() -> controller.summary("틀린토큰"))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> controller.summary(null))
                .isInstanceOf(CustomException.class);

        verify(exportService, never()).summaryCsv();
    }

    @Test
    void correctTokenDownloadsTheCsv() {
        AppStatsExportController controller = controllerWithToken("올바른토큰");
        when(exportService.summaryCsv()).thenReturn("구분,지표,값\n");

        var response = controller.summary("올바른토큰");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .contains("app-stats-summary.csv");
        // 통계 파일이 중간 캐시에 남지 않아야 한다.
        assertThat(response.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");
    }

    private AppStatsExportController controllerWithToken(String token) {
        AppStatsProperties properties = new AppStatsProperties();
        properties.setExportToken(token);
        return new AppStatsExportController(exportService, properties);
    }
}
