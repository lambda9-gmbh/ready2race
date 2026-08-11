set search_path to ready2race, pg_catalog, public;

-- Rundenzeiten (Zwischenzeiten) eines Boots, wie RaceClocker sie liefert (Zyklus 2026-08-10).
--
-- Der Feed trägt neben Start/Finish bis zu vier frei benannte Split-Spalten („Runde 1",
-- „Split 3", ...), jede als Tageszeit-Stempel des Moments, in dem das Boot die Marke passiert
-- hat; 00:00:00.0 heißt „nicht genommen". Gespeichert wird die KUMULIERTE Fahrzeit seit dem
-- gemessenen Start in Millisekunden -- die Tageszeit selbst hängt am Renntag und wäre nach dem
-- Abruf nicht mehr eindeutig (siehe die Datumsregel in RaceClockerPollLogic.stampOnNearestDay),
-- während die Fahrzeit genau das ist, was jede Anzeige zeigt.
--
-- Je Abruf werden die Runden eines Boots vollständig ersetzt (Löschen + Einfügen im selben
-- Takt): der Feed ist die Quelle der Wahrheit, und Umbenennungen der Spalten in RaceClocker
-- sollen ankommen, statt an einem Upsert-Schlüssel zu scheitern.
create table competition_match_team_lap
(
    id                      uuid primary key,
    competition_match_team  uuid      not null references competition_match_team on delete cascade,
    -- Reihenfolge der Spalten im Feed, ab 1. Nicht der Name: der ist frei vergeben und
    -- unterscheidet nicht zuverlässig ("Runde 1" kann nach "Split" umbenannt werden).
    position                int       not null,
    -- Der Spaltenname aus RaceClocker, so wie der Zeitnehmer ihn vergeben hat -- er ist die
    -- Beschriftung in allen Anzeigen.
    name                    text      not null,
    -- Kumulierte Fahrzeit seit dem gemessenen Start in Millisekunden.
    lap_millis              bigint    not null,
    created_at              timestamp not null,
    created_by              uuid      references app_user on delete set null,
    constraint uq_match_team_lap_position unique (competition_match_team, position),
    constraint chk_match_team_lap_millis check (lap_millis >= 0)
);

create index on competition_match_team_lap (competition_match_team);
