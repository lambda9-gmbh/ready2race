alter table event
    add column participant_self_registration boolean not null default false;

create table global_configurations
(
    id                          boolean primary key default true check (id = true),
    create_club_on_registration boolean   not null,
    updated_at                  timestamp not null,
    updated_by                  uuid      references app_user on delete set null
);

insert into global_configurations (id, create_club_on_registration, updated_at, updated_by)
values (true, true, now(), null)