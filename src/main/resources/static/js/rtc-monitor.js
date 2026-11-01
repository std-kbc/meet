// WebRTC 연결 과정과 전송량을 화면에 표시 (학습·디버깅용)
const RtcMonitor = {
    pc: null,
    timer: null,
    last: null,   // 직전 측정값 { time, sent, received } → 실시간 속도 계산
    pairId: null, // 현재 채택된 candidate pair id → 바뀌면 로그

    init({ myId, myName, partnerId, partnerName }) {
        document.getElementById('rtcMonitor').hidden = false;
        this.setText('rtcMe', `${myName} (#${myId})`);
        this.setText('rtcPartner', `${partnerName} (#${partnerId})`);
    },

    // ── 시그널 로그 ──
    log(direction, type, payload = {}) {
        let detail = '';
        if (type === 'NEW_ICE_CANDIDATE' && payload.ice) {
            detail = this.describeCandidate(JSON.parse(payload.ice).candidate);
        } else if (payload.sdp) {
            detail = `SDP ${payload.sdp.length}자`;
        }
        const title = `${direction === 'send' ? '↑ 보냄' : '↓ 받음'} ${type}`;
        this.appendLog(direction, detail ? `${title} · ${detail}` : title);
    },

    note(text) {
        this.appendLog('note', text);
    },

    appendLog(kind, text) {
        const time = new Date().toLocaleTimeString('ko-KR', { hour12: false });
        const item = document.createElement('li');
        item.className = `rtc-log-${kind}`;
        item.textContent = `${time} ${text}`;
        const list = document.getElementById('rtcLog');
        list.appendChild(item);
        list.scrollTop = list.scrollHeight;
    },

    // "candidate:1 1 udp 1677729535 203.0.113.7 54321 typ srflx raddr ..." → "STUN(srflx) udp 203.0.113.7:54321"
    describeCandidate(candidate) {
        if (!candidate) {
            return '후보 수집 완료';
        }
        const parts = candidate.split(' ');
        const type = parts[parts.indexOf('typ') + 1];
        return `${this.typeLabel(type)} ${parts[2]} ${parts[4]}:${parts[5]}`;
    },

    typeLabel(type) {
        const labels = { host: '로컬(host)', srflx: 'STUN(srflx)', prflx: 'P2P(prflx)', relay: 'TURN(relay)' };
        return labels[type] || type;
    },

    // ── PeerConnection 연결 상태 / 전송량 ──
    attach(pc) {
        this.pc = pc;
        this.last = null;
        this.pairId = null;
        this.note('── PeerConnection 생성 ──');

        const showStates = () => {
            this.setText('rtcStates',
                `signaling ${pc.signalingState} · ice ${pc.iceConnectionState} · 연결 ${pc.connectionState}`);
        };
        showStates();
        pc.addEventListener('signalingstatechange', showStates);
        pc.addEventListener('iceconnectionstatechange', showStates);
        pc.addEventListener('icegatheringstatechange', () => this.note(`ICE 후보 수집: ${pc.iceGatheringState}`));
        pc.addEventListener('connectionstatechange', () => {
            showStates();
            this.note(`연결 상태: ${pc.connectionState}`);
        });

        this.timer = setInterval(() => this.updateStats(pc), 1000);
    },

    detach() {
        if (!this.pc) {
            return;
        }
        clearInterval(this.timer);
        if (this.last) {
            this.note(`── 통화 종료 · 보냄 ${this.formatBytes(this.last.sent)} · 받음 ${this.formatBytes(this.last.received)} ──`);
        }
        this.pc = null;
        this.timer = null;
        this.last = null;
        this.pairId = null;
        this.setText('rtcStates', '통화 없음');
    },

    async updateStats(pc) {
        const stats = await pc.getStats();
        if (pc !== this.pc) {
            return; // 기다리는 사이에 통화가 끝남
        }
        const pair = this.selectedPair(stats);
        if (!pair) {
            return; // 아직 연결 경로가 정해지지 않음
        }
        const local = stats.get(pair.localCandidateId);
        const remote = stats.get(pair.remoteCandidateId);

        this.setText('rtcLocal', this.describeStatsCandidate(local));
        this.setText('rtcRemote', this.describeStatsCandidate(remote));
        this.setText('rtcRtt', pair.currentRoundTripTime != null
            ? `${Math.round(pair.currentRoundTripTime * 1000)} ms` : '-');

        if (pair.id !== this.pairId) {
            this.pairId = pair.id;
            this.note(`연결 경로 채택: ${this.typeLabel(local.candidateType)} ↔ ${this.typeLabel(remote.candidateType)}`);
        }

        // 실시간 속도 = (이번 누적 - 직전 누적) ÷ 경과 시간
        let sendRate = 0;
        let receiveRate = 0;
        if (this.last) {
            const seconds = (pair.timestamp - this.last.time) / 1000;
            if (seconds > 0) {
                sendRate = (pair.bytesSent - this.last.sent) * 8 / seconds;
                receiveRate = (pair.bytesReceived - this.last.received) * 8 / seconds;
            }
        }
        this.last = { time: pair.timestamp, sent: pair.bytesSent, received: pair.bytesReceived };

        this.setText('rtcSent', `${this.formatRate(sendRate)} · 누적 ${this.formatBytes(pair.bytesSent)}`);
        this.setText('rtcReceived', `${this.formatRate(receiveRate)} · 누적 ${this.formatBytes(pair.bytesReceived)}`);
    },

    // 실제로 쓰이는 candidate pair (Chrome: transport.selectedCandidatePairId, Firefox: pair.selected)
    selectedPair(stats) {
        let pair = null;
        stats.forEach((report) => {
            if (report.type === 'transport' && report.selectedCandidatePairId) {
                pair = stats.get(report.selectedCandidatePairId);
            }
        });
        if (!pair) {
            stats.forEach((report) => {
                if (report.type === 'candidate-pair' && report.selected) {
                    pair = report;
                }
            });
        }
        return pair;
    },

    describeStatsCandidate(candidate) {
        if (!candidate) {
            return '-';
        }
        const address = candidate.address || candidate.ip;
        return `${this.typeLabel(candidate.candidateType)} ${candidate.protocol} ${address}:${candidate.port}`;
    },

    // ── 표시 형식 ──
    formatBytes(bytes) {
        if (bytes >= 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
        if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
        if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KB`;
        return `${bytes} B`;
    },

    formatRate(bitsPerSecond) {
        if (bitsPerSecond >= 1000 * 1000) return `${(bitsPerSecond / 1000 / 1000).toFixed(2)} Mbps`;
        return `${Math.round(bitsPerSecond / 1000)} kbps`;
    },

    setText(id, text) {
        document.getElementById(id).textContent = text;
    }
};
