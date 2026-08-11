set search_path to ready2race, pg_catalog, public;

-- Der gemessene Start des einzelnen Boots (Zyklus 2026-08-10, Punkt "Timetrail: anzeigen, wenn
-- jemand gestartet ist").
--
-- Bei Wellenstarts starten alle Boote zusammen und `competition_match.started_at` sagt alles.
-- Beim Zeitfahren (RaceClocker INDIVIDUAL) startet jedes Boot einzeln, und am Steg ist die
-- Frage "wer ist schon unterwegs?" offen, solange noch keine Zielzeit da ist. Der Feed traegt
-- den Stempel je Zeile; ab hier wird er je Boot uebernommen -- wie die Runden bei jedem Abruf
-- vollstaendig aus dem Feed, eine in RaceClocker zurueckgenommene Startzeit verschwindet also
-- auch hier wieder.
alter table competition_match_team
    add column started_at timestamp;
