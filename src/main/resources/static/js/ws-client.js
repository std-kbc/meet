const WsClient = {
    stompClient: null,
    connected: false,
    subscription: null,
    onMessage: null,
    onStatusChange: null,

    connect(onMessage, onStatusChange) {
        this.onMessage = onMessage;
        this.onStatusChange = onStatusChange;

        if (this.stompClient && this.connected) {
            this._notifyStatus(true);
            return Promise.resolve();
        }

        return new Promise((resolve, reject) => {
            const token = Auth.getToken();
            const socket = new SockJS('/ws?token=' + encodeURIComponent(token));
            this.stompClient = Stomp.over(socket);
            this.stompClient.debug = () => {};

            this.stompClient.connect({}, () => {
                this.connected = true;
                this._notifyStatus(true);
                resolve();
            }, (error) => {
                this.connected = false;
                this._notifyStatus(false);
                reject(error);
            });
        });
    },

    subscribe(roomId) {
        this.unsubscribe();
        if (!this.stompClient || !this.connected) {
            return;
        }
        this.subscription = this.stompClient.subscribe('/sub/rooms/' + roomId, (message) => {
            if (this.onMessage) {
                this.onMessage(message.body);
            }
        });
    },

    sendMessage(roomId, content) {
        if (!this.stompClient || !this.connected) {
            throw new Error('WebSocket not connected');
        }
        this.stompClient.send(
            '/pub/rooms/' + roomId + '/messages',
            {},
            JSON.stringify({ message: content })
        );
    },

    unsubscribe() {
        if (this.subscription) {
            this.subscription.unsubscribe();
            this.subscription = null;
        }
    },

    disconnect() {
        this.unsubscribe();
        if (this.stompClient && this.connected) {
            this.stompClient.disconnect(() => {
                this.connected = false;
                this._notifyStatus(false);
            });
        }
        this.stompClient = null;
        this.connected = false;
    },

    _notifyStatus(connected) {
        if (this.onStatusChange) {
            this.onStatusChange(connected);
        }
    }
};

window.addEventListener('beforeunload', () => WsClient.disconnect());
