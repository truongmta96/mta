# syntax=docker/dockerfile:1
# Giai đoạn build React bằng Node 22; image này không đi vào runtime cuối.
FROM node:22-bookworm-slim AS build
# Thư mục chứa source và nơi chạy npm.
WORKDIR /app
# Chép manifest và lockfile trước để tái sử dụng cache dependency nếu source thay đổi.
COPY package*.json ./
# CA proxy tùy chọn chỉ mount khi build; NODE_EXTRA_CA_CERTS bổ sung CA, không tắt TLS.
# npm ci cài đúng lockfile và báo lỗi nếu manifest/lockfile không khớp.
RUN --mount=type=secret,id=proxy_ca \
    if [ -f /run/secrets/proxy_ca ]; then export NODE_EXTRA_CA_CERTS=/run/secrets/proxy_ca; fi; npm ci --strict-ssl=true
# Chép source; node_modules và dist trên host bị loại bởi .dockerignore.
COPY . .
# Tạo bundle HTML/CSS/JavaScript production trong /app/dist.
RUN npm run build
# Runtime chỉ dùng Nginx để phục vụ file tĩnh và proxy API.
FROM nginx:1.28-alpine
# Thay cấu hình site mặc định bằng routing của cửa hàng.
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Chỉ lấy kết quả build frontend, không đưa Node/npm/source vào runtime.
COPY --from=build /app/dist /usr/share/nginx/html
