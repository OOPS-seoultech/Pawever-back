package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "filaments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Filament {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private long version;

  @Column(nullable = false, unique = true, length = 64)
  private String spoolId;

  @Column(nullable = false, length = 80)
  private String colorName;

  @Column(nullable = false, length = 40)
  private String material;

  @Column(nullable = false, length = 40)
  private String finish;

  @Column(nullable = false, length = 100)
  private String manufacturer;

  @Column(nullable = false, length = 300)
  private String source;

  private Long priceKrw;

  @Column(nullable = false)
  private long remainingGrams;

  @Column(nullable = false)
  private boolean active;

  @Column(nullable = false)
  private Instant updatedAt;

  public static Filament create(String spoolId) {
    var f = new Filament();
    f.spoolId = spoolId;
    return f;
  }

  public void update(
      String color,
      String material,
      String finish,
      String manufacturer,
      String source,
      Long price,
      long remaining,
      boolean active,
      Instant at) {
    this.colorName = color;
    this.material = material;
    this.finish = finish;
    this.manufacturer = manufacturer;
    this.source = source;
    this.priceKrw = price;
    this.remainingGrams = remaining;
    this.active = active;
    this.updatedAt = updatedAt != null && !at.isAfter(updatedAt) ? updatedAt.plusNanos(1000) : at;
  }
}
