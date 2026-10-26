const VideoCall = {
    roomId: null,
    partnerId: null,
    partnerName: null,
    state: 'IDLE', // IDLE | CALLING(내가 거는 중) | RINGING(나에게 오는 중) | IN_CALL

    // [4-2] 영상 연결 상태
    pc: null,                 // RTCPeerConnection
    localStream: null,        // 내 카메라·마이크
    queue: Promise.resolve(), // 영상 관련 작업을 도착 순서대로 하나씩 처리

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
        this.enqueue(() => this.preparePeer()); // [4-2] OFFER가 오기 전에 카메라·연결 준비
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
                this.enqueue(() => this.startOffer()); // [4-2] 건 사람이 OFFER 시작
                break;
            case 'VIDEO_OFFER':
            case 'VIDEO_ANSWER':
            case 'NEW_ICE_CANDIDATE':
                this.enqueue(() => this.onMediaSignal(msg)); // [4-2]
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

    // ── [4-2] 영상 연결 ──
    enqueue(task) {
        this.queue = this.queue.then(task).catch((e) => this.failMedia(e));
    },

    async preparePeer() {
        const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
        const ice = await Api.getIceServers();

        // 준비하는 사이에 통화가 끝났으면 카메라만 끄고 중단
        if (this.state !== 'IN_CALL') {
            stream.getTracks().forEach(track => track.stop());
            return;
        }

        this.localStream = stream;
        document.getElementById('localVideo').srcObject = stream;

        this.pc = new RTCPeerConnection({ iceServers: ice.iceServers });
        stream.getTracks().forEach(track => this.pc.addTrack(track, stream));

        this.pc.onicecandidate = (event) => {
            if (event.candidate) {
                this.send('NEW_ICE_CANDIDATE', { ice: JSON.stringify(event.candidate) });
            }
        };
        this.pc.ontrack = (event) => {
            document.getElementById('remoteVideo').srcObject = event.streams[0];
        };
        this.pc.onconnectionstatechange = () => {
            if (this.pc && this.pc.connectionState === 'failed') {
                this.hangUp();
                showError('영상 연결에 실패했습니다');
            }
        };
    },

    async startOffer() {
        await this.preparePeer();
        if (!this.pc) {
            return;
        }
        const offer = await this.pc.createOffer();
        await this.pc.setLocalDescription(offer);
        this.send('VIDEO_OFFER', { sdp: offer.sdp });
    },

    async onMediaSignal(msg) {
        if (!this.pc) {
            return; // 이미 끝난 통화의 늦게 도착한 메시지
        }
        switch (msg.type) {
            case 'VIDEO_OFFER': {
                await this.pc.setRemoteDescription({ type: 'offer', sdp: msg.sdp });
                const answer = await this.pc.createAnswer();
                await this.pc.setLocalDescription(answer);
                this.send('VIDEO_ANSWER', { sdp: answer.sdp });
                break;
            }
            case 'VIDEO_ANSWER':
                await this.pc.setRemoteDescription({ type: 'answer', sdp: msg.sdp });
                break;
            case 'NEW_ICE_CANDIDATE':
                await this.pc.addIceCandidate(JSON.parse(msg.ice));
                break;
        }
    },

    failMedia(e) {
        console.error(e);
        if (this.state !== 'IN_CALL') {
            return;
        }
        if (e.name === 'NotAllowedError') {
            showError('카메라·마이크 권한이 거부되었습니다');
        } else if (e.name === 'NotFoundError') {
            showError('카메라·마이크를 찾을 수 없습니다');
        } else {
            showError('영상 연결 중 오류가 발생했습니다');
        }
        this.hangUp();
    },

    closePeer() {
        if (this.pc) {
            this.pc.onicecandidate = null;
            this.pc.ontrack = null;
            this.pc.onconnectionstatechange = null;
            this.pc.close();
            this.pc = null;
        }
        if (this.localStream) {
            this.localStream.getTracks().forEach(track => track.stop());
            this.localStream = null;
        }
        document.getElementById('localVideo').srcObject = null;
        document.getElementById('remoteVideo').srcObject = null;
        this.queue = Promise.resolve();
    },

    // ── 공통 ──
    send(type, extra = {}) {
        WsClient.sendRtc(this.roomId, { targetUserId: this.partnerId, type, ...extra });
    },

    endCall(notice) {
        this.closePeer(); // [4-2]
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
