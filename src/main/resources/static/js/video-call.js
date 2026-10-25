const VideoCall = {
    roomId: null,
    partnerId: null,
    partnerName: null,
    state: 'IDLE', // IDLE | CALLING(내가 거는 중) | RINGING(나에게 오는 중) | IN_CALL

    init(roomId, partnerId, partnerName) {
        this.roomId = roomId;
        this.partnerId = partnerId;
        this.partnerName = partnerName;

        WsClient.subscribeUserQueue('/user/queue/rtc', (msg) => this.onSignal(msg));
        WsClient.subscribeUserQueue('/user/queue/errors', (err) => this.onError(err));

        document.getElementById('callBtn').addEventListener('click', () => this.startCall());
        document.getElementById('acceptCallBtn').addEventListener('click', () => this.accept());
        document.getElementById('rejectCallBtn').addEventListener('click', () => this.reject());
        document.getElementById('cancelCallBtn').addEventListener('click', () => this.cancel());
        document.getElementById('hangUpBtn').addEventListener('click', () => this.hangUp());

        this.setState('IDLE');
    },

    // ── 버튼 동작 ──
    startCall() {
        this.setState('CALLING', `${this.partnerName}님에게 거는 중...`);
        this.send('CALL_REQUEST');
    },

    accept() {
        this.setState('IN_CALL', `${this.partnerName}님과 통화 중`);
        this.send('CALL_ACCEPTED');
    },

    reject() {
        this.send('CALL_REJECTED');
        this.setState('IDLE');
    },

    cancel() {
        this.send('CALL_CANCEL');
        this.setState('IDLE');
    },

    hangUp() {
        this.send('HANG_UP');
        this.endCall();
    },

    // ── 서버에서 받은 메시지 ──
    onSignal(msg) {
        if (msg.fromUserId !== this.partnerId) {
            return; // 이 방의 상대가 아닌 사람의 통화는 이 화면에서 처리하지 않음
        }
        switch (msg.type) {
            case 'CALL_REQUEST':
                this.setState('RINGING', `${this.partnerName}님의 영상통화 요청`);
                break;
            case 'CALL_ACCEPTED':
                this.setState('IN_CALL', `${this.partnerName}님과 통화 중`);
                break;
            case 'CALL_REJECTED':
                this.endCall(`${this.partnerName}님이 통화를 거절했습니다`);
                break;
            case 'CALL_BUSY':
                this.endCall(`${this.partnerName}님은 다른 통화 중입니다`);
                break;
            case 'CALL_CANCEL':
                this.endCall(`${this.partnerName}님이 통화를 취소했습니다`);
                break;
            case 'CALL_ANSWERED_ELSEWHERE':
                this.endCall('다른 탭에서 통화를 받았습니다');
                break;
            case 'HANG_UP':
                this.endCall(`${this.partnerName}님이 통화를 종료했습니다`);
                break;
        }
    },

    onError(err) {
        showError(err.message);
        if (this.state === 'CALLING') {
            this.setState('IDLE');
        }
    },

    // ── 공통 ──
    send(type, extra = {}) {
        WsClient.sendRtc(this.roomId, { targetUserId: this.partnerId, type, ...extra });
    },

    endCall(notice) {
        this.setState('IDLE');
        if (notice) {
            showError(notice);
        }
    },

    setState(state, statusText = '') {
        this.state = state;
        const show = (id, visible) => { document.getElementById(id).hidden = !visible; };
        show('callBtn', state === 'IDLE');
        show('callPanel', state !== 'IDLE');
        show('acceptCallBtn', state === 'RINGING');
        show('rejectCallBtn', state === 'RINGING');
        show('cancelCallBtn', state === 'CALLING');
        show('hangUpBtn', state === 'IN_CALL');
        show('callVideos', state === 'IN_CALL');
        document.getElementById('callStatus').textContent = statusText;
    }
};
