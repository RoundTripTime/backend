ALTER TABLE places
    ALTER COLUMN thumbnail_url TYPE TEXT,
    ADD COLUMN thumbnail_attribution TEXT,
    ADD COLUMN thumbnail_license VARCHAR(100),
    ADD COLUMN thumbnail_license_url TEXT,
    ADD COLUMN thumbnail_source_url TEXT;
