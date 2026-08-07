-- Auflagen-Testregatta 2026: Seed für die drei Schiedsrichter-Themen, für die es bisher keine
-- Testdaten gab -- Auflagen (Testkatalog D6), Ersatzleute (D7) und Renngemeinschaften (G6).
-- Folgt denselben Konventionen wie seed-zeitstrahl.sql (5eed) und seed-foerde.sql (f0de):
-- eigener UUID-Präfix für ALLE Zeilen (hier a4f1), Cleanup-Block ganz vorn (nur a4f1%, fremde
-- Daten bleiben unangetastet, FK-Reihenfolge Kinder vor Eltern), set time zone 'Europe/Berlin'.
--
-- Aufbau:
--   Event "Auflagen-Testregatta 2026" (Kappeln, published, chain_progression_mode='SCHIEDSRICHTER',
--   mixed_team_term='Renngemeinschaft'), EIN Renntag.
--   Ein Wettkampf "Coastal Mixed Doppelvierer mit Steuerperson" (CMix 4x+) mit zwei Rollen
--   ("Ruderer:in" 4x, "Steuerperson" 1x) und zwei Runden: "Vorlauf" (required, Qualifikation,
--   Massenfeld) -> "Finale". Beide Runden sind bereits materialisiert (competition_match +
--   competition_match_team), denn das Schiedsrichter-Dashboard liest ausschließlich echte Läufe;
--   ein bloßer Zeitstrahl-Slot (WAITING) würde die Auflagen nicht zeigen.
--   Der Vorlauf ist currently_running = true, das Finale steht an.
--   4 Boote aus 3 Vereinen, je 5 Personen (4 Ruderer:innen + 1 Steuerperson).
--
-- Was an welchem Boot hängt:
--   Boot 1 "Nordwind 1"            -> alle vier Auflagen-Zustände nebeneinander (siehe unten)
--   Boot 2 "RG Nordwind/Schleiwind"-> Renngemeinschaft (Crew aus zwei Vereinen), Auflagen sauber
--   Boot 3 "Schleiwind 1"          -> Auswechslung mit Grund, in beide Runden vererbt
--   Boot 4 "Damp 1"                -> Vergleichsboot, Steuerpersonen-Wiegen zu spät (LATE)
--
-- Die vier Auflagen-Zustände (Boot 1, gerechnet gegen die Startzeit des Vorlaufs):
--   - erfüllte Pflicht          : "Startberechtigung Coastal 2026" bei allen fünf Personen
--   - fehlende Pflicht          : "Rettungswesten-Kontrolle" fehlt bei Marit Clausen
--   - fehlende optionale        : "Bootsausrüstung geprüft" fehlt bei Tobias Reimers
--   - verletztes Zeitfenster    : "Steuerpersonen-Wiegen" (nur Rolle Steuerperson, Fenster
--                                 120..15 Minuten vor dem Start) bei Silke Andresen um 06:30
--                                 abgehakt -> 210 Minuten vor 10:00 -> TOO_EARLY
--
-- Zeitfenster-Rechnung (LiveDashboardLogic.computeTimeCheck): delta = Minuten zwischen
-- created_at der Erfüllung und der Startzeit des Laufs. TOO_EARLY, wenn delta größer als
-- check_earliest_minutes_before ist; LATE, wenn delta kleiner als check_latest_minutes_before
-- ist; sonst OK. Mit dem Fenster 120/15 und Vorlaufstart 10:00 ergibt sich:
--   06:30 -> delta 210 -> TOO_EARLY (Boot 1)
--   09:30 -> delta  30 -> OK        (Boote 2 und 3)
--   09:52 -> delta   8 -> LATE      (Boot 4)
-- Das Finale startet 75 Minuten später; dort verschieben sich dieselben Erfüllungen um +75
-- Minuten (30 -> 105 bleibt OK, 8 -> 83 wird OK, 210 -> 285 bleibt TOO_EARLY). Deshalb sind die
-- OK-Fälle bewusst auf delta 30 gelegt: dieser Wert liegt in BEIDEN Runden im Fenster.
--
-- Zeitstempel: alle Zeiten stehen als feste Werte auf dem 06.08.2026 im Skript -- so ist
-- nachlesbar, was gemeint ist, und ein zweiter Lauf erzeugt exakt dieselben Abstände. Ganz unten
-- schiebt ein update-Block den kompletten Renntag am Stück auf den Testtag: der Renntag geht auf
-- current_date, und alle Zeitstempel (Lauf-Startzeiten, started_at, Zeitstrahl-Slots und die
-- created_at der Auflagen-Erfüllungen) wandern um denselben Versatz, sodass der Vorlauf vor
-- 12 Minuten gestartet ist. Der gemeinsame Versatz ist der Grund für die feste Schreibweise:
-- würde jede Zeile einzeln aus now() gerechnet, wären die Fenster-Abstände oben nicht mehr
-- nachvollziehbar. now() ist innerhalb der Transaktion konstant, der Versatz also für alle
-- Tabellen derselbe.
-- Hinweis: Am besten tagsüber einspielen. Wird der Seed mitten in der Nacht eingespielt, rutschen
-- die frühen Programmpunkte (Obleute-Besprechung 08:00) rechnerisch auf den Vortag, während der
-- Renntag auf current_date steht.

set search_path to ready2race, pg_catalog, public;
set time zone 'Europe/Berlin';

-- ============================================================================================
-- Cleanup: vorherige a4f1-Zeilen entfernen, FK-Reihenfolge beachten (Kinder vor Eltern).
-- Nur a4f1% -- 5eed%, f0de% und fee1% bleiben unangetastet.
-- competition_match.competition_setup_match ist zugleich der PK, deshalb tragen auch die Läufe
-- den a4f1-Präfix ihrer Setup-Matches.
-- ============================================================================================

