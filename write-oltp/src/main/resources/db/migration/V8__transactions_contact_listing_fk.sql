-- Add NOT NULL relations from transactions → contacts and listings (FKs).
-- This migration assumes:
--   - transactions table exists (V5)
--   - contacts and listings tables exist
-- NOTE:
--   - We enforce simple FKs to ids; tenant consistency is validated at the service layer.

-- 1) Add columns (initially NULL so you can backfill if needed)
alter table transactions
    add column if not exists contact_id  uuid,
    add column if not exists listing_id  uuid;

-- 2) If there are existing rows with NULLs, abort with a clear instruction
do $$
declare
need_backfill bigint;
begin
select count(*) into need_backfill
from transactions
where contact_id is null or listing_id is null;

if need_backfill > 0 then
    raise exception
      'V8 migration requires backfilling transactions.contact_id/listing_id (% rows missing). Set each to existing contacts.id and listings.id (same tenant) before re-running.',
      need_backfill;
end if;
end $$;

-- 3) Enforce NOT NULL (data is clean at this point)
alter table transactions
    alter column contact_id set not null,
alter column listing_id set not null;

-- 4) Add FKs
alter table transactions
    add constraint fk_transactions_contact
        foreign key (contact_id)
            references contacts (id)
            on update restrict
            on delete restrict;

alter table transactions
    add constraint fk_transactions_listing
        foreign key (listing_id)
            references listings (id)
            on update restrict
            on delete restrict;

-- 5) Helpful indexes
create index if not exists idx_transactions_contact_id on transactions (contact_id);
create index if not exists idx_transactions_listing_id on transactions (listing_id);

comment on column transactions.contact_id is 'FK to contacts.id (tenant-checked in service layer)';
comment on column transactions.listing_id is 'FK to listings.id (tenant-checked in service layer)';
