-- V17 replaced uk_categories_name with uk_categories_user_name (name, user_id), but that
-- still fails when the same user has "Outros" under multiple parent groups. Drop it too.
ALTER TABLE categories DROP CONSTRAINT IF EXISTS uk_categories_user_name;
