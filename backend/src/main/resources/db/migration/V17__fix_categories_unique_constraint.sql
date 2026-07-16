-- The original uk_categories_name constraint was UNIQUE(name) globally.
-- After adding user_id to categories (V4), each user owns their own tree,
-- so the uniqueness scope should be per-user: UNIQUE(name, user_id).
ALTER TABLE categories DROP CONSTRAINT uk_categories_name;
ALTER TABLE categories ADD CONSTRAINT uk_categories_user_name UNIQUE (name, user_id);
