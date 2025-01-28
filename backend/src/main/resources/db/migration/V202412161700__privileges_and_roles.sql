set search_path to ready2race, pg_catalog, public;

create table role
(
    id uuid primary key default gen_random_uuid(),
    name text not null,
    description text,
    static boolean not null default false,
    assignable boolean not null default true,
    created_at timestamp not null default now(),
    created_by uuid references app_user on delete set null,
    updated_at timestamp not null default now(),
    updated_by uuid references app_user on delete set null
);

create table privilege
(
    id uuid primary key default gen_random_uuid(),
    action text not null,
    resource text not null,
    scope text not null
);

create unique index on privilege (action, resource, scope);

create table role_has_privilege
(
    role uuid references role on delete cascade,
    privilege uuid references privilege on delete cascade,
    primary key (role, privilege)
);

create index on role_has_privilege (role);

create table app_user_has_role
(
    app_user uuid references app_user on delete cascade,
    role uuid references role on delete cascade,
    primary key (app_user, role)
);

create index on app_user_has_role (app_user);
create index on app_user_has_role (role);
