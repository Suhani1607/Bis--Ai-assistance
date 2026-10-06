-- Migration to remove all admin roles and normalize all accounts to standard USER role
UPDATE users SET role = 'USER' WHERE role != 'USER';
UPDATE users SET name = 'BIS User' WHERE name = 'BIS Administrator';
