# Các màn hình và workflow đã hoàn thiện

## Cập nhật trên Windows

```powershell
git pull origin master
docker compose up -d --build
py scripts/check-ready.py
py scripts/auth-smoke.py
py scripts/commerce-smoke.py
```

Giữ cổng MySQL đã đổi sang 3307 nếu máy bạn bị trùng cổng 3306. Không cần `bash scripts/build-cloud.sh` trên Windows. **Không dùng `docker compose down -v` hoặc xóa database để cập nhật.** Flyway chạy thêm V3/V4/V5/V6 trên schema hiện có; V1/V2 giữ nguyên. Phiên cũ sẽ cần đăng nhập lại sau khi nâng cấp.

Tài khoản phát triển: admin@mta.local / Admin@12345; user@mta.local / User@12345. Seeder chỉ tạo email chưa có, không reset mật khẩu. Có thể tự đăng ký user mới qua giao diện.

## Màn hình quản trị

| Màn hình | Thao tác |
|---|---|
| Sản phẩm | Thêm SKU/điện thoại, sửa tên/thương hiệu/mô tả/biến thể/giá/tồn, xóa mềm, khôi phục. |
| Đơn hàng | Xem dòng hàng/địa chỉ/giao dịch, hủy đơn chưa trả tiền; PAID → PROCESSING → SHIPPED → COMPLETED; tạo yêu cầu hoàn. |
| Tài khoản | Tạo admin/user, sửa email/tên/vai trò/trạng thái, đặt mật khẩu mới, khóa, xóa mềm, khôi phục. |
| Khách hàng | Sửa tên/điện thoại, xóa mềm/khôi phục hồ sơ; thêm khách bằng tạo tài khoản CUSTOMER. |
| Chiến dịch sale | Tạo/sửa lịch nhiều SKU, giá ưu đãi, quota, hạn mức mỗi khách; kết thúc hoặc xóa mềm. |
| Hoàn tiền | Xem yêu cầu, duyệt/từ chối, xác nhận đã nhận hàng trả nếu đơn đã gửi. |
| Nhật ký | Xem thao tác quản lý gần đây; lọc theo tiền tố action trong 30 ngày. |

Các danh sách quản trị có phân trang server 50 dòng/trang. Tìm theo tiền tố tên sản phẩm/khách/chiến dịch; màn tài khoản tìm email, nhật ký tìm action. Nhật ký có index theo thời gian; tên khách và sản phẩm có index phục vụ tìm tiền tố. Tồn vật lý không được nhỏ hơn lượng đang giữ.

Để ngăn mất quyền quản trị, không cho admin tự xóa/khóa/hạ quyền tài khoản đang dùng và không cho loại bỏ admin hoạt động cuối cùng. Mọi thay đổi tài khoản tăng session_version để thu hồi các phiên cũ (kể cả đổi mật khẩu/email).

Xóa tài khoản cũng ẩn hồ sơ khách và hủy các đơn chưa thanh toán, nhưng giữ đơn đã trả tiền. Khôi phục tài khoản khôi phục cả hồ sơ. Xóa riêng hồ sơ khách sẽ khóa tài khoản liên kết; khi khôi phục hồ sơ, admin cần chủ động mở khóa tài khoản tại màn Tài khoản. Không xóa vật lý dữ liệu có liên kết đơn hàng.

## Màn hình khách hàng

- Đăng ký/đăng nhập, xem danh mục và các chiến dịch sale từ database.
- Sale có đồng hồ đếm ngược theo thời gian server; thời gian nhập trên màn admin được đổi từ múi giờ trình duyệt sang UTC.
- Tạo đơn với số lượng 1–5 (còn chịu giới hạn sale), tên/điện thoại/địa chỉ; giá do server tính và kiểm tra lại giá đã xác nhận.
- Mở Đơn hàng → Chi tiết / Thanh toán để thanh toán sandbox, xem lịch sử, hủy đơn hoặc yêu cầu hoàn toàn bộ.
- Sửa hồ sơ và xóa mềm tài khoản bằng mật khẩu xác nhận. Đơn lịch sử vẫn được giữ.

Hiện mỗi đơn chứa một SKU với số lượng lựa chọn. Đây là quy trình đặt đơn hiện có; chưa có giỏ nhiều SKU, phí vận chuyển hoặc mã giảm giá ngoài chiến dịch sale.

## Tồn kho và flash sale

Tạo đơn trong cùng transaction: khóa khách → giữ inventory bằng UPDATE có điều kiện → giữ quota chiến dịch → giữ hạn mức mỗi khách → tạo đơn/reservation/outbox. Nếu bất kỳ bước nào thất bại, rollback toàn bộ. Idempotency-Key chống tạo trùng cùng yêu cầu.

Một SKU không được có hai chiến dịch công khai giao nhau. DRAFT không mở bán; SCHEDULED/ACTIVE chỉ mở trong đúng khung thời gian. Client không thể đặt bằng campaignId chưa bắt đầu/đã kết thúc. Catalog tự dùng giá sale đang mở. Hết suất sale không tự chuyển sang bán giá thường trong cùng chiến dịch.

