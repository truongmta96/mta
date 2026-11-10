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
