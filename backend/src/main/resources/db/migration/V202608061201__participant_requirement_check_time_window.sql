set search_path to ready2race, pg_catalog, public;

alter table participant_requirement
    add column check_earliest_minutes_before int,
    add column check_latest_minutes_before   int;
