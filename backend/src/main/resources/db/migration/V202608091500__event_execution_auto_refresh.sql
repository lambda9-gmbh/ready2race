set search_path to ready2race, pg_catalog, public;

-- Automatischer Datenabgleich auf der Durchführungsseite: Ob die Seite ihren Stand im Hintergrund
-- nachzieht (Zeiten, Ergebnisse, Läufe, Rundenwechsel, Check-ins) und in welchem Takt.
--
-- Die Einstellung sitzt an der Veranstaltung und nicht am Gerät, weil sie eine Aussage über die
-- Veranstaltung ist: Wer am Renntag mit sechs Tablets am Steg steht, will den Takt einmal setzen
-- und nicht sechsmal. Der Wettkampf ist die falsche Ebene - die Durchführungsseite eines
-- Wettkampfs zeigt Daten, die anderswo in derselben Veranstaltung entstehen.
--
-- Voreinstellung an, 5 Sekunden, und zwar auch für bestehende Veranstaltungen: Eine
-- Durchführungsseite, die von sich aus veraltet, ist genau der Zustand, den diese Migration
-- abschafft. Der Takt kostet wenig, weil der Endpunkt seit dieser Änderung mit ETag antwortet und
-- ein unveränderter Stand ohne Rumpf zurückkommt.
--
-- Die Grenzen 5..60 stehen zusätzlich im Request (UpdateEventRequest) - hier als Check, damit sie
-- auch für Daten gelten, die nicht durch die API kommen (Seeds, Prod-Abzüge, Handkorrekturen).
-- Reines Hinzufügen von Spalten: die Views auf `event` brauchen kein vorheriges Droppen,
-- afterMigrate.sql erzeugt sie im selben Lauf ohnehin neu.
alter table event
    add column execution_auto_refresh         boolean not null default true,
    add column execution_auto_refresh_seconds integer not null default 5
        constraint event_execution_auto_refresh_seconds_range
            check (execution_auto_refresh_seconds between 5 and 60);
