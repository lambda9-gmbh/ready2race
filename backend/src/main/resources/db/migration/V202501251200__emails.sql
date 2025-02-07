set search_path to ready2race, pg_catalog, public;

create table email
(
    id                 uuid primary key,
    recipient          text      not null,
    subject            text      not null,
    body               text      not null,
    body_is_html       boolean   not null,
    cc                 text,
    bcc                text,
    priority           int       not null,
    dont_send_before   timestamp not null,
    keep_after_sending bigint    not null,
    sent_at            timestamp,
    last_error         text,
    last_error_at      timestamp,
    created_at         timestamp not null,
    created_by         uuid      references app_user on delete set null,
    updated_at         timestamp not null,
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
    key          text      not null,
    language     char(2)   not null,
    subject      text      not null,
    body         text      not null,
    body_is_html boolean   not null,
    created_at   timestamp not null,
    created_by   uuid      references app_user on delete set null,
    updated_at   timestamp not null,
    updated_by   uuid      references app_user on delete set null,
    primary key (key, language)
);