-- Add currency if missing
ALTER TABLE listings
    ADD COLUMN IF NOT EXISTS currency text;

-- Backfill NULL currency (pick your default; PLN or USD)
UPDATE listings SET currency = COALESCE(NULLIF(btrim(currency), ''), 'USD')
WHERE currency IS NULL OR btrim(currency) = '';

-- Enforce 3-letter ISO-like formatting (uppercase A–Z only)
ALTER TABLE listings
    ALTER COLUMN currency SET NOT NULL;

-- Add/refresh a CHECK constraint (drop if name collides)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'chk_listings_currency_iso'
  ) THEN
ALTER TABLE listings DROP CONSTRAINT chk_listings_currency_iso;
END IF;
END$$;

ALTER TABLE listings
    ADD CONSTRAINT chk_listings_currency_iso
        CHECK (currency ~ '^[A-Z]{3}$');

-- Optional index if you filter by currency
CREATE INDEX IF NOT EXISTS idx_listings_tenant_currency
    ON listings (tenant_id, currency);
