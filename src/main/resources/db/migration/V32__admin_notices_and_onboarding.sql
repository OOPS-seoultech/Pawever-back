CREATE TABLE admin_notices (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0,
  title VARCHAR(200) NOT NULL, body TEXT NOT NULL, guide_key VARCHAR(60) NULL UNIQUE,
  author_id BIGINT NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE admin_onboarding (
  account_id BIGINT PRIMARY KEY, decision VARCHAR(20) NOT NULL, decided_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO admin_notices(title,body,guide_key,created_at,updated_at) VALUES(
'내 PC 작업 폴더 연결 안내 (Chrome · Mac/Windows)',
'# 작업 전, 내 PC의 완성본 폴더를 연결해 주세요

이 안내는 공지사항에서 언제든 다시 볼 수 있습니다. 처음에는 **바로 가기**로 연결을 시작하거나 **다음에 하기**로 미룰 수 있습니다.

## 1. 데스크톱 Google Chrome으로 로그인

맥과 윈도우 모두 지원합니다. 모바일·Safari·Edge·앱 안의 내장 브라우저에서는 폴더 연결을 진행하지 않습니다. 주소창에 https://www.pawever.kr/admin/operations 를 직접 열어 주세요.

## 2. 완성본을 모을 전용 폴더 만들기

- 맥: Finder에서 다운로드 또는 작업 폴더 안에 PAWEVER_완성본 폴더를 만듭니다.
- 윈도우: 파일 탐색기에서 다운로드 또는 작업 폴더 안에 PAWEVER_완성본 폴더를 만듭니다.
- ASIDE가 최종 3MF·STL·모델 원본·4면도 이미지를 사이트가 만든 주문·아이별 하위 폴더에 저장하도록 지정합니다.
- 개인정보나 다른 업무 파일이 섞인 폴더 전체를 선택하지 말고 전용 작업 폴더를 선택합니다.

## 3. 관리자 메뉴의 로컬 파일에서 폴더 연결

1. 상단 로컬 파일 메뉴를 엽니다.
2. 작업 폴더 연결 버튼을 누릅니다.
3. 상위 작업 폴더를 선택하고 브라우저의 폴더 생성·파일 읽기 권한을 허용합니다.
4. 사이트가 그 안에 PAW-EVER 폴더와 주문·아이·자료 종류별 하위 폴더를 생성합니다. 새 작업도 페이지가 열려 있고 접근이 허용되어 있으면 폴더를 이어서 생성합니다.
5. 이 사이트는 폴더를 생성하고 선택한 파일을 읽어 업로드하며, PC 원본을 수정하거나 지우지 않습니다.

사이트 로그인 권한과 PC 폴더 접근 권한은 별개입니다. 경로를 텍스트로 입력하는 것만으로 연결되지 않습니다. 브라우저가 폴더를 거절하면 전용 하위 폴더를 선택해 주세요. macOS의 파일 및 폴더 접근 팝업이 뜨면 선택한 작업 폴더 접근을 허용합니다.

## 4. 완성 파일을 확인하고 등록

1. 파일 목록에서 파일명·용량·수정 시각을 확인합니다.
2. 해당 주문과 아이를 선택합니다. 한 주문에 여러 아이가 있으면 아이 이름까지 확인합니다.
3. 자료 종류를 선택합니다. 모델링 출력물과 채색 3MF를 구분합니다.
4. 파일 등록 버튼을 누릅니다. 등록 완료 표시가 나타나야 팀원이 다른 PC에서 받을 수 있습니다.
5. 여러 아이를 합친 플레이트 파일은 아이 한 마리가 아닌 해당 플레이트에 등록합니다.

파일을 읽기만 한 상태는 내 PC에서만 보입니다. 서버 등록이 끝나야 팀과 공유됩니다. 업로드만으로 모델 승인·출력 시작·고객 알림이 진행되지는 않습니다.

## 파일 이름 권장

주문번호_아이2_별이_채색_v003.3mf 처럼 주문·아이·자료 종류·버전을 구별해 주세요. 이름이 비슷하더라도 자동 확정하지 않고 등록 직전에 대상 아이를 확인합니다. 기존 등록 파일은 덮어쓰지 않고 새 버전으로 남깁니다.

## 다시 접속하거나 다른 PC를 사용할 때

- 같은 PC·같은 크롬 프로필이면 연결 폴더를 기억합니다. 권한이 만료되면 접근 허용 버튼을 다시 누릅니다.
- 다른 PC·다른 크롬 프로필에서는 작업 폴더를 새로 선택합니다.
- 다른 사람의 계정이나 공용 크롬 프로필을 공유하지 않습니다. 연결은 로그인 계정별로 구분합니다.
- 연결 해제는 이 브라우저의 연결만 지우며 원본 파일과 서버에 등록한 파일은 삭제하지 않습니다.

## 목록에 파일이 보이지 않을 때

완전히 저장된 지원 파일인지, 연결한 폴더 안에 있는지 확인하고 목록 새로고침을 누릅니다. 저장 중 파일·지원하지 않는 형식은 등록하지 않습니다. 페이지가 닫히거나 PC가 잠든 동안은 자동 감시·업로드하지 않습니다. 현재 업로드 한도는 파일당 100MB입니다.

## 참고

[Chrome의 로컬 파일 접근 안내](https://developer.chrome.com/docs/capabilities/web-apis/file-system-access)

[Chrome 다운로드 위치 설정](https://support.google.com/chrome/answer/95759?hl=ko)
','local-folder-guide',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));
