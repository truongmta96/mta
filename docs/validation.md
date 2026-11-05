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

## Hoàn thiện quản trị, flash sale và thanh toán sandbox — 05/11/2026

Các giới hạn giao diện/nghiệp vụ của hai phiên bản trước đã được triển khai theo phạm vi sandbox được người dùng chọn. Chi tiết: `commerce.md`.

- Java 17 Docker build và React/Vite build thành công. Flyway nâng database hiện có qua V3–V6, không sửa V1/V2 hoặc xóa volume.
- `commerce-smoke.py --database-checks` với khóa HMAC thử trùng cấu hình API: 4 khách tranh 3 suất → đúng 3 đơn; hạn mức mỗi khách được giữ; sale trùng lịch/chưa mở bị từ chối; không giảm tồn dưới lượng đang giữ.
- Thanh toán tạo lại trả cùng attempt PENDING; thất bại tạo attempt mới; callback thành công gửi lặp không giữ/trừ kho lần hai. Webhook HTTP sai chữ ký, sai amount, timestamp cũ và JSON null bị từ chối; event ID cùng payload idempotent, khác nội dung bị từ chối.
- Thanh toán đến sau khi hủy tự hoàn sandbox, không lấy lại kho. Đơn thử được đặt quá hạn qua SQL, worker thực sự chuyển EXPIRED và trả quota; callback đến sau đó ghi hoàn sandbox và không trả kho thêm lần nữa.
- Hoàn trước giao trả reserved/quota; chuyển trạng thái đúng PAID → PROCESSING → SHIPPED → COMPLETED. Hoàn sau giao bị từ chối khi chưa nhận hàng trả, sau xác nhận mới cộng on_hand lại. Duyệt lặp không cộng kho hai lần.
- Khách tự xóa bằng mật khẩu đúng làm hủy đơn chưa thanh toán, không xóa lịch sử trả tiền/hoàn. Admin khôi phục giữ nguyên ID/liên kết. Khóa, xóa mềm, khôi phục khách/tài khoản và bảo vệ admin đang dùng đã kiểm tra.
- Tạo/sửa/khóa admin mới lưu tên hiển thị và vô hiệu hóa phiên cũ; tìm tiền tố/phân trang server và page không hợp lệ đã kiểm tra.
- `commerce-ui-smoke.py`: Chromium thao tác tạo/sửa/xóa/khôi phục sản phẩm, tạo tài khoản, tạo sale từ giờ Việt Nam, user đăng ký → đặt sale → thanh toán sandbox → yêu cầu hoàn → admin duyệt → user sửa hồ sơ/xóa tài khoản. Viewport mobile 390px không tràn ngang, không pageerror.
- `ui-smoke.py`, `auth-smoke.py`, `smoke.py` đều chạy lại thành công với API mới.
- SQL schema và bản nâng cấp thủ công chứa đủ các migration hiện tại; XML hợp lệ, diff không có lỗi whitespace.

Ảnh: `ui-payment.png`, `ui-refunds.png`, `ui-sales-mobile.png`. Toàn bộ giao dịch mô phỏng; không thu/chuyển tiền thật và không coi kiểm thử này là đánh giá tải production.

## Tách tầng backend — 05/11/2026

- Sắp xếp thành controller, dto, service (interface), service/impl, repository, security, scheduler, bootstrap, config, common và exception.
- Có 9 service interface/implementation tương ứng; 12 repository JDBC với phương thức và tham số cụ thể. 136 chỗ gọi SQL được chuyển về repository; đối chiếu các câu SQL trước/sau không có câu bị mất hoặc thay đổi.
- Controller không còn SQL, JdbcTemplate, dependency ServiceImpl/repository hoặc transaction nghiệp vụ. DTO request tách thành file riêng.
- Transaction chi tiết quản trị chuyển từ controller xuống OrderServiceImpl; luồng sandbox complete có transaction ngoài để không mất transaction do self-invocation. Các thao tác vòng đời đơn yêu cầu transaction hiện có.
- Java 17 Docker build thành công; JUnit LayerArchitectureTest thực thi 5 kiểm tra, 0 failures/errors/skipped. Không thêm dependency test ngoài bộ JUnit sẵn có.
- Không đổi frontend, URL API, cấu trúc schema/migration hoặc cấu hình Compose. Source Java được định dạng lại và dùng import rõ tên lớp.
- Hồi quy trên Docker/MySQL thật sau refactor: check-ready, auth-smoke, smoke, commerce-smoke --database-checks với khóa HMAC thử, auth-revocation-smoke đều PASS. Bao gồm tranh quota, kiểm tra webhook HTTP, worker trả quota hết hạn, hoàn tiền/giao/nhận hàng trả và xóa mềm.
- Chromium: commerce-ui-smoke và ui-smoke đều PASS; đăng ký/đăng nhập, CRUD, đặt sale/thanh toán/duyệt hoàn, hồ sơ/tự xóa, route guard, mobile và không có pageerror.
- Không có bước test được báo PASS khi chưa chạy; LayerArchitectureTest thực thi thật trong mvn package, không phải build zero-test như phiên khởi tạo trước.
