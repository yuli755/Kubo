#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
真多租户 + 数据权限「归属列」地基：静态扫描 + 幂等迁移脚本生成器

为什么要有这个脚本
------------------
Kubo 有 53 个 @Entity，逐表手工盘点归属列一定会漏、一定会和代码漂移。本脚本从源码事实生成两份产物：

1. `tenant_readiness_matrix.md`：每张表 → 资源键 / 是否已注册 / **数据库里真实存在的归属列** /
   声明了 `unique = true` 的列（真多租户必须降为 (tenant_id, col) 复合唯一）/ 接入分级。
2. 多租户地基迁移（幂等：建租户主表与用户租户关系、补 tenant_id / creator_id、建索引、唯一域降级、
   存量回填、校验清单、回滚说明）。2026-09-09 起该段已并入单文件
   `SPMS-Server/docs/database/migration/20260909_data_permission_migrations_consolidated.sql` 的
   「阶段 2/9」；本脚本默认仍把这段单独写进 `--out` 指定路径（默认 `20260906_multitenant_foundation.sql`），
   供改动实体后重生成、再并入合并文件时对照。

关键判断（不是猜的）
--------------------
- `@ManyToOne` 在库里**确实生成了外键列**（`storage_id` / `material_id` / `department_id`…），
  所以 SQL 文本层注入（MyBatis/JSqlParser）能直接 `inventory.storage_id IN (...)`；
  而 JPA 侧 `root.get("warehouseId")` 之所以空转，是因为实体没有映射这个标量字段。
- `@ManyToMany`（如 user→storageList）没有标量列，是**中间表**，因此「用户可见仓库集合」必须在
  服务端解析成 ID 集合再注入 IN，不能靠 SQL 关联。
- 业务表**没有任何** tenant / creator / owner 维度列，这才是行级权限的真正缺口。

只读源码 + 写文档目录，不改任何 Java/TS 代码。
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

RE_TABLE = re.compile(r'@Table\(\s*name\s*=\s*"([a-zA-Z0-9_]+)"', re.S)
RE_PACKAGE = re.compile(r'^\s*package\s+([\w.]+)\s*;', re.M)
RE_REGISTERED = re.compile(r'setResourceKey\(\s*"([a-zA-Z0-9_.]+)"\s*\)')

# 字段声明行（含可选初始化）。注解块用「向上回溯」策略收集，避免 columnDefinition
# 里带括号（如 varchar(255)）导致正则截断 —— 这正是早期版本漏掉 unique=true 的原因。
RE_FIELD_DECL = re.compile(r'^\s*(?:private|protected|public)\s+(?:final\s+)?[\w.]+(?:<[^;]*>)?(?:\[\])?\s+([a-zA-Z0-9_]+)\s*[;=]')
RE_JOIN_COLUMN_NAME = re.compile(r'@JoinColumn\(\s*[^)]*?name\s*=\s*"([a-zA-Z0-9_]+)"', re.S)

# 平台级 / 字典 / 权限元数据表：属于共享域，不加租户列
SHARED_TABLES = {
    "tenant", "user_tenant",
    "menu", "permission", "role", "role_permission_list", "role_menu_list",
    "config", "coderule", "file", "notify",
    # 权限元数据 + 审计/影子表：归属维度由 resource_key / user_id / trace_id 表达，不加租户列。
    # data_permission_diff_log 是 2026-09-06 补的：它落库后没进这个集合，于是被生成器当成业务表
    # 加了 creator_id/tenant_id —— 而实体不映射 creator_id。是 `pnpm ddl:check`（DDL↔实体漂移门禁）
    # 抓出来的，不是人看出来的。**新增 datapermission 下的表时必须同时登记这里**（否则该门禁红）。
    "data_resource", "data_policy", "data_field_policy", "permission_change_outbox",
    "data_permission_diff_log",
    "open_app", "open_app_permission_list",
}


def snake(name: str) -> str:
    """Spring Boot 默认物理命名策略：camelCase -> snake_case"""
    s = re.sub(r"(.)([A-Z][a-z]+)", r"\1_\2", name)
    return re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", s).lower()


