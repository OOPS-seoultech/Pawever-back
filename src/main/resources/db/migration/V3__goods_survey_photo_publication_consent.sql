ALTER TABLE `goods_survey_photos`
    ADD COLUMN `publication_agreed` BOOLEAN NOT NULL DEFAULT FALSE
        AFTER `confirmed_at`;
