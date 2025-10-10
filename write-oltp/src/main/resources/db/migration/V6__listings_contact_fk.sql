-- V4__listings_contact_fk.sql
-- Add a tenant-safe foreign key from listings to contacts.

-- 1) Add columns (initially NULL to allow safe backfill if needed)
alter table listings
    add column if not exists contact_id uuid;

-- 2) If table is NOT empty and any row has nulls, abort with an instruction
do $$
declare
need_backfill bigint;
begin
select count(*) into need_backfill
from listings
where contact_id is null;

if need_backfill > 0 then
    raise exception
      'V4 migration requires backfilling listings.contact_id (% row missing). Please set them to an existing contact in the same tenant before re-running.',
      need_backfill;
end if;
end $$;

-- 3) Enforce NOT NULL now that data is consistent
alter table listings
    alter column contact_id set not null;

-- 4) Ensure a supporting unique constraint exists on contacts(tenant_id, id)
--    (not strictly required if id is PK, but helpful for FK and planner)
create unique index if not exists ux_contacts_tenant_id
    on contacts (tenant_id, id);

-- 5) Add tenant-safe FK
alter table listings
    add constraint fk_listings_contact
        foreign key (contact_id)
            references contacts (id)
            on update restrict
            on delete restrict;

-- 6) Helpful index for join/filter
create index if not exists idx_listings_contact_fk
    on listings (contact_id);

comment on column listings.contact_id is 'FK to contacts.id (same tenant; enforced with composite FK)';
