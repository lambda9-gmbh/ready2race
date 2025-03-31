set search_path to ready2race, pg_catalog, public;

create table competition_setup
(
    competition_properties uuid primary key references competition_properties on delete cascade,
    created_at             timestamp not null,
    created_by             uuid      references app_user on delete set null,
    updated_at             timestamp not null,
    updated_by             uuid      references app_user on delete set null
);

create table competition_setup_round
(
    id                  uuid primary key,
    competition_setup   uuid    not null references competition_setup on delete cascade,
    next_round          uuid references competition_setup_round,
    name                text    not null,
    required            boolean not null,
    use_default_seeding boolean not null
);

create index on competition_setup_round (competition_setup);

create table competition_setup_group
(
    id           uuid primary key,
    duplicatable boolean not null,
    weighting    integer not null,
    teams        integer,
    name         text,
    position     integer not null
);

create table competition_setup_group_statistic_evaluation
(
    competition_setup_round uuid references competition_setup_round on delete cascade,
    name                    text    not null,
    priority                integer not null,
    rank_by_biggest         boolean not null,
    ignore_biggest_values   integer not null,
    ignore_smallest_values  integer not null,
    as_average              boolean not null,
    primary key (competition_setup_round, name)
);

create unique index priority_unique_in_round on competition_setup_group_statistic_evaluation (competition_setup_round, priority);
create index on competition_setup_group_statistic_evaluation (competition_setup_round);

create table competition_setup_match
(
    id                      uuid primary key,
    competition_setup_round uuid    not null references competition_setup_round on delete cascade,
    competition_setup_group uuid references competition_setup_group on delete cascade,
    duplicatable            boolean not null,
    weighting               integer,
    teams                   integer,
    name                    text,
    position                integer not null,
    constraint chk_not_duplicatable_and_in_group check (
        not (duplicatable is true and competition_setup_group is not null)
        )
);

create index on competition_setup_match (competition_setup_round);
create index on competition_setup_match (competition_setup_group);

create table competition_setup_participant
(
    id                      uuid primary key,
    competition_setup_match uuid references competition_setup_match on delete cascade,
    competition_setup_group uuid references competition_setup_group on delete cascade,
    seed                    integer not null,
    ranking                 integer not null,
    constraint chk_either_competition_setup_match_or_competition_setup_group check (
        (competition_setup_match is null and competition_setup_group is not null) or
        (competition_setup_match is not null and competition_setup_group is null) )
);

create index on competition_setup_participant (competition_setup_match);
create index on competition_setup_participant (competition_setup_group);