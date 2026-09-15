-- ============================================================================
-- TasKlean Dev Seed Data (Repeatable Flyway Migration)
-- Runs after all versioned migrations. Re-runs when checksum changes.
-- All inserts are idempotent via ON CONFLICT DO NOTHING.
-- ============================================================================

-- ============================================================================
-- USERS (3 users: Alice is Google-linked, Bob and Charlie are email-based)
-- Platform roles: Alice = SUPER_ADMIN, Charlie = ADMIN (support), Bob = USER —
-- so all three platform roles are available for testing RBAC in dev.
-- ============================================================================
INSERT INTO "user" (uid, email, password_hash, name, middle_name, last_name, photo_url, google_sub, is_email_verified, role)
VALUES
    ('5039facc-3ea1-48f9-87ad-825c2531a3e9', 'alice@example.com', NULL, 'Alice', NULL, 'Johnson', NULL, 'google-sub-alice-12345', TRUE, 'SUPER_ADMIN'),
    ('52ca49f0-40c9-4a16-b089-3c0bbf865d27', 'bob@example.com', '$2a$10$dummyhashfordevonly000000000000000000000000000000', 'Bob', 'James', 'Smith', NULL, NULL, TRUE, 'USER'),
    ('74e61bd9-3971-43b2-8a28-473548ec7000', 'charlie@example.com', '$2a$10$dummyhashfordevonly111111111111111111111111111111', 'Charlie', NULL, 'Williams', NULL, NULL, TRUE, 'ADMIN')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- DEVICES (2 devices: Alice has a phone, Bob has a browser)
-- ============================================================================
INSERT INTO device (device_token, device_type, device_name, browser_info, os_version, app_version, user_id)
VALUES
    ('fcm-token-alice-phone-001', 'ANDROID', 'Pixel 8', NULL, 'Android 14', '1.0.0',
     (SELECT id_user FROM "user" WHERE email = 'alice@example.com')),
    ('fcm-token-bob-browser-001', 'WEB', 'Chrome Desktop', 'Chrome 126.0', 'Windows 11', '1.0.0',
     (SELECT id_user FROM "user" WHERE email = 'bob@example.com'))
ON CONFLICT DO NOTHING;

-- ============================================================================
-- GROUPS (2 groups: "Apartment 4B" and "Family Home")
-- ============================================================================
INSERT INTO "group" (uid, name, description, photo_url, invite_code)
VALUES
    ('3ac1fa2b-e637-4db0-b74f-7f8c61a33350', 'Apartment 4B', 'Roommates in apartment 4B', NULL, 'APT4B001'),
    ('7d95edc0-c88b-493b-bf80-dd9414c21512', 'Family Home', 'The Williams family household', NULL, 'FAMLY002')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- GROUP MEMBERS
