-- 동물 종류
INSERT IGNORE INTO animal_type (id, name) VALUES (1, '강아지');
INSERT IGNORE INTO animal_type (id, name) VALUES (2, '고양이');
INSERT IGNORE INTO animal_type (id, name) VALUES (3, '기타');

-- 강아지 품종
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (1, 1, '골든 리트리버');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (2, 1, '래브라도 리트리버');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (3, 1, '포메라니안');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (4, 1, '말티즈');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (5, 1, '푸들');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (6, 1, '시바 이누');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (7, 1, '비숑 프리제');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (8, 1, '웰시 코기');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (9, 1, '치와와');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (10, 1, '요크셔 테리어');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (11, 1, '기타');

-- 고양이 품종
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (12, 2, '코리안 숏헤어');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (13, 2, '페르시안');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (14, 2, '러시안 블루');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (15, 2, '브리티시 숏헤어');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (16, 2, '스코티시 폴드');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (17, 2, '먼치킨');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (18, 2, '랙돌');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (19, 2, '샴');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (20, 2, '아메리칸 숏헤어');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (21, 2, '기타');

-- 기타 품종
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (22, 3, '토끼');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (23, 3, '햄스터');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (24, 3, '앵무새');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (25, 3, '거북이');
INSERT IGNORE INTO breed (id, animal_type_id, name) VALUES (26, 3, '기타');

-- 미션 (발자국 남기기)
INSERT IGNORE INTO mission (id, name, description) VALUES (1, '함께 산책하기', '반려동물과 특별한 산책을 떠나보세요. 평소 가지 않았던 새로운 길을 함께 걸어보세요.');
INSERT IGNORE INTO mission (id, name, description) VALUES (2, '좋아하는 간식 주기', '반려동물이 가장 좋아하는 간식을 준비해주세요.');
INSERT IGNORE INTO mission (id, name, description) VALUES (3, '함께 사진 찍기', '반려동물과 함께 특별한 사진을 남겨보세요.');
INSERT IGNORE INTO mission (id, name, description) VALUES (4, '발도장 찍기', '반려동물의 발도장을 기념으로 남겨보세요.');
INSERT IGNORE INTO mission (id, name, description) VALUES (5, '편지 쓰기', '반려동물에게 마음을 담은 편지를 써보세요.');
INSERT IGNORE INTO mission (id, name, description) VALUES (6, '함께 놀아주기', '반려동물이 좋아하는 놀이를 함께 해보세요.');
INSERT IGNORE INTO mission (id, name, description) VALUES (7, '특별한 날 만들기', '반려동물을 위한 특별한 하루를 계획해보세요.');
INSERT IGNORE INTO mission (id, name, description) VALUES (8, '영상 남기기', '반려동물의 일상 영상을 촬영해보세요.');
INSERT IGNORE INTO mission (id, name, description) VALUES (9, '함께 낮잠 자기', '반려동물과 함께 편안한 낮잠 시간을 가져보세요.');
INSERT IGNORE INTO mission (id, name, description) VALUES (10, '목욕시켜주기', '반려동물을 깨끗이 목욕시켜주세요.');

-- 체크리스트 (이별준비)
INSERT IGNORE INTO checklist_item (id, title, description, order_index) VALUES (1, '장례업체 알아보기', '주변 반려동물 장례업체를 미리 알아두세요.', 1);
INSERT IGNORE INTO checklist_item (id, title, description, order_index) VALUES (2, '장례 방식 결정하기', '화장, 수목장, 납골 등 장례 방식을 미리 결정해두세요.', 2);
INSERT IGNORE INTO checklist_item (id, title, description, order_index) VALUES (3, '추모 용품 준비하기', '메모리얼 스톤, 추모 액자 등을 준비해두세요.', 3);
INSERT IGNORE INTO checklist_item (id, title, description, order_index) VALUES (4, '마지막 사진 정리하기', '반려동물과의 소중한 사진을 정리해두세요.', 4);
INSERT IGNORE INTO checklist_item (id, title, description, order_index) VALUES (5, '가족과 이야기 나누기', '가족들과 반려동물의 이별에 대해 함께 이야기해보세요.', 5);
INSERT IGNORE INTO checklist_item (id, title, description, order_index) VALUES (6, '수의사 상담하기', '반려동물의 상태에 대해 수의사와 상담해보세요.', 6);
INSERT IGNORE INTO checklist_item (id, title, description, order_index) VALUES (7, '좋아하는 것 목록 만들기', '반려동물이 좋아했던 것들의 목록을 만들어보세요.', 7);
INSERT IGNORE INTO checklist_item (id, title, description, order_index) VALUES (8, '편안한 환경 만들기', '반려동물이 편안하게 지낼 수 있는 환경을 만들어주세요.', 8);

