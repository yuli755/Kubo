-- =====================================================================
-- 退场模块的目录收敛：菜单死链 / 死权限码 / 数据权限外键符号性
--
-- ## 背景（2026-09-28 真机联调实测）
-- 后端与前端真实启动、以管理员会话逐个探测 400 个端点后，发现三处「代码已退场、
-- 目录数据没跟着收敛」的漂移。它们都不会让应用崩，但会让系统看起来实现了、
-- 实际点进去是空白或报错 —— 属于「闭环管理」缺口。
--
-- ### 一、菜单死链（32 个可导航菜单里 5 个是死的，占 15.6%）
-- 前端 `src/view/console/**` 与后端 `module/**` 都已删除 iot / open / asset-device，
-- 但库里的菜单行还在，点进去是**注册了路由却没有组件的空白页**：
--
--     [131] 资产管理 / 设备管理   -> /console/asset/device/list
--     [152] 设备物联 / 设备概览   -> /console/iot/monitor/preview
--     [153] 设备物联 / 参数管理   -> /console/iot/parameter/list
--     [169] 系统管理 / 应用管理   -> /console/open/app/list
--     [170] 系统管理 / 通知管理   -> /console/open/notify/list
--
-- 连带两个**空壳一级分组**：`设备物联`(151) 与**重复的**`系统管理`(165)
-- （另一个 154 才是真的，有 7 个子菜单）。
-- V10 自称「期望：分组恰为上述 7 个一级节点」，但它只 `DELETE ... WHERE path LIKE
-- '/console/mes/%'`：既漏了 iot/open/asset-device，也没有任何清理空壳分组的逻辑
-- （分组节点 path 为空串，LIKE 规则永远匹配不到）。本脚本补齐，并**通用化**空分组清理。
--
-- 注意：`MenuService.initMenu()` 里已经没有这些菜单（对 iot/open/device 关键字命中 0），
-- 所以这是**存量库残留**，不是种子问题；删掉不会被重新播种。
--
-- ### 二、死权限码（463 条里 152 条是死的，占 32.8%）
-- 用 `/v3/api-docs` 的 400 个真实端点反推存活权限身份（`tag` → 控制器身份，
-- `operationId` → 方法名，规则见 `PermissionScanner` L105：`identity + "_" + method.getName()`），
-- 与库对账后，152 条权限对应的控制器**源码已不存在**：
--
--   MES 系(84)  Order17 Picking13 Plan13 Bom11 Operation11 Routing11 RoutingOperation8
--   IoT 系(21)  Device13 Parameter8
--   开放/聊天(39) OpenApp12 Notify10 Member8 Room7 OpenTest1 Oauth1
--   AI/MCP/企微(7) Index_ai Index_aiStream Index_aiAsync Mcp mcp:tools Wecom Wecom_callback
--   其他(1)      Status
--
-- 已逐个确认对应实体源码不存在：MemberEntity / RoomEntity / NotifyEntity / DeviceEntity /
-- ParameterEntity / OrderEntity / BomEntity 全部「不存在」。
--
-- 根因是 `PermissionService.loadPermission()` **只增改、从不删除**，且它只被
-- `DevDataInitRunner` 调用（需 dev 模式且无 init.lock），所以权限目录永不收敛。
-- 危害：角色授权界面里三分之一是可选项但**授了也没用**；`SYSTEM_ROOT` 持有全部 463 条；
-- 任何基于权限总数的统计与巡检都被污染。
--
-- 保留项（勿删）：
--   - `system:root` / `system:data-permission:bypass|ownership|outbox`：由 V2/V7 播种的
--     治理型权限，扫描器不产生，见 `SystemPermissionIdentities`；
--   - `Index` / `Index_index`：IndexController 仍存活，只有 3 个 AI 子权限死了；
--   - `Participant*`(8)：合同参与人，participant-controller 存活；与死掉的 `Member`(聊天室成员) 不同物。
--
-- ### 三、数据权限外键符号性不匹配（引用完整性静默失效）
-- 启动日志里唯一的 DDL 失败：
--
--     alter table data_field_policy add constraint FK6oky... foreign key (resource_id)
--       references data_resource (id)
--     -> Referencing column 'resource_id' and referenced column 'id' are incompatible.
--
-- 实测列类型：`data_resource.id` = bigint **unsigned**，而 `data_field_policy.resource_id`
-- 与 `data_policy.resource_id` = bigint（**有符号**）。MySQL 不允许有符号↔无符号建外键，
-- 于是**列级脱敏策略 → 数据资源**这条引用完整性在库层根本没建起来：删掉一个
-- data_resource，它的字段策略会变成悬挂引用而无人报错。
--
-- 成因：V2 的 DDL 写的是 `resource_id BIGINT UNSIGNED NOT NULL`（正确），但实体
-- `DataFieldPolicyEntity` / `DataPolicyEntity` 的 `@Column` 没有 columnDefinition，
-- JPA 建出有符号列；`CREATE TABLE IF NOT EXISTS` 又因表已存在而跳过 —— 与本项目
-- 「迁移脚本 / 实体 / H2 schema 三处同源」的约定相违。实体侧已同步修正，本脚本修存量库。
--
-- ## 幂等
-- 全部为 DELETE / 条件 ALTER，可重复执行。ALTER 前先探测当前列类型，已对齐则跳过。
--
-- ## 执行后自检
--   -- 1. 不应再有指向已退场模块的菜单
--   SELECT id,name,path FROM menu
--    WHERE path LIKE '/console/iot/%' OR path LIKE '/console/open/%' OR path LIKE '/console/asset/device/%';
--   -- 期望 0 行
--
--   -- 2. 一级分组应恰好 7 个，且无重名
--   SELECT name, COUNT(*) FROM menu WHERE (parent_id IS NULL OR parent_id = 0) GROUP BY name HAVING COUNT(*) > 1;
--   -- 期望 0 行
--
--   -- 3. 不应再有已退场模块的权限码
--   SELECT COUNT(*) FROM permission
--    WHERE identity REGEXP '^(Order|Picking|Plan|Bom|Operation|Routing|RoutingOperation|Device|Parameter|OpenApp|Notify|Member|Room|OpenTest|Oauth|Mcp|Wecom|Status)(_)?';
--   -- 期望 0
--
--   -- 4. 外键应已建立
--   SELECT constraint_name FROM information_schema.key_column_usage
--    WHERE table_schema = DATABASE() AND table_name = 'data_field_policy' AND referenced_table_name = 'data_resource';
--   -- 期望 1 行
--
-- 关联代码：MenuService.initMenu()、PermissionService.loadPermission()、
--           PermissionScanner.scanClasses()、DataFieldPolicyEntity、DataPolicyEntity
-- =====================================================================

