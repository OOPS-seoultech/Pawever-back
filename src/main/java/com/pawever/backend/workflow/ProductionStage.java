package com.pawever.backend.workflow;

public enum ProductionStage {
  BLOCKED,
  MODELING_QUEUE,
  MODELING,
  MODEL_REVIEW,
  COLOR_MAPPING,
  PLATE_PREPARATION,
  PRINT_QUEUE,
  PRINTING,
  POST_PROCESSING,
  QC,
  PACKING,
  COMPLETE
}
