package com.pawever.backend.global.config;

import com.pawever.backend.admin.config.AdminProperties;
import com.pawever.backend.goodssurvey.config.GoodsSurveyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * application.yaml 의 키가 설정 클래스의 필드에 실제로 붙는지 본다.
 *
 * 키 이름을 잘못 적어도 아무 데서도 걸리지 않는다. 스프링은 붙지 않은 값을
 * 조용히 기본값으로 두고, 앱은 그대로 뜬다. 관리자 서명 키가 그렇게 비어 있는
 * 채로 배포된 적이 있다 — admin.jwt.secret 이라고 적었는데 필드는 jwtSecret
 * 이라 admin.jwt-secret 을 찾고 있었다.
 *
 * 값이 맞는지가 아니라 **남는 키가 없는지**를 본다. 기본값과 설정값이 우연히
 * 같으면 값 비교로는 붙었는지 알 수 없다. 실제로 그래서 한 번 놓쳤다.
 */
class ApplicationYamlBindsToPropertiesTest {

    private static final String YAML_PATH = "src/main/resources/application.yaml";

    @Test
    void 관리자_설정에_붙지_않는_키가_없다() throws IOException {
        AdminProperties properties = bindStrictly("admin", AdminProperties.class);

        assertThat(properties.getJwtExpirationMillis()).isEqualTo(28_800_000L);
        // 서명 키와 부트스트랩 토큰은 환경변수 자리라 여기서는 비어 있는 것이 맞다.
        assertThat(properties.getJwtSecret()).isNotNull();
        assertThat(properties.getBootstrapToken()).isNotNull();
    }

    @Test
    void 굿즈_설문_설정에_붙지_않는_키가_없다() throws IOException {
        GoodsSurveyProperties properties = bindStrictly("survey.goods", GoodsSurveyProperties.class);

        assertThat(properties.getCampaignId()).isEqualTo("goods-2026-07");
        assertThat(properties.getSurveyRetentionDays()).isEqualTo(730);
        assertThat(properties.getContractRetentionDays()).isEqualTo(1825);
        assertThat(properties.getListPriceKrw()).isEqualTo(29_900);
    }

    @Test
    void 붙지_않는_키를_넣으면_잡아낸다() throws IOException {
        // 이 테스트가 통과해야 위 둘이 의미를 갖는다. 검사기가 실제로 잡는지 본다.
        StandardEnvironment environment = loadYaml();
        environment.getPropertySources().addFirst(
                new org.springframework.core.env.MapPropertySource(
                        "typo",
                        java.util.Map.of("admin.jwt.secret", "오타로 한 단계 더 들어간 키")
                )
        );

        assertThatThrownBy(() -> Binder.get(environment)
                .bind("admin", org.springframework.boot.context.properties.bind.Bindable
                        .of(AdminProperties.class), new NoUnboundElementsBindHandler(org.springframework.boot.context.properties.bind.BindHandler.DEFAULT)))
                .isInstanceOf(BindException.class);
    }

    private <T> T bindStrictly(String prefix, Class<T> type) throws IOException {
        return Binder.get(loadYaml())
                .bind(prefix,
                        org.springframework.boot.context.properties.bind.Bindable.of(type),
                        new NoUnboundElementsBindHandler(org.springframework.boot.context.properties.bind.BindHandler.DEFAULT))
                .orElseThrow(() -> new AssertionError(
                        prefix + " 설정이 붙지 않았습니다. yaml 키 이름을 확인하세요."));
    }

    private StandardEnvironment loadYaml() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new FileSystemResource(YAML_PATH));
        if (sources.isEmpty()) {
            throw new AssertionError("application.yaml 을 읽지 못했습니다: " + YAML_PATH);
        }

        StandardEnvironment environment = new StandardEnvironment();
        // 뒤에 오는 문서가 앞을 덮도록 순서를 지킨다.
        for (int index = sources.size() - 1; index >= 0; index--) {
            environment.getPropertySources().addFirst(sources.get(index));
        }
        return environment;
    }
}
