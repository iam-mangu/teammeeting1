# 모이다 — A·B 필수 기능 통합본

B가 전달한 코드가 계획서의 어느 단계까지 구현되었는지 확인하고, 그 범위에 맞는 A 담당 기능을 연결한 버전입니다. 3~4일차 전용 중간점검본은 아닙니다. 계획서 밖의 기능은 추가하지 않았습니다.

B 전달본에는 계획서 9~11일차에 해당하는 최종 확정·결과 조회까지 들어 있었습니다. 통합본은 회원가입부터 결과 조회까지 연결하고 동시 요청 검증을 보완했습니다. 최종 발표·시연 준비까지 모두 완료했다는 뜻은 아닙니다.

## 개발 기준

| 항목 | 기준 |
|---|---|
| Java / Spring Boot | 17 / 4.1.1 |
| Gradle Wrapper | 8.14.3 |
| 화면 | Mustache / Bootstrap 5.3.8 / 최소 JavaScript |
| Lombok | Spring Boot 관리 버전 |
| 실행 DB / 기본 검사 DB | MySQL / H2 |
| 로그인 | Spring Security 세션, CSRF 유지 |
| 패키지 | com.example.teammeeting |

## IntelliJ에서 실행

1. ZIP을 풀고 이 폴더의 build.gradle을 프로젝트로 엽니다.
2. Project SDK와 Gradle JVM을 Java 17로 설정하고 Gradle 동기화를 완료합니다. 첫 실행에는 라이브러리 다운로드가 필요할 수 있습니다.
3. MySQL에서 database/create-database.sql을 실행합니다. 기존 DB를 삭제하지 않습니다.
4. 실행 설정의 환경변수에 자신의 DB_USERNAME, DB_PASSWORD를 지정합니다.
5. 기본 DB 주소 localhost:3306/teammeeting을 사용하지 않으면 DB_URL도 지정합니다.
6. TeammeetingApplication을 실행하고 http://localhost:8080 에 접속합니다.
7. 회원가입 후 모임을 생성합니다. 다른 브라우저 또는 시크릿 창에서는 다른 계정으로 가입해 초대 코드로 참여합니다.

기본 계정이나 시연 데이터는 자동 생성하지 않습니다. 비밀번호를 코드에 적지 말고 실행 설정에만 넣으세요. 두 사람이 각자 로컬 DB로 실행하면 서로의 데이터가 자동 공유되지는 않습니다. 한 컴퓨터에서 두 계정으로 검사할 수 있습니다.

PowerShell에서는 run-local.ps1로 DB 계정과 비밀번호를 입력해 실행할 수도 있습니다. 스크립트가 제한되면 IntelliJ 실행을 사용하세요.

## 검사와 실행 파일 생성

```powershell
.\gradlew.bat test
.\gradlew.bat bootJar
```

기본 검사는 H2를 사용하므로 MySQL 계정 없이 실행됩니다. 보고서는 build/reports/tests/test/index.html에서 확인합니다.

실제 MySQL 검사를 하려면 별도 테스트 DB에 대한 TEST_DB_URL, TEST_DB_USERNAME, TEST_DB_PASSWORD를 설정한 뒤 test --rerun-tasks를 실행합니다. 테스트는 테이블과 데이터를 생성·삭제하므로 실제 사용 중인 DB를 지정하면 안 됩니다.

## 연결된 흐름과 검증

회원가입·로그인 → 내 모임 → 모임 생성·초대 참여 → 시간 선택·저장 → 공통 시간·장소 후보·투표 → 팀장 확정 → 결과 조회

H2와 별도 MySQL 8.0.45에서 각각 42개 검사 통과를 확인했습니다. 실제 로그인 세션 두 개를 이용한 전체 폼 흐름과 동시 요청 검사가 포함됩니다. 브라우저에서도 회원가입부터 확정 결과까지 확인했습니다. 조원 개인 PC와 발표 환경 점검은 별도로 남아 있습니다.

- docs/계획서_진행현황과_남은작업.md: B 평가, A 연결 범위, 최종 제출까지 남은 일
- docs/통합_변경내역.md: 원본 대비 실제 변경 내용
- docs/시연_준비와_수동점검.md: 시연용 입력 예시와 두 계정 점검 순서
- docs/검증결과.md, docs/통합_테스트_결과.json: 검사 결과
- docs/merge-provenance.json: 전달 ZIP 확인값과 병합 기록

장소 확정은 약속 장소 결정입니다. 실제 시설 예약을 처리하지 않습니다. 외부 API, 파티룸 예약, 위치 중심 추천 등은 구현하지 않았습니다.

공통_엔티티_기준.md와 B_개발_시작.md는 최초 공통 계약 문서입니다. A_완료범위와_요청주소.md 등 A 단독 문서의 ‘B 연결 예정’ 설명은 이전 이력입니다. 현재 상태는 이 README와 위 통합 문서를 기준으로 확인하세요.
