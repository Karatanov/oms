-- Source SHA256: 846229736506c963e042cf9c2bd68559b51ebda2a0d0b945ef346d3eb41791ed
-- UR III Payments.xlsx | UR III APRIL 2026 payments | B3:H3, B6:H7.
-- Restore three real paid rows only. Never reinterpret Planned/Sent for payment.
SET NAMES utf8mb4;
CREATE TEMPORARY TABLE payment_repair_assert (ok TINYINT NOT NULL CHECK(ok=1));
CREATE TEMPORARY TABLE payment_repair_expected (
 source_reference VARCHAR(180) PRIMARY KEY, source_code VARCHAR(100), batch_id INT,
 payment_date DATE, amount_uah DECIMAL(20,2), amount_eur DECIMAL(20,2), source_sheet VARCHAR(180));
INSERT INTO payment_repair_expected VALUES
 ('UR III APRIL 2026 payments:3','ZP08_04_W',8,'2026-04-14',377224.28,7620.69,'UR III APRIL 2026 payments'),
 ('UR III APRIL 2026 payments:6','CK08_06_W',8,'2026-04-24',51290.00,497.00,'UR III APRIL 2026 payments'),
 ('UR III APRIL 2026 payments:7','RV08_04#Lot2_W',8,'2026-04-24',3529443.71,71301.89,'UR III APRIL 2026 payments');
START TRANSACTION;
SELECT id FROM payment_monitoring_transactions ORDER BY id FOR UPDATE;
CREATE TEMPORARY TABLE payment_repair_before AS SELECT * FROM payment_monitoring_transactions;
INSERT INTO payment_repair_assert SELECT IF(COUNT(*) IN (93,96),1,0) FROM payment_repair_before;
INSERT INTO payment_repair_assert SELECT IF(COUNT(*)=181,1,0) FROM payment_monitoring_rows;
-- Reject a changed source snapshot or collisions, rather than overwrite newer data.
INSERT INTO payment_repair_assert SELECT IF(COUNT(*)=0,1,0)
FROM payment_repair_expected e JOIN payment_monitoring_transactions t USING(source_reference)
WHERE NOT (e.source_code <=> t.source_code AND e.batch_id <=> t.batch_id
 AND e.payment_date <=> t.payment_date AND e.amount_uah <=> t.amount_uah
 AND e.amount_eur <=> t.amount_eur AND e.source_sheet <=> t.source_sheet);
INSERT INTO payment_repair_assert SELECT IF(COUNT(*)=0,1,0)
FROM payment_repair_expected e JOIN payment_monitoring_transactions t
ON t.source_code=e.source_code AND t.payment_date=e.payment_date AND t.amount_uah=e.amount_uah
WHERE t.source_reference<>e.source_reference;
INSERT INTO payment_monitoring_transactions
(source_reference,source_code,batch_id,payment_date,amount_uah,amount_eur,source_sheet,updated_at)
SELECT e.source_reference,e.source_code,e.batch_id,e.payment_date,e.amount_uah,e.amount_eur,e.source_sheet,
 (SELECT MAX(updated_at) FROM payment_repair_before)
FROM payment_repair_expected e LEFT JOIN payment_monitoring_transactions t USING(source_reference)
WHERE t.id IS NULL;
SELECT ROW_COUNT() AS restored_payment_rows;
INSERT INTO payment_repair_assert SELECT IF(COUNT(*)=96 AND SUM(amount_uah)=313077334.60,1,0)
FROM payment_monitoring_transactions;
INSERT INTO payment_repair_assert SELECT IF(COUNT(*)=0,1,0)
FROM payment_repair_before b LEFT JOIN payment_monitoring_transactions t USING(id)
WHERE t.id IS NULL OR NOT (b.source_reference <=> t.source_reference AND b.source_code <=> t.source_code
 AND b.batch_id <=> t.batch_id AND b.payment_date <=> t.payment_date AND b.amount_uah <=> t.amount_uah
 AND b.amount_eur <=> t.amount_eur AND b.source_sheet <=> t.source_sheet AND b.updated_at <=> t.updated_at);
COMMIT;
SELECT COUNT(*) AS payment_rows,SUM(amount_uah) AS paid_uah FROM payment_monitoring_transactions;
