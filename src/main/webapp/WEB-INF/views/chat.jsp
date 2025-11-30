<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.List" %>
<%@ page import="kr.java.redis.model.entity.ChatMessage" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
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
    <%-- JavaScript가 렌더링할 수 있도록 data-* 속성에 데이터를 저장합니다. --%>
    <p class="message-item"
       data-timestamp="<%= msg.getTimestamp() %>"
       data-sender="<%= msg.getSenderId() %>"
       data-message="<%= msg.getMessage() %>"></p>
    <%
            }
        }
    %>
</div>

<script>
    // DOM 요소 가져오기
    const messageItems = document.querySelectorAll('.message-item');
    const messageArea = document.getElementById('messageArea');

    /**
     * ISO 8601 형식의 UTC 시간 문자열을 브라우저의 로컬 시간으로 변환합니다.
     * @param {string} utcTimestamp - UTC 시간 문자열 (e.g., "2025-11-30T10:20:30Z")
     * @returns {string} 포맷된 시간 문자열
     */
    function formatTimestamp(utcTimestamp) {
        const dateObject = new Date(utcTimestamp);
        if (isNaN(dateObject.getTime())) {
            console.error('Invalid Date for timestamp:', utcTimestamp);
            return '시간 오류';
        }
        return dateObject.toLocaleTimeString(navigator.language, { // 브라우저 기본 로캘 사용
            hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
        });
    }

    /**
     * JSP를 통해 전달된 초기 메시지를 JavaScript로 렌더링합니다.
     */
    function renderInitialMessages() {
        messageItems.forEach(item => {
            const time = formatTimestamp(item.dataset.timestamp); // JSP EL과의 충돌을 피하기 위해 `$`를 이스케이프 처리합니다.
            item.innerHTML = `[\${time}] <b>\${item.dataset.sender}</b>: \${item.dataset.message}`;
        });
    }

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
                const time = formatTimestamp(msg.timestamp); // UTC -> Local Time

                // 템플릿 리터럴을 사용하되, JSP EL과의 충돌을 피하기 위해 `$`를 이스케이프 처리합니다.
                messageElement.innerHTML = `[\${time}] <b>\${msg.senderId}</b>: \${msg.message}`;
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
        renderInitialMessages(); // 초기 메시지 렌더링
        scrollToBottom();

        // 2초마다 메시지 갱신을 시작합니다. (Polling)
        setInterval(fetchMessages, 2000);
    };
</script>
</body>
</html>