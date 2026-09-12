-- Operator-reviewed first OWNER bootstrap; not run by application migrations.
-- Keep NULL until the user identifies the existing ACTIVE ADMIN account.
SET @workflow_owner_id = NULL;
START TRANSACTION;
SELECT id, name, role, status FROM admin_accounts ORDER BY id FOR UPDATE;
SET @workflow_owner_count = (SELECT COUNT(*) FROM admin_accounts WHERE role='OWNER' AND status='ACTIVE');
UPDATE admin_accounts SET role='OWNER', version=version+1, permissions_changed_at=UTC_TIMESTAMP(6)
WHERE id=@workflow_owner_id AND role='ADMIN' AND status='ACTIVE' AND @workflow_owner_count=0;
SET @workflow_owner_changed = ROW_COUNT();
INSERT INTO workflow_audit_events(resource, actor_id, action, before_value, after_value, reason, created_at)
SELECT CONCAT('staff:',id), id, 'BOOTSTRAP_OWNER', 'ADMIN', 'OWNER', 'Operator-reviewed first owner bootstrap', UTC_TIMESTAMP(6)
FROM admin_accounts WHERE id=@workflow_owner_id AND @workflow_owner_changed=1;
SELECT @workflow_owner_changed AS accounts_changed;
SELECT id, name, role, status FROM admin_accounts WHERE id=@workflow_owner_id;
-- Default is a dry run. Change only after reviewing the exact target and result.
ROLLBACK;
