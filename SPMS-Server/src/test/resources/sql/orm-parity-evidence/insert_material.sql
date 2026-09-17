-- OrmParityEvidenceTest 的逐行插入（4 个占位符按 id/tenantId/creatorId/ownerId 传参）
INSERT INTO material (id, tenant_id, creator_id, owner_id) VALUES (?, ?, ?, ?);
