set search_path to ready2race, pg_catalog, public;

create table document_template
(
    id uuid primary key,
    name text not null,
    page_margin_top double precision,
    page_margin_left double precision,
    page_margin_right double precision,
    page_margin_bottom double precision
);

create table document_template_data
(
    template uuid primary key references document_template on delete cascade,
    data bytea not null
);

create table document_template_usage
(
    document_type text primary key,
    template uuid not null references document_template on delete cascade
);

create table event_document_template_usage
(
    document_type text not null,
    event uuid not null references event on delete cascade,
    template uuid references document_template on delete cascade,
    primary key (document_type, event)
);

create table event_registration_report
(
    event uuid primary key references event on delete cascade,
    name text not null,
    created_at timestamp not null
);

create table event_registration_report_data
(
    result_document uuid primary key references event_registration_report on delete cascade,
    data bytea not null
);