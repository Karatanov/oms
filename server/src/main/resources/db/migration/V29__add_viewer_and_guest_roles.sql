-- D1 RBAC roles required by the Phase 1 ToR.
-- Keep the legacy CONTRACTOR role intact for existing deployments.
INSERT IGNORE INTO roles (code, name)
VALUES ('VIEWER', 'Спостерігач'),
       ('GUEST', 'Гість');
