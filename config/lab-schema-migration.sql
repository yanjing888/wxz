-- 已有 MySQL 数据库升级前先备份。新数据库由应用建表，无需执行。
-- 解除旧版“一个实验项目对应一个实验室”的 NOT NULL 限制；保留旧映射和记录。
ALTER TABLE lab_rooms MODIFY COLUMN experiment_code VARCHAR(64) NULL;
-- 其他新增列由现有 spring.jpa.hibernate.ddl-auto=update 创建。
-- 若部署环境禁止自动更新表结构，应先在备份恢复的数据库上完成升级并审查变更。
-- 当前学生座位标记：与旧课次/实验记录独立，不修改历史数据。
CREATE TABLE IF NOT EXISTS workstation_bindings (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    class_name VARCHAR(64) NOT NULL,
    teacher_id BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_workstation_binding_room_user (room_id,user_id),
    KEY ix_workstation_binding_station (station_id)
);

-- 学生反馈订正：不覆盖已提交报告及教师批阅记录。
CREATE TABLE IF NOT EXISTS learning_reflections (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    experiment_code VARCHAR(64) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    feedback_revision VARCHAR(64) NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_learning_reflection_user_experiment (user_id,experiment_code)
);
