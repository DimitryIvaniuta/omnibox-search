-- Create OLTP table for Listings with optimistic locking and soft delete.
-- This migration belongs to the write-oltp service (OLTP database).

-- 1) Core table
create table if not exists listings (
    id          uuid primary key,                          -- aggregate id
    tenant_id   text        not null,                      -- multi-tenant key
    title       text        not null check (length(btrim(title)) > 0),
    subtitle    text,
    version     bigint      not null default 0,            -- optimistic lock
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    deleted_at  timestamptz
    );

-- 2) Helpful indexes
create index if not exists idx_listings_tenant
    on listings (tenant_id);

-- Queries in services typically use both tenant and id
create index if not exists idx_listings_tenant_id
    on listings (tenant_id, id);

-- Active (not soft-deleted) per-tenant scans
create index if not exists idx_listings_active_tenant
    on listings (tenant_id)
    where deleted_at is null;

-- 3) Maintain updated_at automatically on any UPDATE
create or replace function trg_set_timestamp_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at := now();
return new;
end;
$$;

drop trigger if exists trg_listings_set_updated_at on listings;
create trigger trg_listings_set_updated_at
    before update on listings
    for each row
    execute function trg_set_timestamp_updated_at();

-- 4) Optional: comment metadata
comment on table listings is 'OLTP aggregate table for Listings (write model, soft-deletable, versioned)';
comment on column listings.version is 'Optimistic locking counter (incremented by application update statements)';
