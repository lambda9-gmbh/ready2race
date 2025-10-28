set search_path to ready2race, pg_catalog, public;

alter table competition_properties
    add column rating_category_required boolean not null default false
;