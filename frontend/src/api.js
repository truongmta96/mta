// Cookie HttpOnly do server quản lý. Không lưu mật khẩu/token đăng nhập trong localStorage.
export async function api(path, options = {}) {
    const method = (options.method || 'GET').toUpperCase();
    const headers = {...options.headers};
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
        // Lấy token từ cùng origin; dùng được cả sau login/logout hoặc session hết hạn.
        const csrfResponse = await fetch('/api/auth/csrf', {credentials: 'same-origin'});
        if (!csrfResponse.ok) throw new Error('Không lấy được mã bảo vệ phiên. Vui lòng tải lại trang.');
        const csrf = await csrfResponse.json();
        headers[csrf.headerName] = csrf.token;
    }
    const response = await fetch('/api' + path, {...options, headers, credentials: 'same-origin'});
    if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        if (response.status === 401 && !path.startsWith('/auth/')) window.dispatchEvent(new Event('session-expired'));
        const error = new Error(body.detail || body.message || `Yêu cầu thất bại (${response.status})`);
        error.status = response.status;
        throw error;
    }
    return response.json();
}
