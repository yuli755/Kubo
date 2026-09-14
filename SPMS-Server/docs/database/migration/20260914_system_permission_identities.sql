-- 2026-09-14：补全 SystemPermissionIdentities 声明的全部系统权限身份。
--
-- 背景：20260909 合并迁移只种了 system:root 与 system:data-permission:bypass 两行，
-- 而 SystemPermissionIdentities 声明了四个身份。ownership（归属迁移）与 outbox（死信管理）
-- 没有权限行，管理端就无法把这两项特权授予任何非 root 角色 —— 两个特权端点只剩
-- isRootUser() 兜底一条路，"仅平台管理员可执行"退化成"仅 root 可执行"。
--
-- 幂等：与 20260909 相同的 INSERT ... WHERE NOT EXISTS 模式，可安全重放。
-- 授予关系：SYSTEM_ROOT 角色对 is_system 权限的绑定由合并迁移里的
-- role_permission_list CROSS JOIN（p.is_system = b'1'）覆盖；此处补行后该绑定
-- 对重放后的库自动生效。已执行过合并迁移且不会再重放的部署，如需把特权授予
-- 非 root「平台管理员」角色，请在执行本文件后于角色管理端自行勾选。

INSERT INTO `permission` (`identity`, `name`, `is_system`, `type`)
SELECT 'system:data-permission:ownership', '迁移数据归属', b'1', 0
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `identity` = 'system:data-permission:ownership');

INSERT INTO `permission` (`identity`, `name`, `is_system`, `type`)
SELECT 'system:data-permission:outbox', '权限变更死信管理', b'1', 0
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `identity` = 'system:data-permission:outbox');

-- 把新增的两条 is_system 权限补授给已存在的 SYSTEM_ROOT 角色（与合并迁移的授权口径一致）
INSERT INTO `role_permission_list` (`role_entity_id`, `permission_list_id`)
SELECT r.`id`, p.`id`
FROM `role` r
CROSS JOIN `permission` p
WHERE r.`code` = 'SYSTEM_ROOT'
  AND p.`identity` IN ('system:data-permission:ownership', 'system:data-permission:outbox')
  AND NOT EXISTS (
      SELECT 1
      FROM `role_permission_list` x
      WHERE x.`role_entity_id` = r.`id`
        AND x.`permission_list_id` = p.`id`
  );
