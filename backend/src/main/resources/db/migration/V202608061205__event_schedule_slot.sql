set search_path to ready2race, pg_catalog, public;

-- Zeitstrahl (Backlog B2): Slots planen Läufe, bevor sie existieren. Ein Slot zeigt entweder auf
-- eine Setup-Zeile (der spätere Lauf trägt dieselbe ID, PK = FK) oder ist ein freier Slot
-- (Pause, Siegerehrung). Die Slot-Zeit ist die Quelle der geplanten Startzeit und wird per
-- Write-Through auf competition_match.start_time gespiegelt.
create table event_schedule_slot
(
    id                      uuid primary key,
    event                   uuid      not null references event on delete cascade,
    start_time              timestamp not null,
    competition_setup_match uuid unique references competition_setup_match on delete cascade,
    name                    text,
    duration_minutes        int,
    skipped_at              timestamp,
    skipped_by              uuid references app_user on delete set null,
    created_at              timestamp not null,
    created_by              uuid references app_user on delete set null,
    updated_at              timestamp not null,
    updated_by              uuid references app_user on delete set null,
    constraint chk_slot_match_xor_name check (
        (competition_setup_match is not null and name is null) or
        (competition_setup_match is null and name is not null) )
);

create index on event_schedule_slot (event, start_time);

-- Geplant vs. real: start_time bleibt die geplante Zeit, started_at ist der echte Start
-- (Schiedsrichter-Aktion, später überschrieben von der Zeitnahme), finished_at das persistierte
-- Ende — schließt das Loch, dass "beendet" bisher aus der Ergebnislage zurückgerechnet wurde.
alter table competition_match
    add column started_at timestamp,
    add column finished_at timestamp;
