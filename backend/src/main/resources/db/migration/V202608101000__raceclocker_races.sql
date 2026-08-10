set search_path to ready2race, pg_catalog, public;

-- Benannte RaceClocker-Rennen je Veranstaltung (Entwurf 2026-08-09).
--
-- Bisher gab es genau zwei Adressen: eine für Zeitfahren, eine für Läufe (V202607211200 auf dem
-- Wettkampf, V202608062100 als Voreinstellung auf der Veranstaltung). Eine Regatta fährt aber mehr
-- Rennen als zwei -- bei der Coastal-Rowing-Regatta sind es Timetrials, Langstrecke und
-- Kurzstrecke. Im Zwei-Slot-Modell ließ sich das nur über Wettkampf-Overrides abbilden, und dann
-- trug jeder Wettkampf sein eigenes Adresspaar bei: die Zahl der Abrufe je Takt wuchs mit der Zahl
-- der WETTKÄMPFE statt mit der Zahl der RENNEN.
--
-- Ab hier ist ein Rennen eine eigene Zeile, und Veranstaltung wie Wettkampf zeigen nur noch darauf.
create table raceclocker_race
(
    id            uuid primary key,
    event         uuid      not null references event on delete cascade,
    name          text      not null,
    results_url   text      not null,
    -- INDIVIDUAL = Einzelstarts (Zeitfahren), WAVE = Start in mehreren Läufen. Der Unterschied ist
    -- nicht kosmetisch: Nur Einzelstarts haben in RaceClocker einen echten Countdown, und eine
    -- gemappte Lauf-Spalte kippt ein Rennen selbsttätig in den Wave-Modus. Als Spalte hinterlegt,
    -- damit sich das künftig prüfen lässt, statt es am Renntag am fehlenden Countdown zu merken.
    start_mode    text      not null,
    -- Noch von niemandem gelesen: der Andockpunkt für die Rundenzeiten der Langstrecke, die in
    -- einem eigenen Zyklus kommen. Wie RaceClocker sie im Feed ausliefert, ist noch unbekannt.
    captures_laps boolean   not null default false,
    position      int       not null,
    created_at    timestamp not null,
    created_by    uuid references app_user on delete set null,
    updated_at    timestamp not null,
    updated_by    uuid references app_user on delete set null,
    constraint chk_raceclocker_race_start_mode check (start_mode in ('INDIVIDUAL', 'WAVE')),
    -- Eine leere Adresse hat keinen Zweck: Sie würde nicht als „nicht konfiguriert" gelesen,
    -- sondern abgerufen -- und scheiterte dann mit einem unbrauchbaren URL-Fehler. Der Backfill
    -- unten filtert sie deshalb schon heraus; diese Bedingung hält sie dauerhaft fern.
    constraint chk_raceclocker_race_url_not_blank check (btrim(results_url) <> ''),
    -- Zwei Rennen gleichen Namens wären in einem Auswahlfeld nicht unterscheidbar, und genau am
    -- Renntag ist das der Fehler, der weh tut.
    constraint uq_raceclocker_race_event_name unique (event, name),
    -- Zwei Rennen mit derselben Adresse wären zwei Abrufe für dieselbe Antwort -- genau die
    -- Verschwendung, die diese Änderung beseitigt. Die Eindeutigkeit trägt zusätzlich den Backfill
    -- unten: er verbindet die alten Spalten über (event, results_url) mit den neuen Zeilen.
    constraint uq_raceclocker_race_event_url unique (event, results_url)
);

create index on raceclocker_race (event);

-- Die Anwahl: dasselbe Vererbungsmuster wie zuvor bei den Adressen -- Wettkampf vor Veranstaltung,
-- gelesen per coalesce. `on delete set null`, damit ein gelöschtes Rennen die Anwahl entwertet,
-- statt das Löschen zu blockieren.
alter table event
    add column raceclocker_race_qualification uuid references raceclocker_race on delete set null,
    add column raceclocker_race_rounds        uuid references raceclocker_race on delete set null;

alter table competition
    add column raceclocker_race_qualification uuid references raceclocker_race on delete set null,
    add column raceclocker_race_rounds        uuid references raceclocker_race on delete set null;

