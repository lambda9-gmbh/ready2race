set search_path to ready2race, pg_catalog, public;

create table app_user_registration
(
    id         uuid primary key,
    token      char(30)  not null,
    email      text      not null,
    password   text      not null,
    firstname  text      not null,
    lastname   text      not null,
    clubname   text,
    language   char(2)   not null,
    expires_at timestamp not null,
    created_at timestamp not null
);

create unique index on app_user_registration (email);

create table app_user_invitation
(
    id         uuid primary key,
    token      char(30)  not null,
    email      text      not null,
    firstname  text      not null,
    lastname   text      not null,
    language   char(2)   not null,
    expires_at timestamp not null,
    created_at timestamp not null,
    created_by uuid      references app_user on delete set null
);

create unique index on app_user_invitation (token);
create unique index on app_user_invitation (email);

create table app_user_invitation_has_role
(
    app_user_invitation uuid references app_user_invitation on delete cascade,
    role                uuid references role on delete cascade,
    primary key (app_user_invitation, role)
);

create index on app_user_invitation_has_role (app_user_invitation);
create index on app_user_invitation_has_role (role);

create table email_address
(
    email text primary key
);

create rule unique_email_app_user as on insert
    to app_user
    do also
    insert into email_address
    values (new.email);

create rule unique_email_app_user_registration as on insert
    to app_user_registration
    do also
    insert into email_address
    values (new.email);

create rule unique_email_app_user_invitation as on insert
    to app_user_invitation
    do also
    insert into email_address
    values (new.email);

create rule delete_email_app_user as on delete
    to app_user
    do also
    delete
    from email_address
    where email = old.email;

create rule delete_email_app_user_registration as on delete
    to app_user_registration
    do also
    delete
    from email_address
    where email = old.email;

create rule delete_email_app_user_invitation as on delete
    to app_user_invitation
    do also
    delete
    from email_address
    where email = old.email;

create table app_user_invitation_to_email
(
    app_user_invitation uuid not null references app_user_invitation on delete cascade,
    email uuid references email on delete set null
);

create unique index on app_user_invitation_to_email (app_user_invitation);
create index on app_user_invitation_to_email (email);

create table app_user_registration_to_email
(
    app_user_registration uuid not null references app_user_registration on delete cascade,
    email uuid references email on delete set null
);

create unique index on app_user_registration_to_email (app_user_registration);
create index on app_user_registration_to_email (email);