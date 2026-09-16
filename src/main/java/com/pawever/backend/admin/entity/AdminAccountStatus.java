package com.pawever.backend.admin.entity;

public enum AdminAccountStatus {

    /** 초대는 보냈고 아직 비밀번호를 정하지 않았다. */
    INVITED,

    /**
     * 비밀번호는 정했지만 아직 권한을 받지 못했다.
     *
     * 가입만으로 관리자 권한이 생기면, 초대 링크가 한 번 새는 것으로
     * 고객 주소와 연락처가 통째로 열린다. 대표가 승인해야 쓸 수 있다.
     */
    PENDING_APPROVAL,

    ACTIVE,

    /** 그만둔 사람. 지우지 않고 막는다. 상태 변경 이력에 이름이 남아 있다. */
    DISABLED
}
