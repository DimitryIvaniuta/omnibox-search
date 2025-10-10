-- Enable pgcrypto for gen_random_uuid()
create extension if not exists pgcrypto;

-- contacts.id is uuid with DB-generated default
alter table contacts
alter column id type uuid using id::uuid,
  alter column id set default gen_random_uuid();

-- LISTINGS
alter table if exists listings
alter column id type uuid using id::uuid,
  alter column id set default gen_random_uuid();

-- Normalize trivial whitespace
update contacts set email = btrim(email)
where email is not null
  and email <> btrim(email);

-- Validate: no NULL emails allowed
do $$
declare
null_cnt bigint;
begin
select count(*) into null_cnt from contacts where email is null;
if null_cnt > 0 then
    raise exception 'V3 migration aborted: contacts.email contains % NULL values. Please backfill before applying NOT NULL.',
      null_cnt;
end if;
end $$;

-- Validate: no duplicates by (tenant_id, lower(email))
do $$
declare
dup_cnt bigint;
begin
select count(*) into dup_cnt
from (
         select tenant_id, lower(email) as le, count(*) as c
         from contacts
         group by tenant_id, lower(email)
         having count(*) > 1
     ) d;
if dup_cnt > 0 then
    raise exception 'V3 migration aborted: found % duplicate contact emails per tenant. Please de-duplicate before proceeding.',
      dup_cnt;
end if;
end $$;

-- Enforce NOT NULL after validations
alter table contacts
    alter column email set not null;

-- Unique index per tenant on normalized email
create unique index if not exists ux_contacts_tenant_email
    on contacts (tenant_id, lower(email));

-- listings.id is uuid with DB-generated default
alter table listings
alter column id type uuid using id::uuid,
  alter column id set default gen_random_uuid();

-- Add column if it does not exist
alter table listings
    add column if not exists mls_id text;

-- Backfill existing NULL mls_id with id::text (stable, unique; adjust if you have a real upstream MLS id)
update listings
    set mls_id = id::text
    where mls_id is null;

-- Enforce NOT NULL
alter table listings
    alter column mls_id set not null;

-- Unique index per tenant
create unique index if not exists ux_listings_tenant_mls_id
    on listings (tenant_id, mls_id);

comment on column contacts.email is 'Email address, required and unique per tenant (tenant_id + lower(email))';
comment on column listings.mls_id is 'External MLS identifier, required and unique per tenant (tenant_id + mls_id)';

