# 📍 광주 이로 (Gwangju IRo)

MBTI, 예산, 시간, 이동수단, 선호 테마를 기반으로 AI가 광주 맞춤 여행 코스를 추천해주는 웹 서비스입니다.

## 기술 스택

| 구분 | 기술 |
|------|------|
| Backend | Java 21, Spring Boot 4.0.4, Spring Data JPA, JWT |
| Frontend | React 18, Axios |
| Database | MySQL 8 |
| AI | OpenAI ChatGPT API |
| 지도 | Kakao Maps SDK |
| 외부 API | 공공데이터 TourAPI 4.0, Kakao REST API, Kakao Mobility API |
| 인증 | 카카오 소셜 로그인 + JWT |
| 배포 | Vercel (프론트), 로컬/ngrok (백엔드) |

## 주요 기능

- **맞춤 코스 추천**: MBTI · 예산 · 시간 · 이동수단 · 선호 테마 조합으로 최적 일정 생성
- **현재 위치 기반 탐색**: GPS로 반경 4km 내 장소 검색
- **동선 최적화**: Nearest Neighbor 알고리즘으로 이동 거리 최소화
- **메인-서브 페어링**: 관광지 + 근처 식당/카페 자동 매칭
- **맛집 투어 모드**: 식당을 중심으로 주변 소화용 관광지 추천
- **AI 일정 설명**: ChatGPT가 각 장소의 추천 이유와 이동 동선 설명 생성
- **실측 이동시간 계산**: Kakao Mobility API로 대중교통/자가용 실제 소요시간 산출 (GPT 의존 제거)
- **이동수단별 자동 전환**: 자가용 600m 미만 → 도보, 뚜벅이 1km 이상 → 대중교통으로 표시 전환
- **총 예상시간/거리**: 실측 이동시간 + 체류시간을 백엔드에서 합산하여 정확하게 제공
- **카카오맵 길찾기**: 각 장소 카드에서 이전 장소 → 현재 장소 구간 카카오맵 길찾기 연동
- **카카오 소셜 로그인**: 카카오 계정으로 1-click 로그인
- **코스 저장**: 마음에 든 코스를 내 계정에 저장 후 재열람/삭제
- **카카오톡 공유**: 코스를 카카오톡 공유 카드로 전송

## 프로젝트 구조

```
demo/
├── src/main/java/com/example/demo/
│   ├── TestController.java         # 코스 추천 API (/api/recommend)
│   ├── AuthController.java         # 카카오 로그인 API (/api/auth/kakao)
│   ├── CourseController.java       # 코스 저장/조회/삭제 API (/api/courses)
│   ├── JwtUtil.java                # JWT 토큰 생성/검증
│   ├── JwtFilter.java              # JWT 인증 필터
│   ├── User.java / UserRepository.java
│   ├── SavedCourse.java / SavedCourseRepository.java
│   ├── ChatGptService.java         # GPT API 호출 및 응답 처리
│   ├── KakaoLocalApiService.java   # 카카오 장소 검색 (서브 장소 폴백)
│   ├── TourApiService.java         # 공공데이터 관광지 API
│   ├── PlaceDbService.java         # DB 장소 조회 로직
│   ├── PlaceRepository.java        # JPA 쿼리
│   └── Place.java / PlaceDto.java
├── src/main/resources/
│   └── application.properties
└── travel-client/                  # React 프론트엔드
    ├── src/
    │   ├── App.js                  # 메인 화면 (조건 선택, 결과 표시, 로그인/저장/공유)
    │   └── KakaoMapService.js      # 카카오 지도 컴포넌트
    └── public/
        └── index.html              # 카카오 Maps SDK 로드
```

## API

### `GET /api/recommend` — 코스 추천

| 파라미터 | 설명 | 예시 |
|---------|------|------|
| `mbti` | 사용자 MBTI | `ENFP` |
| `budget` | 예산대 | `5만원 (적당함)` |
| `time` | 가용 시간 | `6시간 (반나절)` |
| `vehicle` | 이동수단 | `뚜벅이 (대중교통/도보)` |
| `district` | 지역구 또는 내주변 | `동구` |
| `preference` | 선호 테마 | `맛집/카페 투어 (먹방, 디저트)` |
| `lat` / `lng` | 중심 좌표 | `35.146` / `126.923` |
| `exclude` | 제외할 장소명 (중복 방지) | `장소1,장소2` |

**응답 예시**
```json
{
  "totalTime": "약 6시간",
  "totalDistance": "약 12km",
  "plans": [
    {
      "name": "장소명",
      "theme": "놀거리",
      "indoorOutdoor": "실내",
      "lat": 35.146,
      "lng": 126.923,
      "description": "추천 이유",
      "distToNext": "도보 3분"
    }
  ]
}
```

### `POST /api/auth/kakao` — 카카오 로그인

카카오 액세스 토큰을 받아 사용자 정보를 조회/생성하고 JWT를 반환합니다.

```json

{ "accessToken": "카카오_액세스_토큰" }


{ "token": "JWT", "nickname": "홍길동", "profileImage": "..." }
```

### `POST /api/courses/save` — 코스 저장 (JWT 필요)

| 필드 | 설명 |
|------|------|
| `title` | 코스 제목 |
| `courseJson` | plans 배열 JSON 문자열 |
| `conditions` | 검색 조건 요약 |
| `district` | 지역구 |

### `GET /api/courses/my` — 내 코스 목록 (JWT 필요)

### `GET /api/courses/my/{id}` — 코스 상세 조회 (JWT 필요)

### `DELETE /api/courses/my/{id}` — 코스 삭제 (JWT 필요)

## 로컬 실행 방법

### 사전 준비

- Java 21
- MySQL 8 (DB명: `travel`)
- Node.js 18+
- 아래 환경변수 설정

### 환경변수

| 변수명 | 용도 |
|--------|------|
| `GPT_KEY` | OpenAI API 키 |
| `TOUR_KEY` | 공공데이터 TourAPI 서비스키 |
| `KAKAO_KEY` | 카카오 REST API 키 (로컬 API + Mobility API) |
| `DB_USERNAME` | MySQL 사용자명 |
| `DB_PASSWORD` | MySQL 비밀번호 |

### 백엔드 실행

```powershell
$env:GPT_KEY="sk-..."
$env:TOUR_KEY="..."
$env:KAKAO_KEY="..."
$env:DB_USERNAME="root"
$env:DB_PASSWORD="비밀번호"

./gradlew bootRun
```

### 프론트엔드 실행

```bash
cd travel-client
npm install
npm start
```

브라우저에서 `http://localhost:3000` 접속

### 프론트엔드 환경변수 (선택)

백엔드가 `localhost:8080`이 아닌 경우 `travel-client/.env` 파일 생성:

```
REACT_APP_API_URL=http://백엔드주소
```

## 데이터베이스

### `place` 테이블

| 컬럼 | 설명 |
|------|------|
| `name` | 장소명 |
| `mainCategory` / `subCategory` / `detailCategory` | 카테고리 분류 |
| `budgetLevel` | 예산 레벨 |
| `indoorOutdoor` | 실내/실외 |
| `accessibility` | 접근성 (차량/도보) |
| `lat` / `lng` | 좌표 |
| `type` | 메인/서브 장소 구분 |
| `district` | 지역구 |

### `user` 테이블

카카오 로그인 사용자 정보 저장 (kakaoId, nickname, profileImage)

### `saved_course` 테이블

사용자가 저장한 코스 JSON 및 메타정보 저장
