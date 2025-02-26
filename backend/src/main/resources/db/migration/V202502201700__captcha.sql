set search_path to ready2race, pg_catalog, public;

create table captcha
(
    id         uuid primary key,
    solution   int       not null,
    expires_at timestamp not null
);

create index on captcha (expires_at);