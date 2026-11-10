CREATE DATABASE IF NOT EXISTS mta CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE mta;
-- MySQL 8.4. Monetary values are VND. UTC timestamps.
-- Operational tables intentionally NOT partitioned: MySQL partitioned InnoDB
-- tables cannot have foreign keys; unique keys must include partition columns.
CREATE TABLE accounts (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, email VARCHAR(190) NOT NULL UNIQUE,
 password_hash VARCHAR(255) NOT NULL, role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', deleted_at DATETIME(6),
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE TABLE customers (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, account_id BIGINT UNIQUE,
 full_name VARCHAR(150) NOT NULL, phone VARCHAR(30), deleted_at DATETIME(6),
 FOREIGN KEY(account_id) REFERENCES accounts(id), INDEX ix_customer_phone(phone)
);
CREATE TABLE customer_addresses (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, customer_id BIGINT NOT NULL,
 recipient VARCHAR(150) NOT NULL, phone VARCHAR(30) NOT NULL, address VARCHAR(500) NOT NULL,
 FOREIGN KEY(customer_id) REFERENCES customers(id)
);
CREATE TABLE products (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(200) NOT NULL, brand VARCHAR(80) NOT NULL,
 description TEXT, color VARCHAR(30), status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', deleted_at DATETIME(6),
 INDEX ix_product_catalog(status,brand,id)
);
CREATE TABLE product_skus (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, product_id BIGINT NOT NULL, code VARCHAR(80) NOT NULL UNIQUE,
 storage_gb INT NOT NULL, color VARCHAR(40) NOT NULL, price DECIMAL(15,2) NOT NULL,
 FOREIGN KEY(product_id) REFERENCES products(id), CHECK(price>=0)
);
CREATE TABLE inventory (
 sku_id BIGINT PRIMARY KEY, on_hand INT NOT NULL, reserved INT NOT NULL DEFAULT 0,
 FOREIGN KEY(sku_id) REFERENCES product_skus(id),
 CHECK(on_hand>=0 AND reserved>=0 AND reserved<=on_hand)
);
CREATE TABLE sale_campaigns (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(150) NOT NULL,
 starts_at DATETIME(6) NOT NULL, ends_at DATETIME(6) NOT NULL, status VARCHAR(20) NOT NULL,
 CHECK(ends_at>starts_at), INDEX ix_sale_time(status,starts_at,ends_at)
);
CREATE TABLE sale_items (
 campaign_id BIGINT NOT NULL, sku_id BIGINT NOT NULL, sale_price DECIMAL(15,2) NOT NULL,
 quota INT NOT NULL, reserved INT NOT NULL DEFAULT 0, sold INT NOT NULL DEFAULT 0, per_customer_limit INT NOT NULL DEFAULT 1,
 PRIMARY KEY(campaign_id,sku_id), FOREIGN KEY(campaign_id) REFERENCES sale_campaigns(id),
 FOREIGN KEY(sku_id) REFERENCES product_skus(id),
 CHECK(sale_price>=0 AND quota>=reserved+sold AND reserved>=0 AND sold>=0 AND per_customer_limit>0)
);
CREATE TABLE orders (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, customer_id BIGINT NOT NULL,
 idempotency_key VARCHAR(80) NOT NULL, request_hash CHAR(64) NOT NULL,
 status VARCHAR(30) NOT NULL, total DECIMAL(15,2) NOT NULL,
 recipient VARCHAR(150) NOT NULL, phone VARCHAR(30) NOT NULL, address VARCHAR(500) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), expires_at DATETIME(6) NOT NULL,
 UNIQUE(customer_id,idempotency_key), FOREIGN KEY(customer_id) REFERENCES customers(id),
 INDEX ix_order_customer(customer_id,created_at,id), INDEX ix_order_expiry(status,expires_at,id),
 CHECK(total>=0)
);
CREATE TABLE order_items (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, order_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
 product_name VARCHAR(200) NOT NULL, quantity INT NOT NULL, unit_price DECIMAL(15,2) NOT NULL,
 FOREIGN KEY(order_id) REFERENCES orders(id), FOREIGN KEY(sku_id) REFERENCES product_skus(id),
 CHECK(quantity>0 AND unit_price>=0)
);
CREATE TABLE inventory_reservations (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, order_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
 quantity INT NOT NULL, status VARCHAR(20) NOT NULL, expires_at DATETIME(6) NOT NULL,
 UNIQUE(order_id,sku_id), FOREIGN KEY(order_id) REFERENCES orders(id), FOREIGN KEY(sku_id) REFERENCES product_skus(id),
 INDEX ix_reservation_expiry(status,expires_at,id), CHECK(quantity>0)
);
CREATE TABLE sale_customer_quotas (
 campaign_id BIGINT NOT NULL, sku_id BIGINT NOT NULL, customer_id BIGINT NOT NULL,
 reserved INT NOT NULL DEFAULT 0, sold INT NOT NULL DEFAULT 0,
 PRIMARY KEY(campaign_id,sku_id,customer_id), FOREIGN KEY(campaign_id,sku_id) REFERENCES sale_items(campaign_id,sku_id),
 FOREIGN KEY(customer_id) REFERENCES customers(id), CHECK(reserved>=0 AND sold>=0)
);
CREATE TABLE payment_attempts (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, order_id BIGINT NOT NULL, provider VARCHAR(30) NOT NULL,
 provider_reference VARCHAR(100) UNIQUE, status VARCHAR(30) NOT NULL, amount DECIMAL(15,2) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(order_id) REFERENCES orders(id), INDEX ix_payment_order(order_id,created_at), CHECK(amount>=0)
);
CREATE TABLE payment_webhook_events (
 provider VARCHAR(30) NOT NULL, event_id VARCHAR(100) NOT NULL, received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 payload JSON NOT NULL, status VARCHAR(30) NOT NULL, PRIMARY KEY(provider,event_id)
);
CREATE TABLE outbox_events (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, aggregate_id BIGINT NOT NULL, event_type VARCHAR(80) NOT NULL,
 payload JSON NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), published_at DATETIME(6),
 INDEX ix_outbox_pending(published_at,id)
);
CREATE TABLE processed_events (
 consumer_name VARCHAR(80) NOT NULL, event_id BIGINT NOT NULL, processed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(consumer_name,event_id)
);
-- Append-only logs: no foreign keys. Monthly retention via DROP PARTITION.
CREATE TABLE audit_logs (
 id BIGINT NOT NULL AUTO_INCREMENT, created_at DATETIME(6) NOT NULL, actor_id BIGINT,
 action VARCHAR(80) NOT NULL, entity_type VARCHAR(50) NOT NULL, entity_id BIGINT, details JSON,
 PRIMARY KEY(id,created_at), INDEX ix_audit_actor(actor_id,created_at), INDEX ix_audit_entity(entity_type,entity_id,created_at)
) PARTITION BY RANGE COLUMNS(created_at) (
 PARTITION p_before_2026 VALUES LESS THAN ('2026-01-01'),
 PARTITION p2026_01 VALUES LESS THAN ('2026-02-01'),
 PARTITION p2026_02 VALUES LESS THAN ('2026-03-01'),
 PARTITION p2026_03 VALUES LESS THAN ('2026-04-01'),
 PARTITION p2026_04 VALUES LESS THAN ('2026-05-01'),
 PARTITION p2026_05 VALUES LESS THAN ('2026-06-01'),
 PARTITION p2026_06 VALUES LESS THAN ('2026-07-01'),
 PARTITION p2026_07 VALUES LESS THAN ('2026-08-01'),
 PARTITION p2026_08 VALUES LESS THAN ('2026-09-01'),
 PARTITION p2026_09 VALUES LESS THAN ('2026-10-01'),
 PARTITION p2026_10 VALUES LESS THAN ('2026-11-01'),
 PARTITION p2026_11 VALUES LESS THAN ('2026-12-01'),
 PARTITION p2026_12 VALUES LESS THAN ('2027-01-01'),
 PARTITION p_future VALUES LESS THAN (MAXVALUE)
);

