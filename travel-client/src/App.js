import React, { useState } from 'react';
import axios from 'axios';
import KakaoMapService from './KakaoMapService';

function App() {
    const [mbti, setMbti] = useState('');
    const [budget, setBudget] = useState('5만원');
    const [time, setTime] = useState('6시간 (반나절)');
    const [vehicle, setVehicle] = useState('뚜벅이 (대중교통/도보)');
    const [district, setDistrict] = useState('동구');

    const [preference, setPreference] = useState('쇼핑/트렌드 (백화점, 핫플)');

    const [plans, setPlans] = useState([]);
    const [loading, setLoading] = useState(false);
    const [excludePlaces, setExcludePlaces] = useState([]);
    const [selectedLocation, setSelectedLocation] = useState(null);
    const [totalSummary, setTotalSummary] = useState({ time: "", distance: "" });
    // ✅ 1. 추가: 내 실제 위치를 저장할 상태
    const [userLocation, setUserLocation] = useState(null);

    const districtCoords = {
        "동구": { lat: 35.1460, lng: 126.9230 },
        "서구": { lat: 35.1542, lng: 126.8512 },
        "남구": { lat: 35.1230, lng: 126.9126 },
        "북구": { lat: 35.1741, lng: 126.9121 },
        "광산구": { lat: 35.1903, lng: 126.8248 }
    };

    const mbtiList = [
        'ISTJ', 'ISFJ', 'INFJ', 'INTJ', 'ISTP', 'ISFP', 'INFP', 'INTP',
        'ESTP', 'ESFP', 'ENFP', 'ENTP', 'ESTJ', 'ESFJ', 'ENFJ', 'ENTJ'
    ];

    // ★ 1. GPS 가져오는 함수 (getTravelPlan 위에 추가)
    const getUserLocation = () => {
        return new Promise((resolve, reject) => {
            if (navigator.geolocation) {
                navigator.geolocation.getCurrentPosition(
                    (position) => {
                        resolve({
                            lat: position.coords.latitude,
                            lng: position.coords.longitude
                        });
                    },
                    (error) => reject(error)
                );
            } else {
                reject(new Error("GPS를 지원하지 않는 브라우저입니다."));
            }
        });
    };

    const getTravelPlan = async () => {
        if (!mbti) {
            alert("MBTI를 선택해주세요!");
            return;
        }

        setLoading(true);
        try {
            // ★ 2. GPS 로직 분기 처리
            let requestLat;
            let requestLng;

            if (district === "내주변") {
                try {
                    const loc = await getUserLocation();
                    requestLat = loc.lat;
                    requestLng = loc.lng;

                    // ✅ 2. 추가: 가져온 내 위치를 지도에게 주기 위해 State에 저장!
                    setUserLocation({ lat: requestLat, lng: requestLng });

                    console.log("📍 내 위치 가져오기 성공:", requestLat, requestLng);
                } catch (error) {
                    alert("위치 정보를 가져올 수 없습니다. 브라우저의 위치 권한을 허용해주세요!");
                    setLoading(false);
                    return;
                }
            } else {
                // 기존 지역구 선택 시 하드코딩된 좌표 사용
                requestLat = districtCoords[district].lat;
                requestLng = districtCoords[district].lng;
            }

            const excludeParam = excludePlaces.join(',');
            const API_BASE_URL = process.env.REACT_APP_API_URL || "http://localhost:8080";

            // ★ 3. param에 coords.lat 대신 requestLat 변수 사용
            const response = await axios.get(`${API_BASE_URL}/api/recommend`, {
                params: {
                    mbti, budget, time, vehicle, district,
                    preference,
                    lat: requestLat, // 수정됨
                    lng: requestLng, // 수정됨
                    exclude: excludeParam,
                    t: Date.now()
                },
                headers: {
                    "ngrok-skip-browser-warning": "69420"
                }
            });

            const data = typeof response.data === 'string' ? JSON.parse(response.data) : response.data;

            // ✅ 데이터가 '객체'이고 그 안에 'plans' 배열이 있는지 확인
            if (data && Array.isArray(data.plans)) {
                setPlans(data.plans);
                setTotalSummary({ time: data.totalTime, distance: data.totalDistance });

                if (data.plans.length > 0) {
                    const newPlaceNames = data.plans.map(item => item.name);
                    setExcludePlaces(prev => {
                        const updatedList = [...prev, ...newPlaceNames];
                        return updatedList.slice(-24);
                    });
                }
            } else {
                console.error("데이터 형식이 올바르지 않습니다:", data);
                alert("AI 응답 형식이 올바르지 않습니다. 다시 시도해주세요!");
            }

        } catch (error) {
            console.error("에러 발생:", error);
            alert("데이터를 가져오는데 실패했습니다.");
        } finally {
            setLoading(false);
        }
    };

    const selectStyle = {
        padding: '10px', fontSize: '15px', borderRadius: '5px', border: '1px solid #ccc',
        width: '100%', marginBottom: '15px'
    };

    return (
        <div style={{ padding: '20px', fontFamily: 'Arial, sans-serif', maxWidth: '800px', margin: '0 auto' }}>
            <h1 style={{ textAlign: 'center', color: '#2c3e50', marginBottom: '10px' }}>📍 광주 이로</h1>
            <p style={{ textAlign: 'center', color: '#666', marginBottom: '30px' }}>당신의 상황과 성향에 딱 맞는 완벽한 하루 코스</p>

            <div style={{ background: '#f8f9fa', padding: '20px', borderRadius: '10px', marginBottom: '20px' }}>
                <h3 style={{ marginTop: 0, color: '#2c3e50' }}>⚙️ 여행 조건 설정</h3>

                <div style={{ marginBottom: '15px' }}>
                    <label style={{ fontWeight: 'bold', display: 'block', marginBottom: '5px' }}>🗺️ 중심 지역 (구)</label>
                    <select value={district} onChange={(e) => setDistrict(e.target.value)} style={selectStyle}>
                        <option value="내주변">내 주변 (현재 위치 반경 4km)</option>
                        <option value="동구">동구 (동명동/충장로 감성)</option>
                        <option value="서구">서구 (상무지구 핫플)</option>
                        <option value="남구">남구 (양림동 역사/카페)</option>
                        <option value="북구">북구 (전대/용봉동 가성비)</option>
                        <option value="광산구">광산구 (수완/첨단 신도시)</option>
                    </select>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '15px' }}>
                    <div>
                        <label style={{ fontWeight: 'bold', display: 'block', marginBottom: '5px' }}>💰 예산</label>
                        <select value={budget} onChange={(e) => setBudget(e.target.value)} style={selectStyle}>
                            <option>3만원 이하 (가성비)</option>
                            <option>5만원 (적당함)</option>
                            <option>10만원 (여유로움)</option>
                            <option>상관없음 (플렉스)</option>
                        </select>
                    </div>

                    <div>
                        <label style={{ fontWeight: 'bold', display: 'block', marginBottom: '5px' }}>⏱ 가용 시간</label>
                        <select value={time} onChange={(e) => setTime(e.target.value)} style={selectStyle}>
                            <option>4시간 (가볍게)</option>
                            <option>6시간 (반나절)</option>
                            <option>8시간 이상 (종일)</option>
                        </select>
                    </div>

                    <div>
                        <label style={{ fontWeight: 'bold', display: 'block', marginBottom: '5px' }}>🚗 차량 유무</label>
                        <select value={vehicle} onChange={(e) => setVehicle(e.target.value)} style={selectStyle}>
                            <option>뚜벅이 (대중교통/도보)</option>
                            <option>자가용 (주차장 필수)</option>
                        </select>
                    </div>

                    <div>
                        <label style={{ fontWeight: 'bold', display: 'block', marginBottom: '5px' }}>🎯 선호 테마</label>
                        <select value={preference} onChange={(e) => setPreference(e.target.value)} style={selectStyle}>
                            <option>쇼핑/트렌드 (백화점, 핫플)</option>
                            <option>역사/문화 (유적지, 박물관, 전시)</option>
                            <option>자연/공원 (산책, 호수, 힐링)</option>
                            <option>액티비티 (방탈출, 보드게임, 영화)</option>
                            <option>맛집/카페 투어 (먹방, 디저트)</option>
                        </select>
                    </div>
                </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '10px', marginBottom: '20px' }}>
                {mbtiList.map((type) => (
                    <button
                        key={type} onClick={() => setMbti(type)}
                        style={{
                            padding: '12px 0', fontSize: '16px', fontWeight: 'bold', borderRadius: '8px', cursor: 'pointer',
                            border: mbti === type ? '2px solid #2c3e50' : '1px solid #ddd',
                            backgroundColor: mbti === type ? '#2c3e50' : '#fff',
                            color: mbti === type ? '#fff' : '#555',
                        }}
                    >
                        {type}
                    </button>
                ))}
            </div>

            <div style={{ textAlign: 'center', marginBottom: '30px' }}>
                <button
                    onClick={getTravelPlan} disabled={loading || !mbti}
                    style={{
                        padding: '15px 40px', fontSize: '18px', fontWeight: 'bold', cursor: (!mbti || loading) ? 'not-allowed' : 'pointer',
                        backgroundColor: (!mbti || loading) ? '#ccc' : '#e74c3c', color: '#fff', border: 'none', borderRadius: '5px'
                    }}
                >
                    {loading ? '코스 생성 중 ⏳' : '완벽한 코스 생성하기 🚀'}
                </button>
            </div>

            {/* 결과 화면 섹션 */}
            {plans.length > 0 && (
                <div style={{ display: 'flex', gap: '20px', marginTop: '30px', alignItems: 'flex-start' }}>
                    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '15px', maxHeight: '600px', overflowY: 'auto', paddingRight: '10px' }}>

                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '5px' }}>
                            <h3 style={{ color: '#2c3e50', margin: 0 }}>📝 추천 일정표</h3>
                        </div>

                        {/* ✅ 새로 추가된 요약 정보 상자 (총 시간/거리) */}
                        <div style={{
                            background: '#e8f4f8', padding: '15px', borderRadius: '10px',
                            color: '#0056b3', border: '1px solid #b8daff', marginBottom: '5px'
                        }}>
                            <div style={{ display: 'flex', justifyContent: 'space-around', fontSize: '15px', fontWeight: 'bold' }}>
                                <span>⏳ 예상 시간: {totalSummary.time || "계산 중"}</span>
                                <span>🚗 이동 거리: {totalSummary.distance || "계산 중"}</span>
                            </div>
                        </div>

                        {plans.map((spot, index) => (
                            <React.Fragment key={index}>
                                {/* 장소 카드 */}
                                <div
                                    onClick={() => setSelectedLocation({ lat: spot.lat, lng: spot.lng })}
                                    style={{
                                        padding: '15px',
                                        background: '#f8f9fa',
                                        borderRadius: '8px',
                                        borderLeft: '5px solid #e74c3c',
                                        cursor: 'pointer',
                                        boxShadow: '0 2px 4px rgba(0,0,0,0.05)',
                                        transition: 'all 0.2s ease',
                                        border: selectedLocation?.lat === spot.lat ? '1px solid #e74c3c' : '1px solid transparent'
                                    }}
                                    onMouseEnter={(e) => e.currentTarget.style.transform = 'translateY(-2px)'}
                                    onMouseLeave={(e) => e.currentTarget.style.transform = 'translateY(0)'}
                                >
                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
                                        <h4 style={{ margin: 0, fontSize: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                                            {index + 1}. {spot.name}
                                            <span style={{
                                                fontSize: '11px', padding: '2px 6px', borderRadius: '4px', color: '#fff',
                                                backgroundColor: spot.indoorOutdoor === '실내' ? '#3498db' : '#2ecc71',
                                                fontWeight: 'normal'
                                            }}>
                                                {spot.indoorOutdoor || '정보없음'}
                                            </span>
                                        </h4>
                                        <span style={{ fontSize: '12px', color: '#e74c3c', fontWeight: 'bold', background: '#feeae9', padding: '2px 8px', borderRadius: '12px' }}>
                                            #{spot.theme}
                                        </span>
                                    </div>
                                    <p style={{ margin: 0, fontSize: '14px', color: '#555', lineHeight: '1.5' }}>
                                        {spot.description}
                                    </p>

                                    {/* 첫 번째 장소에만 출발지 → 첫 장소 길찾기 버튼 표시 */}
                                    {index === 0 && (
                                        <div style={{ marginTop: '10px' }}>
                                            <a
                                                href={
                                                    district === '내주변' && userLocation
                                                        ? `https://map.kakao.com/link/from/${encodeURIComponent('내 위치')},${userLocation.lat},${userLocation.lng}/to/${encodeURIComponent(spot.name)},${spot.lat},${spot.lng}`
                                                        : `https://map.kakao.com/link/to/${encodeURIComponent(spot.name)},${spot.lat},${spot.lng}`
                                                }
                                                target="_blank"
                                                rel="noopener noreferrer"
                                                onClick={(e) => e.stopPropagation()}
                                                style={{
                                                    display: 'inline-block', padding: '4px 10px', fontSize: '12px',
                                                    backgroundColor: '#fee500', color: '#3c1e1e', borderRadius: '4px',
                                                    textDecoration: 'none', fontWeight: 'bold',
                                                }}
                                            >
                                                🗺️ 카카오맵 길찾기
                                            </a>
                                        </div>
                                    )}
                                </div>

                                {/* 카드 사이 이동 정보 연결 요소 (마지막 카드 다음엔 표시 안 함) */}
                                {index !== plans.length - 1 && (
                                    <div style={{
                                        display: 'flex', alignItems: 'center', gap: '10px',
                                        padding: '8px 14px',
                                        background: '#fff',
                                        border: '1px dashed #ccc',
                                        borderRadius: '6px',
                                        fontSize: '13px',
                                        color: '#555',
                                    }}>
                                        <span style={{ color: '#e74c3c', fontSize: '15px', flexShrink: 0 }}>↓</span>
                                        <span style={{ flex: 1 }}>
                                            {spot.distToNext || '이동 정보 없음'}
                                        </span>
                                        <a
                                            href={`https://map.kakao.com/link/from/${encodeURIComponent(spot.name)},${spot.lat},${spot.lng}/to/${encodeURIComponent(plans[index + 1].name)},${plans[index + 1].lat},${plans[index + 1].lng}`}
                                            target="_blank"
                                            rel="noopener noreferrer"
                                            style={{
                                                display: 'inline-block', padding: '4px 10px', fontSize: '12px',
                                                backgroundColor: '#fee500', color: '#3c1e1e', borderRadius: '4px',
                                                textDecoration: 'none', fontWeight: 'bold', flexShrink: 0,
                                            }}
                                        >
                                            🗺️ 카카오맵 길찾기
                                        </a>
                                    </div>
                                )}
                            </React.Fragment>
                        ))}
                    </div>

                    {/* 지도 컴포넌트 영역 */}
                    <div style={{ flex: 1, position: 'sticky', top: '20px' }}>
                        <KakaoMapService
                            initialData={plans}
                            // ✅ 3. 수정: '내주변'일 때는 userLocation을, 아닐 때는 해당 구의 좌표를 줍니다.
                            // (만약 userLocation이 아직 없다면 광주시청 좌표를 임시로 줍니다)
                            centerCoords={
                                district === '내주변'
                                    ? (userLocation || { lat: 35.1595, lng: 126.8526 })
                                    : districtCoords[district]
                            }
                            selectedLocation={selectedLocation}
                        />
                    </div>
                </div>
            )}
        </div>
    );
}

export default App;