-- 1) Ensure the column 'total' exists and is correct.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='transactions' AND column_name='amount'
  ) AND NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='transactions' AND column_name='total'
  ) THEN
    -- Prefer a simple rename if you don't have dependent code that requires 'amount'
    EXECUTE 'ALTER TABLE transactions RENAME COLUMN amount TO total';
END IF;
END$$;

-- If both 'amount' and 'total' exist for some reason, favor 'total' and drop 'amount' after copy
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='transactions' AND column_name='amount'
  ) AND EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='transactions' AND column_name='total'
  ) THEN
    EXECUTE 'UPDATE transactions SET total = COALESCE(total, amount)';
EXECUTE 'ALTER TABLE transactions DROP COLUMN amount';
END IF;
END$$;

-- 2) Ensure total has the right type/constraints.
ALTER TABLE transactions
ALTER COLUMN total TYPE numeric(19,2) USING total::numeric;

UPDATE transactions SET total = 0 WHERE total IS NULL;

ALTER TABLE transactions
    ALTER COLUMN total SET DEFAULT 0,
ALTER COLUMN total SET NOT NULL;

-- 3) Make status non-null with a sensible default (optional but recommended).
UPDATE transactions SET status = COALESCE(status, 'NEW');
ALTER TABLE transactions
    ALTER COLUMN status SET DEFAULT 'NEW',
ALTER COLUMN status SET NOT NULL;

-- 4) Add a 'visible' flag to support list filters like other entities.
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS visible boolean NOT NULL DEFAULT true;

-- 5) Keep your timestamps consistent: auto-maintain updated_at on row update.
--    (Create trigger function once, reused by other tables if you like.)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'set_updated_at_now') THEN
    CREATE OR REPLACE FUNCTION set_updated_at_now()
    RETURNS trigger AS $fn$
BEGIN
      NEW.updated_at := now();
RETURN NEW;
END;
    $fn$ LANGUAGE plpgsql;
END IF;
END$$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_trigger
    WHERE tgname = 'trg_transactions_updated_at'
  ) THEN
CREATE TRIGGER trg_transactions_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at_now();
END IF;
END$$;

-- 6) Indexes for paging and filtering (tenant + soft-delete + order by id).
CREATE INDEX IF NOT EXISTS idx_transactions_tenant_deleted_id
    ON transactions (tenant_id, deleted_at, id);

-- Optional: status filtering
CREATE INDEX IF NOT EXISTS idx_transactions_tenant_status_deleted
    ON transactions (tenant_id, status, deleted_at);
