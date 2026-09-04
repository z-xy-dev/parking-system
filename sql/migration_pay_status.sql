-- ============================================
-- 迁移：订单表增加支付状态字段
-- 业务模式变更：由"下单即全额预付"改为"订单完成后一次性结算（含超时费）"
-- 重复执行安全：字段已存在时自动跳过
-- ============================================
USE order_db;

SET @col_pay_status := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'order_db' AND TABLE_NAME = 'booking_order' AND COLUMN_NAME = 'pay_status'
);
SET @ddl := IF(@col_pay_status = 0,
    'ALTER TABLE booking_order ADD COLUMN pay_status VARCHAR(20) NOT NULL DEFAULT ''UNPAID'' COMMENT ''支付状态: UNPAID待支付 / PAID已支付（订单完成后统一结算）'' AFTER status',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_paid_at := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'order_db' AND TABLE_NAME = 'booking_order' AND COLUMN_NAME = 'paid_at'
);
SET @ddl := IF(@col_paid_at = 0,
    'ALTER TABLE booking_order ADD COLUMN paid_at DATETIME NULL COMMENT ''支付时间（完成订单时一次性扣款写入）'' AFTER pay_status',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 历史数据：旧模式下已完成订单在下单时就已扣款，直接标记为已支付
UPDATE booking_order
SET pay_status = 'PAID', paid_at = updated_at
WHERE status = 'COMPLETED' AND pay_status = 'UNPAID';