-- ── 1. 菜单死链清理 ─────────────────────────────────────────────────────
-- 先删角色↔菜单关联，再删菜单本身（role_menu_list.menu_list_id 有外键指向 menu）。
DELETE rml FROM `role_menu_list` rml
  JOIN `menu` m ON m.`id` = rml.`menu_list_id`
 WHERE m.`path` LIKE '/console/iot/%'
    OR m.`path` LIKE '/console/open/%'
    OR m.`path` LIKE '/console/asset/device/%';

DELETE FROM `menu`
 WHERE `path` LIKE '/console/iot/%'
    OR `path` LIKE '/console/open/%'
    OR `path` LIKE '/console/asset/device/%';

-- 通用清理空壳分组：分组节点的 path/component 为空，子菜单被删光后就成了点不开的空目录。
-- 分组可嵌套（如「数据权限」175 挂在「系统管理」154 下），故循环删到不再变化为止。
DROP PROCEDURE IF EXISTS kubo_prune_empty_menu_group;

DELIMITER $$

CREATE PROCEDURE kubo_prune_empty_menu_group()
BEGIN
  DECLARE v_rows INT DEFAULT 1;
  WHILE v_rows > 0 DO
    DELETE FROM `menu`
     WHERE (`path` IS NULL OR `path` = '')
       AND NOT EXISTS (
             SELECT 1 FROM (SELECT `parent_id` FROM `menu`) child
              WHERE child.`parent_id` = `menu`.`id`
           );
    SET v_rows = ROW_COUNT();
  END WHILE;
