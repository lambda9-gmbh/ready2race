set search_path to ready2race, pg_catalog, public;

create table email
(
    id                 uuid primary key   default gen_random_uuid(),
    recipient          text      not null,
    subject            text      not null,
    body               text      not null,
    body_is_html       boolean   not null default false,
    cc                 text,
    bcc                text,
    priority           int       not null default 1,
    dont_send_before   timestamp not null default now(),
    keep_after_sending bigint    not null default 0,
    sent_at            timestamp,
    last_error         text,
    last_error_at      timestamp,
    created_at         timestamp not null default now(),
    created_by         uuid      references app_user on delete set null,
    updated_at         timestamp not null default now(),
    updated_by         uuid      references app_user on delete set null
);

create table email_attachment
(
    email uuid  not null references email on delete cascade,
    name  text  not null,
    data  bytea not null
);

create table email_individual_template
(
    key text not null,
    language char(2) not null,
    subject text not null,
    body text not null,
    body_is_html boolean not null default false,
    created_at timestamp not null default now(),
    created_by uuid references app_user on delete set null,
    updated_at timestamp not null default now(),
    updated_by uuid references app_user on delete set null,
    primary key (key, language)
);