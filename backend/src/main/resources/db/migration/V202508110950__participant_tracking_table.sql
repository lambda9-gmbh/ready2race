set search_path to ready2race, pg_catalog, public;

create table participant_tracking
(
    id          uuid primary key,
    participant uuid        not null references participant (id),
    event       uuid        not null references event (id),
    scan_type   varchar(10) not null,
    scanned_at  timestamp   not null,
    scanned_by  uuid references app_user (id)
);

create index idx_participant_tracking_participant on participant_tracking (participant);
create index idx_participant_tracking_event on participant_tracking (event);
create index idx_participant_tracking_scan_type on participant_tracking (scan_type);
create index idx_participant_tracking_scanned_at on participant_tracking (scanned_at desc);