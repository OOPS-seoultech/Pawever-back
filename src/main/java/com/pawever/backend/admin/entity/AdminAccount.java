package com.pawever.backend.admin.entity;

import com.pawever.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 관리자 도메인에 로그인하는 계정.
 *
 * 앱 회원과 테이블을 나눈다. 회원 하나에 역할 칸을 더하는 편이 손은 덜 가지만,
 * 소셜 로그인으로 만들어진 계정 하나가 뚫리면 고객 주소와 연락처까지 열린다.
 * 들어오는 문이 아예 달라야 한다.
 *
 * 비밀번호는 만들어 주지 않는다. 초대 링크를 보내고 본인이 정한다. 만들어 주면
 * 누군가는 그 값을 전달하려고 메신저에 적는다.
 */
@Entity
@Table(name = "admin_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String email;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminAccountStatus status;

    /** 비밀번호를 정하기 전에는 비어 있다. */
    @Column(length = 100)
    private String passwordHash;

    /**
     * 초대 링크에 담기는 값의 해시.
     *
     * 원본을 저장하지 않는다. 데이터베이스를 들여다본 사람이 남의 초대 링크를
     * 그대로 만들어 계정을 가로챌 수 있으면 초대를 나눠 보낸 뜻이 없다.
     */
    @Column(length = 88)
    private String inviteTokenHash;

    private Instant inviteExpiresAt;

    private Instant lastLoginAt;

    public static AdminAccount invite(
            String email,
            String name,
            AdminRole role,
            String inviteTokenHash,
            Instant inviteExpiresAt
    ) {
        AdminAccount account = new AdminAccount();
        account.email = email;
        account.name = name;
        account.role = role;
        account.status = AdminAccountStatus.INVITED;
        account.inviteTokenHash = inviteTokenHash;
        account.inviteExpiresAt = inviteExpiresAt;
        return account;
    }

    /** 초대를 받아 비밀번호를 정한다. 초대 값은 그 자리에서 버린다. */
    public void activate(String passwordHash) {
        this.passwordHash = passwordHash;
        this.status = AdminAccountStatus.ACTIVE;
        this.inviteTokenHash = null;
        this.inviteExpiresAt = null;
    }

    /** 초대를 다시 보낸다. 앞서 보낸 링크는 그 순간 쓸 수 없게 된다. */
    public void reinvite(String inviteTokenHash, Instant inviteExpiresAt) {
        this.inviteTokenHash = inviteTokenHash;
        this.inviteExpiresAt = inviteExpiresAt;
        this.status = AdminAccountStatus.INVITED;
        this.passwordHash = null;
    }

    public void disable() {
        this.status = AdminAccountStatus.DISABLED;
        this.inviteTokenHash = null;
        this.inviteExpiresAt = null;
    }

    public void recordLogin(Instant at) {
        this.lastLoginAt = at;
    }

    public boolean canSignIn() {
        return status == AdminAccountStatus.ACTIVE && passwordHash != null;
    }

    public boolean isInviteUsable(Instant now) {
        return status == AdminAccountStatus.INVITED
                && inviteTokenHash != null
                && inviteExpiresAt != null
                && inviteExpiresAt.isAfter(now);
    }
}
