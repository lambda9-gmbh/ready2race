create table match_result_import_config
(
    id uuid primary key,
    name text not null,
    col_team_start_number text not null,
    col_team_place text not null,
    created_at timestamp not null,
    created_by uuid references app_user on delete set null,
    updated_at timestamp not null,
    updated_by uuid references app_user on delete set null
);

insert into match_result_import_config
    (id, name, col_team_start_number, col_team_place, created_at, created_by, updated_at, updated_by)
values
    (gen_random_uuid(), 'Webscorer', 'Bib', 'Place', now(), null, now(), null)
;