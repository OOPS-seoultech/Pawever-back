package com.pawever.backend.funeral.entity;

import com.pawever.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_funeral_companies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserFuneralCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

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
