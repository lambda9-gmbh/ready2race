create table club
(
    id         uuid      not null primary key,
    name       text      not null,
    created_at timestamp not null,
    created_by uuid      references app_user on delete set null,
    updated_at timestamp not null,
    updated_by uuid      references app_user on delete set null
);

create type gender as enum ('M', 'F', 'O');

create table participant
(
    id                 uuid                 not null primary key,
    club               uuid references club not null,
    firstname          text                 not null,
    lastname           text                 not null,
    year               integer,
    gender             gender               not null,
    phone              text,
    external           boolean              not null default false,
    external_club_name text,
    created_at         timestamp            not null,
    created_by         uuid                 references app_user on delete set null,
    updated_at         timestamp            not null,
    updated_by         uuid                 references app_user on delete set null
        constraint chk_extern_has_club_name check ((external = true and external_club_name is not null) or
                                                   (external = false and external_club_name is null))
);

create index on participant (club);

create table event_registration
(
    id                   uuid primary key,
    event                uuid references event not null,
    club                 uuid references club  not null,
    optional_fee_checked boolean               not null default false,
    created_at           timestamp             not null,
    created_by           uuid                  references app_user on delete set null,
    updated_at           timestamp             not null,
    updated_by           uuid                  references app_user on delete set null
);
create unique index event_registration_unique_for_club on event_registration (event, club);

create index on event_registration (club);
create index on event_registration (event);

create table event_day_registration
(
    id                   uuid      not null primary key,
    event_day            uuid      not null references event_day,
    club                 uuid      not null references club,
    optional_fee_checked boolean   not null default false,
    created_at           timestamp not null,
    created_by           uuid      references app_user on delete set null,
    updated_at           timestamp not null,
    updated_by           uuid      references app_user on delete set null
);

create index on event_day_registration (event_day);
create index on event_day_registration (club);

create table competition_registration
(
    id                   uuid      not null primary key,
    competition          uuid      not null references competition,
    club                 uuid      not null references club,
    name                 text,
    optional_fee_checked boolean   not null default false,
    created_at           timestamp not null,
    created_by           uuid      references app_user on delete set null,
    updated_at           timestamp not null,
    updated_by           uuid      references app_user on delete set null,
    unique (competition, club, name)
);

create index on competition_registration (competition);
create index on competition_registration (club);

create table competition_registration_participant
(
    competition_registration uuid not null references competition_registration on delete cascade,
    participant              uuid not null references participant,
    primary key (competition_registration, participant)
);

create table competition_registration_named_participant
(
    competition_registration uuid not null references competition_registration on delete cascade,
    named_participant        uuid not null references named_participant,
    participant              uuid not null references participant,

    primary key (competition_registration, participant)
);

create index on competition_registration_named_participant (named_participant);
