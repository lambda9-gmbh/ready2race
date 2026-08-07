-- Schiedsrichter-Testrennen: eigene kleine Veranstaltung, mit der die Schiedsrichter ihre
-- Ansicht einmal komplett durchspielen können - vom nicht gesetzten Lauf über die Vorlauf-
-- Durchführung bis zur Progression ins Finale. Folgt denselben Konventionen wie
-- seed-foerde.sql: UUID-Präfix 7e57 für alle Zeilen, Cleanup-Block zuerst (nur 7e57% -
-- f0de%/5eed%/fee1% bleiben unangetastet), set time zone Europe/Berlin.
--
-- Aufbau:
--   Event "Schiedsrichter-Testrennen" (published, chain_progression_mode='SCHIEDSRICHTER'),
--   1 Renntag: Do 13.08.2026 (Vorabend-Training vor der Regatta am 14.08.).
--   4 Vereine (Testclub Nord/Süd/Ost/West), je 1 Einer-Boot mit 1 Person Crew.
--
--   1 Wettkampf "Testrennen Einer" (T1) mit 2 Runden:
--     Runde 1 "Vorlauf" (required, is_qualification, Massenfeld alle 4 Boote)
--     Runde 2 "Finale" (required, teams=2) - die Plätze 1 & 2 des Vorlaufs rücken nach.
--   NICHTS ist materialisiert: Die Schiedsrichter setzen den Vorlauf selbst (Slot wechselt
--   WAITING -> LINKED), aktivieren ihn, erfassen Ergebnisse, beenden - und sehen dann beim
--   Setzen des Finales die Progression (Vorlauf-Plätze 1 & 2 stehen im Finale).
--
--   Zeitstrahl (13.08.):
--     17:00 Programmpunkt "Einweisung Schiedsrichter" (30 min, FREE)
--     17:30 Vorlauf-Slot (WAITING)
--     18:00 Finale-Slot (WAITING)

set search_path to ready2race, pg_catalog, public;
set time zone 'Europe/Berlin';

-- ============================================================================================
-- Cleanup: vorherige 7e57-Zeilen entfernen, FK-Reihenfolge beachten (Kinder vor Eltern).
-- ============================================================================================

delete from participant_tracking
where id::text like '7e57%' or participant::text like '7e57%';

delete from competition_registration_named_participant
where competition_registration::text like '7e57%' or participant::text like '7e57%';

delete from timecode
where id in (
    select timecode from competition_match_team
    where competition_match in (select id from competition_setup_match where id::text like '7e57%')
       or competition_registration in (select id from competition_registration where id::text like '7e57%')
);

delete from competition_match_team
where competition_match in (select id from competition_setup_match where id::text like '7e57%')
   or competition_registration in (select id from competition_registration where id::text like '7e57%');

delete from competition_deregistration
where competition_registration::text like '7e57%';

delete from competition_match
where competition_setup_match::text like '7e57%';

delete from event_schedule_slot
where id::text like '7e57%' or event::text like '7e57%';

delete from competition_registration
where id::text like '7e57%';

delete from event_registration
where id::text like '7e57%';

update competition_setup_round set next_round = null where id::text like '7e57%';

delete from competition_properties
where id::text like '7e57%';

delete from competition
where id::text like '7e57%';

delete from participant
where id::text like '7e57%';

delete from named_participant
where id::text like '7e57%';

delete from club
where id::text like '7e57%';

delete from event
where id::text like '7e57%';

-- ============================================================================================
-- Event + Renntag
-- ============================================================================================

insert into event (id, name, description, location, published, chain_progression_mode,
                    created_at, created_by, updated_at, updated_by)
values ('7e570000-0000-0000-0000-000000000001', 'Schiedsrichter-Testrennen',
        'Übungsrennen für die Schiedsrichter: Vorlauf setzen, durchführen, beenden — dann zeigt das Finale die Progression',
        'Flensburg', true, 'SCHIEDSRICHTER', now(), null, now(), null);