delete from timecode
where id in (
    select timecode from competition_match_team
    where competition_match in (select id from competition_setup_match where id::text like 'a4f1%')
       or competition_registration in (select id from competition_registration where id::text like 'a4f1%')
);

delete from competition_match_team
where competition_match in (select id from competition_setup_match where id::text like 'a4f1%')
   or competition_registration in (select id from competition_registration where id::text like 'a4f1%');

-- inherited_from ist eine Selbstreferenz (Vorlauf -> Finale) ohne ON DELETE CASCADE.
update substitution set inherited_from = null where id::text like 'a4f1%';

delete from substitution
where id::text like 'a4f1%';

delete from competition_deregistration
where competition_registration::text like 'a4f1%';

delete from competition_match
where competition_setup_match::text like 'a4f1%';

delete from event_schedule_slot
where id::text like 'a4f1%' or event::text like 'a4f1%';

-- Was die Oberfläche nach dem Einspielen erzeugt haben kann (Check-in per QR, Anwesenheits-Scans,
-- Urkunden-Versand): ohne diese Zeilen scheitert der zweite Lauf am Personen-Delete unten.
delete from participant_tracking
where event::text like 'a4f1%' or participant::text like 'a4f1%';

delete from qr_codes
where event::text like 'a4f1%' or participant::text like 'a4f1%';

delete from certificate_of_event_participation_sending_job
where event::text like 'a4f1%' or participant::text like 'a4f1%';

delete from participant_has_requirement_for_event
where event::text like 'a4f1%'
   or participant::text like 'a4f1%'
   or participant_requirement::text like 'a4f1%';

delete from event_has_participant_requirement
where event::text like 'a4f1%' or participant_requirement::text like 'a4f1%';

delete from competition_registration_named_participant
where competition_registration::text like 'a4f1%' or participant::text like 'a4f1%';

delete from competition_registration
where id::text like 'a4f1%';

delete from event_registration
where id::text like 'a4f1%';

delete from event_participant
where event::text like 'a4f1%' or participant::text like 'a4f1%';

delete from participant
where id::text like 'a4f1%';

delete from participant_requirement
where id::text like 'a4f1%';

-- next_round ist eine Selbstreferenz (Vorlauf -> Finale) ohne ON DELETE CASCADE - erst
-- entkoppeln, sonst hängt der kaskadierte Mehrzeilen-Delete unten an der internen
-- Lösch-Reihenfolge.
update competition_setup_round set next_round = null where id::text like 'a4f1%';

-- Cascades to competition_setup / competition_setup_round / competition_setup_match /
-- competition_setup_participant / competition_properties_has_named_participant.
delete from competition_properties
where id::text like 'a4f1%';

-- Cascades to event_day_has_competition.
delete from competition
where id::text like 'a4f1%';

-- Erst hier: named_participant hängt an den Meldungen, Auflagen und Ummeldungen oben.
delete from named_participant
where id::text like 'a4f1%';

delete from club
where id::text like 'a4f1%';

-- Cascades to event_day / event_day_has_competition / event_schedule_slot (already gone).
delete from event
where id::text like 'a4f1%';

-- ============================================================================================
-- Event + Renntag
-- mixed_team_term = 'Renngemeinschaft' ist die Grundlage für G6: sobald die Personen eines Boots
-- zu verschiedenen Vereinen gehören, tritt dieser Begriff an die Stelle des Vereinsnamens.
-- ============================================================================================

insert into event (id, name, description, location, published, chain_progression_mode,
                    mixed_team_term, created_at, created_by, updated_at, updated_by)
values ('a4f10001-0000-0000-0000-000000000001', 'Auflagen-Testregatta 2026',
        'Seed für Auflagen (D6), Ersatzleute (D7) und Renngemeinschaften (G6)', 'Kappeln',
        true, 'SCHIEDSRICHTER', 'Renngemeinschaft', now(), null, now(), null);

insert into event_day (id, event, date, name, description, created_at, created_by, updated_at, updated_by)
values ('a4f10002-0000-0000-0000-000000000001', 'a4f10001-0000-0000-0000-000000000001', '2026-08-06',
        'Renntag', 'Wird vom update-Block unten auf current_date gezogen', now(), null, now(), null);

-- ============================================================================================
-- Rollen im Boot
-- ============================================================================================

insert into named_participant (id, name, description, created_at, created_by, updated_at, updated_by)
values
    ('a4f10003-0000-0000-0000-000000000001', 'Ruderer:in', null, now(), null, now(), null),
    ('a4f10003-0000-0000-0000-000000000002', 'Steuerperson', null, now(), null, now(), null);

-- ============================================================================================
-- Auflagen (participant_requirement ist global, nicht je Veranstaltung)
--
-- Nur "Steuerpersonen-Wiegen" hat ein Zeitfenster; die anderen drei sind bewusst ohne, damit im
-- Dashboard erfüllt/fehlend und Zeitfenster-Verstoß getrennt sichtbar werden.
-- ============================================================================================

insert into participant_requirement (id, name, description, optional, check_in_app,
                                      check_earliest_minutes_before, check_latest_minutes_before,
                                      created_at, created_by, updated_at, updated_by)
