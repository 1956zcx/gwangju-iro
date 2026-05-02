package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin(origins = {"https://gwangju-iro.vercel.app", "http://localhost:3000"})
public class TestController {

    @Autowired
    private PlaceDbService placeDbService;

    @Autowired
    private KakaoLocalApiService kakaoLocalApiService;

    @Autowired
    private ChatGptService chatGptService;

    @GetMapping("/api/recommend")
    public String getRecommendations(
            @RequestParam String mbti,
            @RequestParam String budget,
            @RequestParam String time,
            @RequestParam String vehicle,
            @RequestParam String district,
            @RequestParam String preference,
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false, defaultValue = "") String exclude) {

        // 1. 코스 시간에 따른 필요 장소 개수 설정
        int requiredMains = 0;
        int requiredSubs = 0;

        if (time.contains("4시간")) {
            requiredMains = 2;
            requiredSubs = 1;
        } else if (time.contains("6시간")) {
            requiredMains = 3;
            requiredSubs = 1;
        } else {
            requiredMains = 4;
            requiredSubs = 2;
        }

        // 2. 프론트엔드의 긴 텍스트에서 핵심 키워드 추출
        String corePreference = "";
        if (preference.contains("쇼핑")) corePreference = "쇼핑/트렌드";
        else if (preference.contains("액티비티")) corePreference = "액티비티";
        else if (preference.contains("역사")) corePreference = "역사/문화";
        else if (preference.contains("자연")) corePreference = "자연/공원";
        else corePreference = preference;

        // ✅ 3. 이동 수단에 따른 DB 필터링 조건 (리스트로 만들기)
        List<String> accessibilities = new ArrayList<>();
        if (vehicle.contains("자가용")) {
            // 자가용은 차량 전용과 도보 전용 모두 갈 수 있음
            accessibilities.add("차량");
            accessibilities.add("도보");
        } else {
            // 도보 여행자는 도보 전용만 갈 수 있음
            accessibilities.add("도보");
        }

        // ====================================================================
        // 🚀 4. DB에서 조건에 맞는 메인 장소 가져오기 (맛집 투어 분기 + 구명조끼)
        List<PlaceDto> mainSpots = new ArrayList<>();

        // ✅ 사용자가 맛집 투어를 선택했는지 확인!
        boolean isGastronomyTour = preference.contains("맛집") || preference.contains("먹방");

        if (district.equals("내주변") || district.equals("현재위치")) {
            System.out.println("📍 [모드] 현재 위치 기반 반경 4km 검색 시작");

            // 맛집 모드면 서브장소를, 아니면 일반 메인장소를 검색
            if (isGastronomyTour) {
                mainSpots = placeDbService.getFoodSpotsAsMainByRadius(lat, lng, accessibilities);
            } else {
                mainSpots = placeDbService.getMainSpotsByRadius(lat, lng, corePreference, accessibilities);
            }

            // 🚨 구명조끼 발동
            if (mainSpots.size() < requiredMains) {
                System.out.println("⚠️ 반경 내 장소 부족! 범위 확대 검색!");
                List<PlaceDto> backupSpots;
                if (isGastronomyTour) {
                    backupSpots = placeDbService.getFoodSpotsAsMain("", accessibilities); // 맛집 광역검색
                } else {
                    backupSpots = placeDbService.getMainSpots(corePreference, "", accessibilities); // 관광지 광역검색
                }
                for (PlaceDto spot : backupSpots) {
                    boolean isDuplicate = mainSpots.stream().anyMatch(m -> m.getName().equals(spot.getName()));
                    if (!isDuplicate) mainSpots.add(spot);
                    if (mainSpots.size() >= 10) break;
                }
            }
        } else {
            System.out.println("🗺️ [모드] 특정 지역구(" + district + ") 검색 시작");
            if (isGastronomyTour) {
                mainSpots = placeDbService.getFoodSpotsAsMain(district, accessibilities);
            } else {
                mainSpots = placeDbService.getMainSpots(corePreference, district, accessibilities);
            }
        }
        System.out.println("🚨 [디버깅] 최종 메인 장소 개수: " + mainSpots.size());
        // ====================================================================

        // =========================================================================
        // 🚀 5. [알고리즘 켜짐] 'Nearest Neighbor' 동선 최적화
        List<PlaceDto> optimizedSpots = new ArrayList<>();

        if (!mainSpots.isEmpty()) {
            PlaceDto current = mainSpots.get(0);
            optimizedSpots.add(current);
            mainSpots.remove(0);

            // ✅ 수정: 10개 후보를 도중에 자르지 않고 끝까지 거리순으로 예쁘게 정렬합니다.
            while (!mainSpots.isEmpty()) {
                double minDistance = Double.MAX_VALUE;
                int nearestIndex = -1;

                for (int i = 0; i < mainSpots.size(); i++) {
                    PlaceDto candidate = mainSpots.get(i);
                    double dist = Math.pow(current.getLat() - candidate.getLat(), 2) + Math.pow(current.getLng() - candidate.getLng(), 2);

                    if (dist < minDistance) {
                        minDistance = dist;
                        nearestIndex = i;
                    }
                }
                current = mainSpots.get(nearestIndex);
                optimizedSpots.add(current);
                mainSpots.remove(nearestIndex);
            }
        }
        // =========================================================================

        // 5.5. 실측 이동 시간 계산 (카카오 모빌리티 API / Haversine)
        boolean isWalkingMode = vehicle.contains("뚜벅이");
        String travelMode = isWalkingMode ? "도보" : "차량";
        StringBuilder travelInfoBuilder = new StringBuilder();
        travelInfoBuilder.append("[★실측 이동 시간 데이터 (distToNext에 반드시 그대로 사용)★]\n");

        int totalTravelSeconds = 0;
        int mainSegMeters = 0;
        int usableMainCount = Math.min(optimizedSpots.size(), requiredMains);

        for (int i = 0; i < usableMainCount - 1; i++) {
            PlaceDto from = optimizedSpots.get(i);
            PlaceDto to = optimizedSpots.get(i + 1);
            Map<String, Integer> info = kakaoLocalApiService.getTravelInfo(
                    from.getLat(), from.getLng(), to.getLat(), to.getLng(), isWalkingMode);
            int meters = info.get("distance");

            String segmentLabel;
            int minutes;

            if (!isWalkingMode && meters < 600) {
                // 자가용이지만 600m 미만 → 도보
                minutes = Math.max(1, (int) (meters / (4000.0 / 60.0)));
                segmentLabel = "도보";
            } else if (isWalkingMode && meters >= 1000) {
                // 뚜벅이인데 1km 이상 → 대중교통 (대기 5분 + 20km/h 기준 이동시간)
                minutes = Math.max(8, 5 + (int) (meters / 333.0));
                segmentLabel = "대중교통";
            } else {
                minutes = Math.max(1, info.get("duration") / 60);
                segmentLabel = travelMode;
            }

            travelInfoBuilder.append(String.format("- 메인%d → 메인%d: %s %d분 (약 %dm)\n", i + 1, i + 2, segmentLabel, minutes, meters));
            totalTravelSeconds += minutes * 60;
            mainSegMeters += meters;
        }
        int mainSegTravelMin = totalTravelSeconds / 60;
        double mainSegKm = mainSegMeters / 1000.0;
        travelInfoBuilder.append(String.format("이동 합계: %s %d분, 약 %.1fkm\n", travelMode, mainSegTravelMin, mainSegKm));
        travelInfoBuilder.append("(메인 장소 → 바로 옆 짝꿍 서브 장소 이동은 '도보 3분 이내'로 표기)");
        String travelInfoStr = travelInfoBuilder.toString();

        // 6. 메인 장소와 서브 장소(맛집/카페) 짝짓기
        StringBuilder pairedDataBuilder = new StringBuilder();
        int mainCount = 0;
        int subCount = 0;
        Set<String> usedNames = new HashSet<>();
        List<PlaceDto> finalSpotOrder = new ArrayList<>();

        for (PlaceDto spot : optimizedSpots) {
            // ✅ 목표 개수를 채우면 즉시 중단
            if (mainCount >= requiredMains) break;

            // 이름 중복 방지
            if (usedNames.contains(spot.getName())) continue;

            pairedDataBuilder.append(String.format(
                    "★ 메인 장소 %d: [%s: %s (실내외: %s, lat:%f, lng:%f)]\n",
                    mainCount + 1, spot.getDetailCategory(), spot.getName(), spot.getIndoorOutdoor(), spot.getLat(), spot.getLng()
            ));

            usedNames.add(spot.getName());
            mainCount++;
            finalSpotOrder.add(spot);

            // ✅ 수정: 코스 흐름에 맞게 밥 먹는 타이밍을 정확히 제어합니다.
            boolean matchSubNow = false;
            if (requiredMains == 2 && mainCount == 2) matchSubNow = true;
            else if (requiredMains == 3 && mainCount == 3) matchSubNow = true;
            else if (requiredMains == 4 && (mainCount == 2 || mainCount == 4)) matchSubNow = true;
            if (mainCount == optimizedSpots.size() && subCount < requiredSubs) {
                matchSubNow = true;
            }
            // 서브 장소 매칭 시작
            if (matchSubNow && subCount < requiredSubs) {
                PlaceDto matchedSub = null;

                if (isGastronomyTour) {
                    // 🏃 [맛집 투어 모드] 배부르니까 서브 장소로 '소화시킬 관광지(메인 카테고리)'를 찾음!
                    System.out.println("🏃 [소화 모드] 맛집 주변 관광지 검색 시작!");
                    List<PlaceDto> digestSpots = placeDbService.getNearbyMainSpots(spot.getLat(), spot.getLng(), accessibilities);

                    // 🚨 여기에 CCTV (디버깅 코드) 추가!
                    System.out.println("🔎 [" + spot.getName() + "] 주변 1km 내 관광지 검색 결과: " + digestSpots.size() + "개 찾음!");

                    for (PlaceDto digestSpot : digestSpots) {
                        if (!exclude.contains(digestSpot.getName()) && !usedNames.contains(digestSpot.getName())) {
                            matchedSub = digestSpot;
                            System.out.println("🏃 DB 소화용 관광지 매칭 성공: " + matchedSub.getName());
                            break;
                        }
                    }
                } else {
                    // 🏠 [일반 모드] 구경했으니까 서브 장소로 '식당/카페'를 찾음! (기존 로직 살짝 다듬음)
                    List<PlaceDto> dbSubSpots = placeDbService.getSubSpots(spot.getLat(), spot.getLng(), 400, accessibilities);
                    for(PlaceDto f : dbSubSpots){
                        if(!exclude.contains(f.getName()) && !usedNames.contains(f.getName())){
                            matchedSub = f;
                            System.out.println("🏠 DB 서브장소 매칭 성공: " + matchedSub.getName());
                            break;
                        }
                    }

                    // 카카오 API 폴백 (DB에 없을 때)
                    if (matchedSub == null) {
                        List<PlaceDto> nearbyFoods = kakaoLocalApiService.getNearbyFoods(spot.getLat(), spot.getLng(), 800, budget);
                        if (nearbyFoods.isEmpty()) {
                            nearbyFoods = kakaoLocalApiService.getNearbyFoods(spot.getLat(), spot.getLng(), 1000, budget);
                        }
                        for(PlaceDto f : nearbyFoods){
                            if(!exclude.contains(f.getName()) && !usedNames.contains(f.getName())){
                                matchedSub = f;
                                System.out.println("🌐 카카오 API 서브장소 매칭: " + matchedSub.getName());
                                break;
                            }
                        }
                    }
                }

                // 성공적으로 짝꿍 장소를 찾았다면 추가!
                if (matchedSub != null) {
                    pairedDataBuilder.append(String.format(
                            "  ➔ 짝꿍 서브 장소 %d: [%s: %s (lat:%f, lng:%f)]\n",
                            subCount + 1, matchedSub.getDetailCategory(), matchedSub.getName(), matchedSub.getLat(), matchedSub.getLng()
                    ));
                    finalSpotOrder.add(matchedSub);
                    usedNames.add(matchedSub.getName());
                    subCount++;
                }
            }
        }

        // 6.5 모든 구간 실측 distToNext 계산 (GPT 응답에 주입용)
        Map<String, String> distToNextMap = new LinkedHashMap<>();
        int totalTravelMin = 0;
        int totalTravelMeters = 0;
        for (int i = 0; i < finalSpotOrder.size() - 1; i++) {
            PlaceDto from = finalSpotOrder.get(i);
            PlaceDto to = finalSpotOrder.get(i + 1);
            Map<String, Integer> segInfo = kakaoLocalApiService.getTravelInfo(
                    from.getLat(), from.getLng(), to.getLat(), to.getLng(), isWalkingMode);
            int segMeters = segInfo.get("distance");
            String segLabel;
            int segMinutes;
            if (!isWalkingMode && segMeters < 600) {
                segMinutes = Math.max(1, (int) (segMeters / (4000.0 / 60.0)));
                segLabel = "도보";
            } else if (isWalkingMode && segMeters >= 1000) {
                segMinutes = Math.max(8, 5 + (int) (segMeters / 333.0));
                segLabel = "대중교통";
            } else {
                segMinutes = Math.max(1, segInfo.get("duration") / 60);
                segLabel = travelMode;
            }
            distToNextMap.put(from.getName(), segLabel + " " + segMinutes + "분 (약 " + segMeters + "m)");
            totalTravelMin += segMinutes;
            totalTravelMeters += segMeters;
        }

        // 장소별 체류 예상시간 합산
        int totalStayMin = 0;
        for (PlaceDto spot : finalSpotOrder) {
            totalStayMin += estimateStayMinutes(spot);
        }

        int totalMin = totalTravelMin + totalStayMin;
        String totalTimeStr = totalMin >= 60
                ? "약 " + (totalMin / 60) + "시간 " + (totalMin % 60) + "분"
                : "약 " + totalMin + "분";
        String totalDistanceStr = totalTravelMeters >= 1000
                ? String.format("약 %.1fkm", totalTravelMeters / 1000.0)
                : "약 " + totalTravelMeters + "m";

        // 7. 데이터가 하나도 없을 경우의 예외 처리
        if (pairedDataBuilder.length() == 0) {
            System.out.println("⚠️ DB에서 조건에 맞는 장소를 찾지 못했습니다.");
            return "{\n  \"totalTime\": \"알 수 없음\",\n  \"totalDistance\": \"알 수 없음\",\n  \"plans\": []\n}";
        }

        String pairedDataString = pairedDataBuilder.toString();

        // 8. GPT 프롬프트 (환각 방지 + 맛집 누락 방지 완벽 적용)
        String prompt = String.format(
                "너는 '광주 이로'의 수석 AI 여행 플래너야. 자바 코드가 물리적으로 완벽하게 짝지어둔 장소 데이터만 제공할게!\n\n" +
                        "[사용자 조건]\n" +
                        "- 집중 지역: 광주광역시 %s\n" +
                        "- 선호 테마: %s\n" +
                        "- MBTI: %s\n" +
                        "- 총 예산: %s\n" +
                        "- 가용 시간: %s\n" +
                        "- 이동 수단: %s\n\n" +
                        "[★제공된 초밀착 페어링 데이터★]\n" +
                        "%s\n" +
                        "(주의: 반드시 위에서 제공된 데이터만 추천해!)\n\n" +
                        "%s\n\n" +
                        "[★동선 설계 철칙★]\n" +
                        "1. (초강력 경고) 무조건 내가 제공한 '★ 메인 장소'와 '➔ 짝꿍 서브 장소'에 있는 실제 장소 이름만 사용해! 가짜 이름을 절대 지어내지 마!\n" +
                        "2. 내가 제공한 데이터가 3개면 3개로, 5개면 5개로만 코스를 짜. 억지로 개수를 채우지 마.\n" +
                        "3. 🚨 [매우 중요] 내가 제공한 '➔ 짝꿍 서브 장소(맛집/카페)'는 절대로 누락하지 말고, 반드시 코스(plans 배열)의 적절한 순서에 무조건 포함시켜!\n" +
                        "4. [초강력 경고] 내가 제공한 텍스트의 순서(메인 1 ➔ 짝꿍 서브 1 ➔ 메인 2...)를 100%% 완벽하게 똑같이 유지해서 JSON 배열(plans)에 넣어! 절대로 네 마음대로 장소의 순서를 섞거나, 카테고리별로 재배치하지 마! 지도에 그릴 때 선이 꼬이게 됨!\n" +
                        "5. distToNext는 아무 값이나 넣어도 됨. 백엔드에서 실측값으로 덮어씌울 것임.\n" +
                        "6. 각 장소가 '실내'인지 '실외'인지 판단해줘.\n" +
                        "7. 전체 코스의 '총 예상 소요 시간'과 '총 예상 이동 거리'를 계산해줘.\n" +
                        "8. 오직 아래 JSON 형식으로만 응답해. 배열이 아니라 객체 형태야!\n\n" +
                        "형식:\n" +
                        "{\n" +
                        "  \"totalTime\": \"약 6시간\",\n" +
                        "  \"totalDistance\": \"약 12km\",\n" +
                        "  \"plans\": [\n" +
                        "    {\"theme\": \"놀거리\", \"name\": \"장소명A\", \"indoorOutdoor\": \"실내\", \"lat\": 35.xxx, \"lng\": 126.xxx, \"description\": \"추천 이유\", \"distToNext\": \"도보 7분 (약 480m)\"},\n" +
                        "    {\"theme\": \"맛집/카페\", \"name\": \"식당명A\", \"indoorOutdoor\": \"실내\", \"lat\": 35.xxx, \"lng\": 126.xxx, \"description\": \"방금 구경한 장소 바로 근처 맛집입니다.\", \"distToNext\": \"도보 12분 (약 850m)\"}\n" +
                        "  ]\n" +
                        "}",
                district, preference, mbti, budget, time, vehicle, pairedDataString, travelInfoStr
        );

        String gptResponse = chatGptService.getChatResponse(mbti, "Gwangju", prompt);

        // 실측 distToNext 주입 (GPT가 생성한 값을 백엔드 계산값으로 완전히 교체)
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = (ObjectNode) mapper.readTree(gptResponse);
            ArrayNode plans = (ArrayNode) root.get("plans");
            if (plans != null) {
                List<String> orderedDistToNext = new ArrayList<>(distToNextMap.values());
                for (int i = 0; i < plans.size(); i++) {
                    ObjectNode plan = (ObjectNode) plans.get(i);
                    if (i < orderedDistToNext.size()) {
                        plan.put("distToNext", orderedDistToNext.get(i));
                    } else {
                        plan.putNull("distToNext");
                    }
                }
            }
            root.put("totalTime", totalTimeStr);
            root.put("totalDistance", totalDistanceStr);
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            System.err.println("distToNext 주입 실패, GPT 원본 반환: " + e.getMessage());
            return gptResponse;
        }
    }

    private int estimateStayMinutes(PlaceDto spot) {
        String cat = (spot.getDetailCategory() != null ? spot.getDetailCategory() : "").toLowerCase();
        String main = (spot.getMainCategory() != null ? spot.getMainCategory() : "").toLowerCase();
        if (cat.contains("카페") || cat.contains("디저트") || cat.contains("베이커리") || cat.contains("cafe")) {
            return 30;
        }
        if (cat.contains("음식") || cat.contains("식당") || cat.contains("한식") || cat.contains("중식") ||
                cat.contains("일식") || cat.contains("양식") || cat.contains("분식") || cat.contains("국밥") ||
                main.contains("서브장소") || main.contains("먹거리")) {
            return 45;
        }
        return 60;
    }
}