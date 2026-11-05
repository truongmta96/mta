-- Chỉ schema cài thủ công đang ở V2. Không chạy nếu Flyway quản lý database.
USE mta;
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
