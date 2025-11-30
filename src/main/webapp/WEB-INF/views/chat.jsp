<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.List" %>
<%@ page import="kr.java.redis.model.entity.ChatMessage" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%!
    // JSP에서 사용할 날짜 포맷터 선언 (스크립틀릿 전용)
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
%>
<%
    // Controller에서 Model에 담아 전달한 속성들을 스크립틀릿으로 가져옵니다.
    String sessionId = (String) request.getAttribute("senderId"); // Use sessionId for form hidden field
    String nickname = (String) request.getAttribute("nickname"); // New: Get nickname
    List<ChatMessage> messages = (List<ChatMessage>) request.getAttribute("messages");
%>
<!DOCTYPE html>
<html>
<head>
    <title>간단 Redis 채팅</title>
</head>
<body>
<h1>간단 JSP 채팅방 (Redis 기반)</h1>
<p>
    당신의 세션 ID: <b><%= sessionId %></b>
    <br>
    당신의 닉네임: <b><%= nickname %></b> </p>

<hr />

<form action="<%= request.getContextPath() %>/chat" method="POST">

    <input type="hidden" name="senderId" value="<%= sessionId %>" />
    <input type="hidden" name="nickname" value="<%= nickname %>" /> <input type="text" id="message" name="message" size="50" placeholder="메시지를 입력하세요" required >

    <input type="submit" value="전송">
</form>

<hr>

<h2>채팅 메시지</h2>
<div id="messageArea" style="height: 300px; border: 1px solid black; overflow-y: scroll;">

    <%
        // 최신 메시지가 먼저 보이도록 리스트를 역순으로 순회
        if (messages != null) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMessage msg = messages.get(i);
    %>
    <p>
        [<%= msg.getTimestamp().format(FORMATTER) %>]
        <b><%= msg.getSenderId() %></b>: <%= msg.getMessage() %>
    </p>
    <%

            }
        }
    %>
</div>

<script>
    // DOM 요소 가져오기
    const messageArea = document.getElementById('messageArea');
    // 메시지 영역 스크롤을 항상 아래로 이동
    function scrollToBottom() {
        messageArea.scrollTop = messageArea.scrollHeight;
    }

    /**
     * 서버에서 메시지 목록을 비동기적으로 가져오는 함수 (Fetch API + async/await 사용)
     */
    async function fetchMessages() {
        try {
            // Fetch API를 호출하고 응답을 기다림 (await 사용)

            const response = await fetch('<%= request.getContextPath() %>/chat/messages', {
                method: 'GET',
                headers: {
                    'Accept': 'application/json' // JSON 응답을 기대함을 명시

                }
            });
            // 응답 상태 확인
            if (!response.ok) {
                throw new Error('HTTP status ' + response.status);
            }

            // 응답 본문을 JSON으로 파싱 (await 사용)
            const data = await response.json();
            const messages = data.messages;

            // 현재 메시지 영역을 비웁니다.
            messageArea.innerHTML = '';

            // 메시지 목록을 최신순으로 다시 채웁니다.
            for (let i = messages.length - 1; i >= 0; i--) { // let 사용
                const msg = messages[i];
                const messageElement = document.createElement('p');

                // 날짜 형식 지정 (Javascript Date 객체 사용)
                // new Date()가 YYYY-MM-DDTHH:mm:ss 형태를 불안정하게 파싱하므로,
                // 'T' 문자를 공백(' ')으로 대체하여 파싱 호환성을 높입니다.
                const reliableDateString = msg.timestamp.toString().replace('T', ' ');
                const dateObject = new Date(reliableDateString);
                // 새로운 Date 객체 생성

                let time;
                // 파싱이 유효한지 확인 (Invalid Date가 아닌지 체크)
                if (isNaN(dateObject.getTime())) {
                    time = '시간 오류';
                    console.error('Invalid Date for timestamp:', msg.timestamp);
                } else {
                    // 성공적으로 파싱되면 원하는 형식으로 포맷
                    time = dateObject.toLocaleTimeString('ko-KR', {
                        hour: '2-digit', minute:'2-digit', second:'2-digit', hour12: false
                    });
                }

                // 문자열 연결(+) 방식으로 HTML 내용을 생성하여 EL로 인식될 가능성을 완전히 제거
                messageElement.innerHTML = '[' + time + '] ' + '<b>' + msg.senderId + '</b>: ' +
                    msg.message;
                messageArea.appendChild(messageElement);
            }

            // 새 메시지를 받은 후 스크롤
            scrollToBottom();
        } catch (error) {
            console.error('메시지 로드 중 오류 발생:', error);
        }
    }

    // 페이지 로드 시 스크롤
    window.onload = function() {
        scrollToBottom();
        // 2초마다 메시지 갱신을 시작합니다. (Polling)
        setInterval(fetchMessages, 2000);
    };
</script>
</body>
</html>