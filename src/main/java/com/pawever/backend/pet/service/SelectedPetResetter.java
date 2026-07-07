package com.pawever.backend.pet.service;

import com.pawever.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선택된 반려동물(selectedPetId) 초기화를 호출측(읽기 전용) 트랜잭션과 독립적으로 커밋한다.
 * getSelectedPet 은 @Transactional(readOnly=true) 안에서 "초기화 후 예외"를 던지는데,
 * 같은 트랜잭션에서 처리하면 (1) readOnly 로 flush 안 됨 (2) 예외로 롤백 되어 초기화가 유실된다.
 * 이 컴포넌트를 프록시 경유로 호출해 REQUIRES_NEW 트랜잭션에서 확정 커밋시킨다.
 */
@Component
@RequiredArgsConstructor
public class SelectedPetResetter {

    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clear(Long userId) {
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .ifPresent(user -> user.selectPet(null));
    }
}
