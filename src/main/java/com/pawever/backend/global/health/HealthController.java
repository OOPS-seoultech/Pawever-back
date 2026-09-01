package com.pawever.backend.global.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 살아 있는지, 그리고 무엇이 살아 있는지 알린다.
 *
 * 커밋을 함께 내보내는 이유가 있다. 배포는 이 자리가 200 이면 새 버전이 떴다고
 * 보고 트래픽을 넘긴다. 그런데 "200 을 준다"와 "방금 만든 것이 떴다"는 다른
 * 말이다 — 2026-09-01, 같은 포트를 잡고 있던 사흘 된 컨테이너가 200 을
 * 돌려주는 바람에 배포가 성공했다고 보고하고 트래픽을 옛 버전으로 넘겼다.
 * 그 버전에서는 굿즈 API 가 403 이라 38분 동안 신청이 막혔다.
 *
 * 이제 배포는 여기 적힌 커밋이 자기가 방금 빌드한 것과 같은지 본다.
 */
@RestController
public class HealthController {

    /**
     * 이 이미지를 만든 커밋.
     *
     * 도커 빌드 인자로 들어와 실행 환경변수로 남는다. 로컬에서 그냥 띄우면
     * 비어 있고, 그때는 unknown 이 된다 — 확인할 것이 없다는 뜻이다.
     */
    private final String commit;

    public HealthController(@Value("${app.git-sha:}") String gitSha) {
        this.commit = (gitSha == null || gitSha.isBlank()) ? "unknown" : gitSha;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "commit", commit));
    }
}
