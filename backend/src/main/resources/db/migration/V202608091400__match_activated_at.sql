set search_path to ready2race, pg_catalog, public;

-- Aktivierung und Ist-Start sind zwei verschiedene Aussagen (Entwurf 2026-08-09).
--
-- currently_running hiess "laufend", bedeutete aber immer "vom Schiedsrichter an den Start
-- gerufen" -- der Ist-Start steht seit jeher getrennt in started_at. Solange niemand einen Start
-- messen konnte, war die Ungenauigkeit folgenlos; mit dem automatischen RaceClocker-Abruf gibt es
-- einen zuverlaessigen Sender dafuer, und der Name muss die Bedeutung tragen.
--
-- Als Zeitstempel statt als Flag, weil die Frage "seit wann steht der Lauf am Start" am Renntag
-- genauso zaehlt wie "steht er ueberhaupt".
alter table competition_match
    add column activated_at timestamp;

-- Naeherung fuer Bestandsdaten: der Aktivierungszeitpunkt wurde nie festgehalten. started_at ist
-- der beste Beleg, wo er existiert; sonst bleibt updated_at, das bei der Aktivierung mitgeschrieben
-- wurde. Bei einer Regatta, die zum Migrationszeitpunkt nicht laeuft, sind das null Zeilen.
update competition_match
set activated_at = coalesce(started_at, updated_at)
where currently_running;

-- Auf bestehenden Datenbanken haengt competition_match_with_teams an der alten Spalte und blockiert
-- das Drop; competition_setup_round_with_matches haengt wiederum an dieser View. afterMigrate.sql
-- droppt und erzeugt alle Views ohnehin bei jedem Lauf neu -- hier vorab droppen ist deshalb
-- gefahrlos (siehe V202608061207, gleicher Fall).
drop view if exists competition_setup_round_with_matches;
drop view if exists competition_match_with_teams;

alter table competition_match
    drop column currently_running;
