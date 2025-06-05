set search_path to ready2race, pg_catalog, public;

alter table event_document_type
    add column description text;