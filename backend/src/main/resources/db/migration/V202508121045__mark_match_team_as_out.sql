alter table competition_match_team
    add column out boolean not null default false,
    add column failed boolean not null default false,
    add column failed_reason text
;