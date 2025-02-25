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

create table competition_template
(
    id         uuid primary key,
    created_at timestamp not null,
    created_by uuid      references app_user on delete set null,
    updated_at timestamp not null,
    updated_by uuid      references app_user on delete set null
);

create table competition
(
    id         uuid primary key,
    event      uuid      not null references event on delete cascade,
    template   uuid      references competition_template on delete set null,
    created_at timestamp not null,
    created_by uuid      references app_user on delete set null,
    updated_at timestamp not null,
    updated_by uuid      references app_user on delete set null
);

create index on competition (event);

create table event_day_has_competition
(
    event_day  uuid      not null references event_day on delete cascade,
    competition       uuid      not null references competition on delete cascade,
    created_at timestamp not null,
    created_by uuid      references app_user on delete set null,
    primary key (event_day, competition)
);

create index on event_day_has_competition (event_day);
create index on event_day_has_competition (competition);

create table competition_category
(
    id          uuid primary key,
    name        text      not null,
    description text,
    created_at  timestamp not null,
    created_by  uuid      references app_user on delete set null,
    updated_at  timestamp not null,
    updated_by  uuid      references app_user on delete set null
);

create table competition_properties
(
    id                uuid primary key,
    competition              uuid references competition on delete cascade,
    competition_template     uuid references competition_template on delete cascade,
    identifier        text           not null,
    name              text           not null,
    short_name        text,
    description       text,
    count_males       integer        not null,
    count_females     integer        not null,
    count_non_binary  integer        not null,
    count_mixed       integer        not null,
    competition_category     uuid           references competition_category on delete set null,
    constraint chk_either_competition_or_competition_template check ( (competition is null and competition_template is not null) or
                                                        (competition is not null and competition_template is null) )
);
create index on competition_properties (competition);
create index on competition_properties (competition_template);

create table fee
(
    id          uuid primary key,
    name        text      not null,
    label       text,
    description text,
    created_at  timestamp not null,
    created_by  uuid      references app_user on delete set null,
    updated_at  timestamp not null,
    updated_by  uuid      references app_user on delete set null
);

create table competition_properties_has_fee
(
    competition_properties uuid           not null references competition_properties on delete cascade,
    fee             uuid           not null references fee,
    required        boolean        not null,
    amount          decimal(10, 2) not null,
    primary key (competition_properties, fee, required)
);

create index on competition_properties_has_fee (competition_properties);

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

create table competition_properties_has_named_participant
(
    competition_properties   uuid    not null references competition_properties on delete cascade,
    named_participant uuid    not null references named_participant,
    required          boolean not null,
    count_males       integer not null,
    count_females     integer not null,
    count_non_binary  integer not null,
    count_mixed       integer not null,
    primary key (competition_properties, named_participant, required),
    constraint chk_count_sum_greater_0 check (count_males + count_females + count_non_binary + count_mixed > 0)
);

create index on competition_properties_has_named_participant (competition_properties);