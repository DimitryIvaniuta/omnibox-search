-- Create OLTP table for Transactions with DB-generated UUID PK, optimistic locking, and soft delete.
create extension if not exists pgcrypto;

create table if not exists transactions (
                                            id           uuid primary key default gen_random_uuid(),
    tenant_id    text        not null,
    title        text        not null check (length(btrim(title)) > 0),
    subtitle     text,
    amount       numeric(18,2),
    currency     text,
    status       text,                      -- e.g. NEW | PENDING | CLOSED
    version      bigint      not null default 0,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    deleted_at   timestamptz
    );

create index if not exists idx_txn_tenant         on transactions (tenant_id);
create index if not exists idx_txn_active_tenant  on transactions (tenant_id) where deleted_at is null;

-- updated_at auto-maintenance
create or replace function trg_set_timestamp_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at := now();
return new;
end $$;

drop trigger if exists trg_transactions_set_updated_at on transactions;
create trigger trg_transactions_set_updated_at
    before update on transactions
    for each row execute function trg_set_timestamp_updated_at();

comment on table transactions is 'OLTP aggregate for Transactions (write model, versioned, soft-deletable)';
