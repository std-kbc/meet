let rooms = [];

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

function badgeClass(type) {
    return type === 'DIRECT' ? 'badge badge-direct' : 'badge badge-group';
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function goToRoom(roomId) {
    location.href = '/room.html?id=' + roomId;
}

function renderOwnerBadge(room) {
    if (room.roomType !== 'GROUP' || room.myParticipantRole !== 'OWNER') {
        return '';
    }
    return '<span class="badge badge-owner">OWNER</span>';
}

function truncateMessage(text, maxLength = 15) {
    if (!text) {
        return '';
    }
    if (text.length <= maxLength) {
        return text;
    }
    return text.slice(0, maxLength) + '...';
}

function renderRoomList() {
    const list = document.getElementById('roomList');
    if (rooms.length === 0) {
        list.innerHTML = '<li class="empty-state">참여 중인 채팅방이 없습니다.<br>새 방을 만들거나 ID로 입장하세요.</li>';
        return;
    }

    list.innerHTML = rooms.map(room => `
        <li class="room-item" data-room-id="${room.id}">
            <div class="room-item-header">
                <div class="room-item-name">${escapeHtml(room.name)}</div>
                <div class="room-item-meta">
                    ${renderOwnerBadge(room)}
                    <span class="${badgeClass(room.roomType)}">${formatRoomType(room.roomType)}</span>
                    <span>참여 ${room.participantCount}명</span>
                    <span>#${room.id}</span>
                </div>
            </div>
            <div class="room-item-last-message">${escapeHtml(truncateMessage(room.lastMessage)) || '메시지가 없습니다.'}</div>
        </li>
    `).join('');

    list.querySelectorAll('.room-item').forEach(item => {
        item.addEventListener('click', () => goToRoom(Number(item.dataset.roomId)));
    });
}

async function loadRooms() {
    try {
        rooms = await Api.getRooms();
        renderRoomList();
    } catch (e) {
        showError(e.message);
    }
}

function openModal(id) {
    document.getElementById(id).classList.add('visible');
}

function closeModal(id) {
    document.getElementById(id).classList.remove('visible');
    if (id === 'joinModal') {
        resetJoinModal();
    }
}

function closeBrowseRoomPanel() {
    document.getElementById('browseRoomPanel').classList.remove('visible');
}

function resetJoinModal() {
    document.getElementById('joinForm').reset();
    document.getElementById('browseRoomPanel').classList.remove('visible');
    document.getElementById('browseRoomList').innerHTML =
        '<li class="empty-state">목록을 불러오려면 위 버튼을 누르세요.</li>';
}

function selectBrowseRoom(roomId) {
    document.getElementById('joinRoomId').value = roomId;
    document.getElementById('joinRoomId').focus();
}

function renderBrowseRoomList(browseRooms) {
    const list = document.getElementById('browseRoomList');
    if (browseRooms.length === 0) {
        list.innerHTML = '<li class="empty-state">입장 가능한 채팅방이 없습니다.</li>';
        return;
    }

    list.innerHTML = browseRooms.map(room => `
        <li class="browse-room-item">
            <div class="browse-room-info">
                <div class="browse-room-name">${escapeHtml(room.name)}</div>
                <div class="browse-room-meta">
                    <span class="${badgeClass(room.roomType)}">${formatRoomType(room.roomType)}</span>
                    <span>참여 ${room.participantCount}명</span>
                    <span>#${room.id}</span>
                </div>
            </div>
            <button type="button" class="btn-outline btn-sm browse-room-select-btn" data-room-id="${room.id}">
                선택
            </button>
        </li>
    `).join('');

    list.querySelectorAll('.browse-room-select-btn').forEach(button => {
        button.addEventListener('click', () => selectBrowseRoom(Number(button.dataset.roomId)));
    });
}

async function loadBrowseRooms() {
    const panel = document.getElementById('browseRoomPanel');
    const list = document.getElementById('browseRoomList');

    panel.classList.add('visible');
    list.innerHTML = '<li class="empty-state">불러오는 중...</li>';

    try {
        const browseRooms = await Api.browseRooms();
        renderBrowseRoomList(browseRooms);
    } catch (e) {
        list.innerHTML = '<li class="empty-state">목록을 불러오지 못했습니다.</li>';
        showError(e.message);
    }
}

function toggleDirectField() {
    const type = document.getElementById('roomType').value;
    document.getElementById('targetUserIdGroup').style.display =
        type === 'DIRECT' ? 'block' : 'none';
}

async function init() {
    if (!Auth.requireAuth()) return;

    document.getElementById('userInfo').textContent =
        `${Auth.getNickname() || Auth.getUserId()} (${Auth.getRole() || 'USER'})`;

    document.getElementById('logoutBtn').addEventListener('click', () => Auth.logout());
    document.getElementById('createRoomBtn').addEventListener('click', () => openModal('createModal'));
    document.getElementById('joinRoomBtn').addEventListener('click', () => {
        resetJoinModal();
        openModal('joinModal');
    });
    document.getElementById('cancelCreateBtn').addEventListener('click', () => closeModal('createModal'));
    document.getElementById('cancelJoinBtn').addEventListener('click', () => closeModal('joinModal'));
    document.getElementById('browseAllRoomsBtn').addEventListener('click', loadBrowseRooms);
    document.getElementById('closeBrowseRoomBtn').addEventListener('click', closeBrowseRoomPanel);
    document.getElementById('roomType').addEventListener('change', toggleDirectField);

    document.getElementById('createForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        const type = document.getElementById('roomType').value;
        const body = {
            roomType: type,
            name: document.getElementById('roomName').value.trim()
        };
        if (type === 'DIRECT') {
            body.targetUserId = Number(document.getElementById('targetUserId').value);
        }
        try {
            const room = await Api.createRoom(body);
            closeModal('createModal');
            document.getElementById('createForm').reset();
            toggleDirectField();
            goToRoom(room.id);
        } catch (err) {
            showError(err.message);
        }
    });

    document.getElementById('joinForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        const roomId = Number(document.getElementById('joinRoomId').value);
        try {
            await Api.joinRoom(roomId);
            closeModal('joinModal');
            goToRoom(roomId);
        } catch (err) {
            showError(err.message);
        }
    });

    toggleDirectField();
    await loadRooms();
}

init();