values
    ('a4f10004-0000-0000-0000-000000000001', 'Startberechtigung Coastal 2026',
     'Gültige Startberechtigung des Landesruderverbands', false, true, null, null,
     now(), null, now(), null),
    ('a4f10004-0000-0000-0000-000000000002', 'Rettungswesten-Kontrolle',
     'Sichtprüfung der Rettungsweste am Steg', false, true, null, null,
     now(), null, now(), null),
    ('a4f10004-0000-0000-0000-000000000003', 'Bootsausrüstung geprüft',
     'Freiwillige Zusatzkontrolle von Bugball, Leine und Schuhwerk', true, true, null, null,
     now(), null, now(), null),
    ('a4f10004-0000-0000-0000-000000000004', 'Steuerpersonen-Wiegen',
     'Verwiegung der Steuerperson, gültig 120 bis 15 Minuten vor dem Start', false, true, 120, 15,
     now(), null, now(), null);

-- Zuordnung an die Veranstaltung. named_participant = null heißt "gilt für alle";
-- "Steuerpersonen-Wiegen" hängt an der Rolle Steuerperson und deckt damit den rollenbezogenen
-- Zweig in LiveDashboardLogic.requirementApplies ab. Die Rettungswesten-Kontrolle verlangt
-- zusätzlich einen QR-Code, damit auch dieses Flag im Seed vorkommt.
insert into event_has_participant_requirement (event, participant_requirement, named_participant,
                                                qr_code_required, created_at, created_by)
values
    ('a4f10001-0000-0000-0000-000000000001', 'a4f10004-0000-0000-0000-000000000001', null, false, now(), null),
    ('a4f10001-0000-0000-0000-000000000001', 'a4f10004-0000-0000-0000-000000000002', null, true, now(), null),
    ('a4f10001-0000-0000-0000-000000000001', 'a4f10004-0000-0000-0000-000000000003', null, false, now(), null),
    ('a4f10001-0000-0000-0000-000000000001', 'a4f10004-0000-0000-0000-000000000004',
     'a4f10003-0000-0000-0000-000000000002', false, now(), null);

-- ============================================================================================
-- Vereine + Meldungen
-- ============================================================================================

insert into club (id, name, created_at, created_by, updated_at, updated_by)
values
    ('a4f10005-0000-0000-0000-000000000001', 'Ruderverein Nordwind Kappeln e.V.', now(), null, now(), null),
    ('a4f10005-0000-0000-0000-000000000002', 'Ruderclub Schleiwind Arnis e.V.', now(), null, now(), null),
    ('a4f10005-0000-0000-0000-000000000003', 'Wassersportverein Ostseebad Damp e.V.', now(), null, now(), null);

insert into event_registration (id, event, club, message, created_at, created_by, updated_at, updated_by)
values
    ('a4f10006-0000-0000-0000-000000000001', 'a4f10001-0000-0000-0000-000000000001',
     'a4f10005-0000-0000-0000-000000000001', null, now(), null, now(), null),
    ('a4f10006-0000-0000-0000-000000000002', 'a4f10001-0000-0000-0000-000000000001',
     'a4f10005-0000-0000-0000-000000000002', null, now(), null, now(), null),
    ('a4f10006-0000-0000-0000-000000000003', 'a4f10001-0000-0000-0000-000000000001',
     'a4f10005-0000-0000-0000-000000000003', null, now(), null, now(), null);

-- ============================================================================================
-- Wettkampf + Setup (Vorlauf -> Finale)
-- ============================================================================================

insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('a4f10007-0000-0000-0000-000000000001', 'a4f10001-0000-0000-0000-000000000001',
        now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('a4f10002-0000-0000-0000-000000000001', 'a4f10007-0000-0000-0000-000000000001', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name,
                                     description, competition_category)
values ('a4f10008-0000-0000-0000-000000000001', 'a4f10007-0000-0000-0000-000000000001', null,
        '1', 'Coastal Mixed Doppelvierer mit Steuerperson', 'CMix 4x+',
        'Seed für Auflagen, Ersatzleute und Renngemeinschaften', null);

insert into competition_properties_has_named_participant (competition_properties, named_participant,
                                                           count_males, count_females,
                                                           count_non_binary, count_mixed)
values ('a4f10008-0000-0000-0000-000000000001', 'a4f10003-0000-0000-0000-000000000001', 0, 0, 0, 4),
       ('a4f10008-0000-0000-0000-000000000001', 'a4f10003-0000-0000-0000-000000000002', 0, 0, 0, 1);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('a4f10008-0000-0000-0000-000000000001', now(), null, now(), null);

-- Runde 2 zuerst (next_round = null, keine Vorwärtsreferenz), dann Runde 1, die auf sie zeigt.
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name,
                                      required, use_default_seeding, places_option, is_qualification)
values ('a4f10009-0000-0000-0000-000000000002', 'a4f10008-0000-0000-0000-000000000001', null,
        null, 'Finale', true, true, 'EQUAL', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name,
                                      required, use_default_seeding, places_option, is_qualification)
values ('a4f10009-0000-0000-0000-000000000001', 'a4f10008-0000-0000-0000-000000000001', null,
        'a4f10009-0000-0000-0000-000000000002', 'Vorlauf', true, true, 'ASCENDING', true);

-- Je ein Massenfeld-Lauf pro Runde (teams = null): alle vier Boote starten gemeinsam, damit die
-- vier Auflagen-Zustände, die Renngemeinschaft und die Auswechslung in EINEM Lauf nebeneinander
-- stehen.
insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting,
                                      teams, name, execution_order, start_time_offset)
values ('a4f1000a-0000-0000-0000-000000000001', 'a4f10009-0000-0000-0000-000000000001', null, 1, null,
        'Vorlauf', 1, null),
       ('a4f1000a-0000-0000-0000-000000000002', 'a4f10009-0000-0000-0000-000000000002', null, 1, null,
        'Finale', 1, null);

-- ============================================================================================
-- Boote (competition_registration)
--
-- Boot 2 ist über den Ruderverein Nordwind gemeldet, fährt aber mit drei Gästen aus Arnis --
-- daraus entsteht die Renngemeinschaft (siehe Personen weiter unten).
-- ============================================================================================