-- Backfill. Ziel: Für eine laufende Regatta ändert sich nichts.
--
-- Entdoppelt über die Adresse: Zwei Wettkämpfe mit derselben Override-Adresse teilen sich EIN
-- Rennen, statt zwei gleichlautende zu erzeugen. Das ist der Punkt, an dem die Migration die
-- Schulden tilgt, statt sie ins neue Modell zu übertragen.
with sources as (
    select e.id                          as event_id,
           e.raceclocker_tt_results_url  as url,
           'INDIVIDUAL'                  as start_mode,
           true                          as event_level,
           null::text                    as label
    from event e
    where nullif(trim(e.raceclocker_tt_results_url), '') is not null
    union all
    select e.id, e.raceclocker_heats_results_url, 'WAVE', true, null::text
    from event e
    where nullif(trim(e.raceclocker_heats_results_url), '') is not null
    union all
    -- LEFT join, nicht inner: `competition_properties.competition` trägt weder Eindeutigkeit
    -- noch eine Pflicht. Ein Wettkampf ohne Eigenschaftszeile fiele bei einem inneren Join aus
    -- der Menge, bekäme kein Rennen und behielte unten null -- und weil die Vererbung am
    -- Lesepunkt sitzt, holte er ab dann still die Adresse der VERANSTALTUNG statt seiner
    -- eigenen. Ein falscher Feed ohne Fehlermeldung. Fehlt der Name, greift unten der
    -- `label is null`-Zweig und vergibt den allgemeinen Namen.
    select c.event, c.raceclocker_tt_results_url, 'INDIVIDUAL', false,
           coalesce(cp.short_name, cp.identifier)
    from competition c
             left join competition_properties cp on cp.competition = c.id
    where nullif(trim(c.raceclocker_tt_results_url), '') is not null
    union all
    select c.event, c.raceclocker_heats_results_url, 'WAVE', false,
           coalesce(cp.short_name, cp.identifier)
    from competition c
             left join competition_properties cp on cp.competition = c.id
    where nullif(trim(c.raceclocker_heats_results_url), '') is not null
),
deduped as (
    select event_id,
           url,
           -- Dieselbe Adresse in beiden alten Spalten kann nur EIN Rennen sein. WAVE gewinnt, weil
           -- das der Modus ist, in den RaceClocker beim Import mit Lauf-Spalte selbst kippt.
           case when bool_or(start_mode = 'WAVE') then 'WAVE' else 'INDIVIDUAL' end as start_mode,
           bool_or(event_level)                                                     as event_level,
           min(label)                                                               as label
    from sources
    group by event_id, url
),
named as (
    select event_id,
           url,
           start_mode,
           case
               when event_level or label is null
                   then case when start_mode = 'WAVE' then 'Läufe' else 'Zeitfahren' end
               else case when start_mode = 'WAVE' then 'Läufe ' else 'Zeitfahren ' end || label
               end as base_name
    from deduped
),
numbered as (
    select event_id,
           url,
           start_mode,
           base_name,
           -- Zwei Wettkämpfe mit gleichem Kürzel, aber verschiedenen Adressen kollidierten im Namen.
           -- Eine Migration darf nicht an einem Datenzufall scheitern.
           row_number() over (partition by event_id, base_name order by url) as dup,
           row_number() over (partition by event_id order by start_mode, base_name, url) as pos
    from named
)
insert into raceclocker_race (id, event, name, results_url, start_mode, captures_laps, position,
                              created_at, updated_at)
select gen_random_uuid(),
       event_id,
       case when dup = 1 then base_name else base_name || ' (' || dup || ')' end,
       url,
       start_mode,
       false,
       pos::int,
       now(),
       now()
from numbered;

-- Anwahl setzen. Die Verbindung läuft über (event, results_url), das ist oben eindeutig.
update event e
set raceclocker_race_qualification = r.id
from raceclocker_race r
where r.event = e.id
  and r.results_url = e.raceclocker_tt_results_url;

update event e
set raceclocker_race_rounds = r.id
from raceclocker_race r
where r.event = e.id
  and r.results_url = e.raceclocker_heats_results_url;

update competition c
set raceclocker_race_qualification = r.id
from raceclocker_race r
where r.event = c.event
  and r.results_url = c.raceclocker_tt_results_url;

update competition c
set raceclocker_race_rounds = r.id
from raceclocker_race r
where r.event = c.event
  and r.results_url = c.raceclocker_heats_results_url;

-- Die vier alten Spalten bleiben hier absichtlich stehen; sie fallen erst in
-- V202608101010, wenn kein Code sie mehr liest.
--
-- Der Grund ist nicht Vorsicht, sondern Übersetzbarkeit: Kotlin übersetzt alle Hauptquellen als
-- eine Einheit, und jOOQ erzeugt seine Klassen aus genau diesem Schema. Fielen die Spalten schon
-- hier, verlöre der Zweig ab diesem Commit seine Übersetzbarkeit -- bis zum letzten Umbau der
-- Aufrufstellen liefe kein einziger Test mehr, auch keiner, der mit der Sache nichts zu tun hat.
-- Getrennt bleibt jeder Zwischenstand lauffähig und prüfbar, und die Umstellung lässt sich in zwei
-- unabhängigen Schritten ausrollen statt in einem.
