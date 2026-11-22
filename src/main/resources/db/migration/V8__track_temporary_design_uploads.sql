-- Only new user uploads are registered. Existing unregistered files are never collected.
CREATE TABLE design_uploads (
    file_id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_design_uploads_expiry ON design_uploads(lease_until);
CREATE INDEX idx_design_uploads_user ON design_uploads(user_id);
