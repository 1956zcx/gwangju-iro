package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"https://gwangju-iro.vercel.app", "http://localhost:3000"})
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @SuppressWarnings("unchecked")
    @PostMapping("/kakao")
    public ResponseEntity<?> kakaoLogin(@RequestBody Map<String, String> body) {
        String accessToken = body.get("accessToken");

        RestTemplate rt = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> kakaoResponse = rt.exchange(
                "https://kapi.kakao.com/v2/user/me",
                HttpMethod.GET,
                entity,
                Map.class
        );

        Map<String, Object> kakaoUser = kakaoResponse.getBody();
        String kakaoId = String.valueOf(kakaoUser.get("id"));

        Map<String, Object> properties = (Map<String, Object>) kakaoUser.get("properties");
        String nickname = properties != null ? (String) properties.getOrDefault("nickname", "") : "";
        String profileImage = properties != null ? (String) properties.getOrDefault("profile_image", "") : "";

        User user = userRepository.findByKakaoId(kakaoId).orElseGet(() -> {
            User newUser = new User();
            newUser.setKakaoId(kakaoId);
            newUser.setNickname(nickname);
            newUser.setProfileImage(profileImage);
            return userRepository.save(newUser);
        });

        Map<String, Object> result = new HashMap<>();
        result.put("token", jwtUtil.generateToken(kakaoId));
        result.put("nickname", user.getNickname());
        result.put("profileImage", user.getProfileImage());
        return ResponseEntity.ok(result);
    }
}