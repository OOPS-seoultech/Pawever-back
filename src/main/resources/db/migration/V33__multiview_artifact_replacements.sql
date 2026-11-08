ALTER TABLE production_artifacts ADD COLUMN replaces_artifact_id VARCHAR(255) NULL;
CREATE INDEX idx_artifact_replacement ON production_artifacts(replaces_artifact_id, confirmed);
