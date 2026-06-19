set search_path to ready2race, pg_catalog, public;

-- Marks preliminary / qualification rounds (e.g. a time trial). The number of teams that qualify out of the
-- qualification round(s) defines the fixed bracket size N, which in turn drives the match naming below.
alter table competition_setup_round
    add column is_qualification boolean not null default false;

-- Per-participant-count overrides for match name and execution order. Only deviations from the default
-- (competition_setup_match.name / execution_order) are stored. Resolved at round creation against the
-- actual bracket size N and written onto the competition's own competition_setup_match rows.
create table competition_setup_match_naming
(
    competition_setup_round uuid    not null references competition_setup_round on delete cascade,
    participant_count       integer not null,
    match_weighting         integer not null,
    name                    text,
    execution_order         integer,
    primary key (competition_setup_round, participant_count, match_weighting)
);

create index on competition_setup_match_naming (competition_setup_round);

-- The "Zeitfahren" round of the built-in "Beachsprint" template is a qualification round.
update competition_setup_round
set is_qualification = true
where id = '9b6b61a2-4e82-4c07-88eb-d74d213a2de1';