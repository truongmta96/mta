-- Hỗ trợ tìm tiền tố và nhật ký theo thời gian, không thêm index vào mọi cột.
CREATE INDEX ix_customer_name ON customers(full_name,id);
CREATE INDEX ix_product_name ON products(name,id);
CREATE INDEX ix_audit_time ON audit_logs(created_at,id);
