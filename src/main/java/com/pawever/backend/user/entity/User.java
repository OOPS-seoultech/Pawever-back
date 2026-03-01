package com.pawever.backend.user.entity;

import com.pawever.backend.global.common.BaseTimeEntity;
import com.pawever.backend.global.common.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 500)
    private String name;

    private String nickname;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 500)
    private String phone;

    /** 전화번호 중복 검색용 Blind Index (HMAC-SHA256) */
    @Column(unique = true)
    private String phoneHash;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 500)
    private String email;

    private String gender;

    private String birthday;

    private String birthYear;

    private String ageRange;

    private String kakaoId;

    private String naverId;

    private String profileImageUrl;

    private Long selectedPetId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReferralType referralType;

    @Column(length = 255)
    private String referralMemo;

    private LocalDateTime deletedAt;

    public void selectPet(Long petId) {
        this.selectedPetId = petId;
    }

    public void updateProfile(String name, String nickname, String phone, String phoneHash) {
        this.name = name;
        this.nickname = nickname;
        this.phone = phone;
        this.phoneHash = phoneHash;
    }

    public void updateReferral(ReferralType referralType, String referralMemo) {
        this.referralType = referralType;
        this.referralMemo = referralType == ReferralType.OTHER ? referralMemo : null;
    }

    public void updateReferral(ReferralType referralType, String referralMemo) {
        this.referralType = referralType;
        this.referralMemo = referralType == ReferralType.OTHER ? referralMemo : null;
    }

    public void updateProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void withdraw() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