@dataclass
class Entity:
    path: Path
    class_name: str
    table: str | None
    module: str
    unique_columns: list[str] = field(default_factory=list)
    scope_columns: list[str] = field(default_factory=list)
    m2m: list[str] = field(default_factory=list)

    @property
    def resource_key(self) -> str:
        base = self.class_name[:-6] if self.class_name.endswith("Entity") else self.class_name
        return f"{self.module}.{base[0].lower() + base[1:]}" if base else self.module

    @property
    def java_rel(self) -> str:
        return self.path.name


def module_of(path: Path) -> str:
    parts = path.parts
    if "module" in parts and parts.index("module") + 1 < len(parts):
        return parts[parts.index("module") + 1]
    return "unknown"


def annotations_above(lines: list[str], idx: int) -> str:
    """向上收集紧邻的注解块。

    - 以 `@` 开头的行必收；
    - 若已收文本括号不平衡（有未闭合的 `(`），说明注解参数被换行拆开，继续向上收；
    - 遇空行 / 注释 / 上一条语句边界即停。
    """
    buf: list[str] = []
    balance = 0
    j = idx - 1
    while j >= 0:
        s = lines[j].strip()
        if not s.startswith("@") and balance == 0:
            if s == "" or s.startswith("//") or s.startswith("*") or s.startswith("/*"):
                j -= 1
                continue
            break
        buf.append(s)
        balance += s.count("(") - s.count(")")
        j -= 1
    return "\n".join(reversed(buf))


def scan_entities(java_root: Path) -> list[Entity]:
    ents: list[Entity] = []
    for p in sorted(java_root.rglob("*Entity.java")):
        src = p.read_text(encoding="utf-8")
        if "@Entity" not in src:
            continue
        lines = src.splitlines()
        tm = RE_TABLE.search(src)
        ent = Entity(
            path=p,
            class_name=p.stem,
            table=tm.group(1) if tm else None,
            module=module_of(p),
        )
        for i, line in enumerate(lines):
            fm = RE_FIELD_DECL.match(line)
            if not fm:
                continue
            field_name = fm.group(1)
            ann = annotations_above(lines, i)
            if "unique = true" in ann or "unique=true" in ann:
                ent.unique_columns.append(snake(field_name))
            if "@ManyToOne" in ann:
                jc = RE_JOIN_COLUMN_NAME.findall(ann)
                ent.scope_columns.append(jc[-1] if jc else f"{snake(field_name)}_id")
            if "@ManyToMany" in ann:
                ent.m2m.append(snake(field_name))
            if re.match(r"^\s*private\s+Long\s+[a-zA-Z0-9_]*Id\s*[;=]", line):
                ent.scope_columns.append(snake(field_name))

        ent.unique_columns = sorted(set(ent.unique_columns))
        ent.scope_columns = sorted(set(ent.scope_columns) - {"id"})
        ents.append(ent)
    return ents


def load_registered(java_root: Path) -> set[str]:
    for cand in [java_root / "DevDataInitRunner.java", *java_root.rglob("DevDataInitRunner.java")]:
        if cand.exists():
            return set(RE_REGISTERED.findall(cand.read_text(encoding="utf-8")))
    print("⚠️  未找到 DevDataInitRunner.java，已注册资源按空集合处理", file=sys.stderr)
    return set()


def tier_of(ent: Entity, registered: set[str]) -> tuple[str, str]:
    if ent.table in SHARED_TABLES:
        return "T3", "共享域（平台/字典/权限元数据）→ 不加租户列"
    if ent.resource_key in registered:
        return "T1", "已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度"
    if ent.table and ent.table.endswith("_detail"):
        return "T2", "单据明细表 → 随主表加列（冗余 tenant_id 便于直接过滤，避免强制 JOIN）"
    return "T4", "未注册业务表 → 先只加列不拦截，按模块排期接入"