-- Alice = GROUP_ADMIN of Apartment 4B, GROUP_MEMBER of Family Home (multi-group user)
-- Bob   = GROUP_MEMBER of Apartment 4B
-- Charlie = GROUP_ADMIN of Family Home, GROUP_MEMBER of Apartment 4B
-- ============================================================================
INSERT INTO group_member (role, user_id, group_id)
VALUES
    ('GROUP_ADMIN',
     (SELECT id_user FROM "user" WHERE email = 'alice@example.com'),
     (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')),
    ('GROUP_MEMBER',
     (SELECT id_user FROM "user" WHERE email = 'bob@example.com'),
     (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')),
    ('GROUP_MEMBER',
     (SELECT id_user FROM "user" WHERE email = 'charlie@example.com'),
     (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')),
    ('GROUP_ADMIN',
     (SELECT id_user FROM "user" WHERE email = 'charlie@example.com'),
     (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002')),
    ('GROUP_MEMBER',
     (SELECT id_user FROM "user" WHERE email = 'alice@example.com'),
     (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002'))
ON CONFLICT DO NOTHING;

-- ============================================================================
-- CATEGORIES (per group)
-- Apartment 4B: Kitchen, Bathroom, Common Areas
-- Family Home: Indoor, Outdoor
-- ============================================================================
INSERT INTO category (name, color, icon, group_id)
VALUES
    ('Kitchen', '#FF6B35', 'kitchen', (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')),
    ('Bathroom', '#4ECDC4', 'bathroom', (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')),
    ('Common Areas', '#45B7D1', 'living_room', (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')),
    ('Indoor', '#96CEB4', 'home', (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002')),
    ('Outdoor', '#FFEAA7', 'garden', (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002'))
ON CONFLICT DO NOTHING;

-- ============================================================================
-- TAGS (per group)
-- Apartment 4B: quick, deep-clean, weekly
-- Family Home: morning, weekend, seasonal
-- ============================================================================
INSERT INTO tag (name, color, group_id)
VALUES
    ('quick', '#2ECC71', (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')),
    ('deep-clean', '#E74C3C', (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')),
    ('weekly', '#3498DB', (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')),
    ('morning', '#F39C12', (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002')),
    ('weekend', '#9B59B6', (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002')),
    ('seasonal', '#1ABC9C', (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002'))
ON CONFLICT DO NOTHING;

-- ============================================================================
-- TASKS (8 tasks with varied statuses, priorities, recurrence, assignments)
-- ============================================================================

-- Apartment 4B tasks (created by Alice the admin, various assignments)
INSERT INTO task (uid, name, description, priority, recurrence_type, recurrence_pattern, next_due_date,
                  requires_photo_proof, estimated_time_minutes, status, group_id, created_by, assigned_to, category_id)
VALUES
    -- Weekly kitchen clean, assigned to Bob, recurring
    ('e26d3a60-e882-4249-9435-7f5ae8a08c30', 'Clean kitchen counters', 'Wipe down all counters and stovetop', 'HIGH', 'WEEKLY',
     '{"dayOfWeek": "MONDAY"}', NOW() + INTERVAL '3 days', FALSE, 20, 'TODO',
     (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001'),
     (SELECT gm.id_group_member FROM group_member gm JOIN "user" u ON gm.user_id = u.id_user WHERE u.email = 'alice@example.com' AND gm.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')),
     (SELECT gm.id_group_member FROM group_member gm JOIN "user" u ON gm.user_id = u.id_user WHERE u.email = 'bob@example.com' AND gm.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')),
     (SELECT id_category FROM category WHERE name = 'Kitchen' AND group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001'))),

    -- Bathroom deep clean, assigned to Charlie, requires photo proof
    ('79b4c0c6-567f-464c-b7c8-20c03121a111', 'Deep clean bathroom', 'Scrub tiles, clean toilet, mop floor', 'MEDIUM', 'MONTHLY',
     '{"dayOfMonth": 1}', NOW() + INTERVAL '10 days', TRUE, 45, 'TODO',
     (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001'),
     (SELECT gm.id_group_member FROM group_member gm JOIN "user" u ON gm.user_id = u.id_user WHERE u.email = 'alice@example.com' AND gm.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')),
     (SELECT gm.id_group_member FROM group_member gm JOIN "user" u ON gm.user_id = u.id_user WHERE u.email = 'charlie@example.com' AND gm.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')),
     (SELECT id_category FROM category WHERE name = 'Bathroom' AND group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001'))),

    -- Vacuuming, assigned to Alice herself, in progress
    ('60e1f1e7-82a8-49f7-ab7b-d334344e8245', 'Vacuum living room', 'Vacuum carpet and under furniture', 'LOW', 'WEEKLY',
     '{"dayOfWeek": "THURSDAY"}', NOW() + INTERVAL '1 day', FALSE, 15, 'IN_PROGRESS',
     (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001'),
     (SELECT gm.id_group_member FROM group_member gm JOIN "user" u ON gm.user_id = u.id_user WHERE u.email = 'alice@example.com' AND gm.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')),
     (SELECT gm.id_group_member FROM group_member gm JOIN "user" u ON gm.user_id = u.id_user WHERE u.email = 'alice@example.com' AND gm.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')),
     (SELECT id_category FROM category WHERE name = 'Common Areas' AND group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001'))),

    -- One-off task, no recurrence, completed
    ('452b89e0-6cd5-4293-a8dd-916440b470cf', 'Fix leaky faucet', 'Kitchen sink drips when turned off', 'HIGH', NULL,
     NULL, NULL, FALSE, 30, 'COMPLETED',
     (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001'),
     (SELECT gm.id_group_member FROM group_member gm JOIN "user" u ON gm.user_id = u.id_user WHERE u.email = 'bob@example.com' AND gm.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')),
     (SELECT gm.id_group_member FROM group_member gm JOIN "user" u ON gm.user_id = u.id_user WHERE u.email = 'bob@example.com' AND gm.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')),
     (SELECT id_category FROM category WHERE name = 'Kitchen' AND group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001'))),

    -- Archived task
    ('b431c977-ff46-4c37-85ef-3368b1d76ecf', 'Replace air freshener', 'Bathroom air freshener ran out', 'LOW', NULL,
     NULL, NULL, FALSE, 5, 'ARCHIVED',
     (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001'),
     (SELECT gm.id_group_member FROM group_member gm JOIN "user" u ON gm.user_id = u.id_user WHERE u.email = 'charlie@example.com' AND gm.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')),
     NULL,
     (SELECT id_category FROM category WHERE name = 'Bathroom' AND group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')))
ON CONFLICT DO NOTHING;

-- Family Home tasks (created by Charlie the admin)
INSERT INTO task (uid, name, description, priority, recurrence_type, recurrence_pattern, next_due_date,
                  requires_photo_proof, estimated_time_minutes, status, group_id, created_by, assigned_to, category_id)
VALUES
    -- Daily morning task
    ('d090bf88-ec34-48c8-8d37-e9c6a0412c3f', 'Make beds', 'Make all beds in the house', 'MEDIUM', 'DAILY',
     NULL, NOW() + INTERVAL '1 day', FALSE, 10, 'TODO',
     (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002'),
     (SELECT gm.id_group_member FROM group_member gm JOIN "user" u ON gm.user_id = u.id_user WHERE u.email = 'charlie@example.com' AND gm.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002')),
     (SELECT gm.id_group_member FROM group_member gm JOIN "user" u ON gm.user_id = u.id_user WHERE u.email = 'alice@example.com' AND gm.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002')),
     (SELECT id_category FROM category WHERE name = 'Indoor' AND group_id = (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002'))),

    -- Weekend outdoor task with photo proof
    ('8650837b-dd07-4d5c-85d3-1106f121f3bf', 'Mow the lawn', 'Front and back yard', 'HIGH', 'WEEKLY',
     '{"dayOfWeek": "SATURDAY"}', NOW() + INTERVAL '5 days', TRUE, 60, 'TODO',
     (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002'),
     (SELECT gm.id_group_member FROM group_member gm JOIN "user" u ON gm.user_id = u.id_user WHERE u.email = 'charlie@example.com' AND gm.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002')),
     (SELECT gm.id_group_member FROM group_member gm JOIN "user" u ON gm.user_id = u.id_user WHERE u.email = 'charlie@example.com' AND gm.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002')),
     (SELECT id_category FROM category WHERE name = 'Outdoor' AND group_id = (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002'))),

    -- Unassigned task, no category
    ('1a8a71dc-7df7-4d04-a8ef-614002c1f305', 'Organize garage', 'Sort tools and storage boxes', 'LOW', NULL,
     NULL, NULL, FALSE, 120, 'TODO',
     (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002'),
     (SELECT gm.id_group_member FROM group_member gm JOIN "user" u ON gm.user_id = u.id_user WHERE u.email = 'charlie@example.com' AND gm.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002')),
     NULL,
     NULL)
ON CONFLICT DO NOTHING;

-- ============================================================================
-- TASK TAGS
-- ============================================================================
INSERT INTO task_tag (task_id, tag_id)
VALUES
    -- "Clean kitchen counters" tagged: quick, weekly
    ((SELECT id_task FROM task WHERE name = 'Clean kitchen counters'),
     (SELECT id_tag FROM tag WHERE name = 'quick' AND group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001'))),
    ((SELECT id_task FROM task WHERE name = 'Clean kitchen counters'),
     (SELECT id_tag FROM tag WHERE name = 'weekly' AND group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001'))),

    -- "Deep clean bathroom" tagged: deep-clean
    ((SELECT id_task FROM task WHERE name = 'Deep clean bathroom'),
     (SELECT id_tag FROM tag WHERE name = 'deep-clean' AND group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001'))),

    -- "Make beds" tagged: morning
    ((SELECT id_task FROM task WHERE name = 'Make beds'),
     (SELECT id_tag FROM tag WHERE name = 'morning' AND group_id = (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002'))),

    -- "Mow the lawn" tagged: weekend, seasonal
    ((SELECT id_task FROM task WHERE name = 'Mow the lawn'),
     (SELECT id_tag FROM tag WHERE name = 'weekend' AND group_id = (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002'))),
    ((SELECT id_task FROM task WHERE name = 'Mow the lawn'),
     (SELECT id_tag FROM tag WHERE name = 'seasonal' AND group_id = (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002')))
ON CONFLICT DO NOTHING;

-- ============================================================================
-- TASK COMPLETIONS (3 completions: one with photo, two without)
-- ============================================================================
INSERT INTO task_completion (completion_photo_url, completion_note, date_completed, task_id, group_member_id)
SELECT NULL, 'Fixed with new washer from hardware store', NOW() - INTERVAL '2 days',
       t.id_task, gm.id_group_member
FROM task t
JOIN group_member gm ON gm.user_id = (SELECT id_user FROM "user" WHERE email = 'bob@example.com')
    AND gm.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')
WHERE t.name = 'Fix leaky faucet'
AND NOT EXISTS (SELECT 1 FROM task_completion tc WHERE tc.task_id = t.id_task AND tc.completion_note = 'Fixed with new washer from hardware store');

INSERT INTO task_completion (completion_photo_url, completion_note, date_completed, task_id, group_member_id)
SELECT NULL, 'Counters wiped and stovetop scrubbed', NOW() - INTERVAL '7 days',
       t.id_task, gm.id_group_member
FROM task t
JOIN group_member gm ON gm.user_id = (SELECT id_user FROM "user" WHERE email = 'bob@example.com')
    AND gm.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')
WHERE t.name = 'Clean kitchen counters'
AND NOT EXISTS (SELECT 1 FROM task_completion tc WHERE tc.task_id = t.id_task AND tc.completion_note = 'Counters wiped and stovetop scrubbed');

INSERT INTO task_completion (completion_photo_url, completion_note, date_completed, task_id, group_member_id)
SELECT 'https://placeholder.example.com/photos/bathroom-clean-001.jpg', 'Monthly deep clean done', NOW() - INTERVAL '30 days',
       t.id_task, gm.id_group_member
FROM task t
JOIN group_member gm ON gm.user_id = (SELECT id_user FROM "user" WHERE email = 'charlie@example.com')
    AND gm.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')
WHERE t.name = 'Deep clean bathroom'
AND NOT EXISTS (SELECT 1 FROM task_completion tc WHERE tc.task_id = t.id_task AND tc.completion_note = 'Monthly deep clean done');

-- ============================================================================
-- NOTIFICATIONS (mix of read and unread)
-- ============================================================================
INSERT INTO notification (type, title, message, is_read, read_at, user_id, created_by, task_id, group_id)
SELECT 'TASK_ASSIGNED', 'New task assigned', 'You have been assigned "Clean kitchen counters"',
       FALSE, NULL,
       (SELECT id_user FROM "user" WHERE email = 'bob@example.com'),
       (SELECT id_user FROM "user" WHERE email = 'alice@example.com'),
       (SELECT id_task FROM task WHERE name = 'Clean kitchen counters'),
       (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')
WHERE NOT EXISTS (SELECT 1 FROM notification WHERE title = 'New task assigned' AND user_id = (SELECT id_user FROM "user" WHERE email = 'bob@example.com') AND task_id = (SELECT id_task FROM task WHERE name = 'Clean kitchen counters'));

INSERT INTO notification (type, title, message, is_read, read_at, user_id, created_by, task_id, group_id)
SELECT 'TASK_ASSIGNED', 'New task assigned', 'You have been assigned "Deep clean bathroom"',
       TRUE, NOW() - INTERVAL '1 day',
       (SELECT id_user FROM "user" WHERE email = 'charlie@example.com'),
       (SELECT id_user FROM "user" WHERE email = 'alice@example.com'),
       (SELECT id_task FROM task WHERE name = 'Deep clean bathroom'),
       (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')
WHERE NOT EXISTS (SELECT 1 FROM notification WHERE title = 'New task assigned' AND user_id = (SELECT id_user FROM "user" WHERE email = 'charlie@example.com') AND task_id = (SELECT id_task FROM task WHERE name = 'Deep clean bathroom'));

INSERT INTO notification (type, title, message, is_read, read_at, user_id, created_by, task_id, group_id)
SELECT 'TASK_COMPLETED', 'Task completed', 'Bob completed "Fix leaky faucet"',
       TRUE, NOW() - INTERVAL '2 days',
       (SELECT id_user FROM "user" WHERE email = 'alice@example.com'),
       (SELECT id_user FROM "user" WHERE email = 'bob@example.com'),
       (SELECT id_task FROM task WHERE name = 'Fix leaky faucet'),
       (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')
WHERE NOT EXISTS (SELECT 1 FROM notification WHERE title = 'Task completed' AND user_id = (SELECT id_user FROM "user" WHERE email = 'alice@example.com') AND task_id = (SELECT id_task FROM task WHERE name = 'Fix leaky faucet'));

INSERT INTO notification (type, title, message, is_read, read_at, user_id, created_by, task_id, group_id)
SELECT 'TASK_REMINDER', 'Task reminder', 'Don''t forget to "Mow the lawn" this weekend',
       FALSE, NULL,
       (SELECT id_user FROM "user" WHERE email = 'charlie@example.com'),
       NULL,
       (SELECT id_task FROM task WHERE name = 'Mow the lawn'),
       (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002')
WHERE NOT EXISTS (SELECT 1 FROM notification WHERE title = 'Task reminder' AND user_id = (SELECT id_user FROM "user" WHERE email = 'charlie@example.com') AND task_id = (SELECT id_task FROM task WHERE name = 'Mow the lawn'));

INSERT INTO notification (type, title, message, is_read, read_at, user_id, created_by, task_id, group_id)
SELECT 'MEMBER_JOINED', 'New member', 'Alice joined "Family Home"',
       TRUE, NOW() - INTERVAL '5 days',
       (SELECT id_user FROM "user" WHERE email = 'charlie@example.com'),
       (SELECT id_user FROM "user" WHERE email = 'alice@example.com'),
       NULL,
       (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002')
WHERE NOT EXISTS (SELECT 1 FROM notification WHERE title = 'New member' AND user_id = (SELECT id_user FROM "user" WHERE email = 'charlie@example.com') AND group_id = (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002'));

-- ============================================================================
-- AUDIT LOG (sample entries)
-- ============================================================================
INSERT INTO audit_log (entity_type, entity_id, action, action_message, ip_address, group_member_id, group_id, actor_user_id)
SELECT 'GROUP', g.id_group, 'CREATE', 'Group "Apartment 4B" created', '127.0.0.1',
       gm.id_group_member, g.id_group, gm.user_id
FROM "group" g
JOIN group_member gm ON gm.group_id = g.id_group
    AND gm.user_id = (SELECT id_user FROM "user" WHERE email = 'alice@example.com')
WHERE g.invite_code = 'APT4B001'
AND NOT EXISTS (SELECT 1 FROM audit_log WHERE action = 'CREATE' AND entity_type = 'GROUP' AND entity_id = g.id_group);

INSERT INTO audit_log (entity_type, entity_id, action, action_message, ip_address, group_member_id, group_id, actor_user_id)
SELECT 'TASK', t.id_task, 'CREATE', 'Task "Clean kitchen counters" created', '127.0.0.1',
       gm.id_group_member, (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001'), gm.user_id
FROM task t
JOIN group_member gm ON gm.user_id = (SELECT id_user FROM "user" WHERE email = 'alice@example.com')
    AND gm.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')
WHERE t.name = 'Clean kitchen counters'
AND NOT EXISTS (SELECT 1 FROM audit_log WHERE action = 'CREATE' AND entity_type = 'TASK' AND entity_id = t.id_task);

INSERT INTO audit_log (entity_type, entity_id, action, action_message, ip_address, group_member_id, group_id, actor_user_id)
SELECT 'TASK', t.id_task, 'UPDATE', 'Task "Fix leaky faucet" marked as COMPLETED', '127.0.0.1',
       gm.id_group_member, (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001'), gm.user_id
FROM task t
JOIN group_member gm ON gm.user_id = (SELECT id_user FROM "user" WHERE email = 'bob@example.com')
    AND gm.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'APT4B001')
WHERE t.name = 'Fix leaky faucet'
AND NOT EXISTS (SELECT 1 FROM audit_log WHERE action = 'UPDATE' AND entity_type = 'TASK' AND entity_id = t.id_task);

INSERT INTO audit_log (entity_type, entity_id, action, action_message, ip_address, group_member_id, group_id, actor_user_id)
SELECT 'GROUP_MEMBER', gm_alice.id_group_member, 'CREATE', 'Alice joined "Family Home"', '127.0.0.1',
       gm_charlie.id_group_member, (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002'), gm_charlie.user_id
FROM group_member gm_alice
JOIN group_member gm_charlie ON gm_charlie.user_id = (SELECT id_user FROM "user" WHERE email = 'charlie@example.com')
    AND gm_charlie.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002')
WHERE gm_alice.user_id = (SELECT id_user FROM "user" WHERE email = 'alice@example.com')
    AND gm_alice.group_id = (SELECT id_group FROM "group" WHERE invite_code = 'FAMLY002')
AND NOT EXISTS (SELECT 1 FROM audit_log WHERE action = 'CREATE' AND entity_type = 'GROUP_MEMBER' AND entity_id = gm_alice.id_group_member);
