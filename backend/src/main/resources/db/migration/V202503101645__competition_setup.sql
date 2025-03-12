set search_path to ready2race, pg_catalog, public;

create table competition_setup
(
    competition_properties uuid primary key references competition_properties on delete cascade,
    created_at  timestamp not null,
    created_by  uuid      references app_user on delete set null,
    updated_at  timestamp not null,
    updated_by  uuid      references app_user on delete set null
);

create table competition_setup_round
(
    id                uuid primary key,
    competition_setup uuid    not null references competition_setup on delete cascade,
    next_round        uuid references competition_setup_round,
    name              text    not null,
    required          boolean not null
);

create index on competition_setup_round (competition_setup);

create table competition_setup_match
(
    id                      uuid primary key,
    competition_setup_round uuid    not null references competition_setup_round on delete cascade,
    weighting               integer not null,
    teams                   integer,
    name                    text
);

create index on competition_setup_match (competition_setup_round);

create table competition_setup_match_outcome
(
    competition_setup_match uuid    not null references competition_setup_match on delete cascade,
    weighting               integer not null,
    ranking                 integer not null,
    primary key (competition_setup_match, weighting)
);

create index on competition_setup_match_outcome (competition_setup_match);