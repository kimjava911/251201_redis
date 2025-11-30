package kr.java.redis.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/**
 * 채팅 메시지 데이터 모델
 * Redis에 저장/조회하기 위해 Serializable 인터페이스를 구현합니다.
 */
@Data // Lombok: Getter, Setter, toString, equals, hashCode 자동 생성
public class ChatMessage implements Serializable {

    // Redis에 저장되는 객체의 고유 ID (직렬화 관리를 위해 필요)
    private static final long serialVersionUID = 1L;

    // 메시지를 보낸 사용자 ID (여기서는 Session ID를 사용)
    private String senderId;

    // 메시지 내용
    private String message;

    // 메시지 전송 시간
    private Instant timestamp;
}
