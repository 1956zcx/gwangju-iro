package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class KakaoLocalApiService {

    @Value("${kakao.rest.api.key}")
    private String restApiKey;

    // 🎯 용도: 서브 장소(맛집/카페) 전용 검색
    public List<PlaceDto> getNearbyFoods(double lat, double lng, int radius, String budget) {
        List<String> keywords = new ArrayList<>();

        // ✅ 가성비/예산 조건
        if (budget.contains("가성비") || budget.contains("3만원")) {
            keywords.addAll(Arrays.asList("가성비 맛집", "저렴한 맛집", "분식", "국밥", "햄버거", "샌드위치"));
        } else if (budget.contains("10만원") || budget.contains("플렉스")) {
            keywords.addAll(Arrays.asList("오마카세", "레스토랑", "소고기", "파인다이닝"));
        } else {
            keywords.addAll(Arrays.asList("맛집", "밥집", "카페", "디저트"));
        }

        // ✅ 카페 데이터 확보를 위해 무조건 카페 키워드 추가
        keywords.add("감성 카페");
        keywords.add("디저트 카페");

        // size를 15로 넉넉하게 잡아서 필터링 후에도 식당/카페가 충분히 살아남게 함
        return fetchKakaoData(lat, lng, radius, keywords, 15);
    }

    // 🚗 이동 시간 계산: 자가용 → 카카오 모빌리티 API, 뚜벅이 → Haversine 계산
    public Map<String, Integer> getTravelInfo(double originLat, double originLng, double destLat, double destLng, boolean isWalking) {
        if (!isWalking) {
            try {
                RestTemplate restTemplate = new RestTemplate();
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "KakaoAK " + restApiKey);
                HttpEntity<String> entity = new HttpEntity<>(headers);

                URI uri = UriComponentsBuilder
                        .fromUriString("https://apis-navi.kakaomobility.com/v1/directions")
                        .queryParam("origin", originLng + "," + originLat)
                        .queryParam("destination", destLng + "," + destLat)
                        .build().encode().toUri();

                ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
                Map<String, Object> body = response.getBody();
                if (body != null) {
                    List<Map<String, Object>> routes = (List<Map<String, Object>>) body.get("routes");
                    if (routes != null && !routes.isEmpty()) {
                        Map<String, Object> summary = (Map<String, Object>) routes.get(0).get("summary");
                        int duration = ((Number) summary.get("duration")).intValue();
                        int distance = ((Number) summary.get("distance")).intValue();
                        Map<String, Integer> result = new HashMap<>();
                        result.put("duration", duration);
                        result.put("distance", distance);
                        return result;
                    }
                }
            } catch (Exception e) {
                System.err.println("카카오 모빌리티 API 오류 (자가용 폴백 사용): " + e.getMessage());
            }
        }
        return calcByHaversine(originLat, originLng, destLat, destLng, isWalking);
    }

    private Map<String, Integer> calcByHaversine(double lat1, double lng1, double lat2, double lng2, boolean isWalking) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        int straightDist = (int) (R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
        int actualDist = (int) (straightDist * 1.3);
        double speedMps = isWalking ? (4000.0 / 3600.0) : (30000.0 / 3600.0);
        int durationSec = (int) (actualDist / speedMps);
        Map<String, Integer> result = new HashMap<>();
        result.put("duration", durationSec);
        result.put("distance", actualDist);
        return result;
    }

    // ⚙️ 카카오 API 통신 로직 (맛집/카페 전용으로 단순화)
    private List<PlaceDto> fetchKakaoData(double lat, double lng, int radius, List<String> keywords, int size) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + restApiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        List<PlaceDto> spots = new ArrayList<>();

        for (String keyword : keywords) {
            try {
                URI uri = UriComponentsBuilder.fromUriString("https://dapi.kakao.com/v2/local/search/keyword.json")
                        .queryParam("query", keyword)
                        .queryParam("y", lat)
                        .queryParam("x", lng)
                        .queryParam("radius", radius > 20000 ? 20000 : radius)
                        .queryParam("sort", "distance")
                        .queryParam("size", size)
                        .build().encode().toUri();

                ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
                Map<String, Object> body = response.getBody();

                if (body != null && body.get("documents") != null) {
                    List<Map<String, Object>> docs = (List<Map<String, Object>>) body.get("documents");
                    for (Map<String, Object> doc : docs) {
                        String categoryGroupCode = (String) doc.get("category_group_code");

                        // 🚨 [핵심 필터] 오직 음식점(FD6)과 카페(CE7)만 통과시킴!
                        if (!"FD6".equals(categoryGroupCode) && !"CE7".equals(categoryGroupCode)) {
                            continue;
                        }

                        String name = (String) doc.get("place_name");
                        String category = (String) doc.get("category_name");

                        // 🛡️ 방어 로직 1: 술집/유흥 관련 단어 가차 없이 차단
                        if (category.contains("술집") || category.contains("유흥") || category.contains("주점") || category.contains("바") || category.contains("포장마차") ||
                                name.contains("호프") || name.contains("포차") || name.contains("펍") || name.contains("PUB") || name.contains("맥주") || name.contains("단란")) {
                            continue;
                        }

                        // 🛡️ 방어 로직 2: 부속 시설 및 주차장 차단
                        if (name.contains("주차장") || name.contains("지하") || name.contains("관리실") ||
                                name.contains("화장실") || name.contains("입구") || name.contains("전용")) {
                            continue;
                        }

                        double spotLat = Double.parseDouble((String) doc.get("y"));
                        double spotLng = Double.parseDouble((String) doc.get("x"));

                        spots.add(new PlaceDto(name, spotLat, spotLng, category));
                    }
                }
            } catch (Exception e) {
                System.out.println("카카오 로컬 API 에러 (" + keyword + "): " + e.getMessage());
            }
        }
        return spots;
    }
}