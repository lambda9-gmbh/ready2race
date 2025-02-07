set search_path to ready2race, pg_catalog, public;

create table event
(
    id                          uuid primary key,
    name                        text      not null,
    description                 text,
    location                    text,
    registration_available_from timestamp,
    registration_available_to   timestamp,
    invoice_prefix              text,
    created_at                  timestamp not null,
    created_by                  uuid      references app_user on delete set null,
    updated_at                  timestamp not null,
    updated_by                  uuid      references app_user on delete set null
);

create table event_day
(
    id          uuid primary key,
    event       uuid      not null references event on delete cascade,
    date        date      not null,
    name        text,
    description text,
    created_at  timestamp not null,
    created_by  uuid      references app_user on delete set null,
    updated_at  timestamp not null,
    updated_by  uuid      references app_user on delete set null
);

create index on event_day (event);

create table race_template
(
    id         uuid primary key,
    created_at timestamp not null,
    created_by uuid      references app_user on delete set null,
    updated_at timestamp not null,
    updated_by uuid      references app_user on delete set null
);

create table race
(
    id         uuid primary key,
    event      uuid      not null references event on delete cascade,
    template   uuid      references race_template on delete set null,
    created_at timestamp not null,
    created_by uuid      references app_user on delete set null,
    updated_at timestamp not null,
    updated_by uuid      references app_user on delete set null
);

create index on race (event);

create table event_day_has_race
(
    event_day  uuid      not null references event_day on delete cascade,
    race       uuid      not null references race on delete cascade,
    created_at timestamp not null,
    created_by uuid      references app_user on delete set null,
    primary key (event_day, race)
);

create index on event_day_has_race (event_day);
create index on event_day_has_race (race);

create table race_category
(
    id          uuid primary key,
    name        text      not null,
    description text,
    created_at  timestamp not null,
    created_by  uuid      references app_user on delete set null,
    updated_at  timestamp not null,
    updated_by  uuid      references app_user on delete set null
);

create table race_properties
(
    id                uuid primary key,
    race              uuid references race on delete cascade,
    race_template     uuid references race_template on delete cascade,
    identifier        text           not null,
    name              text           not null,
    short_name        text,
    description       text,
    count_males       integer        not null,
    count_females     integer        not null,
    count_non_binary  integer        not null,
    count_mixed       integer        not null,
    participation_fee decimal(10, 2) not null,
    rental_fee        decimal(10, 2) not null,
    race_category     uuid           references race_category on delete set null,
    constraint chk_either_race_or_race_template check ( (race is null and race_template is not null) or
                                                        (race is not null and race_template is null) )
);
create index on race_properties (race);
create index on race_properties (race_template);

create table named_participant
(
    id          uuid primary key,
    name        text      not null,
    description text,
    created_at  timestamp not null,
    created_by  uuid      references app_user on delete set null,
    updated_at  timestamp not null,
    updated_by  uuid      references app_user on delete set null
);

create table race_properties_has_named_participant
(
    race_properties   uuid    not null references race_properties on delete cascade,
    named_participant uuid    not null references named_participant,
    required          boolean not null,
    count_males       integer not null,
    count_females     integer not null,
    count_non_binary  integer not null,
    count_mixed       integer not null,
    primary key (race_properties, named_participant, required),
    constraint chk_count_sum_greater_0 check (count_males + count_females + count_non_binary + count_mixed > 0)
);

create index on race_properties_has_named_participant (race_properties);