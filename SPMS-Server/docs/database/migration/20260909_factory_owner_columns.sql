-- ============================================================================
-- D13-B 工厂维度：给既有部署回填 wms.inventory / wms.move 的工厂归属列登记
-- ============================================================================
-- 决策（D13 方案 A）：工厂 = `storage` 树顶层节点，业务实体的工厂归属就是它身上的
-- `@ManyToOne storage` 关联。FACTORY 策略列绑定走 `FACTORY:storage.id` 关联路径
-- （OwnerColumnRegistration 的「DIM:path」语法，一跳封顶）。
--
-- 为什么需要本脚本，而不是只在 DevDataInitRunner 里补 .setOwnerColumns(...)：
--   DevDataInitRunner 只在「资源不存在」时才写行（if filter(...).isEmpty()），
--   现网这两个资源早已存在且 owner_columns 为空/NULL。若不回填，翻
--   `factory-dimension-enabled: true` 后 FactoryDimensionReadiness 启动校验会报
--   「没有任何已注册资源具备工厂归属列」→ 启动即红。所以回填必须幂等地放进迁移脚本，
--   与启动校验同一批上线。
--
-- 幂等：UPDATE 天然幂等。对已有 owner_columns 采用 CONCAT 追加，不覆盖既有登记
--   （比如某部署已经手登了 WAREHOUSE:storage.id），并且不重复追加（LIKE 判断）。
--   重复执行无副作用（CI 连跑两遍判据）。
-- ============================================================================
UPDATE `data_resource`
SET `owner_columns` = CASE
    WHEN `owner_columns` IS NULL OR `owner_columns` = '' THEN 'FACTORY:storage.id'
    WHEN `owner_columns` NOT LIKE '%FACTORY:storage.id%' THEN CONCAT(`owner_columns`, ',FACTORY:storage.id')
    ELSE `owner_columns`
END
WHERE `resource_key` IN ('wms.inventory', 'wms.move');