insert into competition_registration (id, event_registration, competition, club, name, team_number,
                                       created_at, created_by, updated_at, updated_by)
values
    ('a4f1000b-0000-0000-0000-000000000001', 'a4f10006-0000-0000-0000-000000000001',
     'a4f10007-0000-0000-0000-000000000001', 'a4f10005-0000-0000-0000-000000000001',
     'Nordwind 1', 1, now(), null, now(), null),
    ('a4f1000b-0000-0000-0000-000000000002', 'a4f10006-0000-0000-0000-000000000001',
     'a4f10007-0000-0000-0000-000000000001', 'a4f10005-0000-0000-0000-000000000001',
     'RG Nordwind/Schleiwind', 2, now(), null, now(), null),
    ('a4f1000b-0000-0000-0000-000000000003', 'a4f10006-0000-0000-0000-000000000002',
     'a4f10007-0000-0000-0000-000000000001', 'a4f10005-0000-0000-0000-000000000002',
     'Schleiwind 1', 1, now(), null, now(), null),
    ('a4f1000b-0000-0000-0000-000000000004', 'a4f10006-0000-0000-0000-000000000003',
     'a4f10007-0000-0000-0000-000000000001', 'a4f10005-0000-0000-0000-000000000003',
     'Damp 1', 1, now(), null, now(), null);

-- ============================================================================================
-- Personen
--
-- external = true + external_club_name ist der Weg, auf dem ein meldender Verein Gäste anderer
-- Vereine ins Boot setzt. Genau daraus liest AwardCertificateService die Renngemeinschaft:
-- die Menge der external_club_name einer Mannschaft hat dann mehr als einen Wert, und statt
-- eines Vereinsnamens erscheint der mixed_team_term.
-- ============================================================================================

insert into participant (id, club, firstname, lastname, year, gender, external, external_club_name,
                          created_at, created_by, updated_at, updated_by)
values
    -- Boot 1 "Nordwind 1" -- alle aus dem meldenden Verein (kein RG-Fall)
    ('a4f1000c-0000-0000-0000-000000000001', 'a4f10005-0000-0000-0000-000000000001', 'Lena', 'Petersen', 1998, 'F', false, null, now(), null, now(), null),
    ('a4f1000c-0000-0000-0000-000000000002', 'a4f10005-0000-0000-0000-000000000001', 'Jonas', 'Hinrichs', 1995, 'M', false, null, now(), null, now(), null),
    ('a4f1000c-0000-0000-0000-000000000003', 'a4f10005-0000-0000-0000-000000000001', 'Marit', 'Clausen', 2000, 'F', false, null, now(), null, now(), null),
    ('a4f1000c-0000-0000-0000-000000000004', 'a4f10005-0000-0000-0000-000000000001', 'Tobias', 'Reimers', 1992, 'M', false, null, now(), null, now(), null),
    ('a4f1000c-0000-0000-0000-000000000005', 'a4f10005-0000-0000-0000-000000000001', 'Silke', 'Andresen', 1988, 'F', false, null, now(), null, now(), null),
    -- Boot 2 "RG Nordwind/Schleiwind" -- zwei eigene Leute, drei Gäste aus Arnis
    ('a4f1000c-0000-0000-0000-000000000006', 'a4f10005-0000-0000-0000-000000000001', 'Frieda', 'Boysen', 1997, 'F', false, null, now(), null, now(), null),
    ('a4f1000c-0000-0000-0000-000000000007', 'a4f10005-0000-0000-0000-000000000001', 'Ole', 'Thomsen', 1994, 'M', false, null, now(), null, now(), null),
    ('a4f1000c-0000-0000-0000-000000000008', 'a4f10005-0000-0000-0000-000000000001', 'Nele', 'Carstensen', 1999, 'F', true, 'Ruderclub Schleiwind Arnis e.V.', now(), null, now(), null),
    ('a4f1000c-0000-0000-0000-000000000009', 'a4f10005-0000-0000-0000-000000000001', 'Hauke', 'Jessen', 1996, 'M', true, 'Ruderclub Schleiwind Arnis e.V.', now(), null, now(), null),
    ('a4f1000c-0000-0000-0000-00000000000a', 'a4f10005-0000-0000-0000-000000000001', 'Anke', 'Nissen', 1991, 'F', true, 'Ruderclub Schleiwind Arnis e.V.', now(), null, now(), null),
    -- Boot 3 "Schleiwind 1" -- Malte Sörensen wird ersetzt, Timm Christiansen rückt nach
    ('a4f1000c-0000-0000-0000-00000000000b', 'a4f10005-0000-0000-0000-000000000002', 'Sönke', 'Lorenzen', 1993, 'M', false, null, now(), null, now(), null),
    ('a4f1000c-0000-0000-0000-00000000000c', 'a4f10005-0000-0000-0000-000000000002', 'Imke', 'Paulsen', 1996, 'F', false, null, now(), null, now(), null),
    ('a4f1000c-0000-0000-0000-00000000000d', 'a4f10005-0000-0000-0000-000000000002', 'Malte', 'Sörensen', 1990, 'M', false, null, now(), null, now(), null),
    ('a4f1000c-0000-0000-0000-00000000000e', 'a4f10005-0000-0000-0000-000000000002', 'Rieke', 'Ketelsen', 2001, 'F', false, null, now(), null, now(), null),
    ('a4f1000c-0000-0000-0000-00000000000f', 'a4f10005-0000-0000-0000-000000000002', 'Bernd', 'Matthiesen', 1985, 'M', false, null, now(), null, now(), null),
    ('a4f1000c-0000-0000-0000-000000000010', 'a4f10005-0000-0000-0000-000000000002', 'Timm', 'Christiansen', 1997, 'M', false, null, now(), null, now(), null),
    -- Boot 4 "Damp 1" -- Vergleichsboot mit verspätetem Steuerpersonen-Wiegen
    ('a4f1000c-0000-0000-0000-000000000011', 'a4f10005-0000-0000-0000-000000000003', 'Greta', 'Wollesen', 1998, 'F', false, null, now(), null, now(), null),
    ('a4f1000c-0000-0000-0000-000000000012', 'a4f10005-0000-0000-0000-000000000003', 'Lasse', 'Bruhn', 1995, 'M', false, null, now(), null, now(), null),
    ('a4f1000c-0000-0000-0000-000000000013', 'a4f10005-0000-0000-0000-000000000003', 'Mareike', 'Feddersen', 1997, 'F', false, null, now(), null, now(), null),
    ('a4f1000c-0000-0000-0000-000000000014', 'a4f10005-0000-0000-0000-000000000003', 'Nils', 'Hansen', 1992, 'M', false, null, now(), null, now(), null),
    ('a4f1000c-0000-0000-0000-000000000015', 'a4f10005-0000-0000-0000-000000000003', 'Karin', 'Jürgensen', 1986, 'F', false, null, now(), null, now(), null);