Đơn chưa trả tiền giữ hàng 15 phút. Hủy/hết hạn trả inventory và quota reserved. Trả tiền thành công chuyển quota reserved sang sold và reservation HELD sang CONFIRMED, vẫn giữ tồn đến lúc xuất kho. Khi SHIPPED giảm on_hand/reserved và chuyển reservation sang FULFILLED. Hoàn tiền trước xuất kho trả phần giữ; sau xuất kho cần xác nhận nhận hàng trả rồi mới cộng lại on_hand. Theo chính sách sandbox, hoàn tiền trả cả quota và hạn mức khách nếu chiến dịch vẫn còn hiệu lực.

Chiến dịch đã có đơn không được sửa cấu trúc/giá/lịch/quota; có thể kết thúc/xóa mềm rồi tạo chiến dịch mới. Điều này giữ được liên kết FK và cách tính quota cho đơn cũ. Sản phẩm đã xóa mềm không nhận đơn mới, nhưng đơn đã giữ trước đó vẫn xử lý được.

## Thanh toán sandbox và webhook

Bạn đã chọn sandbox: **mọi giao dịch/hoàn tiền đều mô phỏng, không kết nối ngân hàng hoặc chuyển tiền thật**.

1. User mở chi tiết đơn → Thanh toán sandbox.
2. Server tạo/reuse một payment_attempt PENDING cho đơn, số tiền lấy từ database.
3. Chọn Mô phỏng thành công/thất bại. Server tạo và ký payload, rồi chạy cùng bộ xử lý webhook HMAC như endpoint HTTP; secret không được gửi xuống browser.
4. Thành công xác nhận đơn; thất bại cho phép tạo lần thanh toán mới. Tải lại/truyền lại callback không giữ/trừ kho lần hai.
5. Nếu tiền đến sau khi đơn hủy/hết hạn, sandbox tự ghi hoàn thành công và giữ trạng thái hủy/hết hạn của đơn, không lấy hàng đã nhả cho người khác.
6. User yêu cầu hoàn toàn bộ; admin duyệt/từ chối trong màn Hoàn tiền. Sau khi đã giao, phải xác nhận nhận lại hàng trước khi duyệt. Duyệt lặp không cộng kho hai lần.

Cấu hình Compose: PAYMENT_SANDBOX_ENABLED mặc định true cho phát triển; application.yml mặc định false ngoài Compose. PAYMENT_WEBHOOK_SECRET để trống tạo khóa ngẫu nhiên trong RAM mỗi lần khởi động. Nếu muốn kiểm thử webhook HTTP bên ngoài, tự đặt khóa thử qua môi trường; không commit khóa thật.

Endpoint công khai duy nhất được bỏ CSRF là `POST /api/payments/webhook`. Endpoint này bắt buộc header `X-Payment-Signature`: hex HMAC-SHA256 của **đúng chuỗi JSON gửi đi**. Body: eventId, reference (provider_reference), status SUCCEEDED/FAILED, amount, timestamp (Unix giây). Chỉ chấp nhận thời gian ±300 giây, reference và số tiền đúng. Event ID trùng khác nội dung bị từ chối; callback trùng cùng nội dung không làm lại nghiệp vụ. Các API user/admin vẫn yêu cầu session/role/CSRF.

Khi tắt sandbox, tạo giao dịch/hoàn tiền bị chặn 503; hệ thống không tự đánh dấu đã thu/hoàn tiền. Tích hợp nhà cung cấp thật cần adapter và quy trình đối soát riêng, ngoài lựa chọn sandbox hiện tại. Hoàn tiền hiện là toàn bộ, chưa hỗ trợ hoàn một phần.

## SQL và kiểm thử

- Flyway: V3 thêm refund/campaign liên kết đơn/session_version; V4 index tìm kiếm quản trị; V5 tên hiển thị admin; V6 index bảo vệ admin hoạt động.
- `database/schema.sql`: toàn bộ schema mới cho database trống, không chạy trước Flyway trên cùng database.
- `database/upgrade_from_v2.sql`: nâng schema cài SQL thủ công từ V2; không chạy nếu Flyway đã quản lý hoặc database đã có V3–V6.
- `py scripts/commerce-smoke.py`: tạo các tài khoản, SKU và sale thử riêng; kiểm tra tranh quota, vòng đời đơn/thanh toán/hoàn tiền và xóa mềm; ẩn SKU/sale thử sau khi chạy thành công, giữ lịch sử để đối soát.
- `py scripts/commerce-smoke.py --database-checks`: thêm mô phỏng hết hạn trên đúng đơn thử, cần Docker CLI.
- Đặt MTA_TEST_WEBHOOK_SECRET giống PAYMENT_WEBHOOK_SECRET của API để kiểm tra webhook HTTP ký từ ngoài; nếu không có, script ghi rõ các kiểm tra đó NOT RUN. Trong môi trường phát triển chỉ dùng khóa thử. `--database-checks`/kiểm tra signed HTTP cần Docker CLI để đọc reference của chính giao dịch thử.
- `py scripts/commerce-ui-smoke.py`: browser thật thao tác tạo/sửa/xóa/khôi phục sản phẩm, tạo tài khoản/sale, đăng ký user, đặt sale, trả tiền, duyệt hoàn, sửa hồ sơ/xóa tài khoản; cần Playwright/Chromium.

Các kiểm tra không phải đánh giá khả năng chịu tải production. Session vẫn ở RAM một API instance; Redis chưa làm session store, chưa có MFA/reset mật khẩu/rate limit. Kafka outbox vẫn at-least-once, consumer tương lai phải chống trùng.