-- 이별 가이드
INSERT IGNORE INTO guide (id, name) VALUES (1, '반려동물 사망 직후 대처 가이드');
INSERT IGNORE INTO guide (id, name) VALUES (2, '장례 절차 안내');
INSERT IGNORE INTO guide (id, name) VALUES (3, '펫로스 증후군 극복 가이드');

-- 사망 직후 대처 가이드 단계
INSERT IGNORE INTO guide_step (id, guide_id, title, description, order_index) VALUES (1, 1, '침착하게 상태 확인', '반려동물의 호흡과 심장박동을 확인하세요. 완전히 멈추었는지 확인합니다.', 1);
INSERT IGNORE INTO guide_step (id, guide_id, title, description, order_index) VALUES (2, 1, '시신 안치', '깨끗한 담요나 수건으로 감싸주세요. 서늘한 곳에 안치합니다.', 2);
INSERT IGNORE INTO guide_step (id, guide_id, title, description, order_index) VALUES (3, 1, '장례업체 연락', '미리 알아둔 장례업체에 연락하세요. 24시간 운영 업체가 많습니다.', 3);
INSERT IGNORE INTO guide_step (id, guide_id, title, description, order_index) VALUES (4, 1, '가족에게 알리기', '함께 반려동물을 돌봤던 가족들에게 알려주세요.', 4);

-- 장례 절차 안내 단계
INSERT IGNORE INTO guide_step (id, guide_id, title, description, order_index) VALUES (5, 2, '장례업체 방문', '예약한 장례업체를 방문합니다.', 1);
INSERT IGNORE INTO guide_step (id, guide_id, title, description, order_index) VALUES (6, 2, '추모 시간', '마지막으로 반려동물과 함께하는 시간을 가집니다.', 2);
INSERT IGNORE INTO guide_step (id, guide_id, title, description, order_index) VALUES (7, 2, '화장 또는 장례 진행', '선택한 방식으로 장례를 진행합니다.', 3);
INSERT IGNORE INTO guide_step (id, guide_id, title, description, order_index) VALUES (8, 2, '유골 수습', '화장 후 유골을 수습하고 보관 방법을 결정합니다.', 4);

-- 펫로스 증후군 극복 가이드 단계
INSERT IGNORE INTO guide_step (id, guide_id, title, description, order_index) VALUES (9, 3, '슬픔을 인정하기', '반려동물을 잃은 슬픔은 자연스러운 감정입니다. 울어도 괜찮습니다.', 1);
INSERT IGNORE INTO guide_step (id, guide_id, title, description, order_index) VALUES (10, 3, '추억 정리하기', '함께했던 소중한 추억들을 사진이나 글로 정리해보세요.', 2);
INSERT IGNORE INTO guide_step (id, guide_id, title, description, order_index) VALUES (11, 3, '주변에 도움 구하기', '같은 경험을 한 사람들과 이야기를 나눠보세요.', 3);
INSERT IGNORE INTO guide_step (id, guide_id, title, description, order_index) VALUES (12, 3, '일상 회복하기', '천천히 일상으로 돌아가세요. 시간이 필요합니다.', 4);
