# 공통 뼈대 대비 A 변경 파일

같은 공통 뼈대에서 B가 별도로 작업한 경우, 아래 A 추가·변경 파일을 비교해 병합합니다. B 파일은 덮어쓰지 않습니다.

| 구분 | 파일 |
|---|---|
| 추가 | src/main/java/com/example/teammeeting/availability/AvailabilityController.java |
| 추가 | src/main/java/com/example/teammeeting/availability/AvailabilityForm.java |
| 추가 | src/main/java/com/example/teammeeting/availability/AvailabilityService.java |
| 추가 | src/main/java/com/example/teammeeting/common/PageDataService.java |
| 제외 | src/main/java/com/example/teammeeting/common/ScaffoldController.java |
| 변경 | src/main/java/com/example/teammeeting/config/SecurityConfig.java |
| 추가 | src/main/java/com/example/teammeeting/meeting/MeetingController.java |
| 추가 | src/main/java/com/example/teammeeting/meeting/MeetingForm.java |
| 추가 | src/main/java/com/example/teammeeting/meeting/MeetingService.java |
| 추가 | src/main/java/com/example/teammeeting/user/AccountController.java |
| 추가 | src/main/java/com/example/teammeeting/user/AccountService.java |
| 추가 | src/main/java/com/example/teammeeting/user/SignupForm.java |
| 추가 | src/main/resources/static/css/app.css |
| 추가 | src/main/resources/static/js/app.js |
| 변경 | src/main/resources/templates/error.mustache |
| 변경 | src/main/resources/templates/login.mustache |
| 추가 | src/main/resources/templates/meetings/detail.mustache |
| 추가 | src/main/resources/templates/meetings/list.mustache |
| 추가 | src/main/resources/templates/meetings/new.mustache |
| 변경 | src/main/resources/templates/partials/footer.mustache |
| 변경 | src/main/resources/templates/partials/header.mustache |
| 제외 | src/main/resources/templates/scaffold/index.mustache |
| 추가 | src/main/resources/templates/signup.mustache |
| 추가 | src/test/java/com/example/teammeeting/AIntegrationTest.java |
| 추가 | src/test/java/com/example/teammeeting/AMergeReadinessTest.java |
| 변경 | src/test/java/com/example/teammeeting/CommonSkeletonTest.java |
