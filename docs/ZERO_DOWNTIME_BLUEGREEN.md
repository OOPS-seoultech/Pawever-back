# 무중단 배포 (nginx blue-green)

앱을 2색(**app-blue:28080 / app-green:28081**)으로 돌리고, 호스트 nginx가 "활성 색"을 가리킨다.
배포 = 비활성 색 기동 → `/health` 확인 → nginx를 새 색으로 **graceful 전환** → 옛 색 정지.
**실패 시 전환 안 함**(기존 색이 계속 서빙 → 무중단 유지).

```
client → https://api.pawever.kr → nginx(:443) → upstream pawever_backend(활성 포트) → app-blue(28080) / app-green(28081)
```

---

## ⚠️ 최초 1회 서버 설정 (필수 — 이거 안 하면 배포가 실제로 전환되지 않음)

> 트래픽 적은 시간에, nginx 설정 백업 뜨고 진행. **담당자와 함께 권장.**
> (참고: 이 설정 전에 main에 머지되면, 배포는 성공으로 뜨지만 nginx가 아직 옛 경로라 새 코드가 서빙되지 않는다 — 장애는 아니고 "무효 배포" 상태.)

```bash
ssh pawever && cd /home/ubuntu/Pawever-back
git fetch origin main && git checkout -B main origin/main

# 1) upstream 파일 생성 (현재 서빙 중인 28080을 가리킴)
echo 'upstream pawever_backend { server 127.0.0.1:28080; }' | sudo tee /etc/nginx/conf.d/pawever-upstream.conf

# 2) api.pawever.kr 사이트 설정 백업 후 proxy_pass 변경
#    proxy_pass http://localhost:28080;  →  proxy_pass http://pawever_backend;
sudo cp <사이트설정파일> <사이트설정파일>.bak
sudo nano <사이트설정파일>     # 또는 sed 로 치환
sudo nginx -t && sudo nginx -s reload
#    (이 시점: nginx → upstream → 28080 → 기존 app. 서빙 그대로, 끊김 없음)

# 3) 첫 전환은 green(28081)으로 — 28080 포트 충돌 회피
docker compose --profile bluegreen up -d --build app-green
curl -fsS http://localhost:28081/health           # "ok" 확인
echo 'upstream pawever_backend { server 127.0.0.1:28081; }' | sudo tee /etc/nginx/conf.d/pawever-upstream.conf
sudo nginx -t && sudo nginx -s reload              # nginx → green(28081) 새 코드로 무중단 전환

# 4) 옛 단일 app 정지·정리
docker compose stop app && docker compose rm -f app
```
→ 이제 steady state: **green(28081) 서빙**. 다음 배포부터 GH Actions가 자동으로 blue↔green 교대.

---

## 이후 배포 (자동, GH Actions)

main push(머지) 시 자동으로:
1. 비활성 색 빌드·기동 → 2. `/health` 확인 → 3. nginx upstream 전환 + `nginx -s reload`(끊김 0) → 4. 옛 색 정지.
새 버전이 안 뜨면 **전환하지 않고 기존 색 유지**(job은 실패로 표시).

## 수동 롤백

방금 배포가 나쁘면 (옛 색 컨테이너는 `stop` 상태로 남아 있음):
```bash
CUR=$(grep -oE '28080|28081' /etc/nginx/conf.d/pawever-upstream.conf)
if [ "$CUR" = 28081 ]; then OLD_PORT=28080; OLD=app-blue; else OLD_PORT=28081; OLD=app-green; fi
docker compose --profile bluegreen start "$OLD"
echo "upstream pawever_backend { server 127.0.0.1:$OLD_PORT; }" | sudo tee /etc/nginx/conf.d/pawever-upstream.conf
sudo nginx -t && sudo nginx -s reload
```
(또는 `origin/main` 을 고쳐서 재배포)

## 전제·참고
- 배포 스크립트가 `sudo nginx` 를 쓰므로 **ubuntu 계정에 passwordless sudo 필요**(EC2 기본 제공).
- **로컬 개발:** `docker compose up` = `app`(28080) 하나만 뜸(blue/green은 profile로 격리) → 영향 없음.
- **나중에 ALB 도입 시:** 이 nginx upstream 전환·2색 구조는 제거하고 ALB가 무중단·헬스체크·다중 인스턴스를 대체한다.