END$$

DELIMITER ;

CALL kubo_prune_empty_menu_group();

DROP PROCEDURE IF EXISTS kubo_prune_empty_menu_group;

-- ── 2. 死权限码清理 ─────────────────────────────────────────────────────
-- 匹配规则：identity 恰为已退场控制器身份，或以「该身份 + 下划线」开头。
-- 下划线锚点是必要的：`Operation_%` 不会误伤 `OperationLog_%`，
-- `Routing_%` 不会误伤 `RoutingOperation_%`（后者由自己的分支覆盖）。
-- `Index` 控制器仍存活，只删它的 3 个 AI 子权限，故 Index 不进前缀表。
DELETE rpl FROM `role_permission_list` rpl
  JOIN `permission` p ON p.`id` = rpl.`permission_list_id`
 WHERE p.`identity` IN ('mcp:tools', 'Index_ai', 'Index_aiStream', 'Index_aiAsync')
    OR p.`identity` REGEXP '^(Order|Picking|Plan|Bom|Operation|Routing|RoutingOperation|Device|Parameter|OpenApp|OpenTest|Notify|Member|Room|Oauth|Mcp|Wecom|Status)(_)?';

DELETE FROM `permission`
 WHERE `identity` IN ('mcp:tools', 'Index_ai', 'Index_aiStream', 'Index_aiAsync')
    OR `identity` REGEXP '^(Order|Picking|Plan|Bom|Operation|Routing|RoutingOperation|Device|Parameter|OpenApp|OpenTest|Notify|Member|Room|Oauth|Mcp|Wecom|Status)(_)?';

-- ── 3. 数据权限外键符号性对齐 ───────────────────────────────────────────
DROP PROCEDURE IF EXISTS kubo_align_resource_id;

DELIMITER $$

-- 把 resource_id 对齐成 BIGINT UNSIGNED（与 V2 的 DDL 及 data_resource.id 一致），
-- 仅在类型尚未对齐时执行；随后补建外键（已存在则跳过）。
CREATE PROCEDURE kubo_align_resource_id(IN t VARCHAR(64))
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.`TABLES`
              WHERE `TABLE_SCHEMA` = DATABASE() AND `TABLE_NAME` = t) THEN

    IF EXISTS (SELECT 1 FROM information_schema.`COLUMNS`
                WHERE `TABLE_SCHEMA` = DATABASE()
                  AND `TABLE_NAME` = t
                  AND `COLUMN_NAME` = 'resource_id'
                  AND `COLUMN_TYPE` <> 'bigint unsigned') THEN
      SET @s = CONCAT('ALTER TABLE `', t,
                      '` MODIFY COLUMN `resource_id` BIGINT UNSIGNED NOT NULL COMMENT ''数据资源ID''');
      PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;

    -- 外键名固定，便于幂等判断与后续运维识别（不用 Hibernate 的随机 FK 名）
    IF NOT EXISTS (SELECT 1 FROM information_schema.`TABLE_CONSTRAINTS`
                    WHERE `TABLE_SCHEMA` = DATABASE()
                      AND `TABLE_NAME` = t
                      AND `CONSTRAINT_NAME` = CONCAT('fk_', t, '_resource')) THEN
      -- 悬挂引用会让建约束失败，先清掉（实测两表孤儿行均为 0，此处为防御）
      SET @s = CONCAT('DELETE FROM `', t, '` WHERE `resource_id` NOT IN (SELECT `id` FROM `data_resource`)');
      PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

      SET @s = CONCAT('ALTER TABLE `', t, '` ADD CONSTRAINT `fk_', t, '_resource`',
                      ' FOREIGN KEY (`resource_id`) REFERENCES `data_resource` (`id`)');
      PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;

  END IF;
END$$

DELIMITER ;

CALL kubo_align_resource_id('data_field_policy');
CALL kubo_align_resource_id('data_policy');

DROP PROCEDURE IF EXISTS kubo_align_resource_id;
