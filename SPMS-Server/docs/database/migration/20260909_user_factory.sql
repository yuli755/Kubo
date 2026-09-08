-- ============================================================================
-- D13 工厂维度：用户 ↔ 工厂（仓库/工厂树的顶层 storage 节点）关系表
-- ============================================================================
-- 决策（D13 方案 A）：本仓没有独立的 factory 实体，`storage` 树（parentId 自关联）
-- 就是文档里的「仓库/工厂树」；工厂 = 该树顶层节点。于是 user_factory 是与 user.storageList
-- 同构的 M2M 中间表，只是语义上代表「用户所属的工厂」。
--
-- 与 user_tenant 同构的取舍：
--   * 不建 FOREIGN KEY：框架表（user / storage）由 Hibernate ddl-auto 生成，本迁移脚本
--     假定它们已存在（CI 里这批脚本以 --force 跑在空库上，只容忍 1146）；FK 会让脚本在
--     空库上必然失败。Hibernate 的 schema validate 只核对表与列存在，不核对 FK，因此
--     不建 FK 不影响生产启动校验。
--   * 列名与 @JoinTable 显式对齐：UserEntity.factoryList 声明了
--     @JoinTable(name="user_factory", joinColumns=user_entity_id, inverseJoinColumns=factory_id)，
--     本表必须一字不差，否则生产 ddl-auto=validate 启动即 Schema-validation 失败。
--
-- 幂等：CREATE TABLE IF NOT EXISTS；重复执行无副作用（CI 连跑两遍判据）。
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user_factory` (
    `user_entity_id` BIGINT UNSIGNED NOT NULL COMMENT '用户 ID（UserEntity.id）',
    `factory_id` BIGINT UNSIGNED NOT NULL COMMENT '工厂 ID（storage 树节点 id，通常为顶层节点）',
    PRIMARY KEY (`user_entity_id`, `factory_id`),
    KEY `idx_user_factory_factory` (`factory_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户可访问的工厂集合（D13 工厂维度）';
