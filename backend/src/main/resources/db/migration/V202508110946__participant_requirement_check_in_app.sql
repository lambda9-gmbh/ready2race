set search_path to ready2race, pg_catalog, public;

alter table participant_requirement
    add column check_in_app boolean not null default false;