insert into event_day (id, event, date, name, description, created_at, created_by, updated_at, updated_by)
values ('7e570000-0000-0000-0000-000000000002', '7e570000-0000-0000-0000-000000000001', '2026-08-13', 'Testabend',
        'Einweisung und Übungsrennen', now(), null, now(), null);

-- ============================================================================================
-- Vereine + Meldungen
-- ============================================================================================

insert into club (id, name, created_at, created_by, updated_at, updated_by)
values
    ('7e570000-0000-0000-0000-000000000003', 'Testclub Nord', now(), null, now(), null),
    ('7e570000-0000-0000-0000-000000000004', 'Testclub Süd', now(), null, now(), null),
    ('7e570000-0000-0000-0000-000000000005', 'Testclub Ost', now(), null, now(), null),
    ('7e570000-0000-0000-0000-000000000006', 'Testclub West', now(), null, now(), null);

insert into event_registration (id, event, club, message, created_at, created_by, updated_at, updated_by)
values
    ('7e570000-0000-0000-0000-000000000007', '7e570000-0000-0000-0000-000000000001', '7e570000-0000-0000-0000-000000000003', null, now(), null, now(), null),
    ('7e570000-0000-0000-0000-000000000008', '7e570000-0000-0000-0000-000000000001', '7e570000-0000-0000-0000-000000000004', null, now(), null, now(), null),
    ('7e570000-0000-0000-0000-000000000009', '7e570000-0000-0000-0000-000000000001', '7e570000-0000-0000-0000-000000000005', null, now(), null, now(), null),
    ('7e570000-0000-0000-0000-00000000000a', '7e570000-0000-0000-0000-000000000001', '7e570000-0000-0000-0000-000000000006', null, now(), null, now(), null);

-- ============================================================================================
-- Wettkampf T1 "Testrennen Einer": Vorlauf (alle 4) -> Finale (Plätze 1 & 2)
-- ============================================================================================

insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('7e570000-0000-0000-0000-00000000000b', '7e570000-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('7e570000-0000-0000-0000-000000000002', '7e570000-0000-0000-0000-00000000000b', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name,
                                     description, competition_category)
values ('7e570000-0000-0000-0000-00000000000c', '7e570000-0000-0000-0000-00000000000b', null, 'T1', 'Testrennen Einer', 'Test 1x', null, null);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('7e570000-0000-0000-0000-00000000000c', now(), null, now(), null);

-- Finale zuerst (keine Vorwärtsreferenz), dann Vorlauf (next_round -> Finale).
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name,
                                      required, use_default_seeding, places_option, is_qualification)
values ('7e570000-0000-0000-0000-00000000000d', '7e570000-0000-0000-0000-00000000000c', null, null, 'Finale', true, false, 'CUSTOM', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name,
                                      required, use_default_seeding, places_option, is_qualification)
values ('7e570000-0000-0000-0000-00000000000e', '7e570000-0000-0000-0000-00000000000c', null, '7e570000-0000-0000-0000-00000000000d', 'Vorlauf', true, true, 'ASCENDING', true);

-- Vorlauf: ein Massenfeld-Lauf für alle 4 Boote.
insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting,
                                      teams, name, execution_order, start_time_offset)
values ('7e570000-0000-0000-0000-00000000000f', '7e570000-0000-0000-0000-00000000000e', null, 1, null, 'Vorlauf', 1, null);

-- Finale: 2 Boote.
insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting,
                                      teams, name, execution_order, start_time_offset)
values ('7e570000-0000-0000-0000-000000000010', '7e570000-0000-0000-0000-00000000000d', null, 1, 2, 'Finale', 1, null);

-- Seeding Vorlauf -> Finale: Platz 1 & 2 rücken nach.
insert into competition_setup_participant (id, competition_setup_match, competition_setup_group, seed, ranking)
values ('7e570000-0000-0000-0000-000000000011', '7e570000-0000-0000-0000-000000000010', null, 1, 1),
       ('7e570000-0000-0000-0000-000000000012', '7e570000-0000-0000-0000-000000000010', null, 2, 2);

-- ============================================================================================
-- Boote (je 1 Person Crew - Einer)
-- ============================================================================================

