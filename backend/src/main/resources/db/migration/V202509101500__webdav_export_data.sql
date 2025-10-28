set search_path to ready2race, pg_catalog, public;

create table webdav_export_data
(
    id                    uuid primary key,
    webdav_export_process uuid references webdav_export_process,
    document_type         text not null,
    data_reference        uuid,
    path                  text not null,
    exported_at           timestamp,
    error_at              timestamp,
    error                 text,
    parent_folder         uuid references webdav_export_folder
);

create table webdav_export_dependency
(
    webdav_export_data uuid not null references webdav_export_data,
    depending_on       uuid not null references webdav_export_data,
    primary key (webdav_export_data, depending_on)
);

create table webdav_import_process
(
    id                 uuid primary key,
    import_folder_name text      not null,
    created_at         timestamp not null,
    created_by         uuid      references app_user on delete set null
);

create table webdav_import_data
(
    id                    uuid primary key,
    webdav_import_process uuid references webdav_import_process,
    document_type         text not null,
    path                  text not null,
    imported_at           timestamp,
    error_at              timestamp,
    error                 text
);

create table webdav_import_dependency
(
    webdav_import_data uuid not null references webdav_import_data,
    depending_on       uuid not null references webdav_import_data,
    primary key (webdav_import_data, depending_on)
);