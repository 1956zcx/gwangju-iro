/* global kakao */
import React, { useEffect, useRef } from 'react';

const KakaoMapService = ({ initialData, centerCoords, selectedLocation }) => {
    const mapRef = useRef(null); // ★ 추가: 지도 객체를 제어하기 위해 저장해두는 변수

    useEffect(() => {
        const container = document.getElementById('map');
        const options = {
            center: new kakao.maps.LatLng(centerCoords.lat, centerCoords.lng),
            level: 4,
        };

        const map = new kakao.maps.Map(container, options);
        mapRef.current = map; // 생성된 지도를 저장

        const bounds = new kakao.maps.LatLngBounds();
        const linePath = [];

        initialData.forEach((spot, index) => {
            const position = new kakao.maps.LatLng(spot.lat, spot.lng);
            linePath.push(position);

            // 기본 마커
            new kakao.maps.Marker({
                position: position,
                map: map,
            });

            // ★ 추가: 마커 위에 예쁜 원형 숫자 번호 표시
            const content = `
        <div style="
          background: #2c3e50; 
          color: white; 
          width: 24px; 
          height: 24px; 
          border-radius: 50%; 
          text-align: center; 
          line-height: 24px; 
          font-weight: bold; 
          font-size: 13px; 
          border: 2px solid white; 
          box-shadow: 0px 2px 4px rgba(0,0,0,0.3);
        ">
          ${index + 1}
        </div>
      `;

            new kakao.maps.CustomOverlay({
                position: position,
                content: content,
                yAnchor: 2.7, // 핀 바로 위쪽으로 위치 조정
                map: map
            });

            bounds.extend(position);
        });

        // 경로선(Polyline) 그리기
        const polyline = new kakao.maps.Polyline({
            path: linePath,
            strokeWeight: 4,
            strokeColor: '#e74c3c',
            strokeOpacity: 0.8,
            strokeStyle: 'solid',
        });

        polyline.setMap(map);

        if (initialData.length > 0) {
            map.setBounds(bounds);
        }

    }, [initialData, centerCoords]);

    // ★ 수정: 애니메이션 충돌을 막기 위해 실행 순서와 방식을 변경합니다.
    useEffect(() => {
        if (mapRef.current && selectedLocation) {
            // 혹시 API에서 문자로 넘어왔을 경우를 대비해 확실하게 숫자로 변환
            const lat = parseFloat(selectedLocation.lat);
            const lng = parseFloat(selectedLocation.lng);
            const moveLatLon = new kakao.maps.LatLng(lat, lng);

            // 1. 먼저 지도를 줌인 (확대)
            mapRef.current.setLevel(3);

            // 2. 확대된 상태에서 해당 핀을 향해 스무스하게 이동 (panTo)
            mapRef.current.panTo(moveLatLon);
        }
    }, [selectedLocation]);

    return (
        <div style={{ height: '100%' }}>
            <h3 style={{ color: '#2c3e50', marginTop: 0 }}>🗺️ 추천 경로 지도</h3>
            <div id="map" style={{ width: '100%', height: '500px', borderRadius: '10px', boxShadow: '0 4px 8px rgba(0,0,0,0.1)' }}></div>
        </div>
    );
};

export default KakaoMapService;