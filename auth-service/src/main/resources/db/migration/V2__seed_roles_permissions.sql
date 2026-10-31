INSERT IGNORE INTO permissions (name, description) VALUES
 ('VIEW_EVENT',      'View events and shows'),
 ('BOOK_TICKET',     'Create a booking'),
 ('CANCEL_BOOKING',  'Cancel own booking'),
 ('VIEW_BOOKING',    'View bookings'),
 ('CREATE_EVENT',    'Create events and shows'),
 ('UPDATE_EVENT',    'Update events and shows'),
 ('DELETE_EVENT',    'Delete events and shows'),
 ('VIEW_USERS',      'List and view users'),
 ('BLOCK_USER',      'Activate / deactivate / block users'),
 ('ASSIGN_ROLE',     'Assign or remove user roles'),
 ('DELETE_USER',     'Delete users');

INSERT IGNORE INTO roles (name, description) VALUES
 ('USER',          'Regular customer'),
 ('ADMIN',         'Platform administrator'),
 ('THEATRE_OWNER', 'Owns venues / theatres'),
 ('EVENT_MANAGER', 'Manages events and shows');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.name IN ('VIEW_EVENT', 'BOOK_TICKET', 'CANCEL_BOOKING', 'VIEW_BOOKING')
WHERE r.name = 'USER';

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN';

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.name IN ('VIEW_EVENT', 'VIEW_BOOKING', 'CREATE_EVENT', 'UPDATE_EVENT')
WHERE r.name IN ('THEATRE_OWNER', 'EVENT_MANAGER');
