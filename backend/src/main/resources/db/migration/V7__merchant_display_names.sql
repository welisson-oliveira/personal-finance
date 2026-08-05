CREATE TABLE merchant_display_names (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL,
    normalized_name VARCHAR(255) NOT NULL,
    display_name   VARCHAR(255) NOT NULL,
    updated_at     TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT pk_merchant_display_names PRIMARY KEY (id),
    CONSTRAINT fk_merchant_display_names_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_merchant_display_names UNIQUE (user_id, normalized_name)
);