SQL_HEADER = """-- ============================================================================
-- Kubo 真多租产地基 + 数据权限归属列
-- 由 tools/scripts/gen_multitenant_migration.py 自动生成（请勿手改逻辑，改生成器）
--
-- 特性：幂等可重复执行 / 不删列 / 不截断 / 每步可独立回滚
-- 前置：MySQL 8.0+；执行前备份；`spring.jpa.hibernate.ddl-auto` 必须为 validate
-- 语义：存量数据全部归属「平台默认租户」tenant_id = {default_tenant}
-- 执行：mysql -u<user> -p <db> < 20260906_multitenant_foundation.sql
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0. 幂等工具过程
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS kubo_mt_add_column;
DROP PROCEDURE IF EXISTS kubo_mt_add_index;
DROP PROCEDURE IF EXISTS kubo_mt_replace_unique;
DELIMITER $$

CREATE PROCEDURE kubo_mt_add_column(IN t VARCHAR(64), IN c VARCHAR(64), IN col_ddl TEXT)
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.TABLES
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t)
     AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND COLUMN_NAME = c) THEN
    SET @s = CONCAT('ALTER TABLE `', t, '` ADD COLUMN ', col_ddl);
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END IF;
END$$

CREATE PROCEDURE kubo_mt_add_index(IN t VARCHAR(64), IN idx VARCHAR(64), IN cols TEXT)
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.TABLES
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t)
     AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND INDEX_NAME = idx) THEN
    SET @s = CONCAT('ALTER TABLE `', t, '` ADD INDEX `', idx, '` (', cols, ')');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END IF;
END$$

-- 单列唯一 → (tenant_id, col) 复合唯一；先按列反查真实索引名再 DROP（Hibernate 生成的索引名不可预测）
CREATE PROCEDURE kubo_mt_replace_unique(IN t VARCHAR(64), IN c VARCHAR(64))
BEGIN
  DECLARE old_idx VARCHAR(64) DEFAULT NULL;
  IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t) THEN
    -- 表不存在 = 该资源还没建表 / 不属于本库：与 kubo_mt_add_column、kubo_mt_add_index 同构地静默跳过。
    -- 这里原本 SIGNAL SQLSTATE 45000。CI 的 MySQL 8 job（跑真脚本、跑两遍）实测出它是有害的：
    -- 空库上会打出 64 条 ERROR 1644，而任何非 --force 的客户端在第一条就中止 ——
    -- 于是排在后面的 20260907（filterable_columns、data_permission_diff_log）永远建不出来，
    -- "权限内核表"会静默缺席。真正需要响的"表在但唯一键形状不对"仍由下面的 old_idx/新键分支处理。
    SET @s = NULL;
  END IF;

  SELECT INDEX_NAME INTO old_idx
    FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND NON_UNIQUE = 0
   GROUP BY INDEX_NAME
  HAVING COUNT(*) = 1 AND MAX(CASE WHEN COLUMN_NAME = c THEN 1 ELSE 0 END) = 1
   LIMIT 1;

  IF old_idx IS NOT NULL THEN
    SET @s = CONCAT('ALTER TABLE `', t, '` DROP INDEX `', old_idx, '`');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND INDEX_NAME = CONCAT('uk_', t, '_tenant_', c)) THEN
    SET @s = CONCAT('ALTER TABLE `', t, '` ADD UNIQUE KEY `uk_', t, '_tenant_', c, '` (tenant_id, `', c, '`)');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------------
-- 1. 租户主表 + 用户-租户关系（服务端可信租户来源，替代 X-Tenant-Id 请求头）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tenant` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` BIGINT NULL,
    `update_time` BIGINT NULL,
    `is_disabled` BIT(1) NOT NULL DEFAULT b'0',
    `is_published` BIT(1) NOT NULL DEFAULT b'0',
    `code` VARCHAR(64) NOT NULL COMMENT '租户编码',
    `name` VARCHAR(255) NOT NULL COMMENT '租户名称',
    `status` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    `expire_time` BIGINT NULL COMMENT '到期时间(ms)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户';

CREATE TABLE IF NOT EXISTS `user_tenant` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` BIGINT NULL,
    `update_time` BIGINT NULL,
    `is_disabled` BIT(1) NOT NULL DEFAULT b'0',
    `is_published` BIT(1) NOT NULL DEFAULT b'0',
    `user_id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL,
    `is_default` BIT(1) NOT NULL DEFAULT b'0' COMMENT '登录后的默认租户',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_tenant` (`user_id`, `tenant_id`),
    KEY `idx_user_tenant_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户可访问的租户集合';

INSERT INTO `tenant` (`id`, `code`, `name`, `status`, `is_published`)
SELECT {default_tenant}, 'default', '平台默认租户', 1, b'1'
 WHERE NOT EXISTS (SELECT 1 FROM `tenant` WHERE `id` = {default_tenant});

INSERT INTO `user_tenant` (`user_id`, `tenant_id`, `is_default`)
SELECT u.`id`, {default_tenant}, b'1' FROM `user` u
 WHERE NOT EXISTS (SELECT 1 FROM `user_tenant` ut WHERE ut.`user_id` = u.`id`);
"""


