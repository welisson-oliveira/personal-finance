-- User feedback on detected transaction anomalies. Detection itself is stateless
-- (recomputed from the data); only the user's verdict is persisted here.
-- status: FALSE_POSITIVE (dismissed) | ACKNOWLEDGED (seen/resolved).
CREATE TABLE anomaly_feedback (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL,
    transaction_id UUID         NOT NULL,
    type           VARCHAR(32)  NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP,
    CONSTRAINT pk_anomaly_feedback         PRIMARY KEY (id),
    CONSTRAINT fk_anomaly_feedback_user    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_anomaly_feedback_tx      FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE,
    CONSTRAINT uq_anomaly_feedback         UNIQUE (transaction_id, type)
);

CREATE INDEX idx_anomaly_feedback_user ON anomaly_feedback (user_id);
