set search_path to ready2race, pg_catalog, public;

alter table event
    add column challenge_event             boolean not null default false,
    add column challenge_match_result_type text,
    add column self_submission             boolean not null default false
;

alter table competition_match_team
    add column result_value integer
;

alter table competition_properties
    add column result_confirmation_image_required boolean not null default false
;

create table competition_match_team_document
(
    id                        uuid primary key,
    competition_match_team_id uuid      not null references competition_match_team on delete cascade,
    name                      text      not null,
    created_at                timestamp not null,
    created_by                uuid      references app_user on delete set null,
    updated_at                timestamp not null,
    updated_by                uuid      references app_user on delete set null
);

create table competition_match_team_document_data
(
    competition_match_team_document_id uuid primary key references competition_match_team_document on delete cascade,
    data                               bytea not null
);