set search_path to ready2race, pg_catalog, public;

-- Manueller Check-in/-out (Entwurf 2026-08-09).
--
-- Der QR-Scan bleibt der regulaere Weg. Er versagt aber genau dort, wo es darauf ankommt: ein Boot
-- hat abgelegt, ohne dass jemand das Baendchen gezogen hat -- die Crew ist auf dem Wasser, das
-- Protokoll sagt etwas anderes. Bis hierher gab es dafuer keine Berichtigung: participant_tracking
-- kannte nur Anlegen, nie Aendern, und scanned_at war fest LocalDateTime.now().

-- Wie der Eintrag entstanden ist. Alles Bestehende kam ueber den Scanner, deshalb default 'QR' --
-- der Bestand wird damit korrekt und ohne Datenmigrationsschritt beschriftet.
alter table participant_tracking
    add column source varchar(10) not null default 'QR'; -- QR | MANUAL

-- Die Revisionsspur. Getrennt von participant_tracking, weil beide verschiedene Fragen
-- beantworten: dort steht, was am Steg gilt (eine Zeile = ein Ereignis), hier steht, wer das wann
-- und warum so festgelegt hat. Zusammengelegt haette eine zweite Korrektur die erste ueberschrieben.
create table participant_tracking_change
(
    id                  uuid primary key,
    -- on delete set null statt cascade: die Spur ueberlebt ihren Eintrag. Mit cascade waere
    -- "revisionssicher" ein Versprechen, das der Fremdschluessel nicht haelt.
    tracking            uuid references participant_tracking (id) on delete set null,
    -- Redundant zu participant_tracking, und zwar mit Absicht: eine verwaiste Zeile muss weiterhin
    -- einer Person und einer Veranstaltung zuzuordnen sein.
    participant         uuid        not null references participant (id),
    event               uuid        not null references event (id),
    change_type         varchar(10) not null, -- CREATE | UPDATE
    -- Der Stand VOR der Aenderung; nur eine Korrektur hat einen.
    previous_scan_type  varchar(10),
    previous_scanned_at timestamp,
    new_scan_type       varchar(10) not null,
    new_scanned_at      timestamp   not null,
    -- Pflicht auf Datenbankebene, nicht bloss im Validator: ein vergessener Zweig im Service kann
    -- die Begruendung damit nicht leeren.
    reason              text        not null check (length(btrim(reason)) > 0),
    created_at          timestamp   not null,
    created_by          uuid references app_user (id) on delete set null,
    constraint chk_ptc_previous_matches_type check (
        (change_type = 'UPDATE' and previous_scan_type is not null and previous_scanned_at is not null) or
        (change_type = 'CREATE' and previous_scan_type is null and previous_scanned_at is null))
);

-- Der Dialog liest die Spur je Eintrag, das Protokoll aggregiert sie je Eintrag ueber die ganze
-- Veranstaltung -- beide Wege gehen ueber tracking.
create index idx_participant_tracking_change_tracking on participant_tracking_change (tracking);
create index idx_participant_tracking_change_participant_event on participant_tracking_change (participant, event);
