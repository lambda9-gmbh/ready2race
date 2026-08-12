set search_path to ready2race, pg_catalog, public;

-- Veranstaltungsweiter Hinweisbanner (z.B. Wetterwarnung), gepflegt auf der Event-Hauptseite und
-- ausgespielt über die gepollten öffentlichen Antworten (Mein Event, Boards, Live-Dashboard,
-- öffentliche Ergebnisseite).
--
-- Zwei Spalten, beide nullable: beide null heißt "kein Banner". Damit kein halber Zustand
-- entsteht (Text ohne Stufe oder umgekehrt), erzwingt ein Paar-Check, dass immer beide gesetzt
-- oder beide leer sind - auch für Daten, die nicht durch die API kommen (Seeds, Prod-Abzüge,
-- Handkorrekturen). Die Stufen entsprechen dem Enum EventNoticeSeverity im Backend.
--
-- Reines Hinzufügen von Spalten: die Views auf `event` brauchen kein vorheriges Droppen,
-- afterMigrate.sql erzeugt sie im selben Lauf ohnehin neu (dasselbe Muster wie V202608091500).
alter table event
    add column notice_text     text,
    add column notice_severity text
        constraint event_notice_severity_values
            check (notice_severity in ('INFO', 'WARNING', 'CRITICAL')),
    add constraint event_notice_paired
        check ((notice_text is null) = (notice_severity is null));
