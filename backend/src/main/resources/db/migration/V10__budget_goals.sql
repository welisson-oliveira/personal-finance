CREATE TABLE budget_goals (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID          NOT NULL,
    category_id UUID          NOT NULL,
    amount      DECIMAL(19,2) NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT pk_budget_goals            PRIMARY KEY (id),
    CONSTRAINT fk_budget_goals_user       FOREIGN KEY (user_id)     REFERENCES users (id)      ON DELETE CASCADE,
    CONSTRAINT fk_budget_goals_category   FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE CASCADE,
    CONSTRAINT uk_budget_goals_user_cat   UNIQUE (user_id, category_id),
    CONSTRAINT chk_budget_goals_amount    CHECK (amount > 0)
);
