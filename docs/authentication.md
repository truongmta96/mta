# Đăng nhập và phân quyền

## Chạy trên Windows

```powershell
docker compose up -d --build
py scripts/check-ready.py
py scripts/auth-smoke.py
py scripts/smoke.py
```

Không cần chạy build-cloud.sh trên Windows. Script đó chỉ dành cho session proxy của môi trường cloud.

Compose bật tạo hai tài khoản **chỉ dành cho phát triển** khi chưa có email tương ứng:

| Loại | Email | Mật khẩu mẫu | Vai trò |
|---|---|---|---|
| Quản trị viên | admin@mta.local | Admin@12345 | ADMIN |
| Khách hàng | user@mta.local | User@12345 | CUSTOMER |

Chọn **Đăng nhập** ở đầu trang để dùng tài khoản khách hàng, hoặc **Quản trị** để tới trang đăng nhập admin. Không cần chọn role khi đăng ký; tài khoản đăng ký luôn là CUSTOMER. Có thể đăng ký hai user khác nhau để kiểm tra lịch sử đơn riêng.

Nếu chạy backend ngoài Compose: bật `APP_DEMO_ACCOUNTS_ENABLED=true` và cung cấp `APP_DEMO_ADMIN_PASSWORD`, `APP_DEMO_USER_PASSWORD` để tạo mẫu. Trong application.yml, tính năng này mặc định tắt. Có thể đặt hai biến mật khẩu qua môi trường/.env khi chạy Compose. Seeder không đổi mật khẩu, role hoặc trạng thái của tài khoản đã tồn tại; đổi biến môi trường không reset mật khẩu cũ.

**Không xóa database/volume khi cập nhật.** Dùng lại schema hiện có; mật khẩu được băm BCrypt. Tài khoản demo@mta.local từ migration cũ vẫn bị vô hiệu hóa đăng nhập và các đơn cũ của nó không tự chuyển sang user mới.

## Ma trận quyền

| Màn hình / API | Chưa đăng nhập | CUSTOMER | ADMIN |
|---|---|---|---|
| Cửa hàng, GET products/campaigns/health | Có | Có | Có |
| Đăng nhập, đăng ký khách hàng | Có | Có | Có |
| GET auth/me | 401 | Hồ sơ tài khoản hiện tại | Hồ sơ tài khoản hiện tại |
| Đặt hàng, xem đơn, xem hồ sơ khách | 401 | Chỉ dữ liệu của mình | 403 |
| Khu quản trị: sản phẩm, đơn, tài khoản, khách hàng, sale | 401 | 403 | Có |

React chỉ hiển thị menu phù hợp và chặn màn hình khi nhập trực tiếp hash URL. Spring Security kiểm tra độc lập trên mọi API; sửa JavaScript/localStorage hoặc tự gửi role/customerId không cấp thêm quyền.

Khu quản trị hiện là các màn hình **xem dữ liệu**, mỗi màn hình tối đa 100 bản ghi. Các thao tác thêm/sửa/xóa/phân quyền qua giao diện chưa triển khai. Không gửi password_hash qua các API quản trị. Flash sale và thanh toán thật vẫn chưa triển khai.

## Cơ chế session và CSRF

- `POST /api/auth/login`: email, password và portal tùy chọn ADMIN/CUSTOMER. Portal chỉ giới hạn khu vực đăng nhập, không thay đổi quyền. Sai portal trả 403 trước khi lưu phiên.
- `GET /api/auth/me`: trả id/email/name/role; server tra cứu trạng thái và vai trò từ database.
- `POST /api/auth/register`: email, password (ít nhất 10 ký tự, tối đa 72 bytes UTF-8), name. Vai trò CUSTOMER do server đặt.
- `GET /api/auth/csrf`: trả token và headerName. Gửi header này cho POST, kể cả login, register và logout.
- `POST /api/auth/logout`: cần CSRF, hủy session và cookie đăng nhập.
- Cookie JSESSIONID là HttpOnly, SameSite=Lax; session đổi ID và đổi CSRF token khi đăng nhập, hết hạn sau 30 phút không hoạt động.
- Cookie mặc định chưa bật Secure để chạy local HTTP. Production HTTPS cần `SESSION_COOKIE_SECURE=true` và `APP_DEMO_ACCOUNTS_ENABLED=false` trong môi trường/.env dùng cho Compose. Tắt seeder không xóa hoặc khóa tài khoản mẫu đã tạo; phải xử lý các tài khoản đó riêng trước triển khai.
- Không lưu mật khẩu hay token đăng nhập trong localStorage. React khôi phục thông tin tài khoản qua /auth/me khi tải lại.
- Mỗi request có đăng nhập đều kiểm tra lại status/deleted_at/role. Tài khoản bị khóa/xóa hoặc thay role sẽ mất phiên hiện tại ở request kế tiếp.
- Session hiện nằm trong bộ nhớ một API instance. Restart API sẽ mất phiên; chạy nhiều instance cần bổ sung Spring Session Redis hoặc cơ chế tương đương. Redis hiện chưa lưu session.
- Chưa có khôi phục mật khẩu, MFA hay rate limit đăng nhập; cần bổ sung trước khi công khai hệ thống.

## Kiểm thử

`scripts/auth-smoke.py` dùng HTTP thật, cookie jar và CSRF. Script tạo hai tài khoản giả lập riêng và một đơn giữ kho để kiểm tra quyền; không xóa hay sửa dữ liệu có sẵn. Tài khoản admin demo cần được bật. Nếu mật khẩu demo đã đổi, đặt MTA_ADMIN_PASSWORD/MTA_USER_PASSWORD cho script tương ứng. Trong PowerShell dùng `$env:MTA_ADMIN_PASSWORD = '...'` (không commit mật khẩu thật).

`scripts/smoke.py` đã được cập nhật để đăng nhập user demo trước khi kiểm tra đặt hàng. `scripts/ui-smoke.py` kiểm tra đăng nhập, menu, refresh, route guard và logout trên trình duyệt; cần Python Playwright và Chromium. Không coi smoke test là kiểm thử chịu tải production.

Kiểm tra nâng cao: `py scripts/auth-revocation-smoke.py` cần Docker CLI truy cập Compose MySQL. Script tạo tài khoản thử riêng, lần lượt khóa/xóa mềm/đổi role qua SQL để xác minh phiên cũ bị thu hồi, rồi khôi phục trạng thái tài khoản thử. Không thay đổi các tài khoản có sẵn.
