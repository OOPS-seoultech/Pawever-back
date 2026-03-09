package com.pawever.backend.memorial.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "report_reasons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ReportReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    private Integer orderIndex;
}
