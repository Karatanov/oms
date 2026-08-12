UPDATE projects
SET construction_type = CASE
    WHEN LOWER(construction_type) IN ('reconstruction', 'реконструкція') THEN 'reconstruction'
    WHEN LOWER(construction_type) IN ('capital_repair', 'капітальний ремонт') THEN 'capital_repair'
    WHEN LOWER(construction_type) IN ('new_construction', 'нове будівництво') THEN 'new_construction'
    ELSE 'reconstruction'
END;

ALTER TABLE projects
    MODIFY COLUMN construction_type ENUM('reconstruction', 'capital_repair', 'new_construction') NOT NULL;
