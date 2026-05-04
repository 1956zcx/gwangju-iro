package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SavedCourseRepository extends JpaRepository<SavedCourse, Long> {
    List<SavedCourse> findByUserOrderByCreatedAtDesc(User user);
}