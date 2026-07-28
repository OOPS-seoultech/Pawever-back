-- 사연 동의를 분석 동의(필수)와 SNS 공유 동의(선택) 둘로 줄인다.
-- 게시 전 문안 확인·후속 인터뷰 동의는 더 이상 받지 않는다.
ALTER TABLE `goods_survey_stories`
    DROP COLUMN `review_contact_agreed`,
    DROP COLUMN `interview_agreed`;
