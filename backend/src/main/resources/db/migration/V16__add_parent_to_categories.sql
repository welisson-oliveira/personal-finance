-- Subcategories: a category can point to a parent category (self-reference). A NULL parent means a
-- top-level category. Kept nullable so existing (flat) categories remain valid top-levels.
ALTER TABLE categories ADD COLUMN parent_id UUID;
ALTER TABLE categories
  ADD CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id) ON DELETE CASCADE;
CREATE INDEX idx_categories_parent_id ON categories (parent_id);
