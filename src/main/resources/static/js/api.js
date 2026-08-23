const Api = {
    async request(url, options = {}) {
        const response = await fetch(url, {
            ...options,
            headers: { ...Auth.headers(), ...options.headers }
        });

        if (response.status === 401) {
            location.href = '/login.html';
            throw new Error('Unauthorized');
        }

        if (response.status === 204) {
            return null;
        }

        const data = await response.json().catch(() => ({}));
        if (!response.ok) {
            throw new Error(data.message || 'Request failed');
        }
        return data;
    },

    getRooms() {
        return this.request('/api/chat-rooms');
    },

    browseRooms() {
        return this.request('/api/chat-rooms/browse');
    },

    getRoom(roomId) {
        return this.request('/api/chat-rooms/' + roomId);
    },

    createRoom(body) {
        return this.request('/api/chat-rooms', {
            method: 'POST',
            body: JSON.stringify(body)
        });
    },

    joinRoom(roomId) {
        return this.request('/api/chat-rooms/' + roomId + '/participants', {
            method: 'POST'
        });
    },

    leaveRoom(roomId) {
        return this.request('/api/chat-rooms/' + roomId + '/participants/me', {
            method: 'DELETE'
        });
    },

    getMessages(roomId, cursor, size = 50) {
        let url = '/api/chat-rooms/' + roomId + '/messages?size=' + size;
        if (cursor) {
            url += '&cursor=' + cursor;
        }
        return this.request(url);
    }
};
