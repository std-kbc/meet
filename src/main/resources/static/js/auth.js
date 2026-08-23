const Auth = {
    getToken() {
        return localStorage.getItem('accessToken');
    },

    getUserId() {
        return localStorage.getItem('userId');
    },

    getNickname() {
        return localStorage.getItem('nickname');
    },

    getRole() {
        return localStorage.getItem('role');
    },

    requireAuth() {
        if (!this.getToken()) {
            location.href = '/login.html';
            return false;
        }
        return true;
    },

    headers() {
        return {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + this.getToken()
        };
    },

    async logout() {
        try {
            await fetch('/api/auth/logout', {
                method: 'POST',
                headers: this.headers()
            });
        } finally {
            localStorage.removeItem('accessToken');
            localStorage.removeItem('refreshToken');
            localStorage.removeItem('userId');
            localStorage.removeItem('nickname');
            localStorage.removeItem('role');
            location.href = '/login.html';
        }
    }
};
