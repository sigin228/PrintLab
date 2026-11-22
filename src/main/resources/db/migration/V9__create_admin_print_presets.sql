CREATE TABLE admin_print_presets (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 100),
    image_id UUID NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_admin_print_presets_active ON admin_print_presets (created_at DESC) WHERE active;
