# B 개발 시작

## B의 작업 범위

공통 시간 계산 → 장소 후보 등록 → 투표와 표 변경 → 팀장 확정 → 확정 결과 화면 순서로 구현합니다. 이 A 완료본에도 B 기능과 Controller/Service/DTO/Repository는 없습니다. 회원가입·모임 생성·시간 입력은 A가 구현한 상태입니다.

추가할 파일은 다음과 같습니다.

- coordination/PlaceRepository.java
- coordination/VoteRepository.java
- coordination/CoordinationService.java 또는 기능별 Service
- coordination/CoordinationController.java
- coordination 입력 DTO와 화면 조회용 Service
- templates/coordination/index.mustache
- templates/coordination/result.mustache
- 담당 기능 테스트

공통 엔티티 Place와 Vote는 이미 있으므로 다시 만들지 않습니다. A 기능도 대신 구현하지 않습니다.

## 공통 기준

Java 17 / Spring Boot 4.1.1 / Gradle 8.14.3 / Mustache / Lombok / Bootstrap 5.3.8 / MySQL로 통일합니다. 패키지는 com.example.teammeeting입니다. Lombok 버전은 Spring Boot 의존성 관리에 따르며 직접 지정하지 않습니다.

## 제공하는 메서드

| 메서드 | 용도 |
|---|---|
| MeetingAccess.currentUser(email) | 로그인한 이메일에 대응하는 User |
| MeetingAccess.requireMember(id,email,false) | 모임 조회와 참여자 확인 |
| MeetingAccess.requireMember(id,email,true) | 쓰기 잠금, 참여자, OPEN 상태 확인 |
| MeetingRepository.findByIdForUpdate(id) | 모임 단위 쓰기 잠금 |
| MeetingRepository.findByInviteCodeForUpdate(code) | 초대 참여용 쓰기 잠금 |
| MeetingMemberRepository.findAllByMeetingIdOrderByIdAsc(id) | 전체 참여자 |
| MeetingDateRepository.findAllByMeetingIdOrderByDateAsc(id) | 날짜순 후보 |
| AvailabilityRepository.findAllByMemberMeetingId(id) | 모임의 가능 시간 |

로그인한 Principal.getName()은 소문자로 정규화된 이메일입니다. 쓰기 Service는 @Transactional 안에서 requireMember(id,email,true)를 먼저 호출하고 동일 모임 행을 잠급니다. 잠금 전에 같은 Meeting을 미리 읽어 관리 상태로 만들지 마세요. 조회는 @Transactional(readOnly=true)로 처리합니다.

## 응답과 확정 규칙

- 생성자도 MeetingMember에 포함되어야 합니다. 실제 생성은 A가 담당합니다.
- respondedAt이 null이면 미응답입니다.
- respondedAt이 있고 Availability가 없으면 가능 시간 없음입니다.
- 전체 참여자의 공통 슬롯만 계산하며 시작 시각 9~20, 길이 1시간입니다.
- 미응답자가 있거나 공통 시간이 없으면 확정할 수 없습니다.
- 모임 참여자만 후보 등록과 투표가 가능합니다.
- 투표는 모임별 1인 1표이며 다른 후보로 변경할 수 있습니다.
- 팀장은 1표 이상 받은 최다 득표 장소를 확정합니다. 동률은 그중 선택합니다.
- 최종 날짜, 시작, 종료, 장소와 CONFIRMED 상태를 같은 트랜잭션에서 저장합니다.
- 확정 후 후보 등록과 투표 변경도 차단합니다.

DB 유니크 제약은 중복을 막는 최후 방어선입니다. 다른 모임의 장소, 비참여자, 잘못된 시간 등의 업무 검증은 B가 Service에서 구현해야 합니다.

## 요청 주소와 화면

| 방식 | 주소 |
|---|---|
| GET | /meetings/{meetingId}/coordination |
| POST | /meetings/{meetingId}/places |
| POST | /meetings/{meetingId}/votes |
| POST | /meetings/{meetingId}/confirm |
| GET | /meetings/{meetingId}/result |

모든 변경은 POST를 사용합니다. Mustache 화면의 공통 partial은 partials/header, partials/footer, partials/csrf입니다. POST 폼에 `{{>partials/csrf}}`를 넣습니다. WebAdvice가 signedIn과 csrf를 제공합니다. 장소명과 메모는 일반 Mustache 이중 중괄호로 출력해 HTML을 이스케이프합니다.

## A 완료본을 이용해 개발하는 방법

1. `gradlew.bat test`로 공통 기준이 동작하는지 확인합니다.
2. CommonSkeletonTest의 fixtureUser와 fixtureMeeting을 참고해 테스트에서 회원·모임·참여자를 만듭니다.
3. 테스트에서 후보 날짜와 Availability를 직접 저장해 공통 시간 계산을 검증합니다.
4. PlaceRepository와 VoteRepository를 만들고 담당 Service를 테스트합니다.
5. A가 구현한 회원가입·모임·시간 입력을 이용해 실제 두 계정으로 B 화면을 점검합니다.

테스트 fixture는 src/test에만 둡니다. 본 실행에 고정 비밀번호나 가짜 회원·모임 데이터를 자동 생성하지 않습니다.

## 병합

이 뼈대를 A와 B가 동일하게 사용하고 각자 담당 기능만 추가합니다. 기존 A 프로젝트를 통째로 덮어쓰면 Boot 버전과 공통 설정이 되돌아갈 수 있습니다. A 화면의 링크 연결과 로그인 성공 후 이동 경로 변경은 병합 시 함께 조정합니다. 현재 A 완료본의 로그인 성공 이동 경로는 `/meetings`입니다. 세부 병합 순서는 병합_체크리스트.md를 따릅니다.
