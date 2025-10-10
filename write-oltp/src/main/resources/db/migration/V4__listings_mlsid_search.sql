ALTER TABLE listings
    ADD COLUMN IF NOT EXISTS mls_id TEXT NOT NULL;

-- one-tenant-per-row uniqueness (preferred in your multi-tenant design)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'uq_listings_tenant_mlsid'
  ) THEN
ALTER TABLE listings
    ADD CONSTRAINT uq_listings_tenant_mlsid UNIQUE (tenant_id, mls_id);
END IF;
END $$;

-- Add/refresh search vector to include mls_id
-- If you use a generated column:
ALTER TABLE listings
    ADD COLUMN IF NOT EXISTS search_vector TSVECTOR;

-- Backfill
UPDATE listings
SET search_vector =
        setweight(to_tsvector('simple', coalesce(title,'')), 'A') ||
        setweight(to_tsvector('simple', coalesce(subtitle,'')), 'B') ||
        setweight(to_tsvector('simple', coalesce(mls_id,'')), 'A');

-- Trigger to keep search_vector in sync
CREATE OR REPLACE FUNCTION listings_search_vector_update() RETURNS trigger AS $$
BEGIN
  NEW.search_vector :=
    setweight(to_tsvector('simple', coalesce(NEW.title,'')), 'A') ||
    setweight(to_tsvector('simple', coalesce(NEW.subtitle,'')), 'B') ||
    setweight(to_tsvector('simple', coalesce(NEW.mls_id,'')), 'A');
RETURN NEW;
END $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_listings_search_vector ON listings;
CREATE TRIGGER trg_listings_search_vector
    BEFORE INSERT OR UPDATE OF title, subtitle, mls_id ON listings
    FOR EACH ROW EXECUTE FUNCTION listings_search_vector_update();

-- Indexes: GIN for full-text + BTREE for exact lookups
CREATE INDEX IF NOT EXISTS idx_listings_search_vector ON listings USING GIN (search_vector);
CREATE INDEX IF NOT EXISTS idx_listings_mlsid_btree ON listings (tenant_id, mls_id);