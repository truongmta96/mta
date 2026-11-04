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
