set search_path to ready2race, pg_catalog, public;

create table app_user_registration
(
    token char(30) primary key,
    email text not null,
    password text not null,
    firstname text not null,
    lastname text not null,
    language char(2) not null,
    created_at timestamp not null default now()
);

create unique index on app_user_registration (email);