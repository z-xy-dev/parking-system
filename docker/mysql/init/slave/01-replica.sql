-- 从库初始化：指向主库并开启 GTID 复制（主库 repl 账号由主库 01-init.sql 创建）
CHANGE MASTER TO
  MASTER_HOST='mysql-master',
  MASTER_USER='repl',
  MASTER_PASSWORD='replpass',
  MASTER_AUTO_POSITION=1;
START SLAVE;
