-- Run only against the dedicated synthetic database mta_benchmark.
-- Requires schema.sql loaded into mta_benchmark and V2 demo catalog loaded there.
-- Re-running aborts if seed accounts already exist; no truncation, FK/check disabling, or real PII.
DELIMITER $$
CREATE PROCEDURE seed_large()
BEGIN
 DECLARE b INT DEFAULT 0;
 IF DATABASE() <> 'mta_benchmark' THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Refusing: select mta_benchmark';
 END IF;
 IF EXISTS(SELECT 1 FROM accounts WHERE id >= 1000000) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic IDs already present; use a fresh benchmark database';
 END IF;
 CREATE TEMPORARY TABLE seq(n INT PRIMARY KEY);
 INSERT INTO seq WITH digits AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) SELECT 1+a.n+10*b.n+100*c.n+1000*d.n FROM digits a CROSS JOIN digits b CROSS JOIN digits c CROSS JOIN digits d;
 -- 100k accounts + 100k customers. Accounts intentionally cannot log in.
 WHILE b < 10 DO
  START TRANSACTION;
  INSERT INTO accounts(id,email,password_hash,created_at)
   SELECT 1000000+b*10000+n, CONCAT('synthetic-',b*10000+n,'@example.invalid'),'!DISABLED_SYNTHETIC', '2026-01-01' FROM seq;
  INSERT INTO customers(id,account_id,full_name,phone)
   SELECT 1000000+b*10000+n,1000000+b*10000+n,CONCAT('Khách mẫu ',b*10000+n),NULL FROM seq;
  COMMIT;
  SET b=b+1;
 END WHILE;
 SET b=0;
 -- Historical completed orders: no active reservations or inventory changes.
 WHILE b < 50 DO
  START TRANSACTION;
  INSERT INTO orders(id,customer_id,idempotency_key,request_hash,status,total,recipient,phone,address,created_at,expires_at)
   SELECT 1000000+b*10000+n,1000001+MOD(b*10000+n-1,100000),CONCAT('seed-',b*10000+n),SHA2(CONCAT('seed-',b*10000+n),256),
    'COMPLETED',s.price,CONCAT('Khách mẫu ',MOD(b*10000+n-1,100000)+1),'SYNTHETIC','Địa chỉ giả lập - không giao hàng',
    DATE_ADD('2026-01-01',INTERVAL MOD(b*10000+n,300) DAY),DATE_ADD(DATE_ADD('2026-01-01',INTERVAL MOD(b*10000+n,300) DAY),INTERVAL 15 MINUTE)
   FROM seq JOIN product_skus s ON s.id=1+MOD(b*10000+n-1,6);
  INSERT INTO order_items(order_id,sku_id,product_name,quantity,unit_price)
   SELECT 1000000+b*10000+n,s.id,p.name,1,s.price FROM seq JOIN product_skus s ON s.id=1+MOD(b*10000+n-1,6) JOIN products p ON p.id=s.product_id;
  INSERT INTO payment_attempts(order_id,provider,provider_reference,status,amount,created_at)
   SELECT o.id,'SYNTHETIC',CONCAT('fake-',o.id),'SUCCEEDED',o.total,o.created_at FROM orders o WHERE o.id BETWEEN 1000001+b*10000 AND 1010000+b*10000;
  COMMIT;
  SET b=b+1;
 END WHILE;
 SET b=0;
 WHILE b < 200 DO
  START TRANSACTION;
  INSERT INTO audit_logs(created_at,actor_id,action,entity_type,entity_id,details)
   SELECT DATE_ADD(DATE_ADD('2026-01-01',INTERVAL MOD(b*10000+n,300) DAY),INTERVAL MOD(n,86400) SECOND),
    1000001+MOD(b*10000+n-1,100000),'SYNTHETIC_VIEW','ORDER',1000001+MOD(b*10000+n-1,500000),JSON_OBJECT('synthetic',TRUE) FROM seq;
  COMMIT;
  SET b=b+1;
 END WHILE;
 DROP TEMPORARY TABLE seq;
END$$
DELIMITER ;
CALL seed_large();
DROP PROCEDURE seed_large;
SELECT 'accounts' AS table_name,COUNT(*) AS rows_count FROM accounts
UNION ALL SELECT 'customers',COUNT(*) FROM customers
UNION ALL SELECT 'orders',COUNT(*) FROM orders
UNION ALL SELECT 'order_items',COUNT(*) FROM order_items
UNION ALL SELECT 'payment_attempts',COUNT(*) FROM payment_attempts
UNION ALL SELECT 'audit_logs',COUNT(*) FROM audit_logs;
