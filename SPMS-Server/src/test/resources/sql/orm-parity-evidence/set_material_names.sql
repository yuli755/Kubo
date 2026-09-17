-- OrmParityEvidenceTest：让 id=1 的行满足 business Specification 的 name = "A"
UPDATE material SET name = CASE id WHEN 1 THEN 'A' ELSE 'B' END;
