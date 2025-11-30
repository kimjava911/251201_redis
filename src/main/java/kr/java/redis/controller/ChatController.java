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
     * 1. 사용자의 고유 ID (세션 ID)를 확인합니다.
     * 2. Redis에서 이전 메시지 목록을 조회하여 Model에 담아 뷰로 전달합니다.
     * @param session 현재 세션
     * @param model 뷰에 데이터를 전달하는 Model 객체
     * @return 뷰 이름 ("chat" -> /WEB-INF/views/chat.jsp)
     */
    @GetMapping("/chat")
    public String chatRoom(HttpSession session, Model model) {
        // 세션 ID를 사용자 ID로 사용 (자동 생성 세션을 통한 사용자 구분)
        String sessionId = session.getId();
        model.addAttribute("senderId", sessionId);

        // Redis에서 이전 메시지 목록을 가져옵니다.
        List<ChatMessage> messages = chatService.getMessages();
        model.addAttribute("messages", messages);

        return "chat"; // chat.jsp를 뷰로 사용
    }

    /**
     * 새로운 메시지를 전송합니다. (POST /chat)
     * @param senderId 전송자 ID (세션 ID)
     * @param message 전송할 메시지 내용
     * @return 채팅방 페이지로 리다이렉트 (동기 방식)
     */
    @PostMapping("/chat")
    public String sendMessage(
            @RequestParam("senderId") String senderId,
            @RequestParam("message") String message) {

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSenderId(senderId);
        chatMessage.setMessage(message);

        // 메시지를 Redis에 저장
        chatService.sendMessage(chatMessage);

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
