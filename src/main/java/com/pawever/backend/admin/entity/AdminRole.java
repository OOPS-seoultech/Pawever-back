package com.pawever.backend.admin.entity;

/**
 * 관리자 도메인에서 무엇을 할 수 있는지.
 *
 * 화면에서 메뉴만 숨기지 않는다. 서버가 역할을 보고 막는다. 메뉴만 숨기면
 * 주소를 직접 치거나 요청을 그대로 보내는 것으로 넘어간다.
 */
public enum AdminRole {

    /** 주문·결제·배송·취소 전반. 주소와 연락처를 본다. */
    ADMIN,

    /**
     * 제작에 필요한 것만.
     *
     * 반려견 이름·굿즈·사진까지 본다. 보호자 연락처와 주소는 보지 않는다.
     * 만드는 데 필요 없는 정보라, 볼 수 있게 두면 볼 이유가 없는 것을 보게 된다.
     */
    PRODUCTION;

    /** 스프링 시큐리티가 쓰는 권한 이름. */
    public String authority() {
        return "ROLE_" + name();
    }
}
