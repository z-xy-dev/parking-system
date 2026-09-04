-- ============================================
-- 共享停车位系统 数据库初始化脚本
-- ============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS user_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS parking_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS order_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS payment_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ============================================
-- 1. 用户服务数据库
-- ============================================
USE user_db;

CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    phone VARCHAR(20) COMMENT '手机号',
    real_name VARCHAR(50) COMMENT '真实姓名',
    car_plate VARCHAR(20) COMMENT '车牌号',
    role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色: USER普通用户 / OWNER业主',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 初始测试用户 (密码: 123456)
INSERT INTO user (username, password, phone, real_name, role) VALUES
('admin', '$2a$10$o.VwsdPhxkayzgHu.QDnCeXC8BqZQb/wDjYF17sKqgwcaUS0ZPyL2', '13800000000', '管理员', '粤A88888', 'OWNER'),
('user1', '$2a$10$o.VwsdPhxkayzgHu.QDnCeXC8BqZQb/wDjYF17sKqgwcaUS0ZPyL2', '13800000001', '张三', '粤A12345', 'USER');

-- ============================================
-- 2. 车位服务数据库
-- ============================================
USE parking_db;

CREATE TABLE IF NOT EXISTS parking_space (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '车位ID',
    owner_id BIGINT NOT NULL COMMENT '业主用户ID',
    title VARCHAR(100) NOT NULL COMMENT '车位名称',
    address VARCHAR(255) NOT NULL COMMENT '地址',
    longitude DECIMAL(10,6) DEFAULT 0 COMMENT '经度',
    latitude DECIMAL(10,6) DEFAULT 0 COMMENT '纬度',
    price_per_hour DECIMAL(10,2) NOT NULL COMMENT '每小时价格',
    total_spots INT NOT NULL DEFAULT 1 COMMENT '总车位数',
    available_spots INT NOT NULL DEFAULT 1 COMMENT '可用车位数',
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态: AVAILABLE可用 / FULL已满 / DISABLED禁用',
    description VARCHAR(500) COMMENT '描述',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车位表';

CREATE TABLE IF NOT EXISTS parking_image (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '图片ID',
    space_id BIGINT NOT NULL COMMENT '车位ID',
    image_url VARCHAR(500) NOT NULL COMMENT '图片URL',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车位图片表';

-- 独立车位明细表（支持高并发选位）
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

-- 初始测试车位
INSERT INTO parking_space (owner_id, title, address, longitude, latitude, price_per_hour, total_spots, available_spots, status, description) VALUES
(1, '天河城地下停车场', '广州市天河区天河路208号', 113.323900, 23.129200, 10.00, 10, 10, 'AVAILABLE', '24小时营业，临近地铁站'),
(1, '珠江新城商务停车场', '广州市天河区珠江新城华夏路', 113.325400, 23.119600, 15.00, 10, 10, 'AVAILABLE', '商务区核心位置，步行可达各大写字楼');

-- 为测试车位自动生成独立车位明细（存储过程）
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

-- ============================================
-- 3. 订单服务数据库
-- ============================================
USE order_db;

CREATE TABLE IF NOT EXISTS booking_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '订单ID',
    order_no VARCHAR(32) NOT NULL UNIQUE COMMENT '订单编号(雪花ID)',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    space_id BIGINT NOT NULL COMMENT '车位ID',
    spot_number VARCHAR(20) COMMENT '具体车位编号',
    car_plate VARCHAR(20) COMMENT '车牌号',
    start_time DATETIME NOT NULL COMMENT '开始时间',
    end_time DATETIME NOT NULL COMMENT '结束时间',
    hours INT NOT NULL COMMENT '时长(小时)',
    total_amount DECIMAL(10,2) NOT NULL COMMENT '总金额',
    overdue_hours INT DEFAULT 0 COMMENT '超时小时数',
    overdue_fee DECIMAL(10,2) DEFAULT 0.00 COMMENT '超时费用',
    status VARCHAR(20) NOT NULL DEFAULT 'RESERVED' COMMENT '状态: RESERVED已预订 / USING使用中 / COMPLETED已完成 / CANCELED已取消',
    pay_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID' COMMENT '支付状态: UNPAID待支付 / PAID已支付（订单完成后统一结算）',
    paid_at DATETIME NULL COMMENT '支付时间（完成订单时一次性扣款写入）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ============================================
-- 4. 支付服务数据库
-- ============================================
USE payment_db;

CREATE TABLE IF NOT EXISTS payment_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '支付记录ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    amount DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    method VARCHAR(20) NOT NULL DEFAULT 'WECHAT' COMMENT '支付方式: WECHAT微信 / ALIPAY支付宝',
    trade_no VARCHAR(64) NOT NULL UNIQUE COMMENT '交易流水号',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING待支付 / SUCCESS成功 / FAILED失败',
    paid_at DATETIME COMMENT '支付时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付记录表';
