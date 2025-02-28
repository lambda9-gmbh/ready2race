set search_path to ready2race, pg_catalog, public;

create table event_document_type
(
    id                    uuid primary key,
    name                  text      not null,
    required              boolean   not null,
    confirmation_required boolean   not null,
    created_at            timestamp not null,
    created_by            uuid      references app_user on delete set null,
    updated_at            timestamp not null,
    updated_by            uuid      references app_user on delete set null
);

create table event_document
(
    id                  uuid primary key,
    event               uuid      not null references event on delete cascade,
    event_document_type uuid      references event_document_type on delete set null,
    name                text      not null,
    created_at          timestamp not null,
    created_by          uuid      references app_user on delete set null,
    updated_at          timestamp not null,
    updated_by          uuid      references app_user on delete set null
);

create table event_document_data
(
    event_document uuid primary key references event_document on delete cascade,
    data           bytea not null
);