# Kết quả kiểm chứng — 04/11/2026

## Đã chạy và đạt

- Frontend: `npm install` tạo lockfile; `npm run build` thành công.
- Backend: `mvn package` bằng Maven 3.9.9 / Eclipse Temurin JDK 17 thành công. Chưa có suite JUnit; Maven package không được tính là kiểm thử nghiệp vụ.
- Docker: backend và frontend multi-stage build thành công; build qua session proxy dùng CA và Maven settings dạng BuildKit secrets, không tắt xác minh TLS.
- MySQL 8.4: Flyway áp dụng 2 migrations, 17 bảng nghiệp vụ; khởi động lại xác nhận schema up to date.
- Redis: PING → PONG. Kafka: topic `shop.events` 3 partitions; console consumer nhận `OrderCreated` chứa eventId/orderId từ outbox.
- `scripts/smoke.py`: health/database, 6 SKU, idempotency trả cùng đơn, khóa trùng khác nội dung trả 409, số lượng 0 trả 400, 8 request đồng thời giữ kho đúng số lượng; thanh toán chưa triển khai trả 501. Chạy trên Java 21 rồi xác minh lại trên Java 17.
- Thử tranh mua: tạm giới hạn SKU mẫu còn 3 sản phẩm, 20 request đồng thời → 3 thành công, 17 trả 409. Sau đó phục hồi tồn mẫu. Đây là kiểm tra tính đúng đắn với customer demo, chưa phải kiểm thử tải nhiều người dùng.
- Worker: cho 9 đơn thử quá hạn, xác nhận EXPIRED=9, reserved của SKU thử trở về 0, pending outbox=0.
- Chromium/Playwright: 6 thẻ sản phẩm, lọc Samsung còn 2, mở form đặt hàng, xem đơn, bảng quản trị 6 SKU; viewport mobile 390px không tràn ngang; không có pageerror. Ảnh tại `ui-desktop.png` và `ui-mobile.png`.

## Dữ liệu lớn đã nạp thực tế

Database `mta_benchmark` nằm trong Docker volume `mta_mysql_data`, tách database ứng dụng `mta`.

| Bảng | Số dòng |
|---|---:|
| accounts | 100.001 |
| customers | 100.001 |
| orders | 500.000 |
| order_items | 500.000 |
| payment_attempts | 500.000 |
| audit_logs | 2.000.000 |
| Tổng 6 bảng | 3.700.002 |

Không commit dump hàng triệu dòng; dùng script SQL tái tạo. FK/CHECK và xác minh artifact/TLS giữ hoạt động.

## Kế hoạch truy vấn

Kết quả gốc: `explain-results.txt`.

- Lịch sử khách: index `ix_order_customer`, trả 5 dòng cho khách mẫu.
- Worker đơn hết hạn: covering range scan bằng `ix_order_expiry`. Bộ dữ liệu lịch sử không có đơn pending, nên kết quả này chỉ chứng minh index được chọn, chưa đo tải hàng đợi lớn.
- Audit tháng 6: chỉ partition `p2026_06`, index `ix_audit_actor`.

Các thời gian EXPLAIN trên máy phát triển không phải cam kết hiệu năng production. Chưa tích hợp hoặc kiểm chứng login, phân quyền, cổng thanh toán, quota flash sale hay khả năng phục hồi sự cố.

## Kiểm tra cuối bằng Compose

Cả 5 dịch vụ `api`, `web`, `mysql`, `redis`, `kafka` đang chạy từ cấu hình Compose. Backend runtime Eclipse Temurin 17; frontend phục vụ bằng Nginx. `scripts/check-ready.py` xác minh đường đi Nginx → API → MySQL; UI smoke chạy lại thành công trên bản Docker. Kết thúc kiểm tra có 21 đơn thử EXPIRED và tổng reserved=0; tồn mẫu đã được phục hồi.

Đã lưu draft `install_script` gọi `scripts/build-cloud.sh` và `start_skill` hướng dẫn khởi động/kiểm tra, giữ checkout hiện tại và không nạp lại database benchmark. Draft chưa được Publish; chưa kiểm chứng khôi phục ở task mới.

## Bổ sung login và phân quyền — 05/11/2026

Các kết quả ngày 04/11 ở trên là lịch sử phiên bản chưa có authentication. Phiên bản mới đã bổ sung Spring Security và các trang login user/admin; không còn dùng customer ID 1 cố định.

- Backend Docker build bằng Java 17; frontend Vite production build thành công, chạy qua Nginx.
- `scripts/auth-smoke.py`: khách bị chặn 401; sai mật khẩu bị từ chối; login/register/POST bắt buộc CSRF; token cũ trước login bị từ chối; cookie HttpOnly và session đổi ID sau login; đăng ký luôn CUSTOMER dù client gửi role ADMIN; user nhận 403 với mọi API quản trị; admin truy cập được 5 nhóm API quản trị; không trả password_hash.
- Tạo hai khách thử riêng: đơn thuộc khách tạo, khách còn lại không thấy trong danh sách và nhận 404 khi gọi thanh toán đơn đó. Đăng xuất hủy phiên; request cũ không đọc được đơn nữa.
- `scripts/smoke.py` đã dùng login trước khi kiểm tra đặt hàng/idempotency/validation/giữ kho đồng thời và thanh toán chưa triển khai.
- `scripts/ui-smoke.py`: khách không thấy menu quản lý; nhập trực tiếp route admin bị chặn; chọn sai khu đăng nhập bị báo lỗi; user đăng nhập thấy menu đơn/hồ sơ, tải lại vẫn giữ phiên; admin xem được bảng sản phẩm, tài khoản, khách hàng; logout thành công; mobile 390px không tràn ngang; không có JavaScript pageerror.
- `scripts/auth-revocation-smoke.py`: khóa/xóa mềm/đổi role của tài khoản thử làm request kế tiếp mất quyền, login bằng tài khoản khóa/xóa bị chặn; trạng thái tài khoản thử được phục hồi sau kiểm tra.
- Không sửa migration V1/V2 và không reset database/volume. DemoAccounts tạo mới email admin/user khi cấu hình bật, không ghi đè mật khẩu tài khoản có sẵn.

Giới hạn: quản trị hiện chỉ đọc; chưa có reset mật khẩu/MFA/rate limit đăng nhập; session lưu trong bộ nhớ một instance; chưa tích hợp thanh toán hoặc quota flash sale. Xem `authentication.md` để chạy lại và cấu hình triển khai.
