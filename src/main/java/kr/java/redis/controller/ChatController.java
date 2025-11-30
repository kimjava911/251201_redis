package kr.java.redis.controller;

import jakarta.servlet.http.HttpSession;
import kr.java.redis.model.entity.ChatMessage;
import kr.java.redis.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 채팅방 접속 및 메시지 송수신을 처리하는 컨트롤러 클래스
 */
@Controller
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public String index() {
        return "redirect:/chat";
    }

    /**
     * 채팅방 페이지를 로드합니다. (GET /chat)
     * 1. 사용자의 고유 ID (세션 ID)를 확인하고, 이에 매핑된 닉네임을 가져옵니다. (닉네임 TTL 갱신)
     * 2. Redis에서 이전 메시지 목록을 조회하여 Model에 담아 뷰로 전달합니다.
     * @param session 현재 세션
     * @param model 뷰에 데이터를 전달하는 Model 객체
     * @return 뷰 이름 ("chat" -> /WEB-INF/views/chat.jsp)
     */
    @GetMapping("/chat")
    public String chatRoom(HttpSession session, Model model) {
        // 세션 ID를 기반으로 닉네임을 가져오거나 새로 생성하고 TTL을 갱신합니다.
        String sessionId = session.getId();
        String nickname = chatService.getNickname(sessionId); // New: Get or create nickname

        model.addAttribute("senderId", sessionId); // 세션 ID는 그대로 유지 (폼에 hidden으로 사용)
        model.addAttribute("nickname", nickname); // New: 닉네임을 뷰로 전달

        // Redis에서 이전 메시지 목록을 가져옵니다.
        List<ChatMessage> messages = chatService.getMessages();
        model.addAttribute("messages", messages);

        return "chat"; // chat.jsp를 뷰로 사용
    }

    /**
     * 새로운 메시지를 전송합니다. (POST /chat)
     * @param senderId 전송자 ID (세션 ID - hidden 필드)
     * @param nickname 전송자 닉네임 (New: 폼에서 전달받음)
     * @param message 전송할 메시지 내용
     * @return 채팅방 페이지로 리다이렉트 (동기 방식)
     */
    @PostMapping("/chat")
    public String sendMessage(
            @RequestParam("senderId") String senderId,
            @RequestParam("nickname") String nickname, // New parameter
            @RequestParam("message") String message) {

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setMessage(message);

        // 메시지를 Redis에 저장하고 닉네임의 TTL을 갱신합니다.
        // senderId (세션 ID)를 기반으로 getNickname()을 한 번 더 호출하여 TTL 갱신을 보장할 수 있으나,
        // 이미 chatRoom에서 처리하고 있으므로, 여기서는 폼에서 받은 nickname을 사용하고 service 내부에서 닉네임 TTL 갱신은 ChatService.getNickname()에서 처리됩니다.
        // 하지만 sendMessage 시에도 닉네임 TTL 갱신이 필요하므로, 여기서는 ChatService의 getNickname()을 호출하도록 로직을 추가하는 것이 더 안전합니다.
        // (ChatService.sendMessage에서 닉네임 TTL 갱신 코드가 제거되었으므로)
        chatService.getNickname(senderId); // 메시지 전송 시 닉네임 TTL 갱신 (1시간)

        chatService.sendMessage(chatMessage, nickname); // Modified: Pass nickname

        // 메시지 전송 후 채팅방 페이지로 다시 이동
        return "redirect:/chat";
    }

    /**
     * AJAX 요청을 통해 새로운 메시지 목록을 가져옵니다. (GET /chat/messages)
     * 클라이언트 측에서 주기적으로 호출하여 채팅 화면을 업데이트할 때 사용됩니다 (Polling).
     * @return JSON 형태로 메시지 목록을 반환
     */
    @GetMapping("/chat/messages")
    @ResponseBody // JSON 또는 XML 응답을 위해 사용
    public ResponseEntity<Map<String, Object>> getNewMessages() {
        List<ChatMessage> messages = chatService.getMessages();

        // JSON 형태로 메시지를 전달하고
        // JavaScript가 이를 파싱하여 화면을 업데이트하도록 합니다.
        Map<String, Object> response = new HashMap<>();
        response.put("messages", messages);

        return ResponseEntity.ok(response);
    }


}