def emit_sql(entities: list[Entity], registered: set[str], default_tenant: int, backfill_creator: int) -> str:
    out = [SQL_HEADER.format(default_tenant=default_tenant), "", "-- ---------------------------------------------------------------------------",
           "-- 2. 业务表：加列 + 索引 + 唯一域降级 + 存量回填", "-- ---------------------------------------------------------------------------"]
    for ent in sorted((e for e in entities if e.table and e.table not in SHARED_TABLES), key=lambda e: e.table):
        tier, _ = tier_of(ent, registered)
        out.append(f"-- [{tier}] {ent.resource_key}  ← {ent.java_rel}")
        out.append(f"CALL kubo_mt_add_column('{ent.table}', 'tenant_id', "
                   f"'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT {default_tenant} COMMENT ''租户ID''');")
        out.append(f"CALL kubo_mt_add_column('{ent.table}', 'creator_id', "
                   f"'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');")
        out.append(f"CALL kubo_mt_add_index('{ent.table}', 'idx_{ent.table}_tenant', 'tenant_id');")
        if ent.scope_columns:
            out.append(f"-- 可用归属列：{', '.join(ent.scope_columns)}")
        if ent.m2m:
            out.append(f"-- ⚠️ @ManyToMany 无标量列（中间表）：{', '.join(ent.m2m)} → 只能服务端解析成 ID 集合再注入 IN")
        for col in ent.unique_columns:
            out.append(f"CALL kubo_mt_replace_unique('{ent.table}', '{col}');  -- 原唯一列 {col} → (tenant_id, {col})")
        out.append(f"UPDATE `{ent.table}` SET `tenant_id` = {default_tenant} WHERE `tenant_id` IS NULL OR `tenant_id` = 0;")
        out.append(f"UPDATE `{ent.table}` SET `creator_id` = {backfill_creator} WHERE `creator_id` IS NULL;")
        out.append("")

    out += ["-- ---------------------------------------------------------------------------",
            "-- 3. 执行后校验（任一非 0 都不得开启 enforce；建议做成 CI 检查）",
            "-- ---------------------------------------------------------------------------"]
    for e in sorted((x for x in entities if x.table and x.table not in SHARED_TABLES), key=lambda x: x.table):
        out.append(f"SELECT '{e.table}' tbl, COUNT(*) bad_tenant FROM `{e.table}` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;")
    out += ["",
            "-- ---------------------------------------------------------------------------",
            "-- 4. 回滚",
            f"--    · 数据：UPDATE <表> SET tenant_id = {default_tenant};",
            "--    · 能力：kubo.data-permission.mode = shadow|off（应用侧开关，不依赖本脚本）",
            "--    · 索引：ALTER TABLE <表> DROP INDEX uk_<表>_tenant_<col>; 仅在确认无跨租户重复后重建单列唯一",
            "--    · 严禁在 ddl-auto=validate 环境 DROP COLUMN（会让启动失败）",
            "-- ---------------------------------------------------------------------------"]
    return "\n".join(out) + "\n"


