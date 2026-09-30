-- Keep existing IDs and measured values, including zero, unchanged.
ALTER TABLE filaments
    MODIFY remaining_grams BIGINT NULL,
    ADD COLUMN color_category VARCHAR(40) NULL;

CREATE SEQUENCE filament_spool_sequence START WITH 1 INCREMENT BY 1;
