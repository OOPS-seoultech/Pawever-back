package com.pawever.backend.workflow;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

final class ArtifactFormats {
  private ArtifactFormats() {}

  static boolean matchesImage(String name, String type, byte[] signature) {
    if (signature == null) return false;
    String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    return switch (extension) {
      case "png" ->
          type.equals("image/png")
              && signature.length >= 8
              && Arrays.equals(
                  Arrays.copyOf(signature, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
      case "jpg", "jpeg" ->
          type.equals("image/jpeg")
              && signature.length >= 3
              && signature[0] == (byte) 255
              && signature[1] == (byte) 216
              && signature[2] == (byte) 255;
      case "webp" ->
          type.equals("image/webp")
              && signature.length >= 12
              && new String(signature, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
              && new String(signature, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
      default -> false;
    };
  }
}
