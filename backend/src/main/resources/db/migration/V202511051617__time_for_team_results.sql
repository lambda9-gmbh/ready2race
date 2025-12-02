create table if not exists timecode
(
    id                    uuid primary key,
    time                  bigint not null,
    base_unit             text   not null,
    millisecond_precision text   not null
);

alter table competition_match_team
    add column places_calculated boolean not null default false,
    add column timecode       uuid references timecode on delete set null unique;

alter table match_result_import_config
    add column col_team_time text,
    alter column col_team_place drop not null,
    add constraint chk_place_or_time_not_null check (
        (col_team_place is not null)
        or
        (col_team_time is not null)
    );