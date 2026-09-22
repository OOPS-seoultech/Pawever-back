package com.pawever.backend.global.event;

/**
 * 본문을 읽지도 못한 요청이 들어왔다.
 *
 * 사람이 잘못 적어 생기는 일이 아니다. 화면이 보내는 모양과 서버가 받는 모양이
 * 어긋났다는 뜻이고, 그러면 그 화면에서 오는 요청은 하나도 통과하지 못한다.
 * 한 사람이 한 번 겪는 오류와 달리 전부가 막히므로, 로그에만 두지 않고 알린다.
 *
 * @param path 어느 요청이었는지. 본문은 담지 않는다 - 이름·연락처·주소가 그
 *             안에 있고, 이 값은 사람이 읽는 채널로 나간다.
 */
public record ApiContractBreachEvent(String path) {
}
