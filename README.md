# MTA Mobile

Bộ khung cửa hàng điện thoại: Java 17 / Spring Boot, React, MySQL 8.4, Redis, Kafka và Docker.

## Chạy nhanh

```bash
docker compose up -d --build
# Giao diện: cổng 5173; API: cổng 8080, chỉ bind loopback.
python3 scripts/smoke.py
```

Yêu cầu Docker Compose v2, khoảng 10 GB đĩa trống cho images và dữ liệu benchmark. Mật khẩu trong Compose chỉ dùng phát triển; đổi và cấu hình secret trước khi triển khai thực tế. Compose bật tài khoản phát triển ADMIN/CUSTOMER; xem [đăng nhập và phân quyền](docs/authentication.md). Không dùng mật khẩu mẫu khi triển khai công khai.

Trong cloud được cấp session proxy và `CODEX_PROXY_CERT`, chạy `bash scripts/build-cloud.sh` rồi `docker compose up -d`. Script truyền CA và Maven proxy settings qua BuildKit secrets, giữ xác minh TLS. Ngoài cloud có thể dùng lệnh build thông thường. Xem `docs/development.md`.

## Những gì đã có

- Giao diện responsive: danh sách điện thoại, tìm kiếm/lọc hãng, form tạo đơn, xem đơn, bảng tồn kho quản trị chỉ đọc.
- Giá lấy từ database; đặt hàng có validation, idempotency theo khách, cập nhật tồn kho có điều kiện, transaction tạo đơn/reservation/outbox.
- Worker tự hết hạn đơn sau 15 phút và trả kho; publisher gửi outbox sang Kafka theo at-least-once.
- Flyway migration: 17 bảng, khóa ngoại, check constraints, index và partition tháng cho audit.
- Catalog 6 điện thoại mẫu; tài khoản ADMIN/CUSTOMER phát triển được tạo với mật khẩu băm BCrypt khi bật cấu hình demo.

## Giới hạn bản khởi tạo

Đã có đăng nhập/đăng ký khách hàng, phân quyền ADMIN/CUSTOMER ở API và giao diện. Quản trị ghi dữ liệu, xóa mềm qua API, thanh toán/webhook/hoàn tiền và đặt flash sale chưa triển khai. Schema đã chuẩn bị dữ liệu tương ứng. API thanh toán trả 501; đặt SKU trong chiến dịch đang diễn ra trả 409 để không bán sai giá/quota. Banner sale là nội dung mẫu cố định; chưa đồng bộ chiến dịch qua API.

Hiện API và worker chạy chung ứng dụng. Chưa có Kafka consumer nghiệp vụ, dead-letter queue hay bảo đảm thứ tự khi chạy nhiều publisher. Consumer tương lai phải chống trùng theo event ID. Redis đã cấu hình và kiểm tra kết nối, chưa dùng cache tồn kho để tránh hiển thị sai trong bản đầu. Chưa kiểm thử tải production.

## SQL và dữ liệu lớn

- `database/schema.sql`: tạo database `mta` và toàn bộ bảng thủ công.
- `backend/src/main/resources/db/migration/`: Flyway tự tạo schema và catalog khi API khởi động. Chọn **một** cách khởi tạo database ứng dụng; không chạy schema.sql trước Flyway trên cùng database.
- `bash scripts/seed-large.sh`: tạo database riêng `mta_benchmark`, không can thiệp dữ liệu ứng dụng. Script từ chối khi database đã tồn tại; không tự xóa dữ liệu. Nếu lần nạp lỗi, xem log và xử lý database thử nghiệm có chủ đích trước khi chạy lại.
- `database/seed_large.sql`: sinh bằng SQL theo batch 10.000, transaction mỗi batch, giữ FK/CHECK hoạt động, không tải dump lớn vào Git.
- `database/explain.sql`: truy vấn kiểm tra index và partition pruning.

Số lượng mục tiêu: 100.001 accounts, 100.001 customers, 500.000 orders, 500.000 order_items, 500.000 payment_attempts, 2.000.000 audit_logs, cùng catalog nhỏ. Tổng 3.700.002 bản ghi ở 6 bảng chính. Dữ liệu hoàn toàn giả lập; email dùng example.invalid; mật khẩu bị vô hiệu hóa. Đơn lịch sử hoàn tất không giữ kho hiện tại. Dữ liệu đủ lớn để thử truy vấn, không mô phỏng đầy đủ hành vi phân phối tải thực tế.

## Quyết định index / partition

`orders(customer_id, created_at, id)` phục vụ lịch sử khách; `orders(status, expires_at, id)` phục vụ worker; `UNIQUE(customer_id,idempotency_key)` chống trùng. Outbox có `(published_at,id)`. Các FK có index tương ứng. Không thêm index mọi cột vì làm chậm ghi và tăng dung lượng.

MySQL không hỗ trợ FK trên bảng InnoDB partition. Vì vậy các bảng nghiệp vụ giữ nguyên FK và không partition. Chỉ `audit_logs` append-only không có FK được partition theo tháng. PK `(id,created_at)` tuân thủ quy tắc mọi unique key phải chứa cột partition; ID đơn lẻ không phải ràng buộc unique toàn cục của bảng audit. Truy vấn log cần có điều kiện thời gian để pruning. `p_future` giữ dữ liệu ngoài khoảng chuẩn bị; cần lịch REORGANIZE để bổ sung partition trước tháng mới. Không DROP partition nếu chưa có chính sách lưu trữ/backup.

## Kiểm thử

`npm ci && npm run build` trong frontend; `mvn package` với JDK 17 trong backend. `scripts/smoke.py` chạy thật với MySQL/backend và tạo 9 đơn thử nghiệm. Kết quả kiểm chứng của phiên dựng source được ghi tại `docs/validation.md`.

## Đọc cấu hình

Các file cấu hình có comment tiếng Việt. Xem [giải thích cấu hình](docs/configuration.md) để hiểu cách các file phối hợp và ý nghĩa các trường JSON không thể chèn comment trực tiếp.

## Đăng nhập

Sau khi build lại, tài khoản phát triển mặc định: **admin@mta.local / Admin@12345** và **user@mta.local / User@12345**. Bấm Đăng nhập hoặc Quản trị trên giao diện. Xem [ma trận quyền, cấu hình và API](docs/authentication.md). User chỉ được xem/đặt đơn của mình; admin xem các màn hình quản lý. Chạy `py scripts/auth-smoke.py` trên Windows để kiểm tra phân quyền.