-- Aufstellung: je vier Ruderer:innen und eine Steuerperson. Timm Christiansen
-- (…000010) steht bewusst NICHT in der gemeldeten Aufstellung -- er kommt erst über die
-- Auswechslung ins Boot.
insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('a4f1000b-0000-0000-0000-000000000001', 'a4f10003-0000-0000-0000-000000000001', 'a4f1000c-0000-0000-0000-000000000001'),
    ('a4f1000b-0000-0000-0000-000000000001', 'a4f10003-0000-0000-0000-000000000001', 'a4f1000c-0000-0000-0000-000000000002'),
    ('a4f1000b-0000-0000-0000-000000000001', 'a4f10003-0000-0000-0000-000000000001', 'a4f1000c-0000-0000-0000-000000000003'),
    ('a4f1000b-0000-0000-0000-000000000001', 'a4f10003-0000-0000-0000-000000000001', 'a4f1000c-0000-0000-0000-000000000004'),
    ('a4f1000b-0000-0000-0000-000000000001', 'a4f10003-0000-0000-0000-000000000002', 'a4f1000c-0000-0000-0000-000000000005'),

    ('a4f1000b-0000-0000-0000-000000000002', 'a4f10003-0000-0000-0000-000000000001', 'a4f1000c-0000-0000-0000-000000000006'),
    ('a4f1000b-0000-0000-0000-000000000002', 'a4f10003-0000-0000-0000-000000000001', 'a4f1000c-0000-0000-0000-000000000007'),
    ('a4f1000b-0000-0000-0000-000000000002', 'a4f10003-0000-0000-0000-000000000001', 'a4f1000c-0000-0000-0000-000000000008'),
    ('a4f1000b-0000-0000-0000-000000000002', 'a4f10003-0000-0000-0000-000000000001', 'a4f1000c-0000-0000-0000-000000000009'),
    ('a4f1000b-0000-0000-0000-000000000002', 'a4f10003-0000-0000-0000-000000000002', 'a4f1000c-0000-0000-0000-00000000000a'),

    ('a4f1000b-0000-0000-0000-000000000003', 'a4f10003-0000-0000-0000-000000000001', 'a4f1000c-0000-0000-0000-00000000000b'),
    ('a4f1000b-0000-0000-0000-000000000003', 'a4f10003-0000-0000-0000-000000000001', 'a4f1000c-0000-0000-0000-00000000000c'),
    ('a4f1000b-0000-0000-0000-000000000003', 'a4f10003-0000-0000-0000-000000000001', 'a4f1000c-0000-0000-0000-00000000000d'),
    ('a4f1000b-0000-0000-0000-000000000003', 'a4f10003-0000-0000-0000-000000000001', 'a4f1000c-0000-0000-0000-00000000000e'),
    ('a4f1000b-0000-0000-0000-000000000003', 'a4f10003-0000-0000-0000-000000000002', 'a4f1000c-0000-0000-0000-00000000000f'),

    ('a4f1000b-0000-0000-0000-000000000004', 'a4f10003-0000-0000-0000-000000000001', 'a4f1000c-0000-0000-0000-000000000011'),
    ('a4f1000b-0000-0000-0000-000000000004', 'a4f10003-0000-0000-0000-000000000001', 'a4f1000c-0000-0000-0000-000000000012'),
    ('a4f1000b-0000-0000-0000-000000000004', 'a4f10003-0000-0000-0000-000000000001', 'a4f1000c-0000-0000-0000-000000000013'),
    ('a4f1000b-0000-0000-0000-000000000004', 'a4f10003-0000-0000-0000-000000000001', 'a4f1000c-0000-0000-0000-000000000014'),
    ('a4f1000b-0000-0000-0000-000000000004', 'a4f10003-0000-0000-0000-000000000002', 'a4f1000c-0000-0000-0000-000000000015');

-- ============================================================================================
-- Läufe: beide Runden materialisiert
--
-- Der Vorlauf läuft (currently_running = true) und ist vor gut zehn Minuten gestartet -- nur so
-- taucht er im Live-Tab des Schiedsrichter-Dashboards auf und zeigt die Auflagen. Das Finale
-- steht an (UPCOMING) und trägt die vererbte Auswechslung.
-- ============================================================================================

insert into competition_match (competition_setup_match, start_time, currently_running, started_at,
                                finished_at, created_at, created_by, updated_at, updated_by)
