-- =====================================================================
-- 菜单权限标识列（阶段 F-4）
-- 问题：前端路由守卫 permissionGuard 与 Console.vue 的 applyMenuPermissions
--       均已按 menu.permission → meta.permission 接通，但后端 menu 表没有
--       permission 列，权限与菜单是两套互不关联的数据——知道路径就能直接
--       访问任意已注册路由，菜单可见性形同虚设。
-- 方案：menu 表加 permission 列（默认空串 = 不校验，行为与存量完全一致；
--       非空时前端要求当前用户权限集合包含该标识才放行该路由）。
-- 幂等：可重复执行；列已存在时跳过。
-- 关联实体：cn.hamm.spms.module.system.menu.MenuEntity
-- =====================================================================
DROP PROCEDURE IF EXISTS kubo_add_menu_permission;
DELIMITER $$
CREATE PROCEDURE kubo_add_menu_permission()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'menu'
                     AND column_name = 'permission') THEN
        ALTER TABLE menu ADD COLUMN permission varchar(255) DEFAULT '' COMMENT '权限标识，如 Material_getPage；非空时前端路由守卫要求用户拥有该权限';
    END IF;
END$$
DELIMITER ;
CALL kubo_add_menu_permission();
DROP PROCEDURE IF EXISTS kubo_add_menu_permission;
