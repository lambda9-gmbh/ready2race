alter table event
    add column submission_needs_verification boolean not null default false
;

alter table competition_match_team
    add column result_verified_by uuid references app_user on delete set null,
    add column result_verified_at timestamp
;