values ('a4f1000a-0000-0000-0000-000000000001', '2026-08-06 10:00:00', true, '2026-08-06 10:01:00',
        null, now(), null, now(), null),
       ('a4f1000a-0000-0000-0000-000000000002', '2026-08-06 11:15:00', false, null,
        null, now(), null, now(), null);

insert into competition_match_team (id, competition_match, competition_registration, start_number,
                                     place, out, failed, created_at, created_by, updated_at, updated_by)
values
    -- Vorlauf
    ('a4f1000d-0000-0000-0000-000000000001', 'a4f1000a-0000-0000-0000-000000000001', 'a4f1000b-0000-0000-0000-000000000001', 1, null, false, false, now(), null, now(), null),
    ('a4f1000d-0000-0000-0000-000000000002', 'a4f1000a-0000-0000-0000-000000000001', 'a4f1000b-0000-0000-0000-000000000002', 2, null, false, false, now(), null, now(), null),
    ('a4f1000d-0000-0000-0000-000000000003', 'a4f1000a-0000-0000-0000-000000000001', 'a4f1000b-0000-0000-0000-000000000003', 3, null, false, false, now(), null, now(), null),
    ('a4f1000d-0000-0000-0000-000000000004', 'a4f1000a-0000-0000-0000-000000000001', 'a4f1000b-0000-0000-0000-000000000004', 4, null, false, false, now(), null, now(), null),
    -- Finale
    ('a4f1000d-0000-0000-0000-000000000005', 'a4f1000a-0000-0000-0000-000000000002', 'a4f1000b-0000-0000-0000-000000000001', 1, null, false, false, now(), null, now(), null),
    ('a4f1000d-0000-0000-0000-000000000006', 'a4f1000a-0000-0000-0000-000000000002', 'a4f1000b-0000-0000-0000-000000000002', 2, null, false, false, now(), null, now(), null),
    ('a4f1000d-0000-0000-0000-000000000007', 'a4f1000a-0000-0000-0000-000000000002', 'a4f1000b-0000-0000-0000-000000000003', 3, null, false, false, now(), null, now(), null),
    ('a4f1000d-0000-0000-0000-000000000008', 'a4f1000a-0000-0000-0000-000000000002', 'a4f1000b-0000-0000-0000-000000000004', 4, null, false, false, now(), null, now(), null);

-- ============================================================================================
-- Zeitstrahl-Slots
-- ============================================================================================

insert into event_schedule_slot (id, event, start_time, competition_setup_match, name, duration_minutes,
                                  created_at, created_by, updated_at, updated_by)
values
    ('a4f1000e-0000-0000-0000-000000000001', 'a4f10001-0000-0000-0000-000000000001',
     '2026-08-06 08:00:00', null, 'Obleute-Besprechung', 30, now(), null, now(), null),
    ('a4f1000e-0000-0000-0000-000000000002', 'a4f10001-0000-0000-0000-000000000001',
     '2026-08-06 10:00:00', 'a4f1000a-0000-0000-0000-000000000001', null, 30, now(), null, now(), null),
    ('a4f1000e-0000-0000-0000-000000000003', 'a4f10001-0000-0000-0000-000000000001',
     '2026-08-06 11:15:00', 'a4f1000a-0000-0000-0000-000000000002', null, 30, now(), null, now(), null),
    ('a4f1000e-0000-0000-0000-000000000004', 'a4f10001-0000-0000-0000-000000000001',
     '2026-08-06 12:30:00', null, 'Siegerehrung', 30, now(), null, now(), null);

-- ============================================================================================
-- Auswechslung (D7): Malte Sörensen raus, Timm Christiansen rein -- mit Grund, und in die
-- Folgerunde vererbt.
--
-- CompetitionExecutionService.getActuallyParticipatingParticipants wertet die Ummeldungen je
-- (Runde, Meldung) aus: die ausgewechselte Person fällt aus der Aufstellung, die eingewechselte
-- übernimmt deren Rolle. Damit fährt Boot 3 im Vorlauf mit Sönke Lorenzen, Imke Paulsen, Rieke
-- Ketelsen, Timm Christiansen und Bernd Matthiesen.
--
-- Die Vererbung entspricht SubstitutionRecord.applyNewRound: neue id, neue Runde, alles andere
-- unverändert, inherited_from zeigt auf die ursprüngliche Ummeldung. order_for_round ist je
-- Runde eindeutig, deshalb steht in beiden Runden die 1.
-- ============================================================================================

insert into substitution (id, competition_registration, competition_setup_round, participant_out,
                           participant_in, reason, order_for_round, named_participant, inherited_from,
                           created_at, created_by, updated_at, updated_by)
values ('a4f1000f-0000-0000-0000-000000000001', 'a4f1000b-0000-0000-0000-000000000003',
        'a4f10009-0000-0000-0000-000000000001', 'a4f1000c-0000-0000-0000-00000000000d',
        'a4f1000c-0000-0000-0000-000000000010',
        'Rückenverletzung beim Einrudern - Ersatz aus dem Vereinskader', 1,
        'a4f10003-0000-0000-0000-000000000001', null, now(), null, now(), null);

insert into substitution (id, competition_registration, competition_setup_round, participant_out,
                           participant_in, reason, order_for_round, named_participant, inherited_from,
                           created_at, created_by, updated_at, updated_by)
values ('a4f1000f-0000-0000-0000-000000000002', 'a4f1000b-0000-0000-0000-000000000003',
        'a4f10009-0000-0000-0000-000000000002', 'a4f1000c-0000-0000-0000-00000000000d',
        'a4f1000c-0000-0000-0000-000000000010',
        'Rückenverletzung beim Einrudern - Ersatz aus dem Vereinskader', 1,
        'a4f10003-0000-0000-0000-000000000001', 'a4f1000f-0000-0000-0000-000000000001',
        now(), null, now(), null);

