package com.pawever.backend.admin.dto;

import com.pawever.backend.admin.entity.WorkRole;

import java.util.Set;

/**
 * 실무 권한 승인.
 *
 * 어떤 일을 맡을지는 승인하는 사람이 정한다. 가입할 때 보낸 값이 권한을
 * 정하면, 요청을 고쳐 스스로 권한을 키울 수 있다.
 *
 * @param workRoles 맡길 일. 비어 있으면 아무 제작 작업도 할 수 없다
 */
public record AdminApproveRequest(Set<WorkRole> workRoles) {
}
