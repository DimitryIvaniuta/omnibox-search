create extension if not exists pg_trgm;

create table if not exists contacts (
    id uuid primary key,
    tenant_id text not null,
    full_name text not null,
    email text,
    phone text,
    label text,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);
create index if not exists idx_contacts_tenant on contacts(tenant_id);

create table if not exists outbox_events (
    id bigserial primary key,
    tenant_id text not null,
    aggregate_type text not null, -- CONTACT, LISTING, ...
    aggregate_id text not null,
    type text not null, -- ContactCreated/Updated/Deleted
    payload jsonb not null,
    occurred_at timestamptz not null default now(),
    published boolean not null default false,
    published_at timestamptz
);
create index if not exists idx_outbox_unpublished on outbox_events(published) where published=false;