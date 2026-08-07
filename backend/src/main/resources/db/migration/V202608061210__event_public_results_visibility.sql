set search_path to ready2race, pg_catalog, public;

-- Ab welchem Zustand ein Lauf als Ergebnis auf den öffentlichen Ansichten erscheint
-- (Athleten-Anzeige "Letztes Ergebnis", Kiosk-Ansicht, öffentliche Ergebnisseite):
--   FINISHED_ONLY    - nur beendete Läufe (competition_match.finished_at gesetzt)
--   RESULTS_COMPLETE - zusätzlich Läufe, deren Boote alle gewertet sind (Zustand AWAITING_FINISH)
--
-- Die Voreinstellung ist bewusst FINISHED_ONLY und gilt auch für bestehende Veranstaltungen. Bis
-- hierher erschien ein Ergebnis, sobald alle Boote gewertet waren - also bevor der Lauf beendet
-- war und damit zu einem Zeitpunkt, an dem noch eine Zeitstrafe eintreffen kann. Ein öffentlich
-- gezeigtes Ergebnis, das sich danach noch ändert, ist schlimmer als eines, das ein paar Minuten
-- später erscheint: das erste ist fotografiert und geteilt, bevor es korrigiert wird, das zweite
-- kostet nur den Beenden-Klick, den der Schiedsrichter ohnehin macht. Ein Standardwert, der die
-- Gefahr reproduziert, macht die Einstellung zur Zierde.
--
-- Bewusst OHNE Backfill auf das alte Verhalten: sonst liefe ausgerechnet die Veranstaltung, für
-- die diese Regel gebaut wurde, weiter mit der alten Sichtbarkeit, weil sie schon in der Datenbank
-- steht. Folge und Preis: Läufe, die vollständig gewertet, aber nie formal beendet wurden,
-- verschwinden aus den öffentlichen Ergebnissen, bis sie beendet werden oder die Veranstaltung auf
-- RESULTS_COMPLETE umgestellt wird.
-- Reines Hinzufügen einer Spalte: die Views auf `event` brauchen anders als in V202608051000
-- kein vorheriges Droppen, afterMigrate.sql erzeugt sie im selben Lauf ohnehin neu.
alter table event
    add column public_results_visibility text not null default 'FINISHED_ONLY';
