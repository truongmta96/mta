# MTA Mobile

Hệ thống cửa hàng điện thoại với thanh toán sandbox: Java 17 / Spring Boot, React, MySQL 8.4, Redis, Kafka và Docker.

## Chạy nhanh

```bash
docker compose up -d --build
# Giao diện: cổng 5173; API: cổng 8080, chỉ bind loopback.
python3 scripts/smoke.py
```

Yêu cầu Docker Compose v2, khoảng 10 GB đĩa trống cho images và dữ liệu benchmark. Mật khẩu trong Compose chỉ dùng phát triển; đổi và cấu hình secret trước khi triển khai thực tế. Compose bật tài khoản phát triển ADMIN/CUSTOMER; xem [đăng nhập và phân quyền](docs/authentication.md). Không dùng mật khẩu mẫu khi triển khai công khai.

Trong cloud được cấp session proxy và `CODEX_PROXY_CERT`, chạy `bash scripts/build-cloud.sh` rồi `docker compose up -d`. Script truyền CA và Maven proxy settings qua BuildKit secrets, giữ xác minh TLS. Ngoài cloud có thể dùng lệnh build thông thường. Xem `docs/development.md`.

## Những gì đã có

- Giao diện responsive cho khách/admin; quản trị thêm/sửa/xóa mềm/khôi phục sản phẩm, tài khoản, khách hàng; xem và cập nhật vòng đời đơn.
- Giá lấy từ database; đặt hàng có validation, idempotency theo khách, cập nhật tồn kho có điều kiện, transaction tạo đơn/reservation/outbox.
- Worker tự hết hạn đơn sau 15 phút và trả kho; publisher gửi outbox sang Kafka theo at-least-once.
- Flyway V1–V7: 19 bảng nghiệp vụ, khóa ngoại, check constraints, index tìm kiếm và partition tháng cho audit.
- Flash sale nhiều SKU: lịch mở bán, giá ưu đãi, quota và tối đa 1 sản phẩm/tài khoản/toàn chiến dịch; kiểm soát đồng thời bằng transaction.
- Thanh toán sandbox, webhook HMAC có chống trùng/kiểm tra số tiền/thời gian, yêu cầu và duyệt hoàn toàn bộ, trả kho/quota đúng trạng thái.
- Hồ sơ user có sửa thông tin/xóa mềm tài khoản bằng mật khẩu; quản trị bảo vệ admin cuối cùng và thu hồi phiên khi thay đổi tài khoản.
- Catalog 6 điện thoại mẫu; tài khoản ADMIN/CUSTOMER phát triển được tạo với mật khẩu băm BCrypt khi bật cấu hình demo.

## Phạm vi hiện tại

Các màn hình trong bản khởi tạo đã được triển khai với API lưu dữ liệu. Theo lựa chọn của bạn, thanh toán và hoàn tiền dùng **sandbox**, không thu/chuyển tiền thật. Mở [hướng dẫn workflow](docs/commerce.md) để xem thao tác quản trị, flash sale, thanh toán, hoàn tiền, xóa mềm và cách nâng cấp không mất dữ liệu.

Hiện mỗi đơn gồm một SKU với số lượng lựa chọn; chưa có giỏ nhiều SKU, phí giao hàng hoặc hoàn tiền một phần. Session ở RAM một API instance; chưa có MFA/reset mật khẩu/rate limit. API và worker chạy chung ứng dụng; Kafka outbox at-least-once, chưa có consumer nghiệp vụ/DLQ. Redis cache metadata sản phẩm/chiến dịch và fallback MySQL khi lỗi; tồn kho/quota vẫn đọc DB. Chưa đánh giá tải production.

## SQL và dữ liệu lớn

- `database/schema.sql`: tạo database `mta` và schema mới thủ công cho database trống. `database/upgrade_from_v2.sql` nâng schema cài thủ công từ V2; không chạy các file này nếu Flyway đang quản lý database.
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

Sau khi build lại, tài khoản phát triển mặc định: **admin@mta.local / Admin@12345** và **user@mta.local / User@12345**. Bấm Đăng nhập hoặc Quản trị trên giao diện. Xem [ma trận quyền, cấu hình và API](docs/authentication.md). User chỉ được xem/đặt đơn của mình; admin quản lý dữ liệu, đơn, sale và yêu cầu hoàn tiền. Chạy `py scripts/auth-smoke.py` trên Windows để kiểm tra phân quyền.

## Kiểm thử workflow trên Windows

```powershell
py scripts/commerce-smoke.py
# Tùy chọn khi có Playwright/Chromium:
py scripts/commerce-ui-smoke.py
```

Các kiểm tra tạo dữ liệu thử riêng và không xóa lịch sử. Xem [docs/commerce.md](docs/commerce.md) để kiểm tra thêm webhook HTTP có chữ ký và mô phỏng hết hạn với Docker. Không cần xóa volume khi cập nhật; Flyway tự áp dụng migration bổ sung.

## Cấu trúc backend

Backend đã tách thành **Controller → Service interface → ServiceImpl → Repository**. SQL chỉ nằm trong `repository/`; nghiệp vụ/transaction nằm trong `service/impl/`; DTO request nằm trong `dto/`. Scheduler, bootstrap và xử lý session cũng được tách riêng. Xem [hướng dẫn kiến trúc backend](docs/backend-architecture.md) và kiểm tra `LayerArchitectureTest` chạy khi `mvn test/package`.

## Redis fallback

Đã có cache-aside cho metadata sản phẩm và sale: Redis lỗi/timeout hoặc JSON hỏng thì tự đọc MySQL. Timeout mặc định 200 ms, backoff 5 giây, TTL 10 giây; xóa cache sau commit quản trị. Xem [hướng dẫn và kiểm thử Redis fallback](docs/cache-fallback.md). Trên Windows chạy `py scripts/cache-fallback-smoke.py`; thêm `--outages` chỉ trong môi trường phát triển để kiểm tra stop Redis và timeout thật. Không cần xóa database hoặc volume.