-- ============================================================================================
-- Erfüllte Auflagen (D6)
--
-- created_at ist hier kein Buchhaltungsfeld, sondern der fachliche Zeitpunkt der Abnahme --
-- daran rechnet computeTimeCheck das Zeitfenster. Deshalb stehen hier feste Uhrzeiten, die der
-- update-Block unten gemeinsam mit den Startzeiten verschiebt.
-- ============================================================================================

-- Boot 1 "Nordwind 1": Startberechtigung bei allen fünf -> erfüllte Pflicht-Auflage.
insert into participant_has_requirement_for_event (participant, event, participant_requirement,
                                                    created_at, created_by, note)
values
    ('a4f1000c-0000-0000-0000-000000000001', 'a4f10001-0000-0000-0000-000000000001', 'a4f10004-0000-0000-0000-000000000001', '2026-08-06 08:10:00', null, null),
    ('a4f1000c-0000-0000-0000-000000000002', 'a4f10001-0000-0000-0000-000000000001', 'a4f10004-0000-0000-0000-000000000001', '2026-08-06 08:10:00', null, null),
    ('a4f1000c-0000-0000-0000-000000000003', 'a4f10001-0000-0000-0000-000000000001', 'a4f10004-0000-0000-0000-000000000001', '2026-08-06 08:12:00', null, 'Pass nachgereicht, Vereinsliste geprüft'),
    ('a4f1000c-0000-0000-0000-000000000004', 'a4f10001-0000-0000-0000-000000000001', 'a4f10004-0000-0000-0000-000000000001', '2026-08-06 08:10:00', null, null),
    ('a4f1000c-0000-0000-0000-000000000005', 'a4f10001-0000-0000-0000-000000000001', 'a4f10004-0000-0000-0000-000000000001', '2026-08-06 08:10:00', null, null);

-- Boot 1: Rettungswesten-Kontrolle fehlt bei Marit Clausen (…000003) -> fehlende Pflicht-Auflage.
insert into participant_has_requirement_for_event (participant, event, participant_requirement,
                                                    created_at, created_by, note)
values
    ('a4f1000c-0000-0000-0000-000000000001', 'a4f10001-0000-0000-0000-000000000001', 'a4f10004-0000-0000-0000-000000000002', '2026-08-06 09:15:00', null, null),
    ('a4f1000c-0000-0000-0000-000000000002', 'a4f10001-0000-0000-0000-000000000001', 'a4f10004-0000-0000-0000-000000000002', '2026-08-06 09:15:00', null, null),
    ('a4f1000c-0000-0000-0000-000000000004', 'a4f10001-0000-0000-0000-000000000001', 'a4f10004-0000-0000-0000-000000000002', '2026-08-06 09:16:00', null, null),
    ('a4f1000c-0000-0000-0000-000000000005', 'a4f10001-0000-0000-0000-000000000001', 'a4f10004-0000-0000-0000-000000000002', '2026-08-06 09:16:00', null, null);

-- Boot 1: Bootsausrüstung (optional) fehlt bei Tobias Reimers (…000004) -> fehlende optionale Auflage.
insert into participant_has_requirement_for_event (participant, event, participant_requirement,
                                                    created_at, created_by, note)
values
    ('a4f1000c-0000-0000-0000-000000000001', 'a4f10001-0000-0000-0000-000000000001', 'a4f10004-0000-0000-0000-000000000003', '2026-08-06 09:20:00', null, null),
    ('a4f1000c-0000-0000-0000-000000000002', 'a4f10001-0000-0000-0000-000000000001', 'a4f10004-0000-0000-0000-000000000003', '2026-08-06 09:20:00', null, null),
    ('a4f1000c-0000-0000-0000-000000000003', 'a4f10001-0000-0000-0000-000000000001', 'a4f10004-0000-0000-0000-000000000003', '2026-08-06 09:20:00', null, null),
    ('a4f1000c-0000-0000-0000-000000000005', 'a4f10001-0000-0000-0000-000000000001', 'a4f10004-0000-0000-0000-000000000003', '2026-08-06 09:20:00', null, null);

-- Boot 1: Steuerpersonen-Wiegen um 06:30 -> 210 Minuten vor dem Vorlauf (10:00), Fenster ist
-- 120..15 -> TOO_EARLY. Abgehakt ist die Auflage trotzdem; das Dashboard zeigt sie als erfüllt
-- MIT Zeitfenster-Warnung.
insert into participant_has_requirement_for_event (participant, event, participant_requirement,
                                                    created_at, created_by, note)
values ('a4f1000c-0000-0000-0000-000000000005', 'a4f10001-0000-0000-0000-000000000001',
        'a4f10004-0000-0000-0000-000000000004', '2026-08-06 06:30:00', null,
        'Vor der Anreise der Konkurrenz gewogen - deutlich zu früh');

-- Boot 2 "RG Nordwind/Schleiwind": alle drei allgemeinen Auflagen erfüllt.
insert into participant_has_requirement_for_event (participant, event, participant_requirement,
                                                    created_at, created_by, note)
select p.id, 'a4f10001-0000-0000-0000-000000000001', r.id, '2026-08-06 08:30:00', null, null
from (values
    ('a4f1000c-0000-0000-0000-000000000006'::uuid),
    ('a4f1000c-0000-0000-0000-000000000007'::uuid),
    ('a4f1000c-0000-0000-0000-000000000008'::uuid),
    ('a4f1000c-0000-0000-0000-000000000009'::uuid),
    ('a4f1000c-0000-0000-0000-00000000000a'::uuid)
) as p(id)
cross join (values
    ('a4f10004-0000-0000-0000-000000000001'::uuid),
    ('a4f10004-0000-0000-0000-000000000002'::uuid),
    ('a4f10004-0000-0000-0000-000000000003'::uuid)
) as r(id);

