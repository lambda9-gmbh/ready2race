set search_path to ready2race, pg_catalog, public;

create table app_user
(
    id         uuid primary key,
    email      text      not null,
    password   text      not null,
    firstname  text      not null,
    lastname   text      not null,
    language   char(2)   not null,
    created_at timestamp not null,
    created_by uuid      references app_user on delete set null,
    updated_at timestamp not null,
    updated_by uuid      references app_user on delete set null
);

create unique index on app_user (email);

create table app_user_session
(
    token      char(30) primary key,
    app_user   uuid      not null references app_user on delete cascade,
    expires_at timestamp not null,
    created_at timestamp not null
);

create index on app_user_session (app_user);
