create extension if not exists pg_trgm;

create table if not exists search_items (
    id bigserial primary key,
    tenant_id varchar(36) not null,
    entity_type varchar(24) not null,
    entity_id varchar(64) not null,
    title text not null,
    subtitle text,
    tsv tsvector generated always as (
            setweight(to_tsvector('english', coalesce(title,'')), 'A') ||
            setweight(to_tsvector('english', coalesce(subtitle,'')), 'B')
        ) stored
    );

create index if not exists idx_search_items_tsv_gin on search_items using gin (tsv);
create index if not exists idx_search_items_title_trgm on search_items using gin (lower(title) gin_trgm_ops);
create index if not exists idx_search_items_subtitle_trgm on search_items using gin (lower(subtitle) gin_trgm_ops);
create index if not exists idx_search_items_tenant on search_items (tenant_id);