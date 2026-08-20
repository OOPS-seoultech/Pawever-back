package com.pawever.backend.admin.entity;

public enum AdminAccountStatus {

    /** 초대는 보냈고 아직 비밀번호를 정하지 않았다. */
    INVITED,

    ACTIVE,

    /** 그만둔 사람. 지우지 않고 막는다. 상태 변경 이력에 이름이 남아 있다. */
    DISABLED
}
