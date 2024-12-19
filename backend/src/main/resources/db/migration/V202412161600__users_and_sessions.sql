set search_path to ready2race, pg_catalog, public;

create table app_user
(
    id uuid primary key default gen_random_uuid(),
    email text not null unique,
    password text not null,
    firstname text not null,
    lastname text not null,
    created_at timestamp not null default now(),
    created_by uuid not null references app_user on update cascade,
    updated_at timestamp not null default now(),
    updated_by uuid not null references app_user on update cascade
);

create index on app_user (email);

create table app_user_session
(
    token char(30) primary key,
    app_user uuid not null references app_user on delete cascade on update cascade,
    last_used timestamp not null default now(),
    created_at timestamp not null default now()
);

create index on app_user_session (app_user);
create index on app_user_session (last_used);
