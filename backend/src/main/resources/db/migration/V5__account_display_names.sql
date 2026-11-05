-- Lưu tên hiển thị cho cả ADMIN không có hồ sơ khách hàng.
ALTER TABLE accounts ADD COLUMN display_name VARCHAR(150) NULL;
