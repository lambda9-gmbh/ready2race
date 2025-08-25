set search_path to ready2race, pg_catalog, public;

create table webdav_export_process
(
    id         uuid primary key,
    name       text      not null,
    created_at timestamp not null,
    created_by uuid      references app_user on delete set null
);

create table webdav_export
(
    id                    uuid primary key,
    webdav_export_process uuid references webdav_export_process,
    event_name            text not null,
    document_type         text not null,
    data_reference        uuid,
    path                  text not null,
    exported_at           timestamp,
    error_at              timestamp,
    error                 text
);