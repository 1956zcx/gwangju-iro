package com.example.demo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/courses")
@CrossOrigin(origins = {"https://gwangju-iro.vercel.app", "http://localhost:3000"})
public class CourseController {

    @Autowired
    private SavedCourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/save")
    public ResponseEntity<?> saveCourse(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String kakaoId = (String) request.getAttribute("kakaoId");
        User user = userRepository.findByKakaoId(kakaoId).orElseThrow();

        SavedCourse course = new SavedCourse();
        course.setUser(user);
        course.setTitle(body.get("title"));
        course.setCourseJson(body.get("courseJson"));
        course.setConditions(body.get("conditions"));
        course.setDistrict(body.get("district"));
        courseRepository.save(course);

        return ResponseEntity.ok(Map.of("message", "저장 완료"));
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyCourses(HttpServletRequest request) {
        String kakaoId = (String) request.getAttribute("kakaoId");
        User user = userRepository.findByKakaoId(kakaoId).orElseThrow();

        List<Map<String, Object>> result = courseRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(c -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", c.getId());
                    map.put("title", c.getTitle());
                    map.put("district", c.getDistrict() != null ? c.getDistrict() : "");
                    map.put("conditions", c.getConditions() != null ? c.getConditions() : "");
                    map.put("createdAt", c.getCreatedAt().toString());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/my/{id}")
    public ResponseEntity<?> getCourse(@PathVariable Long id, HttpServletRequest request) {
        String kakaoId = (String) request.getAttribute("kakaoId");
        SavedCourse course = courseRepository.findById(id).orElseThrow();

        if (!course.getUser().getKakaoId().equals(kakaoId)) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", course.getId());
        result.put("title", course.getTitle());
        result.put("district", course.getDistrict() != null ? course.getDistrict() : "");
        result.put("createdAt", course.getCreatedAt().toString());
        result.put("courseJson", course.getCourseJson());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/my/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id, HttpServletRequest request) {
        String kakaoId = (String) request.getAttribute("kakaoId");
        SavedCourse course = courseRepository.findById(id).orElseThrow();

        if (!course.getUser().getKakaoId().equals(kakaoId)) {
            return ResponseEntity.status(403).build();
        }

        courseRepository.delete(course);
        return ResponseEntity.ok(Map.of("message", "삭제 완료"));
    }
}