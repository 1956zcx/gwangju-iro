package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    // PlaceRepository.java
    @Query(value = "SELECT * FROM places WHERE main_category LIKE %:mainCategory% " +
            "AND sub_category LIKE %:preference% " +
            "AND district LIKE %:district% " +
            "AND accessibility IN (:accessibilities) " + // ✅ IN 절로 변경!
            "ORDER BY RAND() LIMIT 15", nativeQuery = true)
    List<Place> findRandomMainSpots(@Param("mainCategory") String mainCategory,
                                    @Param("preference") String preference,
                                    @Param("district") String district,
                                    @Param("accessibilities") List<String> accessibilities); // ✅ List로 타입 변경
    // ====================================================================
    // 2. (추가) 현재 위치 반경 4km 기반 검색
    @Query(value = "SELECT *, (6371 * acos(cos(radians(:lat)) * cos(radians(lat)) * cos(radians(lng) - radians(:lng)) + sin(radians(:lat)) * sin(radians(lat)))) AS distance " +
            "FROM places " +
            "WHERE main_category = '메인장소' " +
            "AND sub_category LIKE %:preference% " +
            "AND accessibility IN (:accessibilities) " +
            "HAVING distance <= 4 " + // ✅ 반경 4km 제한
            "ORDER BY RAND() LIMIT 15", nativeQuery = true)
    List<Place> findMainSpotsWithinRadius(@Param("lat") double lat, @Param("lng") double lng,
                                          @Param("preference") String preference,
                                          @Param("accessibilities") List<String> accessibilities);

    @Query(value = "SELECT *, (6371 * acos(cos(radians(:lat)) * cos(radians(lat)) * cos(radians(lng) - radians(:lng)) + sin(radians(:lat)) * sin(radians(lat)))) AS distance " +
            "FROM places " +
            "WHERE main_category = '서브장소' " +
            "AND accessibility IN (:accessibilities) " + // ✅ 서브 장소도 IN 절 적용!
            "HAVING distance <= :radius / 1000.0 " +
            "ORDER BY distance ASC LIMIT 5", nativeQuery = true)
    List<Place> findNearbySubSpots(@Param("lat") double lat, @Param("lng") double lng,
                                   @Param("radius") int radius,
                                   @Param("accessibilities") List<String> accessibilities); // ✅ List로 타입 변경

    // ====================================================================
    // 🍔 [맛집 투어용] 특정 지역구에서 DB 서브 장소(맛집/카페) 가져오기
    @Query(value = "SELECT * FROM places WHERE main_category = '서브장소' " +
            "AND district LIKE %:district% " +
            "AND accessibility IN (:accessibilities) " +
            "ORDER BY RAND() LIMIT 15", nativeQuery = true)
    List<Place> findFoodSpotsAsMainByDistrict(@Param("district") String district,
                                              @Param("accessibilities") List<String> accessibilities);

    // 🍔 [맛집 투어용] 내 주변 4km 반경에서 DB 서브 장소 가져오기
    @Query(value = "SELECT *, (6371 * acos(cos(radians(:lat)) * cos(radians(lat)) * cos(radians(lng) - radians(:lng)) + sin(radians(:lat)) * sin(radians(lat)))) AS distance " +
            "FROM places WHERE main_category = '서브장소' " +
            "AND accessibility IN (:accessibilities) " +
            "HAVING distance <= 4 " +
            "ORDER BY RAND() LIMIT 15", nativeQuery = true)
    List<Place> findFoodSpotsAsMainByRadius(@Param("lat") double lat, @Param("lng") double lng,
                                            @Param("accessibilities") List<String> accessibilities);

    // 맛집 투어 중 '소화용' 관광지(메인)를 찾기 위한 쿼리
    @Query(value = "SELECT *, (6371 * acos(cos(radians(:lat)) * cos(radians(lat)) * cos(radians(lng) - radians(:lng)) + sin(radians(:lat)) * sin(radians(lat)))) AS distance " +
            "FROM places " +
            "WHERE main_category = '메인장소' " + // ✅ 이번에는 '메인' 카테고리를 찾습니다.
            "AND accessibility IN (:accessibilities) " +
            "HAVING distance <= 1 " + // 반경 3km 내외 소화 코스
            "ORDER BY distance ASC LIMIT 5", nativeQuery = true)
    List<Place> findNearbyMainSpots(@Param("lat") double lat, @Param("lng") double lng,
                                    @Param("accessibilities") List<String> accessibilities);
}