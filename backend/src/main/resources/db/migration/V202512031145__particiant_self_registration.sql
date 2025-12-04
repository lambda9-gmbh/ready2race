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
values (true, true, now(), null);

create table app_user_registration_competition_registration
(
    id                    uuid primary key,
    app_user_registration uuid    not null references app_user_registration,
    competition_id        uuid    not null references competition,
    ratingCategory        uuid references rating_category,
    late_registration     boolean not null
);

create index on app_user_registration_competition_registration (app_user_registration);
create index on app_user_registration_competition_registration (competition_id);

create table app_user_registration_competition_registration_fee
(
    app_user_registration_competition_registration uuid not null references app_user_registration_competition_registration,
    optional_fee                                   uuid not null references fee,
    primary key (app_user_registration_competition_registration, optional_fee)
);

alter table app_user_registration
    add column year    integer null                 default null,
    add column gender  gender  null                 default null,
    add column club_id uuid    null references club default null,
    add constraint chk_either_clubname_or_club_id_or_none check (
        (clubname is null and club_id is null) or
        (clubname is not null and club_id is null) or
        (clubname is null and club_id is not null));

create table app_user_club_representative_approval
(
    app_user   uuid primary key references app_user,
    club       uuid      not null references club,
    approved   boolean   not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    updated_by uuid      references app_user on delete set null
);