# Phát triển cục bộ

Mỗi cloud task đã được cô lập: dùng checkout `/workspace/mta`, không tạo worktree nếu không có yêu cầu.

1. `docker compose up -d mysql redis kafka`
2. Trong `backend`: JDK 17 + Maven 3.9+, chạy `mvn spring-boot:run`.
3. Trong `frontend`: Node 22, `npm ci`, `npm run dev`.
4. Kiểm tra `/api/health`, danh sách 6 sản phẩm, tạo đơn từ form và kiểm tra tồn giảm.
5. Chạy `python3 scripts/smoke.py` để kiểm tra API. Đơn thử sẽ tự hết hạn.

Tiến trình phải được khởi động lại sau khi phục hồi snapshot; Docker volumes giữ dữ liệu nhưng process không được giả định còn chạy.

Có thể dùng `bash scripts/build-cloud.sh` để tự truyền cấu hình session proxy và CA. Script đã được chạy kiểm chứng. Trong cloud có proxy, truyền CA công khai với `--secret id=proxy_ca,src="$CODEX_PROXY_CERT"` cho từng `docker build`. Build Java cần Maven settings dùng proxy hiện có; không đưa token hoặc proxy credential vào repository. Có thể build ngoài container rồi chạy Docker runtime với JAR đã build nếu network của build chưa được cấu hình. Không dùng HTTP proxy cho kết nối nội bộ JDBC/Redis/Kafka.

## Đăng nhập khi phát triển

Xem `docs/authentication.md` để biết tài khoản mẫu và ma trận quyền. Compose bật tài khoản mẫu khi chưa tồn tại. Backend chạy ngoài Compose mặc định không tạo tài khoản mẫu; cần bật APP_DEMO_ACCOUNTS_ENABLED và cấu hình hai biến mật khẩu nếu muốn dùng demo. Script smoke hiện đăng nhập và gửi CSRF trước khi đặt hàng.

## Backend sau refactor

Xem `docs/backend-architecture.md`: controller chỉ gọi service interface; xử lý ở service/impl; SQL và JDBC ở repository. `mvn test` trong backend chạy LayerArchitectureTest mà không cần database. Kiểm thử nghiệp vụ đầy đủ vẫn cần Compose và các script HTTP/browser như trước. Không cần reset database hoặc chạy migration mới cho thay đổi phân tầng này.
