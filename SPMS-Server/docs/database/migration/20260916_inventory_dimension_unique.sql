-- =====================================================================
-- 库存维度唯一键（阶段 C-3）
-- 问题：inventory 表对「物料×仓库」「物料×生产单元」两个维度没有唯一约束，
--       并发「查无则建」（入库/移库完成时 create-if-absent）会插入重复行；
--       出库路径按单行读库存，重复行意味着后续出库永远少算总量。
-- 方案：先去重（同维度保留最小 id，数量合并到该行），再建唯一索引。
-- 约束：storage_id 与 structure_id 互斥使用（NULL 在 MySQL 唯一索引中互不
--       冲突），因此每个维度一条唯一索引，另一维度的 NULL 行不受该索引约束。
-- 幂等：可重复执行；索引存在时跳过创建。
-- 关联实体：cn.hamm.spms.module.wms.inventory.InventoryEntity
-- =====================================================================

-- 1) 去重：仓库维度（type=1，storage 非空）
UPDATE inventory i
JOIN (
    SELECT material_id, storage_id, MIN(id) AS keep_id, SUM(quantity) AS total_qty
    FROM inventory
    WHERE storage_id IS NOT NULL
    GROUP BY material_id, storage_id
    HAVING COUNT(*) > 1
) d ON i.material_id = d.material_id AND i.storage_id = d.storage_id
SET i.quantity = IF(i.id = d.keep_id, d.total_qty, i.quantity);

DELETE i FROM inventory i
JOIN (
    SELECT material_id, storage_id, MIN(id) AS keep_id
    FROM inventory
    WHERE storage_id IS NOT NULL
    GROUP BY material_id, storage_id
    HAVING COUNT(*) > 1
) d ON i.material_id = d.material_id AND i.storage_id = d.storage_id
WHERE i.id <> d.keep_id;

-- 2) 去重：生产单元维度（type=2，structure 非空）
UPDATE inventory i
JOIN (
    SELECT material_id, structure_id, MIN(id) AS keep_id, SUM(quantity) AS total_qty
    FROM inventory
    WHERE structure_id IS NOT NULL
    GROUP BY material_id, structure_id
    HAVING COUNT(*) > 1
) d ON i.material_id = d.material_id AND i.structure_id = d.structure_id
SET i.quantity = IF(i.id = d.keep_id, d.total_qty, i.quantity);

DELETE i FROM inventory i
JOIN (
    SELECT material_id, structure_id, MIN(id) AS keep_id
    FROM inventory
    WHERE structure_id IS NOT NULL
    GROUP BY material_id, structure_id
    HAVING COUNT(*) > 1
) d ON i.material_id = d.material_id AND i.structure_id = d.structure_id
WHERE i.id <> d.keep_id;

-- 3) 建唯一索引（MySQL 的 CREATE INDEX 不支持 IF NOT EXISTS，用存储过程判重）
DROP PROCEDURE IF EXISTS kubo_add_inventory_unique;
DELIMITER $$
CREATE PROCEDURE kubo_add_inventory_unique()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'inventory'
                     AND index_name = 'uk_inventory_material_storage') THEN
        CREATE UNIQUE INDEX uk_inventory_material_storage ON inventory (material_id, storage_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'inventory'
                     AND index_name = 'uk_inventory_material_structure') THEN
        CREATE UNIQUE INDEX uk_inventory_material_structure ON inventory (material_id, structure_id);
    END IF;
END$$
DELIMITER ;
CALL kubo_add_inventory_unique();
DROP PROCEDURE IF EXISTS kubo_add_inventory_unique;
