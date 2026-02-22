package com.pawever.backend.funeral.entity;

import com.pawever.backend.pet.entity.Pet;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pet_funeral_companies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PetFuneralCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "funeral_company_id", nullable = false)
    private FuneralCompany funeralCompany;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegistrationType type;

    public void updateType(RegistrationType type) {
        this.type = type;
    }
}
