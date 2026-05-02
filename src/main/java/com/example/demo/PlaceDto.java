package com.example.demo;

public class PlaceDto {
    private String name;
    private String mainCategory;
    private String subCategory;
    private String detailCategory;
    private String budgetLevel;
    private String indoorOutdoor;
    private String accessibility; // ✅ 새롭게 추가된 필드
    private double lat;
    private double lng;

    // ✅ 1. DB용 생성자 (인수 9개로 업데이트 - PlaceDbService에서 사용)
    public PlaceDto(String name, String mainCategory, String subCategory, String detailCategory,
                    String budgetLevel, String indoorOutdoor, String accessibility, double lat, double lng) {
        this.name = name;
        this.mainCategory = mainCategory;
        this.subCategory = subCategory;
        this.detailCategory = detailCategory;
        this.budgetLevel = budgetLevel;
        this.indoorOutdoor = indoorOutdoor;
        this.accessibility = accessibility; // ✅ 추가
        this.lat = lat;
        this.lng = lng;
    }

    // ✅ 2. 카카오 API용 생성자 (기존 4개짜리 유지 - KakaoLocalApiService에서 사용)
    public PlaceDto(String name, double lat, double lng, String category) {
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.detailCategory = category;
        this.mainCategory = "서브장소";
        this.subCategory = "먹거리";
        this.budgetLevel = "알수없음";
        this.indoorOutdoor = "실내";
        this.accessibility = "도보"; // 카카오 장소는 기본적으로 도보 가능으로 간주
    }

    // Getter 메서드들 (accessibility 추가 필수!)
    public String getName() { return name; }
    public String getMainCategory() { return mainCategory; }
    public String getSubCategory() { return subCategory; }
    public String getDetailCategory() { return detailCategory; }
    public String getBudgetLevel() { return budgetLevel; }
    public String getIndoorOutdoor() { return indoorOutdoor; }
    public String getAccessibility() { return accessibility; } // ✅ 추가
    public double getLat() { return lat; }
    public double getLng() { return lng; }
    public String getCategory() { return detailCategory; }
}