set search_path to ready2race, pg_catalog, public;

create table event_registration_result_document
(
    event uuid primary key references event on delete cascade,
    name text not null,
    created_at timestamp not null
);

create table event_registration_result_document_data
(
    result_document uuid primary key references event_registration_result_document on delete cascade,
    data bytea not null
);