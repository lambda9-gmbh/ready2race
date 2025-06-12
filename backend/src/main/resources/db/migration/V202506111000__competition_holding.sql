set search_path to ready2race, pg_catalog, public;

alter table competition_registration
    rename column start_number to team_number;

create table competition_match
(
    competition_setup_match uuid primary key references competition_setup_match,
    start_time              timestamp,
    created_at              timestamp not null,
    created_by              uuid      references app_user on delete set null,
    updated_at              timestamp not null,
    updated_by              uuid      references app_user on delete set null
);

create table competition_match_team
(
    id                       uuid primary key,
    competition_match        uuid      not null references competition_match on delete cascade,
    competition_registration uuid      not null references competition_registration,
    place                    integer,
    created_at               timestamp not null,
    created_by               uuid      references app_user on delete set null,
    updated_at               timestamp not null,
    updated_by               uuid      references app_user on delete set null
);

create unique index starting_position_unique_in_match on competition_match_team (competition_match);

create index on competition_match_team (competition_match);