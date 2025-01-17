set search_path to ready2race, pg_catalog, public;

create table role
(
    id uuid primary key default gen_random_uuid(),
    name text not null,
    description text,
    static boolean not null default false,
    assignable boolean not null default true,
    created_at timestamp not null default now(),
    created_by uuid not null references app_user on update cascade,
    updated_at timestamp not null default now(),
    updated_by uuid not null references app_user on update cascade
);

create table privilege
(
    name text primary key
);

create table role_has_privilege
(
    role uuid references role on delete cascade on update cascade,
    privilege text references privilege on delete cascade on update cascade,
    association_bound boolean not null,
    primary key (role, privilege)
);

create index on role_has_privilege (role);

create table app_user_has_role
(
    app_user uuid references app_user on delete cascade on update cascade,
    role uuid references role on delete cascade on update cascade,
    primary key (app_user, role)
);

create index on app_user_has_role (app_user);
