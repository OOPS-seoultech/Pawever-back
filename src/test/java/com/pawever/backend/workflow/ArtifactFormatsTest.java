package com.pawever.backend.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArtifactFormatsTest {
  @Test
  void imageExtensionMimeAndSignatureMustAgree() {
    byte[] png = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    assertThat(ArtifactFormats.matchesImage("views.png", "image/png", png)).isTrue();
    assertThat(ArtifactFormats.matchesImage("views.jpg", "image/jpeg", png)).isFalse();
    assertThat(ArtifactFormats.matchesImage("views.png", "image/png", "<script>".getBytes()))
        .isFalse();
    assertThat(ArtifactFormats.matchesImage("views.webp", "image/webp", new byte[0])).isFalse();
  }
}