insert into competition_registration (id, event_registration, competition, club, name, team_number,
                                       created_at, created_by, updated_at, updated_by)
values
    ('7e570000-0000-0000-0000-000000000013', '7e570000-0000-0000-0000-000000000007', '7e570000-0000-0000-0000-00000000000b', '7e570000-0000-0000-0000-000000000003', 'Nord 1', 1, now(), null, now(), null),
    ('7e570000-0000-0000-0000-000000000014', '7e570000-0000-0000-0000-000000000008', '7e570000-0000-0000-0000-00000000000b', '7e570000-0000-0000-0000-000000000004', 'Süd 1', 2, now(), null, now(), null),
    ('7e570000-0000-0000-0000-000000000015', '7e570000-0000-0000-0000-000000000009', '7e570000-0000-0000-0000-00000000000b', '7e570000-0000-0000-0000-000000000005', 'Ost 1', 3, now(), null, now(), null),
    ('7e570000-0000-0000-0000-000000000016', '7e570000-0000-0000-0000-00000000000a', '7e570000-0000-0000-0000-00000000000b', '7e570000-0000-0000-0000-000000000006', 'West 1', 4, now(), null, now(), null);

insert into named_participant (id, name, description, created_at, created_by, updated_at, updated_by)
values ('7e570000-0000-0000-0000-000000000017', 'Crew', 'Ruderin/Ruderer', now(), null, now(), null);

insert into participant (id, club, firstname, lastname, year, gender, external, created_at, created_by, updated_at, updated_by)
values
    ('7e570000-0000-0000-0000-000000000018', '7e570000-0000-0000-0000-000000000003', 'Nina', 'Nordmann', 1998, 'F', false, now(), null, now(), null),
    ('7e570000-0000-0000-0000-000000000019', '7e570000-0000-0000-0000-000000000004', 'Sven', 'Sundmann', 1995, 'M', false, now(), null, now(), null),
    ('7e570000-0000-0000-0000-00000000001a', '7e570000-0000-0000-0000-000000000005', 'Ove', 'Ostholm', 2000, 'M', false, now(), null, now(), null),
    ('7e570000-0000-0000-0000-00000000001b', '7e570000-0000-0000-0000-000000000006', 'Wenke', 'Westphal', 1997, 'F', false, now(), null, now(), null);

insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('7e570000-0000-0000-0000-000000000013', '7e570000-0000-0000-0000-000000000017', '7e570000-0000-0000-0000-000000000018'),
    ('7e570000-0000-0000-0000-000000000014', '7e570000-0000-0000-0000-000000000017', '7e570000-0000-0000-0000-000000000019'),
    ('7e570000-0000-0000-0000-000000000015', '7e570000-0000-0000-0000-000000000017', '7e570000-0000-0000-0000-00000000001a'),
    ('7e570000-0000-0000-0000-000000000016', '7e570000-0000-0000-0000-000000000017', '7e570000-0000-0000-0000-00000000001b');

-- ============================================================================================
-- Zeitstrahl (13.08.): Programmpunkt + zwei WAITING-Lauf-Slots - nichts materialisiert,
-- die Schiedsrichter setzen die Runden selbst.
-- ============================================================================================

insert into event_schedule_slot (id, event, start_time, competition_setup_match, name, duration_minutes,
                                  created_at, created_by, updated_at, updated_by)
values
    ('7e570000-0000-0000-0000-00000000001c', '7e570000-0000-0000-0000-000000000001', '2026-08-13 17:00:00', null, 'Einweisung Schiedsrichter', 30, now(), null, now(), null),
    ('7e570000-0000-0000-0000-00000000001d', '7e570000-0000-0000-0000-000000000001', '2026-08-13 17:30:00', '7e570000-0000-0000-0000-00000000000f', null, 20, now(), null, now(), null),
    ('7e570000-0000-0000-0000-00000000001e', '7e570000-0000-0000-0000-000000000001', '2026-08-13 18:00:00', '7e570000-0000-0000-0000-000000000010', null, 15, now(), null, now(), null);
