
-- Normalize currency: uppercase, not null
UPDATE transactions SET currency = UPPER(btrim(currency)) WHERE currency IS NOT NULL;
UPDATE transactions SET currency = 'USD'
WHERE currency IS NULL OR btrim(currency) = '';

ALTER TABLE transactions
    ALTER COLUMN currency SET NOT NULL;

-- Refresh CHECK constraint
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'chk_transactions_currency_iso'
  ) THEN
ALTER TABLE transactions DROP CONSTRAINT chk_transactions_currency_iso;
END IF;
END$$;

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_currency_iso
        CHECK (currency ~ '^[A-Z]{3}$');

-- Index for pagination/filtering
CREATE INDEX IF NOT EXISTS idx_transactions_tenant_currency
    ON transactions (tenant_id, currency);