# syntax=docker/dockerfile:1
# Giai đoạn build: Maven 3.9.9 với JDK 17 để biên dịch và đóng gói.
FROM maven:3.9.9-eclipse-temurin-17 AS build
# Thư mục làm việc cho các lệnh tiếp theo trong giai đoạn này.
WORKDIR /app
# Chép cấu hình Maven và mã nguồn từ build context backend.
COPY pom.xml .
COPY src src
# BuildKit secret mount chỉ tồn tại trong bước RUN, không COPY CA/settings vào image.
# Nếu có proxy_ca: tạo truststore tạm gồm CA proxy và CA chuẩn của JDK, vẫn xác minh TLS.
# maven_settings cung cấp cấu hình proxy Maven khi build trong cloud.
# mvn -B package build không tương tác; xóa truststore tạm sau khi build thành công.
# Cache Maven repository giúp lần build sau không phải tải lại dependency không đổi.
RUN --mount=type=cache,target=/root/.m2/repository --mount=type=secret,id=proxy_ca --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml \
    if [ -f /run/secrets/proxy_ca ]; then keytool -importcert -noprompt -alias session-proxy -file /run/secrets/proxy_ca -keystore /tmp/build-truststore -storepass changeit; cp "$JAVA_HOME/lib/security/cacerts" /tmp/base-cacerts; keytool -importkeystore -noprompt -srckeystore /tmp/base-cacerts -srcstorepass changeit -destkeystore /tmp/build-truststore -deststorepass changeit; export MAVEN_OPTS="-Djavax.net.ssl.trustStore=/tmp/build-truststore -Djavax.net.ssl.trustStorePassword=changeit"; fi; \
    mvn -B package && rm -f /tmp/build-truststore /tmp/base-cacerts
# Giai đoạn runtime: chỉ dùng JRE 17, không mang theo Maven và source của giai đoạn build.
FROM eclipse-temurin:17-jre
# Thư mục làm việc cho các lệnh tiếp theo trong giai đoạn này.
WORKDIR /app
# Lấy executable JAR đã build; tên file tương ứng artifactId/version trong pom.xml.
COPY --from=build /app/target/shop-0.1.0.jar app.jar
# Chạy bằng UID không phải root.
USER 10001
# Khai báo cổng ứng dụng; EXPOSE không tự publish cổng ra host.
EXPOSE 8080
# Lệnh chạy khi container khởi động; dạng JSON gọi Java trực tiếp.
ENTRYPOINT ["java","-jar","app.jar"]
