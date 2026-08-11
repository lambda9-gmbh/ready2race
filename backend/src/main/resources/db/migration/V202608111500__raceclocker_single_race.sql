set search_path to ready2race, pg_catalog, public;

-- Ein RaceClocker-Rennen je Wettkampf (Wunsch vom 11.08.2026).
--
-- RaceClocker hat nachgeliefert: Die Unterscheidung zwischen Einzelstarts (Zeitfahren) und Läufen
-- ist dort entfallen, es gibt nur noch „Rennen". Damit verliert die hiesige Zweiteilung beide
-- Gründe, aus denen sie bestand:
--
--   1. `raceclocker_race.start_mode` beschrieb, WIE ein Rennen startet -- wichtig, solange nur
--      Einzelstarts einen echten Countdown hatten und eine gemappte Lauf-Spalte ein Rennen
--      selbsttätig in den Wave-Modus kippte. Beides gibt es nicht mehr; die Spalte beschreibt
--      einen Unterschied, den das Fremdsystem nicht mehr kennt.
--   2. Die Anwahl je Rundenart (`raceclocker_race_qualification` / `raceclocker_race_rounds` samt
--      der Startlisten-Preset-Paare) existierte, weil eine Qualifikationsrunde als eigenes
--      Zeitfahren-Rennen gefahren wurde. Ab jetzt fährt ein Wettkampf ALLE seine Runden --
--      Qualifikation wie Folgerunden -- in EINEM Rennen, also braucht er genau einen Zeiger.
--
-- Das Setup-Konzept `competition_setup_round.is_qualification` (Setzung, Weiterkommen) bleibt
-- davon vollständig unberührt -- nur seine Rolle als Weiche für Rennwahl und Startlisten-Preset
-- entfällt.
--
-- Zusammenführungs-Regel (Entscheidung von Thomas): coalesce(rounds, qualification) -- das
-- Läufe-Rennen gewinnt, das Zeitfahren-Rennen ist nur der Rückfall, wenn kein Läufe-Rennen
-- angewählt war. Dieselbe Regel für die Startlisten-Presets. Begründung: Das Läufe-Rennen trägt
-- die Mehrzahl der Runden, und ein Wettkampf, der beides gesetzt hatte, soll nach dem Umbau in
-- dem Rennen weiterlaufen, in dem sein Turnierbaum ohnehin endet.
--
-- KEIN Rollback ohne Dump: Die verlierende Hälfte jedes Spaltenpaars und jeder start_mode sind
-- nach dieser Migration unwiederbringlich weg. Wer den Stand davor braucht, braucht den Dump
-- von davor.

-- 1. Die Startart am Rennen: Constraint zuerst, dann die Spalte.
alter table raceclocker_race
    drop constraint chk_raceclocker_race_start_mode;

alter table raceclocker_race
    drop column start_mode;

-- 2. Wettkampf: ein Rennen statt zwei. `on delete set null` wie bisher, damit ein gelöschtes
-- Rennen die Anwahl entwertet, statt das Löschen zu blockieren.
alter table competition
    add column raceclocker_race uuid references raceclocker_race on delete set null;

update competition
set raceclocker_race = coalesce(raceclocker_race_rounds, raceclocker_race_qualification)
where raceclocker_race_rounds is not null
   or raceclocker_race_qualification is not null;

alter table competition
    drop column raceclocker_race_qualification,
    drop column raceclocker_race_rounds;

-- 3. Veranstaltung: Die beiden Rennen-Spalten sind seit V202608111200 tote Spalten -- die
-- Zuordnung liegt vollständig am Wettkampf, kein Code liest oder schreibt sie mehr, und der
-- dortige Backfill hat die effektive Vererbung bereits auf die Wettkämpfe übertragen. Es gibt
-- deshalb keinen Nachfolger `event.raceclocker_race`; sie fallen ersatzlos.
alter table event
    drop column raceclocker_race_qualification,
    drop column raceclocker_race_rounds;

-- 4. Startlisten-Presets: dasselbe Muster, ein Preset statt zwei. Die Zweiteilung existierte nur,
-- weil das Zeitfahren-Preset keine Lauf-Spalte tragen durfte (sonst kippte das Rennen in den
-- Wave-Modus) -- mit dem Ende der Startarten ist auch dieser Zwang weg. Die Vererbung
-- Wettkampf-vor-Veranstaltung bleibt wie gehabt, nur eben eindimensional.
alter table event
    add column startlist_config uuid references startlist_export_config on delete set null;

update event
set startlist_config = coalesce(startlist_config_rounds, startlist_config_qualification)
where startlist_config_rounds is not null
   or startlist_config_qualification is not null;

alter table event
    drop column startlist_config_qualification,
    drop column startlist_config_rounds;

alter table competition
    add column startlist_config uuid references startlist_export_config on delete set null;

update competition
set startlist_config = coalesce(startlist_config_rounds, startlist_config_qualification)
where startlist_config_rounds is not null
   or startlist_config_qualification is not null;

alter table competition
    drop column startlist_config_qualification,
    drop column startlist_config_rounds;
