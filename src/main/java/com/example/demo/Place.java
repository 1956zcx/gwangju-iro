package com.example.demo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "places")
@Getter @Setter // 롬복(Lombok)을 안 쓰신다면 우클릭 -> Generate로 Getter/Setter를 만들어주세요!
public class Place {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String address;
    private String district;       // 지역구 (남구, 북구 등)
    private String budgetLevel;    // 가격대 (저, 중, 고, 무료 등)
    private String indoorOutdoor;  // 실내/실외
    private String accessibility;  // 도보/차량
    private String mainCategory;   // 대분류
    private String subCategory;    // 중분류(테마)
    private String detailCategory; // 세부 카테고리
    private double lng;            // X좌표
    private double lat;            // Y좌표
}