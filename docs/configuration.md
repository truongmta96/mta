# Giải thích các file cấu hình

Comment tiếng Việt nằm trực tiếp trong `backend/pom.xml`, `backend/src/main/resources/application.yml`, `compose.yml`, hai Dockerfile, `frontend/nginx.conf`, `frontend/vite.config.js`, `.gitignore` và hai `.dockerignore`.

## frontend/package.json

JSON chuẩn không hỗ trợ comment. Vì vậy giữ file này hợp lệ và giải thích tại đây:

| Trường | Ý nghĩa |
|---|---|
| `name` | Tên package frontend: `mta-store`. |
| `version` | Phiên bản frontend `0.1.0`, độc lập với phiên bản backend. |
| `private: true` | Ngăn npm publish nhầm package lên registry. |
| `type: module` | File JavaScript của package dùng ES modules, ví dụ `import`/`export`. |
| `scripts.dev` | Chạy Vite dev server; `--host 0.0.0.0` cho phép lắng nghe trên mọi interface của môi trường chạy. |
| `scripts.build` | Chạy `vite build`, tạo bundle production vào `dist/`. |
| `dependencies.react` | Thư viện xây dựng giao diện bằng component và quản lý state. |
| `dependencies.react-dom` | Render cây component React vào DOM trình duyệt. |
| `dependencies.lucide-react` | Các biểu tượng dưới dạng React component. |
| `devDependencies.vite` | Dev server và công cụ build frontend; không phải server production trong image Nginx. |

Các dependency khai báo phiên bản cụ thể. Docker dùng `npm ci` để cài theo lockfile, rồi build bundle. Browser nhận bundle đã build, không đọc trực tiếp package.json.

## frontend/package-lock.json

File npm tự sinh để cố định cây dependency, gồm cả dependency gián tiếp. Không chèn comment hoặc sửa thủ công:

- `lockfileVersion`: phiên bản định dạng lockfile.
- `packages`: thông tin từng package và vị trí trong cây dependency; mục chuỗi rỗng mô tả package gốc.
- `version`: phiên bản package đã khóa.
- `resolved`: nguồn tải artifact khi có.
- `integrity`: mã kiểm tra tính toàn vẹn artifact; không sửa để bỏ qua lỗi xác minh.
- `dependencies`: các dependency package cần.
- `dev`, `optional`, `os`, `cpu`: thông tin phạm vi cài đặt và điều kiện nền tảng khi có.

Khi chủ động thay dependency, dùng npm để cập nhật và commit cả package.json lẫn package-lock.json. `npm ci` không cập nhật lockfile.

## Các lớp cấu hình phối hợp thế nào?

- `pom.xml` và package.json định nghĩa dependency và công cụ build.
- `application.yml` cấu hình tiến trình Spring Boot; biến môi trường do Compose truyền ghi đè các giá trị mặc định tương ứng.
- Dockerfile tạo image. `.dockerignore` giới hạn file được gửi vào build context.
- `compose.yml` khởi động các image cùng network, cổng và volume; không thay thế cấu hình nghiệp vụ Java.
- `vite.config.js` proxy API khi phát triển. `nginx.conf` phục vụ bundle và proxy API khi chạy image production.
- `.gitignore` giới hạn file chưa được theo dõi được thêm vào Git; không tác động đến file đã được Git theo dõi và không thay thế `.dockerignore`.