-- Boot 3 "Schleiwind 1": alle drei allgemeinen Auflagen erfüllt -- auch beim Ersatzmann Timm
-- Christiansen (…000010) und beim ausgewechselten Malte Sörensen (…00000d), der vor der
-- Verletzung bereits abgenommen war.
insert into participant_has_requirement_for_event (participant, event, participant_requirement,
                                                    created_at, created_by, note)
select p.id, 'a4f10001-0000-0000-0000-000000000001', r.id, '2026-08-06 08:40:00', null, null
from (values
    ('a4f1000c-0000-0000-0000-00000000000b'::uuid),
    ('a4f1000c-0000-0000-0000-00000000000c'::uuid),
    ('a4f1000c-0000-0000-0000-00000000000d'::uuid),
    ('a4f1000c-0000-0000-0000-00000000000e'::uuid),
    ('a4f1000c-0000-0000-0000-00000000000f'::uuid),
    ('a4f1000c-0000-0000-0000-000000000010'::uuid)
) as p(id)
cross join (values
    ('a4f10004-0000-0000-0000-000000000001'::uuid),
    ('a4f10004-0000-0000-0000-000000000002'::uuid),
    ('a4f10004-0000-0000-0000-000000000003'::uuid)
) as r(id);

-- Boot 4 "Damp 1": alle drei allgemeinen Auflagen erfüllt.
insert into participant_has_requirement_for_event (participant, event, participant_requirement,
                                                    created_at, created_by, note)
select p.id, 'a4f10001-0000-0000-0000-000000000001', r.id, '2026-08-06 08:50:00', null, null
from (values
    ('a4f1000c-0000-0000-0000-000000000011'::uuid),
    ('a4f1000c-0000-0000-0000-000000000012'::uuid),
    ('a4f1000c-0000-0000-0000-000000000013'::uuid),
    ('a4f1000c-0000-0000-0000-000000000014'::uuid),
    ('a4f1000c-0000-0000-0000-000000000015'::uuid)
) as p(id)
cross join (values
    ('a4f10004-0000-0000-0000-000000000001'::uuid),
    ('a4f10004-0000-0000-0000-000000000002'::uuid),
    ('a4f10004-0000-0000-0000-000000000003'::uuid)
) as r(id);

-- Steuerpersonen-Wiegen der übrigen Boote:
--   Anke Nissen (Boot 2) und Bernd Matthiesen (Boot 3) um 09:30 -> delta 30 -> OK. Der Wert liegt
--   auch im Finale (Start 11:15, delta 105) noch im Fenster.
--   Karin Jürgensen (Boot 4) um 09:52 -> delta 8 -> LATE, der zweite Zeitfenster-Fall. Im Finale
--   wäre dieselbe Abnahme mit delta 83 wieder OK -- das Fenster hängt am Lauf, nicht am Tag.
insert into participant_has_requirement_for_event (participant, event, participant_requirement,
                                                    created_at, created_by, note)
values
    ('a4f1000c-0000-0000-0000-00000000000a', 'a4f10001-0000-0000-0000-000000000001', 'a4f10004-0000-0000-0000-000000000004', '2026-08-06 09:30:00', null, null),
    ('a4f1000c-0000-0000-0000-00000000000f', 'a4f10001-0000-0000-0000-000000000001', 'a4f10004-0000-0000-0000-000000000004', '2026-08-06 09:30:00', null, null),
    ('a4f1000c-0000-0000-0000-000000000015', 'a4f10001-0000-0000-0000-000000000001', 'a4f10004-0000-0000-0000-000000000004', '2026-08-06 09:52:00', null, 'Waage war belegt, erst kurz vor dem Start frei');

-- ============================================================================================
-- Auf den Testtag ziehen
--
-- Ein gemeinsamer Versatz für alle Zeitstempel: der Vorlauf startet 12 Minuten vor dem
-- Einspielen, alles andere behält seinen Abstand dazu -- damit bleiben die oben gerechneten
-- Zeitfenster exakt erhalten. now() ist innerhalb der Transaktion konstant, deshalb liefert der
-- Ausdruck in jedem der vier Statements denselben Wert. localtimestamp statt now(), weil die
-- Spalten timestamp ohne Zeitzone sind und die Anwendung in Europe/Berlin rechnet.
--
-- Wer lieber am festen Datum 06.08.2026 testet, lässt diesen Block einfach weg.
-- ============================================================================================

update event_day
set date = current_date, updated_at = now()
where id = 'a4f10002-0000-0000-0000-000000000001';

with shift as (
    select date_trunc('minute', localtimestamp) - interval '12 minutes'
         - timestamp '2026-08-06 10:00:00' as delta
)
update competition_match
set start_time = start_time + (select delta from shift),
    started_at = started_at + (select delta from shift),
    updated_at = now()
where competition_setup_match::text like 'a4f1%';

with shift as (
    select date_trunc('minute', localtimestamp) - interval '12 minutes'
         - timestamp '2026-08-06 10:00:00' as delta
)
update event_schedule_slot
set start_time = start_time + (select delta from shift),
    updated_at = now()
where id::text like 'a4f1%';

with shift as (
    select date_trunc('minute', localtimestamp) - interval '12 minutes'
         - timestamp '2026-08-06 10:00:00' as delta
)
update participant_has_requirement_for_event
set created_at = created_at + (select delta from shift)
where event = 'a4f10001-0000-0000-0000-000000000001';
