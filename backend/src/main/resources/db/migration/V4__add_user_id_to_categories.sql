ALTER TABLE categories
    ADD COLUMN user_id UUID,
    ADD CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
