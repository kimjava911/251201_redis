package kr.java.redis.service;

import kr.java.redis.model.entity.ChatMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 채팅 관련 비즈니스 로직을 처리하는 서비스 클래스
 * Redis List를 사용하여 메시지들을 저장하고 조회합니다.
 */
@Service
public class ChatService {

    // 채팅 메시지를 저장하는 Redis List의 키
    private static final String CHAT_KEY = "chat:room:main";
    // 한 번에 불러올 메시지의 최대 개수
    private static final long MAX_MESSAGES = 50;

    private final RedisTemplate<String, ChatMessage> chatMessageRedisTemplate;

    // 생성자 주입
    public ChatService(RedisTemplate<String, ChatMessage> chatMessageRedisTemplate) {
        this.chatMessageRedisTemplate = chatMessageRedisTemplate;
    }

    /**
     * 새로운 메시지를 Redis List에 저장합니다.
     * Redis List의 왼쪽(Head)에 메시지를 추가합니다 (LPUSH).
     * @param message 전송할 메시지 객체
     * @return 저장된 메시지 객체
     */
    public ChatMessage sendMessage(ChatMessage message) {
        message.setTimestamp(LocalDateTime.now());
        // List의 왼쪽(가장 최신)에 메시지 객체 저장
        chatMessageRedisTemplate.opsForList().leftPush(CHAT_KEY, message);
        // 리스트 크기가 MAX_MESSAGES를 초과하면 오래된 메시지를 삭제 (최신 50개 유지)
        chatMessageRedisTemplate.opsForList().trim(CHAT_KEY, 0, MAX_MESSAGES - 1);
        return message;
    }

    /**
     * Redis List에서 최근 메시지 목록을 조회합니다.
     * List의 Range를 사용하여 가장 최신 메시지부터 조회합니다 (0부터 MAX_MESSAGES-1).
     * @return 최근 메시지 목록 (List<ChatMessage>)
     */
    public List<ChatMessage> getMessages() {
        // start 0: 가장 왼쪽(최신), end -1: 가장 오른쪽(가장 오래된)
        // 여기서는 최신 메시지부터 50개만 조회합니다.
        return chatMessageRedisTemplate.opsForList().range(CHAT_KEY, 0, MAX_MESSAGES - 1);
    }
}
