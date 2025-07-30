create table startlist_export_config
(
    id uuid primary key,
    name text not null,
    col_participant_firstname text,
    col_participant_lastname text,
    col_participant_gender text,
    col_participant_role text,
    col_participant_year text,
    col_participant_club text,
    col_club_name text,
    col_team_name text,
    col_team_start_number text,
    col_match_name text,
    col_match_start_time text,
    col_round_name text,
    col_competition_identifier text,
    col_competition_name text,
    col_competition_short_name text,
    col_competition_category text,
    created_at timestamp not null,
    created_by uuid references app_user on delete set null,
    updated_at timestamp not null,
    updated_by uuid references app_user on delete set null
);

insert into startlist_export_config
    (id, name, col_participant_firstname, col_participant_lastname, col_participant_gender, col_participant_role,
     col_participant_year, col_participant_club, col_club_name, col_team_name, col_team_start_number, col_match_name,
     col_match_start_time, col_round_name, col_competition_identifier, col_competition_name, col_competition_short_name,
     col_competition_category, created_at, created_by, updated_at, updated_by)
values
    (gen_random_uuid(), 'Webscorer Einzelrennen', 'First name', 'Last name', 'Gender', null, null, 'Team name', null, 'Team name 2', 'Bib', null, 'Start time', null, null, null, null, 'Distance', now(), null, now(), null),
    (gen_random_uuid(), 'Webscorer Teamrennen', null, 'Name', 'Gender', null, null, 'Team name', null, 'Team name 2', 'Bib', null, 'Start time', null, null, null, null, 'Distance', now(), null, now(), null)
;