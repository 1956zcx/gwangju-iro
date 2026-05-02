package com.example.demo; // 패키지명은 동현님 프로젝트에 맞게!

import com.example.demo.PlaceRepository;
import com.example.demo.Place;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PlaceDbService {

    @Autowired
    private PlaceRepository placeRepository;

    // 🎯 1. 테마와 지역구에 맞는 메인 장소 가져오기
    // PlaceDbService.java 내 getMainSpots 메서드
    public List<PlaceDto> getMainSpots(String preference, String district, List<String> accessibilities) {
        List<Place> entityList = placeRepository.findRandomMainSpots("메인", preference, district, accessibilities);

        List<PlaceDto> dtoList = new ArrayList<>();
        for (Place p : entityList) {
            dtoList.add(new PlaceDto(
                    p.getName(), p.getMainCategory(), p.getSubCategory(), p.getDetailCategory(),
                    p.getBudgetLevel(), p.getIndoorOutdoor(), p.getAccessibility(),
                    p.getLat(), p.getLng()
            ));
        }
        return dtoList;
    }
    // ====================================================================
    // (추가) 반경 4km 전용 메인 장소 검색
    public List<PlaceDto> getMainSpotsByRadius(double lat, double lng, String preference, List<String> accessibilities) {
        List<Place> entityList = placeRepository.findMainSpotsWithinRadius(lat, lng, preference, accessibilities);

        List<PlaceDto> dtoList = new ArrayList<>();
        for (Place p : entityList) {
            dtoList.add(new PlaceDto(
                    p.getName(), p.getMainCategory(), p.getSubCategory(), p.getDetailCategory(),
                    p.getBudgetLevel(), p.getIndoorOutdoor(), p.getAccessibility(),
                    p.getLat(), p.getLng()
            ));
        }
        return dtoList;
    }
    // ✅ 서브 장소 검색: String -> List<String> 으로 변경
    public List<PlaceDto> getSubSpots(double lat, double lng, int radius, List<String> accessibilities) {
        List<Place> entityList = placeRepository.findNearbySubSpots(lat, lng, radius, accessibilities);
        List<PlaceDto> dtoList = new ArrayList<>();

        for (Place p : entityList) {
            dtoList.add(new PlaceDto(
                    p.getName(), p.getMainCategory(), p.getSubCategory(),
                    p.getDetailCategory(), p.getBudgetLevel(), p.getIndoorOutdoor(),
                    p.getAccessibility(), p.getLat(), p.getLng()
            ));
        }
        return dtoList;
    }
    // ====================================================================
    // 🍔 [맛집 투어용] 지역구 기반 맛집/카페 메인 승격
    public List<PlaceDto> getFoodSpotsAsMain(String district, List<String> accessibilities) {
        List<Place> entityList = placeRepository.findFoodSpotsAsMainByDistrict(district, accessibilities);
        List<PlaceDto> dtoList = new ArrayList<>();
        for (Place p : entityList) {
            dtoList.add(new PlaceDto(p.getName(), p.getMainCategory(), p.getSubCategory(), p.getDetailCategory(), p.getBudgetLevel(), p.getIndoorOutdoor(), p.getAccessibility(), p.getLat(), p.getLng()));
        }
        return dtoList;
    }

    // 🍔 [맛집 투어용] 내 주변 반경 기반 맛집/카페 메인 승격
    public List<PlaceDto> getFoodSpotsAsMainByRadius(double lat, double lng, List<String> accessibilities) {
        List<Place> entityList = placeRepository.findFoodSpotsAsMainByRadius(lat, lng, accessibilities);
        List<PlaceDto> dtoList = new ArrayList<>();
        for (Place p : entityList) {
            dtoList.add(new PlaceDto(p.getName(), p.getMainCategory(), p.getSubCategory(), p.getDetailCategory(), p.getBudgetLevel(), p.getIndoorOutdoor(), p.getAccessibility(), p.getLat(), p.getLng()));
        }
        return dtoList;
    }
    // 🏃 [맛집 투어용] 식당 주변의 소화시킬 '메인 관광지' 찾기
    public List<PlaceDto> getNearbyMainSpots(double lat, double lng, List<String> accessibilities) {

        // 1. Repository에 요청해서 DB에서 진짜 데이터(Entity)를 긁어옵니다.
        List<Place> entityList = placeRepository.findNearbyMainSpots(lat, lng, accessibilities);

        // 2. 컨트롤러로 넘겨주기 위해 DTO(택배 상자) 전용 바구니를 만듭니다.
        List<PlaceDto> dtoList = new ArrayList<>();

        // 3. DB에서 꺼낸 데이터를 하나씩 예쁜 DTO 상자에 옮겨 담습니다.
        for (Place p : entityList) {
            dtoList.add(new PlaceDto(
                    p.getName(),
                    p.getMainCategory(),
                    p.getSubCategory(),
                    p.getDetailCategory(),
                    p.getBudgetLevel(),
                    p.getIndoorOutdoor(),
                    p.getAccessibility(),
                    p.getLat(),
                    p.getLng()
            ));
        }

        // 4. 포장이 끝난 바구니를 컨트롤러로 반환합니다!
        return dtoList;
    }
}