set search_path to ready2race, pg_catalog, public;

-- Automatischer RaceClocker-Abruf (Entwurf 2026-08-07).
--
-- Die Takte stehen auf der Veranstaltung und nicht auf dem Wettkampf: Ein Abruf holt immer das
-- ganze RaceClocker-Rennen, und das Rennen wird pro Veranstaltung angelegt (siehe V202608062100).
-- Zwei Wettkämpfe am selben Rennen mit verschiedenen Takten wären nicht auflösbar.
alter table event
    add column raceclocker_auto_pull                 boolean not null default false,
    add column raceclocker_interval_active_seconds   int     not null default 5,
    add column raceclocker_interval_upcoming_seconds int     not null default 60,
    add column raceclocker_watch_before_minutes      int     not null default 15,
    add column raceclocker_watch_after_minutes       int     not null default 120;

-- Aus für Bestandsdaten: Eine Veranstaltung, die bisher von Hand nachgezogen wurde, soll nicht
-- durch eine Migration anfangen, sich selbst zu schreiben. Die Vorgaben der übrigen vier Spalten
-- gelten damit erst, wenn jemand die Automatik bewusst einschaltet.

alter table competition_match
    -- Wann zuletzt VERSUCHT wurde abzurufen, nicht wann zuletzt etwas geschrieben wurde: Am
    -- Renntag ist genau die Frage "läuft der Abruf überhaupt noch" die wichtige. Ein Lauf ohne
    -- Änderungen sieht sonst aus wie einer, dessen Abruf steht.
    add column raceclocker_polled_at      timestamp,
    -- Der ErrorCode des letzten Fehlschlags (z. B. RACECLOCKER_UNREACHABLE), null = in Ordnung.
    -- Als Code und nicht als Text, damit die Oberfläche ihn übersetzen kann statt eine englische
    -- Server-Meldung anzuzeigen.
    add column raceclocker_poll_error     text,
    -- Gesetzt, sobald jemand Ergebnisse von Hand einträgt oder eine Datei hochlädt. Die Automatik
    -- lässt den Lauf dann in Ruhe, bis er in der Oberfläche wieder freigegeben wird - das
    -- Regattabüro soll nicht gegen den Job anschreiben müssen.
    add column raceclocker_auto_paused_at timestamp;

-- Die beobachteten Läufe werden über Veranstaltung, finished_at und start_time gesucht; ein
-- eigener Index lohnt bei der Zeilenzahl eines Regattaprogramms nicht.
