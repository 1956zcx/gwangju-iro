package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Service
public class TourApiService {

    @Value("${tourapi.serviceKey}")
    private String serviceKey;

    private final String BASE_URL = "http://apis.data.go.kr/B551011/KorService2/locationBasedList2";

    // ★ String 반환에서 List<PlaceDto> 반환으로 변경!
    public List<PlaceDto> getTouristSpots(double lat, double lng, int radius) {
        RestTemplate restTemplate = new RestTemplate();
        List<PlaceDto> spots = new ArrayList<>();

        // fromHttpUrl 대신 fromUriString 사용
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
                .queryParam("serviceKey", serviceKey)
                .queryParam("numOfRows", 50)
                .queryParam("pageNo", 1)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "GwangjuOnTheRock")
                .queryParam("_type", "json")
                .queryParam("mapX", lng)
                .queryParam("mapY", lat)
                .queryParam("radius", radius)
                // ✅ 새로 추가: E(거리순) 정렬! 무조건 중심 좌표에서 가장 가까운 곳부터 가져옵니다.
                .queryParam("arrange", "E")
                .build()
                .toUri();

        System.out.println("관광지 요청 URL: " + uri);

        try {
            String response = restTemplate.getForObject(uri, String.class);

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode itemsNode = rootNode.path("response").path("body").path("items").path("item");

            if (itemsNode.isArray()) {
                for (JsonNode node : itemsNode) {
                    String name = node.path("title").asText();
                    double spotLat = node.path("mapy").asDouble();
                    double spotLng = node.path("mapx").asDouble();

                    // 공공데이터의 '콘텐츠 타입' 코드를 가져옵니다.
                    String contentTypeId = node.path("contenttypeid").asText();

                    // ✅ 핵심 방어 로직 (화이트리스트 방식 적용)
                    // 12(관광지), 14(문화시설), 28(레포츠)가 아니면 무조건 제외!
                    // 이렇게 하면 15(축제), 39(식당), 32(숙박), 38(쇼핑) 등이 한 번에 완벽하게 걸러집니다.
                    if (!("12".equals(contentTypeId) || "14".equals(contentTypeId) || "28".equals(contentTypeId))) {
                        continue;
                    }

                    // ✅ 2차 텍스트 방어 (여전히 유지)
                    if (name.contains("식당") || name.contains("카페") || name.contains("가든") || name.contains("횟집") || name.contains("축제")) {
                        continue;
                    }

                    // 검증을 완벽하게 통과한 '진짜 상설 관광지'만 바구니에 담습니다.
                    spots.add(new PlaceDto(name, spotLat, spotLng, "관광지"));
                }
            }
        } catch (Exception e) {
            System.err.println("관광지 API 호출 또는 파싱 에러 발생!");
            e.printStackTrace();
        }

        return spots;
    }
}