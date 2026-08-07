set search_path to ready2race, pg_catalog, public;

alter table gap_document_placeholder
    add column font_size   int,
    add column bold        boolean not null default false,
    add column italic      boolean not null default false,
    add column static_text text;

alter table gap_document_template
    add column font_name text;

create table gap_document_template_font
(
    template  uuid primary key references gap_document_template on delete cascade,
    file_name text  not null,
    data      bytea not null
);
