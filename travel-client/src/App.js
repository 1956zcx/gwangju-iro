import React, { useState, useEffect } from 'react';
import axios from 'axios';
import KakaoMapService from './KakaoMapService';

function App() {
    // 기존 state
    const [mbti, setMbti] = useState('');
    const [budget, setBudget] = useState('5만원 (적당함)');
    const [time, setTime] = useState('6시간 (반나절)');
    const [vehicle, setVehicle] = useState('뚜벅이 (대중교통/도보)');
    const [district, setDistrict] = useState('동구');
    const [preference, setPreference] = useState('쇼핑/트렌드 (백화점, 핫플)');
    const [plans, setPlans] = useState([]);
    const [loading, setLoading] = useState(false);
    const [excludePlaces, setExcludePlaces] = useState([]);
    const [selectedLocation, setSelectedLocation] = useState(null);
    const [totalSummary, setTotalSummary] = useState({ time: "", distance: "" });
    const [userLocation, setUserLocation] = useState(null);

    // 로그인/코스 저장 state
    const [token, setToken] = useState(localStorage.getItem('token'));
    const [userInfo, setUserInfo] = useState(() => {
        const saved = localStorage.getItem('userInfo');
        return saved ? JSON.parse(saved) : null;
    });
    const [showMyCourses, setShowMyCourses] = useState(false);
    const [myCourses, setMyCourses] = useState([]);
    const [myCoursesLoading, setMyCoursesLoading] = useState(false);

    const API_BASE_URL = process.env.REACT_APP_API_URL || "http://localhost:8080";

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

    useEffect(() => {
        if (window.Kakao && !window.Kakao.isInitialized()) {
            window.Kakao.init('181f347746ff790346069e9bf4f42f17');
        }
        axios.defaults.headers.common['ngrok-skip-browser-warning'] = '69420';
    }, []);

    // ★ GPS 가져오는 함수
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
            let requestLat;
            let requestLng;

            if (district === "내주변") {
                try {
                    const loc = await getUserLocation();
                    requestLat = loc.lat;
                    requestLng = loc.lng;
                    setUserLocation({ lat: requestLat, lng: requestLng });
                    console.log("📍 내 위치 가져오기 성공:", requestLat, requestLng);
                } catch (error) {
                    alert("위치 정보를 가져올 수 없습니다. 브라우저의 위치 권한을 허용해주세요!");
                    setLoading(false);
                    return;
                }
            } else {
                requestLat = districtCoords[district].lat;
                requestLng = districtCoords[district].lng;
            }

            const excludeParam = excludePlaces.join(',');

            const response = await axios.get(`${API_BASE_URL}/api/recommend`, {
                params: {
                    mbti, budget, time, vehicle, district,
                    preference,
                    lat: requestLat,
                    lng: requestLng,
                    exclude: excludeParam,
                    t: Date.now()
                },
                headers: {
                    "ngrok-skip-browser-warning": "69420"
                }
            });

            const data = typeof response.data === 'string' ? JSON.parse(response.data) : response.data;

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

    // 카카오 로그인
    const handleKakaoLogin = () => {
        if (!window.Kakao || !window.Kakao.isInitialized()) {
            alert('카카오 SDK가 로드되지 않았습니다. 페이지를 새로고침해주세요.');
            return;
        }
        window.Kakao.Auth.login({
            success: async (auth) => {
                try {
                    const res = await axios.post(`${API_BASE_URL}/api/auth/kakao`, {
                        accessToken: auth.access_token
                    });
                    const { token: jwt, nickname, profileImage } = res.data;
                    localStorage.setItem('token', jwt);
                    localStorage.setItem('userInfo', JSON.stringify({ nickname, profileImage }));
                    setToken(jwt);
                    setUserInfo({ nickname, profileImage });
                } catch (e) {
                    alert('로그인에 실패했습니다. 다시 시도해주세요.');
                }
            },
            fail: (err) => {
                console.error('카카오 로그인 실패:', err);
                alert('카카오 로그인에 실패했습니다.');
            }
        });
    };

    const handleLogout = () => {
        localStorage.removeItem('token');
        localStorage.removeItem('userInfo');
        setToken(null);
        setUserInfo(null);
        setShowMyCourses(false);
    };

    // 카톡 공유
    const shareCourse = () => {
        if (!window.Kakao || !window.Kakao.isInitialized()) {
            alert('카카오 SDK가 로드되지 않았습니다. 페이지를 새로고침해주세요.');
            return;
        }
        const preview = plans.slice(0, 3).map((p, i) => `${i + 1}. ${p.name}`).join('\n');
        const more = plans.length > 3 ? ` 외 ${plans.length - 3}곳` : '';
        const description = `${preview}${more}\n⏳ ${totalSummary.time}  🚗 ${totalSummary.distance}`;
        const siteUrl = 'https://gwangju-iro.vercel.app';
        window.Kakao.Share.sendDefault({
            objectType: 'feed',
            content: {
                title: `📍 광주 이로 - ${district} ${time.split(' ')[0]} 코스`,
                description,
                imageUrl: `${siteUrl}/logo512.png`,
                link: {
                    mobileWebUrl: siteUrl,
                    webUrl: siteUrl,
                }
            },
            buttons: [
                {
                    title: '코스 확인하기',
                    link: {
                        mobileWebUrl: siteUrl,
                        webUrl: siteUrl,
                    }
                }
            ]
        });
    };

    // 코스 저장
    const saveCourse = async () => {
        if (!token) {
            if (window.confirm('로그인이 필요합니다. 카카오 로그인하시겠습니까?')) {
                handleKakaoLogin();
            }
            return;
        }
        const today = new Date().toLocaleDateString('ko-KR');
        const defaultTitle = `${district} 코스 - ${today}`;
        const inputTitle = window.prompt('코스 이름을 입력하세요\n(비워두면 기본 이름으로 저장됩니다)', defaultTitle);
        if (inputTitle === null) return;
        const title = inputTitle.trim() || defaultTitle;

        const conditions = JSON.stringify({ mbti, budget, time, vehicle, district, preference });
        const courseJson = JSON.stringify({
            totalTime: totalSummary.time,
            totalDistance: totalSummary.distance,
            plans
        });
        try {
            await axios.post(`${API_BASE_URL}/api/courses/save`,
                { title, courseJson, district, conditions },
                { headers: { Authorization: `Bearer ${token}` } }
            );
            alert('코스가 저장되었습니다!');
        } catch (e) {
            alert('저장에 실패했습니다. 다시 시도해주세요.');
        }
    };

    // 내 코스 목록 조회
    const fetchMyCourses = async () => {
        if (!token) {
            alert('로그인이 필요합니다.');
            return;
        }
        setShowMyCourses(true);
        setMyCoursesLoading(true);
        try {
            const res = await axios.get(`${API_BASE_URL}/api/courses/my`, {
                headers: { Authorization: `Bearer ${token}` }
            });
            setMyCourses(res.data);
        } catch (e) {
            alert('코스 목록을 불러오는데 실패했습니다.');
            setShowMyCourses(false);
        } finally {
            setMyCoursesLoading(false);
        }
    };

    // 저장된 코스 상세 보기
    const viewCourse = async (id) => {
        try {
            const res = await axios.get(`${API_BASE_URL}/api/courses/my/${id}`, {
                headers: { Authorization: `Bearer ${token}` }
            });
            const data = JSON.parse(res.data.courseJson);
            setPlans(data.plans);
            setTotalSummary({ time: data.totalTime, distance: data.totalDistance });
            setUserLocation(null);
            setSelectedLocation(null);
            setShowMyCourses(false);
            window.scrollTo({ top: 0, behavior: 'smooth' });
        } catch (e) {
            alert('코스를 불러오는데 실패했습니다.');
        }
    };

    // 코스 삭제
    const deleteCourse = async (e, id) => {
        e.stopPropagation();
        if (!window.confirm('이 코스를 삭제하시겠습니까?')) return;
        try {
            await axios.delete(`${API_BASE_URL}/api/courses/my/${id}`, {
                headers: { Authorization: `Bearer ${token}` }
            });
            setMyCourses(prev => prev.filter(c => c.id !== id));
        } catch (e) {
            alert('삭제에 실패했습니다.');
        }
    };

    const selectStyle = {
        padding: '10px', fontSize: '15px', borderRadius: '5px', border: '1px solid #ccc',
        width: '100%', marginBottom: '15px'
    };

    return (
        <div style={{ padding: '20px', fontFamily: 'Arial, sans-serif', maxWidth: '800px', margin: '0 auto' }}>

            {/* 상단 로그인 바 */}
            <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: '10px', marginBottom: '10px' }}>
                {userInfo ? (
                    <>
                        <span style={{ fontSize: '14px', color: '#555' }}>👤 {userInfo.nickname}님</span>
                        <button
                            onClick={fetchMyCourses}
                            style={{ padding: '6px 14px', fontSize: '13px', borderRadius: '6px', border: '1px solid #2c3e50', backgroundColor: '#fff', color: '#2c3e50', cursor: 'pointer' }}
                        >
                            내 코스
                        </button>
                        <button
                            onClick={handleLogout}
                            style={{ padding: '6px 14px', fontSize: '13px', borderRadius: '6px', border: '1px solid #ccc', backgroundColor: '#fff', color: '#888', cursor: 'pointer' }}
                        >
                            로그아웃
                        </button>
                    </>
                ) : (
                    <button
                        onClick={handleKakaoLogin}
                        style={{ padding: '8px 18px', fontSize: '14px', borderRadius: '6px', border: 'none', backgroundColor: '#fee500', color: '#3c1e1e', cursor: 'pointer', fontWeight: 'bold' }}
                    >
                        카카오 로그인
                    </button>
                )}
            </div>

            {/* 내 코스 패널 */}
            {showMyCourses && (
                <div style={{ background: '#fff', border: '1px solid #ddd', borderRadius: '10px', padding: '20px', marginBottom: '20px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px' }}>
                        <h3 style={{ margin: 0, color: '#2c3e50' }}>📚 내 저장 코스</h3>
                        <button
                            onClick={() => setShowMyCourses(false)}
                            style={{ background: 'none', border: 'none', fontSize: '20px', cursor: 'pointer', color: '#888' }}
                        >
                            ✕
                        </button>
                    </div>
                    {myCoursesLoading ? (
                        <p style={{ textAlign: 'center', color: '#888' }}>불러오는 중...</p>
                    ) : myCourses.length === 0 ? (
                        <p style={{ textAlign: 'center', color: '#888' }}>저장된 코스가 없습니다.</p>
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                            {myCourses.map(course => (
                                <div
                                    key={course.id}
                                    onClick={() => viewCourse(course.id)}
                                    style={{
                                        padding: '14px 16px', background: '#f8f9fa', borderRadius: '8px',
                                        border: '1px solid #eee', cursor: 'pointer', display: 'flex',
                                        justifyContent: 'space-between', alignItems: 'center'
                                    }}
                                    onMouseEnter={(e) => e.currentTarget.style.background = '#edf2f7'}
                                    onMouseLeave={(e) => e.currentTarget.style.background = '#f8f9fa'}
                                >
                                    <div>
                                        <div style={{ fontWeight: 'bold', fontSize: '15px', color: '#2c3e50' }}>{course.title}</div>
                                        {course.conditions && (() => {
                                            try {
                                                const c = JSON.parse(course.conditions);
                                                return (
                                                    <div style={{ fontSize: '12px', color: '#555', marginTop: '4px', display: 'flex', gap: '5px', flexWrap: 'wrap' }}>
                                                        <span style={{ background: '#edf2f7', padding: '1px 6px', borderRadius: '4px' }}>{c.mbti}</span>
                                                        <span style={{ background: '#edf2f7', padding: '1px 6px', borderRadius: '4px' }}>{c.time?.split(' ')[0]}</span>
                                                        <span style={{ background: '#edf2f7', padding: '1px 6px', borderRadius: '4px' }}>{c.budget?.split(' (')[0]}</span>
                                                        <span style={{ background: '#edf2f7', padding: '1px 6px', borderRadius: '4px' }}>{c.vehicle?.includes('뚜벅') ? '뚜벅이' : '자가용'}</span>
                                                        <span style={{ background: '#edf2f7', padding: '1px 6px', borderRadius: '4px' }}>{c.preference?.split(' (')[0]}</span>
                                                    </div>
                                                );
                                            } catch { return null; }
                                        })()}
                                        <div style={{ fontSize: '12px', color: '#aaa', marginTop: '3px' }}>
                                            {new Date(course.createdAt).toLocaleDateString('ko-KR')}
                                        </div>
                                    </div>
                                    <button
                                        onClick={(e) => deleteCourse(e, course.id)}
                                        style={{ background: 'none', border: 'none', color: '#ccc', fontSize: '18px', cursor: 'pointer', padding: '4px 8px', borderRadius: '4px' }}
                                        onMouseEnter={(e) => e.currentTarget.style.color = '#e74c3c'}
                                        onMouseLeave={(e) => e.currentTarget.style.color = '#ccc'}
                                    >
                                        ✕
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}

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
                            <div style={{ display: 'flex', gap: '8px' }}>
                                <button
                                    onClick={shareCourse}
                                    style={{
                                        padding: '7px 16px', fontSize: '13px', fontWeight: 'bold',
                                        borderRadius: '6px', border: 'none', backgroundColor: '#fee500',
                                        color: '#3c1e1e', cursor: 'pointer'
                                    }}
                                >
                                    💬 카톡 공유
                                </button>
                                <button
                                    onClick={saveCourse}
                                    style={{
                                        padding: '7px 16px', fontSize: '13px', fontWeight: 'bold',
                                        borderRadius: '6px', border: 'none', backgroundColor: '#2c3e50',
                                        color: '#fff', cursor: 'pointer'
                                    }}
                                >
                                    💾 저장하기
                                </button>
                            </div>
                        </div>

                        {/* 총 시간/거리 요약 */}
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

                                {/* 카드 사이 이동 정보 연결 요소 */}
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