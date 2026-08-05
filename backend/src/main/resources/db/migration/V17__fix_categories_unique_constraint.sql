-- The original uk_categories_name constraint (UNIQUE on name alone) was designed for the
-- old global category seed (V2), before categories became user-owned (V4). It incorrectly
-- prevents a user from having the same subcategory name under different parent groups
-- (e.g. "Outros" under Receitas AND "Outros" under Moradia). Drop it entirely; uniqueness
-- is enforced at the application level where necessary.
ALTER TABLE categories DROP CONSTRAINT uk_categories_name;
