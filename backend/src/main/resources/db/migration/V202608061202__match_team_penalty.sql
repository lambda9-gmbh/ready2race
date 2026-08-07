set search_path to ready2race, pg_catalog, public;

-- Zeitstrafen je Mannschaft und Lauf. Zeitmess-Tools wie RaceClocker liefern die
-- Strafe in Sekunden zusammen mit einer Begründung; die gemeldete Ergebniszeit
-- enthält die Strafe dort bereits. Sie wird zusätzlich separat gespeichert, damit
-- Schiedsrichter und Ergebnislisten nachvollziehen können, warum eine Zeit abweicht.
alter table competition_match_team
    add column penalty_seconds int,
    add column penalty_note    text;
