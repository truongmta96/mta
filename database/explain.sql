USE mta_benchmark;
EXPLAIN ANALYZE SELECT id,status,total FROM orders WHERE customer_id=1000001 ORDER BY created_at DESC,id DESC LIMIT 20;
EXPLAIN ANALYZE SELECT id FROM orders WHERE status='PENDING_PAYMENT' AND expires_at<=UTC_TIMESTAMP() ORDER BY expires_at,id LIMIT 100;
EXPLAIN SELECT id FROM audit_logs WHERE created_at>='2026-06-01' AND created_at<'2026-07-01' AND actor_id=1000001;
-- Add a future month before it begins; schedule this through operations/migrations.
-- ALTER TABLE audit_logs REORGANIZE PARTITION p_future INTO
-- (PARTITION p2027_01 VALUES LESS THAN ('2027-02-01'), PARTITION p_future VALUES LESS THAN (MAXVALUE));
