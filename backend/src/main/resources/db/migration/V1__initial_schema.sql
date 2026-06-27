CREATE TABLE users (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT pk_users    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE categories (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL,
    color      VARCHAR(7),
    icon       VARCHAR(50),
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT pk_categories      PRIMARY KEY (id),
    CONSTRAINT uk_categories_name UNIQUE (name)
);

CREATE TABLE import_sessions (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL,
    document_type VARCHAR(20)  NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    period_start  DATE,
    period_end    DATE,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT pk_import_sessions      PRIMARY KEY (id),
    CONSTRAINT fk_import_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_import_doc_type     CHECK (document_type IN ('EXTRATO', 'FATURA')),
    CONSTRAINT chk_import_status       CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED'))
);

CREATE TABLE known_persons (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL,
    name                VARCHAR(255) NOT NULL,
    relationship        VARCHAR(30)  NOT NULL,
    default_income_type VARCHAR(30)  NOT NULL DEFAULT 'REIMBURSEMENT',
    default_label       VARCHAR(255),
    active              BOOLEAN      NOT NULL DEFAULT true,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT pk_known_persons      PRIMARY KEY (id),
    CONSTRAINT fk_known_persons_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_known_relationship CHECK (relationship IN ('HOUSE_MEMBER', 'FAMILY', 'FRIEND', 'OTHER')),
    CONSTRAINT chk_known_income_type  CHECK (default_income_type IN ('REIMBURSEMENT', 'INCOME', 'OWN_TRANSFER', 'ALWAYS_REVIEW'))
);

CREATE TABLE merchant_rules (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID,
    merchant_name   VARCHAR(255) NOT NULL,
    normalized_name VARCHAR(255) NOT NULL,
    category_id     UUID,
    subcategory     VARCHAR(100),
    expense_type    VARCHAR(20)  NOT NULL DEFAULT 'NON_ESSENTIAL',
    confidence      SMALLINT     NOT NULL DEFAULT 100,
    created_by      VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT pk_merchant_rules          PRIMARY KEY (id),
    CONSTRAINT fk_merchant_rules_user     FOREIGN KEY (user_id)     REFERENCES users (id)       ON DELETE CASCADE,
    CONSTRAINT fk_merchant_rules_category FOREIGN KEY (category_id) REFERENCES categories (id)  ON DELETE SET NULL,
    CONSTRAINT chk_merchant_expense_type  CHECK (expense_type IN ('ESSENTIAL', 'NON_ESSENTIAL', 'INVESTMENT')),
    CONSTRAINT chk_merchant_created_by    CHECK (created_by IN ('SYSTEM', 'USER')),
    CONSTRAINT chk_merchant_confidence    CHECK (confidence BETWEEN 0 AND 100)
);

CREATE TABLE merchant_aliases (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    merchant_rule_id UUID         NOT NULL,
    alias            VARCHAR(255) NOT NULL,
    CONSTRAINT pk_merchant_aliases      PRIMARY KEY (id),
    CONSTRAINT fk_merchant_aliases_rule FOREIGN KEY (merchant_rule_id) REFERENCES merchant_rules (id) ON DELETE CASCADE,
    CONSTRAINT uk_merchant_alias        UNIQUE (alias)
);

CREATE TABLE review_queue (
    id                     UUID          NOT NULL DEFAULT gen_random_uuid(),
    user_id                UUID          NOT NULL,
    import_session_id      UUID,
    raw_description        VARCHAR(500)  NOT NULL,
    normalized_description VARCHAR(255),
    amount                 DECIMAL(19,2) NOT NULL,
    transaction_date       DATE          NOT NULL,
    suggested_category_id  UUID,
    status                 VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    resolved_at            TIMESTAMP,
    created_at             TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT pk_review_queue        PRIMARY KEY (id),
    CONSTRAINT fk_review_queue_user   FOREIGN KEY (user_id)           REFERENCES users (id)           ON DELETE CASCADE,
    CONSTRAINT fk_review_queue_import FOREIGN KEY (import_session_id) REFERENCES import_sessions (id) ON DELETE SET NULL,
    CONSTRAINT fk_review_queue_cat    FOREIGN KEY (suggested_category_id) REFERENCES categories (id)  ON DELETE SET NULL,
    CONSTRAINT chk_review_status      CHECK (status IN ('PENDING', 'REVIEWED', 'SKIPPED'))
);

CREATE TABLE transactions (
    id                     UUID           NOT NULL DEFAULT gen_random_uuid(),
    user_id                UUID           NOT NULL,
    description            VARCHAR(500)   NOT NULL,
    normalized_description VARCHAR(255),
    amount                 DECIMAL(19,2)  NOT NULL,
    type                   VARCHAR(20)    NOT NULL,
    income_type            VARCHAR(30),
    budget_group           VARCHAR(20),
    date                   DATE           NOT NULL,
    notes                  TEXT,
    category_id            UUID,
    import_session_id      UUID,
    known_person_id        UUID,
    source                 VARCHAR(20)    NOT NULL DEFAULT 'MANUAL',
    card_holder            VARCHAR(100),
    installment_info       VARCHAR(20),
    shared                 BOOLEAN        NOT NULL DEFAULT false,
    total_amount           DECIMAL(19,2),
    user_share             DECIMAL(19,2),
    created_at             TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP,
    CONSTRAINT pk_transactions        PRIMARY KEY (id),
    CONSTRAINT fk_transactions_user   FOREIGN KEY (user_id)           REFERENCES users (id)           ON DELETE CASCADE,
    CONSTRAINT fk_transactions_cat    FOREIGN KEY (category_id)       REFERENCES categories (id)      ON DELETE SET NULL,
    CONSTRAINT fk_transactions_import FOREIGN KEY (import_session_id) REFERENCES import_sessions (id) ON DELETE SET NULL,
    CONSTRAINT fk_transactions_person FOREIGN KEY (known_person_id)   REFERENCES known_persons (id)   ON DELETE SET NULL,
    CONSTRAINT chk_transactions_type  CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT chk_income_type        CHECK (income_type IN ('INCOME', 'REIMBURSEMENT', 'OWN_TRANSFER', 'INVESTMENT') OR income_type IS NULL),
    CONSTRAINT chk_budget_group       CHECK (budget_group IN ('ESSENTIAL', 'NON_ESSENTIAL', 'INVESTMENT') OR budget_group IS NULL),
    CONSTRAINT chk_source             CHECK (source IN ('MANUAL', 'EXTRATO', 'FATURA'))
);
