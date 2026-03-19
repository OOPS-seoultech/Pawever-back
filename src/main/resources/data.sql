-- 동물 종류·품종은 breeds_data.sql에서 로드

-- 미션(발자국 남기기) 54개는 missions_data.sql에서 로드 (ON DUPLICATE KEY UPDATE 사용)

-- 체크리스트 (이별준비) - checklist_items
-- 기존 임시 항목(6~8) 정리 (FK 때문에 pet_checklists 먼저 삭제)
DELETE FROM pet_checklists WHERE checklist_item_id IN (6, 7, 8);
DELETE FROM checklist_items WHERE id IN (6, 7, 8);

-- 5단계 스텝퍼(이별방법/안치준비/행정처리/물건정리/지원사업)로 고정
INSERT INTO checklist_items (id, title, description, order_index) VALUES (1, '이별방법', NULL, 1)
ON DUPLICATE KEY UPDATE title=VALUES(title), description=VALUES(description), order_index=VALUES(order_index);
INSERT INTO checklist_items (id, title, description, order_index) VALUES (2, '안치준비', NULL, 2)
ON DUPLICATE KEY UPDATE title=VALUES(title), description=VALUES(description), order_index=VALUES(order_index);
INSERT INTO checklist_items (id, title, description, order_index) VALUES (3, '행정처리', NULL, 3)
ON DUPLICATE KEY UPDATE title=VALUES(title), description=VALUES(description), order_index=VALUES(order_index);
INSERT INTO checklist_items (id, title, description, order_index) VALUES (4, '물건정리', NULL, 4)
ON DUPLICATE KEY UPDATE title=VALUES(title), description=VALUES(description), order_index=VALUES(order_index);
INSERT INTO checklist_items (id, title, description, order_index) VALUES (5, '지원사업', NULL, 5)
ON DUPLICATE KEY UPDATE title=VALUES(title), description=VALUES(description), order_index=VALUES(order_index);

-- 이별 가이드 (guides)
INSERT IGNORE INTO guides (id, name) VALUES (1, '반려동물 사망 직후 대처 가이드');
INSERT IGNORE INTO guides (id, name) VALUES (2, '장례 절차 안내');
INSERT IGNORE INTO guides (id, name) VALUES (3, '펫로스 증후군 극복 가이드');

-- 사망 직후 대처 가이드 단계 (guide_steps)
INSERT IGNORE INTO guide_steps (id, guide_id, title, description, order_index) VALUES (1, 1, '침착하게 상태 확인', '반려동물의 호흡과 심장박동을 확인하세요. 완전히 멈추었는지 확인합니다.', 1);
INSERT IGNORE INTO guide_steps (id, guide_id, title, description, order_index) VALUES (2, 1, '시신 안치', '깨끗한 담요나 수건으로 감싸주세요. 서늘한 곳에 안치합니다.', 2);
INSERT IGNORE INTO guide_steps (id, guide_id, title, description, order_index) VALUES (3, 1, '장례업체 연락', '미리 알아둔 장례업체에 연락하세요. 24시간 운영 업체가 많습니다.', 3);
INSERT IGNORE INTO guide_steps (id, guide_id, title, description, order_index) VALUES (4, 1, '가족에게 알리기', '함께 반려동물을 돌봤던 가족들에게 알려주세요.', 4);

-- 장례 절차 안내 단계
INSERT IGNORE INTO guide_steps (id, guide_id, title, description, order_index) VALUES (5, 2, '장례업체 방문', '예약한 장례업체를 방문합니다.', 1);
INSERT IGNORE INTO guide_steps (id, guide_id, title, description, order_index) VALUES (6, 2, '추모 시간', '마지막으로 반려동물과 함께하는 시간을 가집니다.', 2);
INSERT IGNORE INTO guide_steps (id, guide_id, title, description, order_index) VALUES (7, 2, '화장 또는 장례 진행', '선택한 방식으로 장례를 진행합니다.', 3);
INSERT IGNORE INTO guide_steps (id, guide_id, title, description, order_index) VALUES (8, 2, '유골 수습', '화장 후 유골을 수습하고 보관 방법을 결정합니다.', 4);

-- 펫로스 증후군 극복 가이드 단계
INSERT IGNORE INTO guide_steps (id, guide_id, title, description, order_index) VALUES (9, 3, '슬픔을 인정하기', '반려동물을 잃은 슬픔은 자연스러운 감정입니다. 울어도 괜찮습니다.', 1);
INSERT IGNORE INTO guide_steps (id, guide_id, title, description, order_index) VALUES (10, 3, '추억 정리하기', '함께했던 소중한 추억들을 사진이나 글로 정리해보세요.', 2);
INSERT IGNORE INTO guide_steps (id, guide_id, title, description, order_index) VALUES (11, 3, '주변에 도움 구하기', '같은 경험을 한 사람들과 이야기를 나눠보세요.', 3);
INSERT IGNORE INTO guide_steps (id, guide_id, title, description, order_index) VALUES (12, 3, '일상 회복하기', '천천히 일상으로 돌아가세요. 시간이 필요합니다.', 4);

-- 댓글 신고 사유 (report_reasons) - 추모관 댓글 신고용
INSERT IGNORE INTO report_reasons (id, name, order_index) VALUES (1, '비하 및 모욕', 1);
INSERT IGNORE INTO report_reasons (id, name, order_index) VALUES (2, '부적절한 홍보', 2);
INSERT IGNORE INTO report_reasons (id, name, order_index) VALUES (3, '종교 및 정치적 분쟁', 3);
INSERT IGNORE INTO report_reasons (id, name, order_index) VALUES (4, '개인정보 노출', 4);
INSERT IGNORE INTO report_reasons (id, name, order_index) VALUES (5, '자해 및 위험한 콘텐츠', 5);

-- 자주 묻는 질문 (faqs) - Figma Q&A 화면 기준
INSERT IGNORE INTO faqs (id, question, answer, order_index) VALUES (1, '이별 후에는 장례식장을 찾아볼 수 없는 건가요?', '이별 후에도 포에버 앱에서 등록된 반려동물 장례업체 목록을 계속 조회하실 수 있습니다. 다만 이별 전에 미리 장례업체를 알아보고 저장해두시면, 급한 상황에서 더 빠르게 연락하실 수 있습니다.', 1);
INSERT IGNORE INTO faqs (id, question, answer, order_index) VALUES (2, '음성 녹음만 업로드 할 수 있나요?', '아니요. 포에버에서는 사진, 영상, 음성 녹음 등 다양한 형태의 추억을 남기실 수 있습니다. 발자국 남기기 미션을 통해 원하시는 방식으로 소중한 순간을 기록해보세요.', 2);
INSERT IGNORE INTO faqs (id, question, answer, order_index) VALUES (3, '이별 전에 별자리 추모관을 보여주는 이유가 무엇인가요?', '이별 전에 별자리 추모관을 미리 보여드리는 것은, 반려동물과 함께할 수 있는 시간을 더 의미 있게 쓰실 수 있도록 돕기 위함입니다. 미리 알아두시면 이별 후에도 추모 공간을 편하게 이용하실 수 있습니다.', 3);
INSERT IGNORE INTO faqs (id, question, answer, order_index) VALUES (4, '그 외 기타 질문 내용', '기타 문의가 있으시면 pawever01@gmail.com 로 메일을 보내주세요. 영업시간 10:00 - 19:00 (24시간 이내 답변) 안내드리겠습니다.', 4);
