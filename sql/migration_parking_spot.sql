-- ============================================
-- 迁移脚本: 为现有停车场创建独立车位明细表
-- 执行前请确保已连接到 parking_db
-- ============================================

USE parking_db;

-- 创建独立车位明细表
CREATE TABLE IF NOT EXISTS parking_spot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '明细ID',
    space_id BIGINT NOT NULL COMMENT '所属停车场ID',
    spot_number INT NOT NULL COMMENT '车位编号(1-N)',
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态: AVAILABLE空闲 / OCCUPIED已占 / DISABLED禁用',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_space_spot (space_id, spot_number),
    INDEX idx_space_status (space_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='独立车位明细表';

-- 为已存在的停车场批量生成独立车位明细
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS generate_spots_for_existing_spaces()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE v_space_id BIGINT;
    DECLARE v_total INT;
    DECLARE v_i INT;
    DECLARE cur CURSOR FOR SELECT id, total_spots FROM parking_space WHERE total_spots > 0;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO v_space_id, v_total;
        IF done THEN LEAVE read_loop; END IF;

        SET v_i = 1;
        WHILE v_i <= v_total DO
            INSERT IGNORE INTO parking_spot (space_id, spot_number, status, version)
            VALUES (v_space_id, v_i, 'AVAILABLE', 0);
            SET v_i = v_i + 1;
        END WHILE;
    END LOOP;
    CLOSE cur;
END //
DELIMITER ;
CALL generate_spots_for_existing_spaces();
DROP PROCEDURE IF EXISTS generate_spots_for_existing_spaces;

-- 注意：车位占用状态由应用运行时管理（下单->reserveSpot 置 OCCUPIED，
--       完成/取消->releaseSpot 置 AVAILABLE），禁止在此按 available_spots 反推占用。
-- 旧逻辑（spot_number > available_spots 即标 OCCUPIED）会与真实订单脱钩，
-- 产生"被占用却无订单"的孤儿车位。初始化时所有车位均为 AVAILABLE，由业务自行占用。
