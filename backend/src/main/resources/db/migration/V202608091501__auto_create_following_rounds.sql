set search_path to ready2race, pg_catalog, public;

-- Folgerunden automatisch erzeugen (Entwurf 2026-08-09).
--
-- Bewusst standardmäßig aus: Die Automatik erzeugt Paarungen ohne Rückfrage, und wer seinen Ablauf
-- noch einrichtet, will das nicht. Eingeschaltet wird sie pro Veranstaltung.
alter table event
    add column auto_create_following_rounds boolean not null default false;

-- Die Übersteuerung des einzelnen Wettkampfs, nullable statt not-null: null heißt "erben", true und
-- false heißen "ausdrücklich". Dasselbe Muster wie competition.timing_system über
-- event.timing_system (V202608062100) — die Lesestelle entscheidet mit coalesce.
--
-- Auf `competition` und nicht auf `competition_properties`, weil letztere laut Check-Constraint
-- auch an einer Wettkampf-Vorlage hängen kann. Eine Vorlage trägt keine Ablaufsteuerung einer
-- konkreten Regatta.
alter table competition
    add column auto_create_following_rounds boolean;

-- Merkt, dass diese Runde schon einmal gesetzt war. Steht auf der SETUP-Runde und nicht auf den
-- Läufen, weil es genau deren Löschen überleben muss: Erst daran ist zu erkennen, ob eine erzeugte
-- Runde die erste ihrer Art ist oder die Wiederholung nach einer Ergebniskorrektur.
-- deleteCurrentRound räumt die Spalte deshalb NICHT ab.
--
-- Nicht zu verwechseln mit "round_materialized" (EventScheduleRepo, korrelierte Subquery): Das ist
-- eine Ist-Abfrage, "hat diese Runde JETZT Läufe" - sie wird mit jedem Löschen wieder falsch. Diese
-- Spalte hier hält dagegen fest, dass die Runde JEMALS Läufe hatte, und bleibt es auch danach. Wer
-- beide verwechselt, verschiebt Zeitstrahl-Zustände.
alter table competition_setup_round
    add column materialized_at timestamp;

-- Der sichtbare Vermerk am Lauf, wenn seine Paarung aus einer Wiederholung stammt. Gegenstück zu
-- raceclocker_auto_paused_at: ein Zeitstempel, den die Orga-Ansichten als Hinweis zeigen. Die
-- öffentliche Anzeige und die Athleten-Anzeige lesen ihn nicht.
alter table competition_match
    add column pairings_recalculated_at timestamp;

-- Beide Views hängen an den geänderten Tabellen; afterMigrate.sql erzeugt sie ohnehin bei jedem
-- Lauf neu. Vorab droppen hält bestehende Datenbanken sauber (gleiches Vorgehen wie V202608091400).
drop view if exists competition_setup_round_with_matches;
drop view if exists competition_match_with_teams;

-- Nachtrag für Bestandsdaten: Runden, die zu diesem Zeitpunkt bereits Läufe haben, sind für die
-- laufende Regatta keine Erst-Erzeugung mehr, sondern längst gesetzt. Ohne diesen Nachtrag bliebe
-- ihr materialized_at NULL, und ihre nächste Wiedererzeugung (Ergebniskorrektur, Automatik) würde
-- als Erst-Erzeugung gelesen - mit der Folge, dass der Vermerk am wiedererzeugten Lauf ausbleibt,
-- ausgerechnet dort, wo er gerade gebraucht wird. Der genaue Zeitpunkt ist beliebig, nur "gesetzt"
-- zählt.
update competition_setup_round
set materialized_at = now()
where materialized_at is null
  and exists (
    select 1
    from competition_setup_match
    join competition_match on competition_match.competition_setup_match = competition_setup_match.id
    where competition_setup_match.competition_setup_round = competition_setup_round.id
  );
