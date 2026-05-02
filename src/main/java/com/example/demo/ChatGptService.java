package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Service
public class ChatGptService {

    @Value("${gpt.api.key}")
    private String apiKey;

    private final String ENDPOINT = "https://api.openai.com/v1/chat/completions";

    public String getChatResponse(String mbti, String location, String prompt) {
        RestTemplate restTemplate = new RestTemplate();

        // 1. 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        // 2. 메시지 구성 (System + User)
        // ChatGptService.java 의 messages 구성 부분 수정
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "너는 오직 순수한 JSON 데이터만 생성하는 기계야. 서론, 결론, 부연 설명은 절대 하지 마. " +
                        "반드시 사용자 프롬프트에서 마지막에 요구한 JSON 객체({ ... }) 형식과 키값을 정확히 지켜서 응답해."));
        messages.add(Map.of("role", "user", "content", prompt));

        // 3. 요청 바디 구성
        Map<String, Object> body = new HashMap<>();
        body.put("model", "gpt-5.4-mini");
        body.put("messages", messages);
        body.put("temperature", 0.8);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(ENDPOINT, request, Map.class);
            Map<String, Object> responseBody = response.getBody();

            if (responseBody != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                String content = (String) message.get("content");

                // ★ 마크다운 태그(```json 등)가 포함되어 오면 제거해서 순수 JSON만 남깁니다.
                content = content.replaceAll("(?i)```json", "") // 대소문자 구분 없이 제거
                        .replaceAll("```", "")
                        .trim();

                return content;
            }
        } catch (Exception e) {
            System.err.println("GPT API 호출 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }

        // 4. 에러 발생 시나 응답이 없을 경우 빈 JSON 배열 반환
        return "[]";
    }
}