set search_path to ready2race, pg_catalog, public;

-- Drei Betriebsarten der Lauf-Kette (C1): SCHIEDSRICHTER (Beenden + Kette über das
-- Schiedsrichter-Dashboard, wie bisher), REGATTABUERO (Beenden/Aktivieren exklusiv über den
-- Zeitplan - das Regattabüro gibt nach Kontrolle frei, dann zieht die Kette), DEAKTIVIERT (Beenden
-- wirkt nur auf den Lauf selbst, keine automatische Aktivierung der nächsten Läufe). Ersetzt den
-- bisherigen Boolean auto_activate_next_match.
alter table event
    add column chain_progression_mode text not null default 'DEAKTIVIERT';

update event
set chain_progression_mode = case when auto_activate_next_match then 'SCHIEDSRICHTER' else 'DEAKTIVIERT' end;

-- Auf bestehenden Datenbanken hängt event_view an der alten Spalte und blockiert das Drop.
-- afterMigrate.sql droppt und erzeugt alle Views ohnehin bei jedem Lauf neu — hier vorab droppen
-- ist deshalb gefahrlos.
drop view if exists event_view;

alter table event
    drop column auto_activate_next_match;
