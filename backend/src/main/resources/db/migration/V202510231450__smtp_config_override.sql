set search_path to ready2race, pg_catalog, public;

create table if not exists smtp_config_override
(
    id             uuid primary key,
    host           text      not null,
    port           integer   not null,
    username       text      not null,
    password       text      not null,
    smtp_strategy  text      not null,
    from_address   text      not null,
    from_name      text,
    smtp_localhost text,
    reply_to       text,
    created_at     timestamp not null,
    created_by     uuid      references app_user on delete set null
);