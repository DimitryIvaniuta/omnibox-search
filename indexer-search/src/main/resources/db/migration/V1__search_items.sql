create extension if not exists pg_trgm;

-- Read-optimized consolidated search table
create table if not exists search_items (
                                            id           bigserial primary key,
                                            tenant_id    text        not null,
                                            entity_type  text        not null,    -- e.g., CONTACT, LISTING, REFERRAL, TRANSACTION, PRODUCT, MAILING
                                            entity_id    text        not null,    -- the source entity id (UUID or string)
                                            title        text        not null,
                                            subtitle     text        null,

    -- Weighted full-text vector (A=title, B=subtitle)
                                            tsv tsvector generated always as (
                                            setweight(to_tsvector('english', coalesce(title,   '')), 'A') ||
    setweight(to_tsvector('english', coalesce(subtitle,'')), 'B')
    ) stored
    );

-- Uniqueness per tenant + entity kind + entity id
create unique index if not exists ux_search_key
    on search_items (tenant_id, entity_type, entity_id);

-- FTS index
create index if not exists idx_search_items_tsv_gin
    on search_items using gin (tsv);

-- Trigram indexes for case-insensitive LIKE / prefix / typo-tolerant matches
create index if not exists idx_search_items_title_trgm
    on search_items using gin (lower(title) gin_trgm_ops);

create index if not exists idx_search_items_subtitle_trgm
    on search_items using gin (lower(subtitle) gin_trgm_ops);

-- Common filter
create index if not exists idx_search_items_tenant
    on search_items (tenant_id);