-- Bổ sung workflow phiên bản mới. Chỉ chạy toàn bộ file này trên database trống.
-- Bổ sung, không thay đổi V1/V2 đã chạy trên máy người dùng.
ALTER TABLE accounts ADD COLUMN session_version INT NOT NULL DEFAULT 0;
ALTER TABLE sale_campaigns ADD COLUMN deleted_at DATETIME(6) NULL;
ALTER TABLE orders ADD COLUMN campaign_id BIGINT NULL,
 ADD CONSTRAINT fk_order_campaign FOREIGN KEY(campaign_id) REFERENCES sale_campaigns(id);
CREATE TABLE refunds (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 order_id BIGINT NOT NULL UNIQUE,
 payment_attempt_id BIGINT NOT NULL UNIQUE,
 amount DECIMAL(15,2) NOT NULL,
 status VARCHAR(30) NOT NULL,
 reason VARCHAR(500) NOT NULL,
 return_received BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 FOREIGN KEY(order_id) REFERENCES orders(id),
 FOREIGN KEY(payment_attempt_id) REFERENCES payment_attempts(id),
 CHECK(amount>0), INDEX ix_refund_status(status,created_at,id)
);

-- Hỗ trợ tìm tiền tố và nhật ký theo thời gian, không thêm index vào mọi cột.
CREATE INDEX ix_customer_name ON customers(full_name,id);
CREATE INDEX ix_product_name ON products(name,id);
CREATE INDEX ix_audit_time ON audit_logs(created_at,id);

-- Lưu tên hiển thị cho cả ADMIN không có hồ sơ khách hàng.
ALTER TABLE accounts ADD COLUMN display_name VARCHAR(150) NULL;

-- Không quét/khóa toàn bộ accounts khi bảo vệ admin hoạt động cuối cùng.
CREATE INDEX ix_account_active_admin ON accounts(role,status,deleted_at,id);

-- Giới hạn tổng trên toàn chiến dịch, không chỉ từng SKU.
-- Một customer liên kết tối đa một account (customers.account_id UNIQUE).
CREATE TABLE sale_campaign_customer_quotas (
 campaign_id BIGINT NOT NULL,
 customer_id BIGINT NOT NULL,
 reserved BIGINT NOT NULL DEFAULT 0,
 sold BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(campaign_id,customer_id),
 FOREIGN KEY(campaign_id) REFERENCES sale_campaigns(id),
 FOREIGN KEY(customer_id) REFERENCES customers(id),
 CHECK(reserved>=0 AND sold>=0)
);
-- Giữ nguyên lượng đã giữ/đã mua trước nâng cấp, kể cả lịch sử vượt 1.
-- UPDATE nghiệp vụ chỉ cho phép lần mua mới khi reserved + sold + quantity <= 1.
INSERT INTO sale_campaign_customer_quotas(campaign_id,customer_id,reserved,sold)
 SELECT campaign_id,customer_id,SUM(reserved),SUM(sold)
 FROM sale_customer_quotas GROUP BY campaign_id,customer_id;
UPDATE sale_items SET per_customer_limit=1;
