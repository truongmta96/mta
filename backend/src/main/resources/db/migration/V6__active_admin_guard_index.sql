-- Không quét/khóa toàn bộ accounts khi bảo vệ admin hoạt động cuối cùng.
CREATE INDEX ix_account_active_admin ON accounts(role,status,deleted_at,id);
