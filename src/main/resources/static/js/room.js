function showError(message) {
    const banner = document.getElementById('errorBanner');
    banner.textContent = message;
    banner.classList.add('visible');
    setTimeout(() => banner.classList.remove('visible'), 4000);
}

function formatRoomType(type) {
    const map = { DIRECT: '1:1', GROUP: '그룹', CHANNEL: '채널' };
    return map[type] || type;
}

function getRoomIdFromQuery() {
    const id = new URLSearchParams(location.search).get('id');
    const roomId = Number(id);
    return Number.isFinite(roomId) && roomId > 0 ? roomId : null;
}

function formatTime(sentAt) {
    if (!sentAt) return '';
    const date = new Date(sentAt);
    return date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
}

const displayedMessageIds = new Set();

function renderMessage(msg) {
    if (displayedMessageIds.has(msg.id)) {
        return;
    }
    displayedMessageIds.add(msg.id);

    const messagesEl = document.getElementById('messages');
    const placeholder = messagesEl.querySelector('.message-placeholder');
    if (placeholder) {
        placeholder.remove();
    }

    const isMine = String(msg.senderId) === Auth.getUserId();
    const senderLabel = isMine
        ? '나'
        : (msg.senderNickname || 'user#' + msg.senderId);
    const div = document.createElement('div');
    div.className = 'message ' + (isMine ? 'message-mine' : 'message-other');
    div.dataset.messageId = msg.id;

    div.innerHTML = `
        <div class="message-meta">${escapeHtml(senderLabel)} · ${formatTime(msg.sentAt)}</div>
        <div class="message-body">${escapeHtml(msg.message)}</div>
    `;

    messagesEl.appendChild(div);
    messagesEl.scrollTop = messagesEl.scrollHeight;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function onWsMessage(body) {
    try {
        const msg = JSON.parse(body);
        renderMessage(msg);
    } catch (e) {
        showError('메시지 수신 오류');
    }
}

function updateConnectionStatus(connected) {
    const dot = document.getElementById('connectionDot');
    const label = document.getElementById('connectionLabel');
    const sendBtn = document.getElementById('sendBtn');
    const messageInput = document.getElementById('messageInput');

    dot.className = 'status-dot ' + (connected ? 'status-connected' : 'status-disconnected');
    label.textContent = connected ? '실시간 연결됨' : '연결 끊김';
    sendBtn.disabled = !connected;
    messageInput.disabled = !connected;
}

async function loadMessages(roomId) {
    const history = await Api.getMessages(roomId);
    const messagesEl = document.getElementById('messages');
    messagesEl.innerHTML = '';

    if (history.messages.length === 0) {
        messagesEl.innerHTML = '<div class="message message-placeholder">첫 메시지를 보내보세요.</div>';
        return;
    }

    history.messages.forEach(renderMessage);
}

async function sendMessage(roomId) {
    const input = document.getElementById('messageInput');
    const content = input.value.trim();
    if (!content || input.dataset.sending === 'true') {
        return;
    }

    input.dataset.sending = 'true';

    try {
        WsClient.sendMessage(roomId, content);
        input.value = '';
    } catch (err) {
        showError(err.message);
    } finally {
        window.setTimeout(() => {
            input.dataset.sending = 'false';
        }, 200);
    }
}

async function leaveRoom(roomId) {
    if (!confirm('채팅방에서 나가시겠습니까?')) {
        return;
    }

    try {
        WsClient.unsubscribe();
        await Api.leaveRoom(roomId);
        location.href = '/index.html';
    } catch (err) {
        showError(err.message);
    }
}

async function init() {
    if (!Auth.requireAuth()) {
        return;
    }

    const roomId = getRoomIdFromQuery();
    if (!roomId) {
        location.href = '/index.html';
        return;
    }

    document.getElementById('userInfo').textContent =
        `${Auth.getNickname() || Auth.getUserId()} (${Auth.getRole() || 'USER'})`;
    document.getElementById('logoutBtn').addEventListener('click', () => Auth.logout());
    document.getElementById('backBtn').addEventListener('click', () => {
        location.href = '/index.html';
    });
    document.getElementById('leaveRoomBtn').addEventListener('click', () => leaveRoom(roomId));

    const messageInput = document.getElementById('messageInput');
    const sendBtn = document.getElementById('sendBtn');
    let isComposing = false;

    messageInput.addEventListener('compositionstart', () => {
        isComposing = true;
    });
    messageInput.addEventListener('compositionend', () => {
        isComposing = false;
    });

    sendBtn.addEventListener('click', () => sendMessage(roomId));
    messageInput.addEventListener('keydown', (e) => {
        if (e.key !== 'Enter' || e.shiftKey) {
            return;
        }
        if (isComposing || e.isComposing || e.keyCode === 229) {
            return;
        }
        e.preventDefault();
        sendMessage(roomId);
    });

    try {
        const detail = await Api.getRoom(roomId);
        const room = detail.room;

        document.title = `Chat - ${room.name}`;
        document.getElementById('chatRoomName').textContent = room.name;
        document.getElementById('chatRoomMeta').textContent =
            `${formatRoomType(room.roomType)} · ${room.status} · 참여 ${room.participantCount}명 · #${room.id}`;

        const participantText = detail.participants
            .map(p => `${p.nickname || 'user#' + p.userId} (${p.participantRole})`)
            .join(', ');
        document.getElementById('chatParticipants').textContent = '참여자: ' + participantText;

        document.getElementById('messages').innerHTML =
            '<div class="message message-placeholder">메시지 불러오는 중...</div>';

        await loadMessages(roomId);
        await WsClient.connect(onWsMessage, updateConnectionStatus);
        WsClient.subscribe(roomId);
    } catch (e) {
        showError(e.message);
        document.getElementById('chatRoomName').textContent = '채팅방을 불러올 수 없습니다';
        setTimeout(() => {
            location.href = '/index.html';
        }, 1500);
    }
}

init();
