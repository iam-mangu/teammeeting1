# A 완료 범위와 요청 주소

계획서와 공통 프롬프트에서 A에게 배정된 회원·모임·날짜·가능 시간·응답 상태를 구현했습니다. 공통 시간 계산, 장소 후보, 투표와 확정은 B에게 남겨 두었습니다.

| 방식 | 주소 | 담당 동작 |
|---|---|---|
| GET | / | 로그인 상태에 따라 로그인 또는 내 모임으로 이동 |
| GET / POST | /signup | 회원가입 화면과 저장 |
| GET / POST | /login | 로그인 화면과 Security 인증 |
| POST | /logout | Security 로그아웃 |
| GET | /meetings | 내 모임 목록 |
| GET | /meetings/new | 모임 생성 화면 |
| POST | /meetings | 모임·생성자 참여·후보 날짜 저장 |
| POST | /meetings/join | 초대 코드 참여 |
| GET | /meetings/{meetingId} | 참여자와 가능 시간 입력 |
| POST | /meetings/{meetingId}/availability | 선택한 시간 전체 저장·교체 |
| POST | /meetings/{meetingId}/availability/none | 가능 시간 없음 응답 |

## 입력 데이터

- 회원가입: email, name, password
- 모임 생성: name, 반복되는 dates(yyyy-MM-dd)
- 참여: code
- 가능 시간: 반복되는 slots(후보날짜ID_시작시), 예: 12_14

날짜는 모임당 중복 없이 1~7개이며 과거 날짜를 금지합니다. 슬롯 시작 시각은 정수 9~20이고 해당 모임 후보 날짜에 속해야 합니다. 일반 저장에는 최소 한 슬롯이 필요하며, 가능한 시간이 없으면 별도 요청을 사용합니다.

## A 업무 파일

- user: SignupForm, AccountService, AccountController
- meeting: MeetingForm, MeetingService, MeetingController
- availability: AvailabilityForm, AvailabilityService, AvailabilityController
- common: PageDataService(list/detail만 제공)
- templates: login, signup, meetings 하위 화면
- static: app.css, app.js

공통 모델과 Repository는 기존 뼈대 그대로입니다. coordination에는 공통 엔티티 Place와 Vote 및 package-info만 있습니다.

## 저장 규칙

- 가입 이메일은 공백 제거 후 소문자로 정규화하고 비밀번호는 해시합니다.
- 모임 생성과 생성자 참여 및 후보 날짜 저장은 같은 트랜잭션입니다.
- 초대 참여와 시간 수정은 공통 모임 쓰기 잠금을 먼저 얻습니다.
- 시간 수정은 기존 선택 삭제, 새 선택 저장, respondedAt 변경을 같은 트랜잭션으로 처리합니다.
- 잘못된 입력은 기존 선택을 지우기 전에 거부합니다.
- 확정 후의 A 요청은 공통 OPEN 상태 검사로 거부합니다. 확정 처리 자체는 B 담당입니다.

## 아직 구현하지 않은 B 요청

GET /meetings/{meetingId}/coordination, POST /meetings/{meetingId}/places, POST /meetings/{meetingId}/votes, POST /meetings/{meetingId}/confirm, GET /meetings/{meetingId}/result에 해당하는 B Controller와 Service는 없습니다. 화면에는 비활성 안내만 있습니다.
