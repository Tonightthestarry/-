-- 用户表(仅用于认证，业务数据全部存MongoDB)
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(200) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'user',
    nickname VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 操作日志表
CREATE TABLE IF NOT EXISTS sys_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50),
    operation VARCHAR(100),
    target VARCHAR(200),
    status VARCHAR(20),
    detail TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 默认管理员: admin / 123456
MERGE INTO sys_user (username, password, role, nickname) KEY(username)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5Eh', 'admin', '管理员');

-- 默认普通用户: user / 123456
MERGE INTO sys_user (username, password, role, nickname) KEY(username)
VALUES ('user', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5Eh', 'user', '普通用户');
