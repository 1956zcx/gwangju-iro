# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

광주광역시 맞춤 여행 코스 추천 서비스. Spring Boot 백엔드 + React 프론트엔드 모노레포 구조.

- 백엔드: `src/` (Java 21, Spring Boot 4.0.4, Gradle)
- 프론트엔드: `travel-client/` (React 18, CRA)
- DB: MySQL, DB명 `travel`, 테이블: `place`

## 개발 명령어

### 백엔드

```powershell
# 환경변수 설정 후 실행
$env:GPT_KEY="sk-..."
$env:TOUR_KEY="..."
$env:KAKAO_KEY="..."
$env:DB_USERNAME="root"
$env:DB_PASSWORD="비밀번호"

./gradlew bootRun       # 개발 서버 (포트 8080)
./gradlew build         # 빌드
./gradlew test          # 전체 테스트
./gradlew test --tests "com.example.demo.DemoApplicationTests"  # 단일 테스트
```

### 프론트엔드

```bash
cd travel-client
npm install             # 최초 1회
npm start               # 개발 서버 (포트 3000)
npm run build           # 프로덕션 빌드
```

백엔드가 `localhost:8080`이 아닌 경우 `travel-client/.env`에 `REACT_APP_API_URL=...` 설정.

## 핵심 아키텍처

### 요청 흐름

```
React (App.js)
  → GET /api/recommend (TestController)
      → PlaceDbService (MySQL 조회)
      → KakaoLocalApiService (서브 장소 폴백)
      → ChatGptService (GPT API)
  ← JSON { totalTime, totalDistance, plans[] }
```

### 장소 선정 로직 (TestController.java)

1. **모드 분기**: `district=내주변` → 반경 4km GPS 검색 / 그 외 → 지역구 검색
2. **맛집 투어 모드**: `preference`에 "맛집"/"먹방" 포함 시 서브 장소(식당)를 메인으로 승격, 관광지를 소화용 서브로 배치
3. **구명조끼 시스템**: 반경 내 장소가 `requiredMains`보다 부족하면 광역 검색으로 자동 확대
4. **동선 최적화**: Nearest Neighbor 알고리즘으로 이동 거리 최소화
5. **메인-서브 페어링**: 메인 장소 N개 탐방 후 서브(식당/카페) 1개 삽입. 타이밍 규칙:
   - 2개 코스: 마지막 메인 후
   - 3개 코스: 마지막 메인 후
   - 4개 코스: 2번째, 4번째 메인 후
6. **서브 장소 탐색 순서**: DB(`findNearbySubSpots`) → 없으면 Kakao API 폴백

### GPT 프롬프트 설계 원칙

`TestController.java` 의 `prompt` 문자열 참고. 핵심 규칙:

- 자바 코드가 페어링한 장소 데이터만 사용하도록 강제 (환각 방지)
- 장소 순서를 절대 바꾸지 말 것 (지도 동선이 꼬임)
- 서브 장소(짝꿍) 누락 금지
- 순수 JSON만 응답하도록 system 프롬프트 설정
- GPT 응답에서 마크다운 코드블록(` ```json `) 제거 처리 포함

현재 GPT 모델: `gpt-5.4-mini` (`ChatGptService.java:39`)

### CORS

`TestController.java`에 `@CrossOrigin` 하드코딩:
- `https://gwangju-iro.vercel.app`
- `http://localhost:3000`

프론트 배포 URL 변경 시 이 값도 수정 필요.

### Vercel 배포

루트의 `vercel.json`이 `travel-client/`를 빌드 루트로 지정. 프론트만 Vercel 배포, 백엔드는 별도 서버(ngrok 등) 필요.