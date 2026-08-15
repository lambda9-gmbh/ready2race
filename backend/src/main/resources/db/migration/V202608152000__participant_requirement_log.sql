set search_path to ready2race, pg_catalog, public;

-- Revisionsspur der Bedingungs-Bestätigungen (Regattatag 15.08.2026).
--
-- Der Anlass steht in der Sache selbst: An der Waage verschwanden Bestätigungen, weil die Scan-App
-- den Ersetzen-Weg mit einer einzelnen Person aufrief. Als die Frage aufkam, WER sie gelöscht hat,
-- gab es darauf keine Antwort -- participant_has_requirement_for_event kennt nur den aktuellen
-- Stand, und ein geloeschter Datensatz hinterlaesst dort nichts. Die einzige Rekonstruktion waere
-- ein Datenbank-Backup gewesen.
--
-- Getrennt von participant_has_requirement_for_event, aus demselben Grund wie
-- participant_tracking_change neben participant_tracking (V202608091600): Dort steht, was gilt --
-- hier steht, wer das wann festgelegt hat. In einer Tabelle haette die zweite Aenderung die erste
-- ueberschrieben, und genau die erste ist im Zweifel die gesuchte.
create table participant_requirement_log
(
    id                     uuid primary key,
    -- Redundanz mit Absicht: der Eintrag muss einer Person, einer Bedingung und einer
    -- Veranstaltung zuzuordnen bleiben, auch wenn die Erfuellungszeile laengst geloescht ist --
    -- das ist der haeufigste Fall, um den es hier ueberhaupt geht.
    event                  uuid        not null references event (id),
    participant            uuid        not null references participant (id),
    participant_requirement uuid       not null references participant_requirement (id),
    -- Der Bezugsrahmen der Aenderung (V202608141900). null heisst "ohne diese Einschraenkung" --
    -- dieselbe Bedeutung wie in der Erfuellungstabelle, damit sich beide vergleichen lassen.
    event_day              uuid references event_day (id) on delete set null,
    competition            uuid references competition (id) on delete set null,
    action                 varchar(10) not null, -- APPROVED | REVOKED
    -- Woher die Aenderung kam. Genau diese Unterscheidung fehlte am Regattatag: Ein an der Waage
    -- gesetzter Haken und ein im Buero gesetzter sehen im Bestand gleich aus.
    source                 varchar(10) not null, -- SCAN | BULK | IMPORT
    note                   text,
    created_at             timestamp   not null,
    -- on delete set null wie in participant_tracking_change: die Spur ueberlebt den Benutzer.
    created_by             uuid references app_user (id) on delete set null,
    constraint chk_prl_action check (action in ('APPROVED', 'REVOKED')),
    constraint chk_prl_source check (source in ('SCAN', 'BULK', 'IMPORT'))
);

-- Die beiden Fragen, die gestellt werden: "was ist mit dieser Bedingung passiert?" (die Ansicht in
-- der Verwaltung) und "was ist mit dieser Person passiert?" (der Einzelfall am Steg).
create index idx_participant_requirement_log_requirement
    on participant_requirement_log (event, participant_requirement, created_at desc);
create index idx_participant_requirement_log_participant
    on participant_requirement_log (event, participant, created_at desc);
