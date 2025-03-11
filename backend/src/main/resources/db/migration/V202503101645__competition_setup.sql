set search_path to ready2race, pg_catalog, public;

create table competition_setup_round
(
    id          uuid primary key,
    competition uuid    not null references competition on delete cascade,
    next_round  uuid references competition_setup_round,
    name        text    not null,
    required    boolean not null
);

create index on competition_setup_round (competition);

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
    weighting               integer not null
);

create index on competition_setup_match_outcome (competition_setup_match);