def emit_matrix(entities: list[Entity], registered: set[str]) -> str:
    stat: dict[str, int] = {"T1": 0, "T2": 0, "T3": 0, "T4": 0}
    rows = ["| 模块 | 资源键 | 注册 | 表 | SQL 可注入的归属列 | 唯一列(需降级) | 分级 | 说明 |",
            "|---|---|---|---|---|---|---|---|"]
    for ent in sorted(entities, key=lambda e: (e.module, e.class_name)):
        tier, note = tier_of(ent, registered)
        stat[tier] += 1
        scope = ", ".join(f"`{c}`" for c in ent.scope_columns) or "**无**"
        if ent.m2m:
            scope += f"（M2M: {', '.join(ent.m2m)}）"
        rows.append(f"| {ent.module} | `{ent.resource_key}` | {'✅' if ent.resource_key in registered else '—'} "
                    f"| `{ent.table or '—'}` | {scope} | {len(ent.unique_columns) or '—'} | {tier} | {note} |")

    t1 = [e for e in entities if tier_of(e, registered)[0] == "T1"]
    no_scope = [e for e in t1 if not e.scope_columns]
    uniq = sum(len(e.unique_columns) for e in entities if e.table not in SHARED_TABLES)
    return "\n".join([
        "# 真多租户 · 归属就绪矩阵（脚本自动生成）",
        "",
        "> 生成：`python3 tools/scripts/gen_multitenant_migration.py`；表格内容由扫描结果决定，请勿手工编辑。",
        "",
        f"- 扫描 `@Entity`：**{len(entities)}**；已注册数据资源：**{len(registered)}**",
        f"- 分级：T1 已注册 **{stat['T1']}** / T2 明细表 **{stat['T2']}** / T3 共享域 **{stat['T3']}** / T4 未注册 **{stat['T4']}**",
        f"- 需从「单列唯一」降为「(tenant_id, col) 复合唯一」的列：**{uniq} 个**（这是真多租户最大的兼容性风险点）",
        f"- ⚠️ 已注册但**没有任何可注入归属列**的资源（行级只能做 tenant 维度）："
        + (", ".join(f"`{e.resource_key}`" for e in no_scope) if no_scope else "无"),
        "",
        "## 如何读这张表",
        "",
        "- `SQL 可注入的归属列`：来自 `@ManyToOne` 生成的外键列（`xxx_id`）或手写标量 `xxxId`。",
        "  MyBatis/JSqlParser 在 SQL 文本层注入时**可以直接用这些列**，无需改实体；",
        "  这正是之前 JPA `root.get(\"warehouseId\")` 空转的根因（实体没映射该标量字段）。",
        "- `M2M`：中间表关系，SQL 里没有标量列，必须在服务端展开成 ID 集合后注入 `IN (...)`。",
        "- `T4`：未注册资源。enforce 模式下未注册即默认拒绝，因此要么补注册、要么显式登记为共享/豁免，",
        "  **不允许靠 `catch → return 放行`**（当前 `DataPermissionSpecification:67` 就是这个形状）。",
        "",
        "---",
        "",
    ] + rows) + "\n"


def main() -> int:
    ap = argparse.ArgumentParser(description="生成真多租产地基迁移与归属矩阵")
    ap.add_argument("--root", default="SPMS-Server/src/main/java/cn/hamm/spms")
    ap.add_argument("--out", default="SPMS-Server/docs/database/migration/20260906_multitenant_foundation.sql")
    ap.add_argument("--matrix", default="SPMS-Server/docs/database/tenant_readiness_matrix.md")
    ap.add_argument("--default-tenant", type=int, default=1)
    ap.add_argument("--backfill-creator", type=int, default=1, help="存量数据 creator_id 回填值（默认超级管理员 id=1）")
    args = ap.parse_args()

    java_root = Path(args.root)
    if not java_root.exists():
        print(f"❌ 源码根不存在：{java_root}（请在仓库根执行）", file=sys.stderr)
        return 2

    entities = scan_entities(java_root)
    registered = load_registered(java_root)
    sql = emit_sql(entities, registered, args.default_tenant, args.backfill_creator)
    matrix = emit_matrix(entities, registered)

    for path, content in ((args.out, sql), (args.matrix, matrix)):
        p = Path(path)
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content, encoding="utf-8")
        print(f"✅ {path}  ({len(content.splitlines())} 行)")
    print(f"   实体 {len(entities)} / 已注册资源 {len(registered)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
