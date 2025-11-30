package kr.java.redis.service;

import kr.java.redis.model.entity.ChatMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 채팅 관련 비즈니스 로직을 처리하는 서비스 클래스
 * Redis List를 사용하여 메시지 키를 관리하고, 개별 키에 TTL을 설정합니다.
 */
@Service
public class ChatService {

    // 채팅 메시지 키 관리를 위한 Redis List의 키
    private static final String CHAT_KEY = "chat:room:main";
    // 세션-닉네임 매핑 키 접두사
    private static final String NICKNAME_KEY_PREFIX = "user:nickname:";
    // 개별 메시지 저장을 위한 키 접두사
    private static final String MESSAGE_KEY_PREFIX = "chat:message:";

    // 닉네임 유효 시간 (1시간) 및 메시지 유효 시간 (5분)
    private static final long NICKNAME_TTL_HOURS = 1;
    private static final long MESSAGE_TTL_MINUTES = 5;

    // 한 번에 불러올 메시지의 최대 개수
    private static final long MAX_MESSAGES = 50;

    private final RedisTemplate<String, ChatMessage> chatMessageRedisTemplate;
    private final StringRedisTemplate stringRedisTemplate; // StringRedisTemplate 주입

    // 생성자 주입
    public ChatService(
            RedisTemplate<String, ChatMessage> chatMessageRedisTemplate,
            StringRedisTemplate stringRedisTemplate) {
        this.chatMessageRedisTemplate = chatMessageRedisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 세션 ID에 해당하는 닉네임을 가져옵니다. 닉네임이 없으면 새로 생성하고 TTL을 설정합니다.
     * @param sessionId 세션 ID
     * @return 닉네임
     */
    public String getNickname(String sessionId) {
        String nicknameKey = NICKNAME_KEY_PREFIX + sessionId;
        String nickname = stringRedisTemplate.opsForValue().get(nicknameKey);

        if (nickname == null) {
            // 닉네임이 없는 경우, 새로 생성 (간단하게 Session ID의 일부를 사용)
            nickname = "익명" + sessionId.substring(0, 4);
            // Redis에 닉네임 저장 및 1시간 TTL 설정 (Set-If-Absent: NX 옵션과 동일)
            stringRedisTemplate.opsForValue().set(
                    nicknameKey,
                    nickname,
                    NICKNAME_TTL_HOURS,
                    TimeUnit.HOURS // TTL 단위
            );
        } else {
            // 닉네임이 존재하는 경우, TTL 갱신 (1시간)
            stringRedisTemplate.expire(nicknameKey, NICKNAME_TTL_HOURS, TimeUnit.HOURS);
        }
        return nickname;
    }

    /**
     * 새로운 메시지를 Redis에 개별 String 키로 저장하고, 인덱스 List에 키를 추가합니다.
     * @param message 전송할 메시지 객체
     * @param nickname 전송자 닉네임
     * @return 저장된 메시지 객체
     */
    public ChatMessage sendMessage(ChatMessage message, String nickname) {
        message.setSenderId(nickname); // 세션 ID 대신 닉네임 사용
        message.setTimestamp(LocalDateTime.now());

        // 1. 메시지 객체를 개별 키로 저장하고 5분 TTL 설정
        String messageKey = MESSAGE_KEY_PREFIX + UUID.randomUUID().toString();

        // chatMessageRedisTemplate를 사용하여 ChatMessage 객체 직렬화 및 TTL 설정
        chatMessageRedisTemplate.opsForValue().set(
                messageKey,
                message,
                MESSAGE_TTL_MINUTES,
                TimeUnit.MINUTES // 5분 TTL
        );

        // 2. 메시지 키를 인덱스 List의 왼쪽(가장 최신)에 추가 (List에는 키만 저장)
        // StringRedisTemplate을 사용하여 List Key를 String으로 관리
        stringRedisTemplate.opsForList().leftPush(CHAT_KEY, messageKey);

        // 3. 리스트 크기가 MAX_MESSAGES를 초과하면 오래된 메시지 키 삭제
        stringRedisTemplate.opsForList().trim(CHAT_KEY, 0, MAX_MESSAGES - 1);

        return message;
    }

    /**
     * Redis List에 저장된 메시지 키들을 조회하여 개별 메시지 객체를 가져옵니다.
     * 만료된 메시지 키(null 반환)는 인덱스 List에서도 제거합니다.
     * @return 최근 메시지 목록 (List<ChatMessage>)
     */
    public List<ChatMessage> getMessages() {
        // 1. 인덱스 List에서 키 목록 조회
        List<String> messageKeys = stringRedisTemplate.opsForList().range(CHAT_KEY, 0, MAX_MESSAGES - 1);

        if (messageKeys == null || messageKeys.isEmpty()) {
            return List.of();
        }

        // 2. 키들을 사용하여 메시지 객체를 MultiGet으로 한 번에 조회
        List<ChatMessage> messages = chatMessageRedisTemplate.opsForValue().multiGet(messageKeys);

        // 3. 만료된 메시지(null)를 필터링하고 인덱스 List에서 제거할 키 목록을 준비
        List<String> keysToRemove = new java.util.ArrayList<>();
        List<ChatMessage> validMessages = new java.util.ArrayList<>();

        for (int i = 0; i < messageKeys.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (msg != null) {
                validMessages.add(msg);
            } else {
                keysToRemove.add(messageKeys.get(i));
            }
        }

        // 4. 만료된 키들을 인덱스 List에서 제거 (LREM을 사용)
        if (!keysToRemove.isEmpty()) {
            for (String key : keysToRemove) {
                // count=0: 리스트에서 해당 값을 가진 모든 요소를 제거
                stringRedisTemplate.opsForList().remove(CHAT_KEY, 0, key);
            }
        }

        return validMessages;
    }
}