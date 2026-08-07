-- Deutsche Meisterschaft Coastal Rowing 2026 -- vollstaendiges Programm nach den drei
-- offiziellen Zeitplaenen der Coastal Regatta Flensburg (Stand 05.08.2026):
--   Fr 14.08. FoerdeSPRINT Time Trial, Sa 15.08. FoerdeSPRINT 500 m, So 16.08. FoerdeRACE.
--
-- Zweck: Gegenstueck zur Zeitstrahl-Import-Datei. Die Spalte "Wettkampf" der Importdatei
-- traegt die Rennnummer aus dem Zeitplan; die liegt hier als competition_properties.identifier.
-- Die Lauf-Namen ("Zeitfahren", "VF1".."VF4", "HF1"/"HF2", "Finale A"/"Finale B",
-- "Vorlauf 1 DM"/"Vorlauf 2 DM", "Finale DM") entsprechen den Setup-Zeilen 1:1.
--
-- Die Kurzbezeichnungen sind bewusst NICHT eindeutig -- "CF 1x" gibt es als FoerdeRACE Nr. 3
-- und als FoerdeSPRINT Nr. 11, genau wie im echten Zeitplan. Eindeutig ist nur die Rennnummer.
--
-- Aufgeraeumt wird ueber die Zugehoerigkeit zur Veranstaltung, nicht ueber ein UUID-Praefix:
-- die Veranstaltung enthaelt Zeilen aus mehreren 5eed000x-Familien, und '5eed%' wuerde die
-- "Zeitstrahl Testregatta" (5eed0000-...) mit loeschen. Die acht Vereine bleiben erhalten und
-- werden weiterverwendet.
--
-- Freilose: Bei den Wettkaempfen mit drei Booten faehrt das schnellste Zeitfahr-Boot direkt
-- ins Finale A. Eine Setzung bezieht sich immer auf die unmittelbar vorige Runde, deshalb
-- bleiben genau diese Finallaeufe ungesetzt und werden in der UI besetzt.

set search_path to ready2race, pg_catalog, public;
set time zone 'Europe/Berlin';

-- =========================================================================================
-- Cleanup: alles, was an der Veranstaltung haengt (Kinder vor Eltern).
-- =========================================================================================

delete from participant_tracking where event = '5eed0001-0000-0000-0000-000000000001';

delete from competition_registration_named_participant
where competition_registration in (
    select cr.id from competition_registration cr
    join competition c on c.id = cr.competition where c.event = '5eed0001-0000-0000-0000-000000000001');

delete from timecode where id in (
    select t.timecode from competition_match_team t
    join competition_registration cr on cr.id = t.competition_registration
    join competition c on c.id = cr.competition where c.event = '5eed0001-0000-0000-0000-000000000001');

delete from competition_match_team where competition_registration in (
    select cr.id from competition_registration cr
    join competition c on c.id = cr.competition where c.event = '5eed0001-0000-0000-0000-000000000001');

delete from competition_deregistration where competition_registration in (
    select cr.id from competition_registration cr
    join competition c on c.id = cr.competition where c.event = '5eed0001-0000-0000-0000-000000000001');

-- substitution haengt an competition_registration UND competition_setup_round, beides ohne
-- Cascade - muss vor beiden weg.
delete from substitution where competition_registration in (
    select cr.id from competition_registration cr
    join competition c on c.id = cr.competition where c.event = '5eed0001-0000-0000-0000-000000000001');

delete from app_user_registration_competition_registration where competition_id in (
    select id from competition where event = '5eed0001-0000-0000-0000-000000000001');

delete from competition_match where competition_setup_match in (
    select m.id from competition_setup_match m
    join competition_setup_round r on r.id = m.competition_setup_round
    join competition_setup cs on cs.competition_properties = r.competition_setup
    join competition_properties cp on cp.id = cs.competition_properties
    join competition c on c.id = cp.competition where c.event = '5eed0001-0000-0000-0000-000000000001');

delete from event_schedule_slot where event = '5eed0001-0000-0000-0000-000000000001';

delete from competition_registration where competition in (
    select id from competition where event = '5eed0001-0000-0000-0000-000000000001');

-- event_registration bleibt stehen: an den Meldungen haengen Rechnungen, und je Verein gibt
-- es ohnehin genau eine, die alle Wettkaempfe teilen.

-- next_round ist eine Selbstreferenz ohne ON DELETE CASCADE - erst entkoppeln.
update competition_setup_round set next_round = null where id in (
    select r.id from competition_setup_round r
    join competition_setup cs on cs.competition_properties = r.competition_setup
    join competition_properties cp on cp.id = cs.competition_properties
    join competition c on c.id = cp.competition where c.event = '5eed0001-0000-0000-0000-000000000001');

-- Cascades to competition_setup / _round / _match / _participant.
delete from competition_properties where competition in (
    select id from competition where event = '5eed0001-0000-0000-0000-000000000001');

-- Cascades to event_day_has_competition.
delete from competition where event = '5eed0001-0000-0000-0000-000000000001';

delete from event_day where event = '5eed0001-0000-0000-0000-000000000001';

delete from participant where id::text like '5eed0001-0030%';

delete from named_participant where id = '5eed0001-00c0-0000-0000-000000000001';

-- =========================================================================================
-- Veranstaltung + Renntage
-- =========================================================================================

update event
set description = 'Coastal Regatta Flensburg 2026 - FördeSPRINT (Fr/Sa) und FördeRACE mit den Deutschen Meisterschaften (So)',
    location = 'Flensburg',
    published = true,
    updated_at = now()
where id = '5eed0001-0000-0000-0000-000000000001';

insert into event_day (id, event, date, name, description, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0001-0000-0000-000000000001', '5eed0001-0000-0000-0000-000000000001', '2026-08-14', 'FördeSPRINT Time Trial', 'Zeitfahren zur Setzung der Sprint-Läufe', now(), null, now(), null),
       ('5eed0001-0001-0000-0000-000000000002', '5eed0001-0000-0000-0000-000000000001', '2026-08-15', 'FördeSPRINT', '500-m-Sprint im K.-o.-Modus', now(), null, now(), null),
       ('5eed0001-0001-0000-0000-000000000003', '5eed0001-0000-0000-0000-000000000001', '2026-08-16', 'FördeRACE', 'Finals 6 km Rundkurs, Vorläufe 4 km Rundkurs', now(), null, now(), null);

insert into named_participant (id, name, description, created_at, created_by, updated_at, updated_by)
values ('5eed0001-00c0-0000-0000-000000000001', 'Crew', 'Ruderin/Ruderer oder Steuerperson', now(), null, now(), null);

-- ----------------------------------------------------------------------------------------
-- Rennen 1 - CF 2x (Frauen-Doppelzweier), 6 Boote
-- ----------------------------------------------------------------------------------------
insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0010-0000-0000-000000000001', '5eed0001-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('5eed0001-0001-0000-0000-000000000003', '5eed0001-0010-0000-0000-000000000001', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name, description, competition_category)
values ('5eed0001-0011-0000-0000-000000000001', '5eed0001-0010-0000-0000-000000000001', null, '1', 'Frauen-Doppelzweier', 'CF 2x', null, null);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0011-0000-0000-000000000001', now(), null, now(), null);

-- Runden rückwärts angelegt (next_round zeigt vorwärts).
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000001', '5eed0001-0011-0000-0000-000000000001', null, null, 'Finale', true, true, 'EQUAL', false);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000001', '5eed0001-0012-0000-0000-000000000001', null, 1, null, 'Finale DM', 1, null);

insert into competition_registration (id, event_registration, competition, club, name, team_number, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0020-0000-0000-000000000001', '5eed000a-0000-0000-0000-000000000001', '5eed0001-0010-0000-0000-000000000001', '5eed0002-0000-0000-0000-000000000001', 'Bremerhaven CF 2x', 1, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000002', '5eed000a-0000-0000-0000-000000000002', '5eed0001-0010-0000-0000-000000000001', '5eed0002-0000-0000-0000-000000000002', 'ARV Kiel CF 2x', 2, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000003', '5eed000a-0000-0000-0000-000000000003', '5eed0001-0010-0000-0000-000000000001', '5eed0002-0000-0000-0000-000000000003', 'Bergedorf CF 2x', 3, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000004', '5eed000a-0000-0000-0000-000000000004', '5eed0001-0010-0000-0000-000000000001', '5eed0002-0000-0000-0000-000000000004', 'Bremen HANSA CF 2x', 4, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000005', '5eed000a-0000-0000-0000-000000000005', '5eed0001-0010-0000-0000-000000000001', '5eed0002-0000-0000-0000-000000000005', 'München CF 2x', 5, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000006', '5eed000a-0000-0000-0000-000000000006', '5eed0001-0010-0000-0000-000000000001', '5eed0002-0000-0000-0000-000000000006', 'Schleswig CF 2x', 6, now(), null, now(), null);

insert into participant (id, club, firstname, lastname, year, gender, external, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0030-0000-0000-000000000001', '5eed0002-0000-0000-0000-000000000001', 'Merle', 'Petersen', 1990, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000002', '5eed0002-0000-0000-0000-000000000001', 'Lena', 'Matthiesen', 1991, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000003', '5eed0002-0000-0000-0000-000000000002', 'Svea', 'Ingwersen', 1992, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000004', '5eed0002-0000-0000-0000-000000000002', 'Frieda', 'Rathjen', 1993, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000005', '5eed0002-0000-0000-0000-000000000003', 'Annika', 'Petersen', 1994, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000006', '5eed0002-0000-0000-0000-000000000003', 'Maren', 'Matthiesen', 1995, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000007', '5eed0002-0000-0000-0000-000000000004', 'Gesa', 'Ingwersen', 1996, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000008', '5eed0002-0000-0000-0000-000000000004', 'Nele', 'Rathjen', 1997, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000009', '5eed0002-0000-0000-0000-000000000005', 'Wiebke', 'Petersen', 1998, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000000a', '5eed0002-0000-0000-0000-000000000005', 'Inken', 'Matthiesen', 1999, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000000b', '5eed0002-0000-0000-0000-000000000006', 'Femke', 'Ingwersen', 2000, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000000c', '5eed0002-0000-0000-0000-000000000006', 'Astrid', 'Rathjen', 2001, 'F', false, now(), null, now(), null);

insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('5eed0001-0020-0000-0000-000000000001', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000001'),
    ('5eed0001-0020-0000-0000-000000000001', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000002'),
    ('5eed0001-0020-0000-0000-000000000002', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000003'),
    ('5eed0001-0020-0000-0000-000000000002', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000004'),
    ('5eed0001-0020-0000-0000-000000000003', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000005'),
    ('5eed0001-0020-0000-0000-000000000003', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000006'),
    ('5eed0001-0020-0000-0000-000000000004', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000007'),
    ('5eed0001-0020-0000-0000-000000000004', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000008'),
    ('5eed0001-0020-0000-0000-000000000005', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000009'),
    ('5eed0001-0020-0000-0000-000000000005', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000000a'),
    ('5eed0001-0020-0000-0000-000000000006', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000000b'),
    ('5eed0001-0020-0000-0000-000000000006', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000000c');

-- ----------------------------------------------------------------------------------------
-- Rennen 2 - CM 4x+ (Männer-Doppelvierer mit Steuerfrau/-mann), 6 Boote
-- ----------------------------------------------------------------------------------------
insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0010-0000-0000-000000000002', '5eed0001-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('5eed0001-0001-0000-0000-000000000003', '5eed0001-0010-0000-0000-000000000002', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name, description, competition_category)
values ('5eed0001-0011-0000-0000-000000000002', '5eed0001-0010-0000-0000-000000000002', null, '2', 'Männer-Doppelvierer mit Steuerfrau/-mann', 'CM 4x+', null, null);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0011-0000-0000-000000000002', now(), null, now(), null);

-- Runden rückwärts angelegt (next_round zeigt vorwärts).
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000002', '5eed0001-0011-0000-0000-000000000002', null, null, 'Finale', true, true, 'EQUAL', false);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000002', '5eed0001-0012-0000-0000-000000000002', null, 1, null, 'Finale DM', 1, null);

insert into competition_registration (id, event_registration, competition, club, name, team_number, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0020-0000-0000-000000000007', '5eed000a-0000-0000-0000-000000000001', '5eed0001-0010-0000-0000-000000000002', '5eed0002-0000-0000-0000-000000000001', 'Bremerhaven CM 4x+', 1, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000008', '5eed000a-0000-0000-0000-000000000002', '5eed0001-0010-0000-0000-000000000002', '5eed0002-0000-0000-0000-000000000002', 'ARV Kiel CM 4x+', 2, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000009', '5eed000a-0000-0000-0000-000000000003', '5eed0001-0010-0000-0000-000000000002', '5eed0002-0000-0000-0000-000000000003', 'Bergedorf CM 4x+', 3, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000000a', '5eed000a-0000-0000-0000-000000000004', '5eed0001-0010-0000-0000-000000000002', '5eed0002-0000-0000-0000-000000000004', 'Bremen HANSA CM 4x+', 4, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000000b', '5eed000a-0000-0000-0000-000000000005', '5eed0001-0010-0000-0000-000000000002', '5eed0002-0000-0000-0000-000000000005', 'München CM 4x+', 5, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000000c', '5eed000a-0000-0000-0000-000000000006', '5eed0001-0010-0000-0000-000000000002', '5eed0002-0000-0000-0000-000000000006', 'Schleswig CM 4x+', 6, now(), null, now(), null);

insert into participant (id, club, firstname, lastname, year, gender, external, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0030-0000-0000-00000000000d', '5eed0002-0000-0000-0000-000000000001', 'Lasse', 'Andresen', 2002, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000000e', '5eed0002-0000-0000-0000-000000000001', 'Nils', 'Hansen', 2003, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000000f', '5eed0002-0000-0000-0000-000000000001', 'Boye', 'Brodersen', 2004, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000010', '5eed0002-0000-0000-0000-000000000001', 'Jann', 'Ingwersen', 2005, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000011', '5eed0002-0000-0000-0000-000000000001', 'Rune', 'Rathjen', 1990, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000012', '5eed0002-0000-0000-0000-000000000002', 'Sven', 'Petersen', 1991, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000013', '5eed0002-0000-0000-0000-000000000002', 'Timo', 'Matthiesen', 1992, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000014', '5eed0002-0000-0000-0000-000000000002', 'Ove', 'Nissen', 1993, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000015', '5eed0002-0000-0000-0000-000000000002', 'Jonas', 'Paulsen', 1994, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000016', '5eed0002-0000-0000-0000-000000000002', 'Finn', 'Østergaard', 1995, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000017', '5eed0002-0000-0000-0000-000000000003', 'Malte', 'Carstensen', 1996, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000018', '5eed0002-0000-0000-0000-000000000003', 'Ole', 'Callsen', 1997, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000019', '5eed0002-0000-0000-0000-000000000003', 'Hauke', 'Sievers', 1998, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000001a', '5eed0002-0000-0000-0000-000000000003', 'Thies', 'Lauritzen', 1999, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000001b', '5eed0002-0000-0000-0000-000000000003', 'Arne', 'Clausen', 2000, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000001c', '5eed0002-0000-0000-0000-000000000004', 'Sönke', 'Godbersen', 2001, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000001d', '5eed0002-0000-0000-0000-000000000004', 'Tjark', 'Hinrichsen', 2002, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000001e', '5eed0002-0000-0000-0000-000000000004', 'Bjarne', 'Kjeldsen', 2003, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000001f', '5eed0002-0000-0000-0000-000000000004', 'Mads', 'Lorenzen', 2004, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000020', '5eed0002-0000-0000-0000-000000000004', 'Emil', 'Struve', 2005, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000021', '5eed0002-0000-0000-0000-000000000005', 'Lasse', 'Boysen', 1990, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000022', '5eed0002-0000-0000-0000-000000000005', 'Nils', 'Skovgaard', 1991, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000023', '5eed0002-0000-0000-0000-000000000005', 'Boye', 'Asmussen', 1992, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000024', '5eed0002-0000-0000-0000-000000000005', 'Jann', 'Iversen', 1993, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000025', '5eed0002-0000-0000-0000-000000000005', 'Rune', 'Volquardsen', 1994, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000026', '5eed0002-0000-0000-0000-000000000006', 'Sven', 'Andresen', 1995, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000027', '5eed0002-0000-0000-0000-000000000006', 'Timo', 'Hansen', 1996, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000028', '5eed0002-0000-0000-0000-000000000006', 'Ove', 'Brodersen', 1997, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000029', '5eed0002-0000-0000-0000-000000000006', 'Jonas', 'Ingwersen', 1998, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000002a', '5eed0002-0000-0000-0000-000000000006', 'Finn', 'Rathjen', 1999, 'M', false, now(), null, now(), null);

insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('5eed0001-0020-0000-0000-000000000007', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000000d'),
    ('5eed0001-0020-0000-0000-000000000007', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000000e'),
    ('5eed0001-0020-0000-0000-000000000007', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000000f'),
    ('5eed0001-0020-0000-0000-000000000007', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000010'),
    ('5eed0001-0020-0000-0000-000000000007', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000011'),
    ('5eed0001-0020-0000-0000-000000000008', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000012'),
    ('5eed0001-0020-0000-0000-000000000008', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000013'),
    ('5eed0001-0020-0000-0000-000000000008', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000014'),
    ('5eed0001-0020-0000-0000-000000000008', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000015'),
    ('5eed0001-0020-0000-0000-000000000008', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000016'),
    ('5eed0001-0020-0000-0000-000000000009', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000017'),
    ('5eed0001-0020-0000-0000-000000000009', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000018'),
    ('5eed0001-0020-0000-0000-000000000009', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000019'),
    ('5eed0001-0020-0000-0000-000000000009', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000001a'),
    ('5eed0001-0020-0000-0000-000000000009', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000001b'),
    ('5eed0001-0020-0000-0000-00000000000a', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000001c'),
    ('5eed0001-0020-0000-0000-00000000000a', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000001d'),
    ('5eed0001-0020-0000-0000-00000000000a', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000001e'),
    ('5eed0001-0020-0000-0000-00000000000a', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000001f'),
    ('5eed0001-0020-0000-0000-00000000000a', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000020'),
    ('5eed0001-0020-0000-0000-00000000000b', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000021'),
    ('5eed0001-0020-0000-0000-00000000000b', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000022'),
    ('5eed0001-0020-0000-0000-00000000000b', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000023'),
    ('5eed0001-0020-0000-0000-00000000000b', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000024'),
    ('5eed0001-0020-0000-0000-00000000000b', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000025'),
    ('5eed0001-0020-0000-0000-00000000000c', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000026'),
    ('5eed0001-0020-0000-0000-00000000000c', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000027'),
    ('5eed0001-0020-0000-0000-00000000000c', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000028'),
    ('5eed0001-0020-0000-0000-00000000000c', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000029'),
    ('5eed0001-0020-0000-0000-00000000000c', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000002a');

-- ----------------------------------------------------------------------------------------
-- Rennen 3 - CF 1x (Frauen-Einer), 6 Boote
-- ----------------------------------------------------------------------------------------
insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0010-0000-0000-000000000003', '5eed0001-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('5eed0001-0001-0000-0000-000000000003', '5eed0001-0010-0000-0000-000000000003', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name, description, competition_category)
values ('5eed0001-0011-0000-0000-000000000003', '5eed0001-0010-0000-0000-000000000003', null, '3', 'Frauen-Einer', 'CF 1x', null, null);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0011-0000-0000-000000000003', now(), null, now(), null);

-- Runden rückwärts angelegt (next_round zeigt vorwärts).
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000003', '5eed0001-0011-0000-0000-000000000003', null, null, 'Finale', true, true, 'EQUAL', false);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000003', '5eed0001-0012-0000-0000-000000000003', null, 1, null, 'Finale DM', 1, null);

insert into competition_registration (id, event_registration, competition, club, name, team_number, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0020-0000-0000-00000000000d', '5eed000a-0000-0000-0000-000000000001', '5eed0001-0010-0000-0000-000000000003', '5eed0002-0000-0000-0000-000000000001', 'Bremerhaven CF 1x', 1, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000000e', '5eed000a-0000-0000-0000-000000000002', '5eed0001-0010-0000-0000-000000000003', '5eed0002-0000-0000-0000-000000000002', 'ARV Kiel CF 1x', 2, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000000f', '5eed000a-0000-0000-0000-000000000003', '5eed0001-0010-0000-0000-000000000003', '5eed0002-0000-0000-0000-000000000003', 'Bergedorf CF 1x', 3, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000010', '5eed000a-0000-0000-0000-000000000004', '5eed0001-0010-0000-0000-000000000003', '5eed0002-0000-0000-0000-000000000004', 'Bremen HANSA CF 1x', 4, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000011', '5eed000a-0000-0000-0000-000000000005', '5eed0001-0010-0000-0000-000000000003', '5eed0002-0000-0000-0000-000000000005', 'München CF 1x', 5, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000012', '5eed000a-0000-0000-0000-000000000006', '5eed0001-0010-0000-0000-000000000003', '5eed0002-0000-0000-0000-000000000006', 'Schleswig CF 1x', 6, now(), null, now(), null);

insert into participant (id, club, firstname, lastname, year, gender, external, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0030-0000-0000-00000000002b', '5eed0002-0000-0000-0000-000000000001', 'Svea', 'Andresen', 2000, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000002c', '5eed0002-0000-0000-0000-000000000002', 'Frieda', 'Asmussen', 2001, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000002d', '5eed0002-0000-0000-0000-000000000003', 'Annika', 'Struve', 2002, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000002e', '5eed0002-0000-0000-0000-000000000004', 'Maren', 'Boysen', 2003, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000002f', '5eed0002-0000-0000-0000-000000000005', 'Gesa', 'Kjeldsen', 2004, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000030', '5eed0002-0000-0000-0000-000000000006', 'Nele', 'Clausen', 2005, 'F', false, now(), null, now(), null);

insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('5eed0001-0020-0000-0000-00000000000d', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000002b'),
    ('5eed0001-0020-0000-0000-00000000000e', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000002c'),
    ('5eed0001-0020-0000-0000-00000000000f', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000002d'),
    ('5eed0001-0020-0000-0000-000000000010', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000002e'),
    ('5eed0001-0020-0000-0000-000000000011', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000002f'),
    ('5eed0001-0020-0000-0000-000000000012', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000030');

-- ----------------------------------------------------------------------------------------
-- Rennen 4 - CM 2x (Männer-Doppelzweier), 6 Boote
-- ----------------------------------------------------------------------------------------
insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0010-0000-0000-000000000004', '5eed0001-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('5eed0001-0001-0000-0000-000000000003', '5eed0001-0010-0000-0000-000000000004', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name, description, competition_category)
values ('5eed0001-0011-0000-0000-000000000004', '5eed0001-0010-0000-0000-000000000004', null, '4', 'Männer-Doppelzweier', 'CM 2x', null, null);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0011-0000-0000-000000000004', now(), null, now(), null);

-- Runden rückwärts angelegt (next_round zeigt vorwärts).
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000004', '5eed0001-0011-0000-0000-000000000004', null, null, 'Finale', true, true, 'EQUAL', false);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000004', '5eed0001-0012-0000-0000-000000000004', null, 1, null, 'Finale DM', 1, null);

insert into competition_registration (id, event_registration, competition, club, name, team_number, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0020-0000-0000-000000000013', '5eed000a-0000-0000-0000-000000000001', '5eed0001-0010-0000-0000-000000000004', '5eed0002-0000-0000-0000-000000000001', 'Bremerhaven CM 2x', 1, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000014', '5eed000a-0000-0000-0000-000000000002', '5eed0001-0010-0000-0000-000000000004', '5eed0002-0000-0000-0000-000000000002', 'ARV Kiel CM 2x', 2, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000015', '5eed000a-0000-0000-0000-000000000003', '5eed0001-0010-0000-0000-000000000004', '5eed0002-0000-0000-0000-000000000003', 'Bergedorf CM 2x', 3, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000016', '5eed000a-0000-0000-0000-000000000004', '5eed0001-0010-0000-0000-000000000004', '5eed0002-0000-0000-0000-000000000004', 'Bremen HANSA CM 2x', 4, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000017', '5eed000a-0000-0000-0000-000000000005', '5eed0001-0010-0000-0000-000000000004', '5eed0002-0000-0000-0000-000000000005', 'München CM 2x', 5, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000018', '5eed000a-0000-0000-0000-000000000006', '5eed0001-0010-0000-0000-000000000004', '5eed0002-0000-0000-0000-000000000006', 'Schleswig CM 2x', 6, now(), null, now(), null);

insert into participant (id, club, firstname, lastname, year, gender, external, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0030-0000-0000-000000000031', '5eed0002-0000-0000-0000-000000000001', 'Tjark', 'Carstensen', 1990, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000032', '5eed0002-0000-0000-0000-000000000001', 'Bjarne', 'Callsen', 1991, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000033', '5eed0002-0000-0000-0000-000000000002', 'Mads', 'Paulsen', 1992, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000034', '5eed0002-0000-0000-0000-000000000002', 'Emil', 'Østergaard', 1993, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000035', '5eed0002-0000-0000-0000-000000000003', 'Lasse', 'Carstensen', 1994, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000036', '5eed0002-0000-0000-0000-000000000003', 'Nils', 'Callsen', 1995, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000037', '5eed0002-0000-0000-0000-000000000004', 'Boye', 'Paulsen', 1996, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000038', '5eed0002-0000-0000-0000-000000000004', 'Jann', 'Østergaard', 1997, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000039', '5eed0002-0000-0000-0000-000000000005', 'Rune', 'Carstensen', 1998, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000003a', '5eed0002-0000-0000-0000-000000000005', 'Sven', 'Callsen', 1999, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000003b', '5eed0002-0000-0000-0000-000000000006', 'Timo', 'Paulsen', 2000, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000003c', '5eed0002-0000-0000-0000-000000000006', 'Ove', 'Østergaard', 2001, 'M', false, now(), null, now(), null);

insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('5eed0001-0020-0000-0000-000000000013', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000031'),
    ('5eed0001-0020-0000-0000-000000000013', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000032'),
    ('5eed0001-0020-0000-0000-000000000014', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000033'),
    ('5eed0001-0020-0000-0000-000000000014', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000034'),
    ('5eed0001-0020-0000-0000-000000000015', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000035'),
    ('5eed0001-0020-0000-0000-000000000015', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000036'),
    ('5eed0001-0020-0000-0000-000000000016', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000037'),
    ('5eed0001-0020-0000-0000-000000000016', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000038'),
    ('5eed0001-0020-0000-0000-000000000017', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000039'),
    ('5eed0001-0020-0000-0000-000000000017', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000003a'),
    ('5eed0001-0020-0000-0000-000000000018', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000003b'),
    ('5eed0001-0020-0000-0000-000000000018', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000003c');

-- ----------------------------------------------------------------------------------------
-- Rennen 5 - CMix 2x (Mixed-Doppelzweier), 6 Boote
-- ----------------------------------------------------------------------------------------
insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0010-0000-0000-000000000005', '5eed0001-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('5eed0001-0001-0000-0000-000000000003', '5eed0001-0010-0000-0000-000000000005', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name, description, competition_category)
values ('5eed0001-0011-0000-0000-000000000005', '5eed0001-0010-0000-0000-000000000005', null, '5', 'Mixed-Doppelzweier', 'CMix 2x', null, null);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0011-0000-0000-000000000005', now(), null, now(), null);

-- Runden rückwärts angelegt (next_round zeigt vorwärts).
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000005', '5eed0001-0011-0000-0000-000000000005', null, null, 'Finale', true, true, 'EQUAL', false);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000005', '5eed0001-0012-0000-0000-000000000005', null, 1, null, 'Finale DM', 1, null);

insert into competition_registration (id, event_registration, competition, club, name, team_number, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0020-0000-0000-000000000019', '5eed000a-0000-0000-0000-000000000001', '5eed0001-0010-0000-0000-000000000005', '5eed0002-0000-0000-0000-000000000001', 'Bremerhaven CMix 2x', 1, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000001a', '5eed000a-0000-0000-0000-000000000002', '5eed0001-0010-0000-0000-000000000005', '5eed0002-0000-0000-0000-000000000002', 'ARV Kiel CMix 2x', 2, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000001b', '5eed000a-0000-0000-0000-000000000003', '5eed0001-0010-0000-0000-000000000005', '5eed0002-0000-0000-0000-000000000003', 'Bergedorf CMix 2x', 3, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000001c', '5eed000a-0000-0000-0000-000000000004', '5eed0001-0010-0000-0000-000000000005', '5eed0002-0000-0000-0000-000000000004', 'Bremen HANSA CMix 2x', 4, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000001d', '5eed000a-0000-0000-0000-000000000005', '5eed0001-0010-0000-0000-000000000005', '5eed0002-0000-0000-0000-000000000005', 'München CMix 2x', 5, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000001e', '5eed000a-0000-0000-0000-000000000006', '5eed0001-0010-0000-0000-000000000005', '5eed0002-0000-0000-0000-000000000006', 'Schleswig CMix 2x', 6, now(), null, now(), null);

insert into participant (id, club, firstname, lastname, year, gender, external, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0030-0000-0000-00000000003d', '5eed0002-0000-0000-0000-000000000001', 'Jonas', 'Petersen', 2002, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000003e', '5eed0002-0000-0000-0000-000000000001', 'Lena', 'Matthiesen', 2003, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000003f', '5eed0002-0000-0000-0000-000000000002', 'Malte', 'Ingwersen', 2004, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000040', '5eed0002-0000-0000-0000-000000000002', 'Frieda', 'Rathjen', 2005, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000041', '5eed0002-0000-0000-0000-000000000003', 'Hauke', 'Petersen', 1990, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000042', '5eed0002-0000-0000-0000-000000000003', 'Maren', 'Matthiesen', 1991, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000043', '5eed0002-0000-0000-0000-000000000004', 'Arne', 'Ingwersen', 1992, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000044', '5eed0002-0000-0000-0000-000000000004', 'Nele', 'Rathjen', 1993, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000045', '5eed0002-0000-0000-0000-000000000005', 'Tjark', 'Petersen', 1994, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000046', '5eed0002-0000-0000-0000-000000000005', 'Inken', 'Matthiesen', 1995, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000047', '5eed0002-0000-0000-0000-000000000006', 'Mads', 'Ingwersen', 1996, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000048', '5eed0002-0000-0000-0000-000000000006', 'Astrid', 'Rathjen', 1997, 'F', false, now(), null, now(), null);

insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('5eed0001-0020-0000-0000-000000000019', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000003d'),
    ('5eed0001-0020-0000-0000-000000000019', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000003e'),
    ('5eed0001-0020-0000-0000-00000000001a', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000003f'),
    ('5eed0001-0020-0000-0000-00000000001a', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000040'),
    ('5eed0001-0020-0000-0000-00000000001b', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000041'),
    ('5eed0001-0020-0000-0000-00000000001b', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000042'),
    ('5eed0001-0020-0000-0000-00000000001c', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000043'),
    ('5eed0001-0020-0000-0000-00000000001c', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000044'),
    ('5eed0001-0020-0000-0000-00000000001d', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000045'),
    ('5eed0001-0020-0000-0000-00000000001d', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000046'),
    ('5eed0001-0020-0000-0000-00000000001e', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000047'),
    ('5eed0001-0020-0000-0000-00000000001e', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000048');

-- ----------------------------------------------------------------------------------------
-- Rennen 6 - CF 4x+ (Frauen-Doppelvierer mit Steuerfrau/-mann), 6 Boote
-- ----------------------------------------------------------------------------------------
insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0010-0000-0000-000000000006', '5eed0001-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('5eed0001-0001-0000-0000-000000000003', '5eed0001-0010-0000-0000-000000000006', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name, description, competition_category)
values ('5eed0001-0011-0000-0000-000000000006', '5eed0001-0010-0000-0000-000000000006', null, '6', 'Frauen-Doppelvierer mit Steuerfrau/-mann', 'CF 4x+', null, null);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0011-0000-0000-000000000006', now(), null, now(), null);

-- Runden rückwärts angelegt (next_round zeigt vorwärts).
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000006', '5eed0001-0011-0000-0000-000000000006', null, null, 'Finale', true, true, 'EQUAL', false);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000006', '5eed0001-0012-0000-0000-000000000006', null, 1, null, 'Finale DM', 1, null);

insert into competition_registration (id, event_registration, competition, club, name, team_number, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0020-0000-0000-00000000001f', '5eed000a-0000-0000-0000-000000000001', '5eed0001-0010-0000-0000-000000000006', '5eed0002-0000-0000-0000-000000000001', 'Bremerhaven CF 4x+', 1, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000020', '5eed000a-0000-0000-0000-000000000002', '5eed0001-0010-0000-0000-000000000006', '5eed0002-0000-0000-0000-000000000002', 'ARV Kiel CF 4x+', 2, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000021', '5eed000a-0000-0000-0000-000000000003', '5eed0001-0010-0000-0000-000000000006', '5eed0002-0000-0000-0000-000000000003', 'Bergedorf CF 4x+', 3, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000022', '5eed000a-0000-0000-0000-000000000004', '5eed0001-0010-0000-0000-000000000006', '5eed0002-0000-0000-0000-000000000004', 'Bremen HANSA CF 4x+', 4, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000023', '5eed000a-0000-0000-0000-000000000005', '5eed0001-0010-0000-0000-000000000006', '5eed0002-0000-0000-0000-000000000005', 'München CF 4x+', 5, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000024', '5eed000a-0000-0000-0000-000000000006', '5eed0001-0010-0000-0000-000000000006', '5eed0002-0000-0000-0000-000000000006', 'Schleswig CF 4x+', 6, now(), null, now(), null);

insert into participant (id, club, firstname, lastname, year, gender, external, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0030-0000-0000-000000000049', '5eed0002-0000-0000-0000-000000000001', 'Sofie', 'Andresen', 1998, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000004a', '5eed0002-0000-0000-0000-000000000001', 'Karen', 'Hansen', 1999, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000004b', '5eed0002-0000-0000-0000-000000000001', 'Bente', 'Brodersen', 2000, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000004c', '5eed0002-0000-0000-0000-000000000001', 'Keike', 'Ingwersen', 2001, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000004d', '5eed0002-0000-0000-0000-000000000001', 'Ida', 'Rathjen', 2002, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000004e', '5eed0002-0000-0000-0000-000000000002', 'Tomke', 'Petersen', 2003, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000004f', '5eed0002-0000-0000-0000-000000000002', 'Hanna', 'Matthiesen', 2004, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000050', '5eed0002-0000-0000-0000-000000000002', 'Mia', 'Nissen', 2005, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000051', '5eed0002-0000-0000-0000-000000000002', 'Merle', 'Paulsen', 1990, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000052', '5eed0002-0000-0000-0000-000000000002', 'Lena', 'Østergaard', 1991, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000053', '5eed0002-0000-0000-0000-000000000003', 'Svea', 'Carstensen', 1992, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000054', '5eed0002-0000-0000-0000-000000000003', 'Frieda', 'Callsen', 1993, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000055', '5eed0002-0000-0000-0000-000000000003', 'Annika', 'Sievers', 1994, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000056', '5eed0002-0000-0000-0000-000000000003', 'Maren', 'Lauritzen', 1995, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000057', '5eed0002-0000-0000-0000-000000000003', 'Gesa', 'Clausen', 1996, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000058', '5eed0002-0000-0000-0000-000000000004', 'Nele', 'Godbersen', 1997, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000059', '5eed0002-0000-0000-0000-000000000004', 'Wiebke', 'Hinrichsen', 1998, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000005a', '5eed0002-0000-0000-0000-000000000004', 'Inken', 'Kjeldsen', 1999, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000005b', '5eed0002-0000-0000-0000-000000000004', 'Femke', 'Lorenzen', 2000, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000005c', '5eed0002-0000-0000-0000-000000000004', 'Astrid', 'Struve', 2001, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000005d', '5eed0002-0000-0000-0000-000000000005', 'Sofie', 'Boysen', 2002, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000005e', '5eed0002-0000-0000-0000-000000000005', 'Karen', 'Skovgaard', 2003, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000005f', '5eed0002-0000-0000-0000-000000000005', 'Bente', 'Asmussen', 2004, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000060', '5eed0002-0000-0000-0000-000000000005', 'Keike', 'Iversen', 2005, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000061', '5eed0002-0000-0000-0000-000000000005', 'Ida', 'Volquardsen', 1990, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000062', '5eed0002-0000-0000-0000-000000000006', 'Tomke', 'Andresen', 1991, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000063', '5eed0002-0000-0000-0000-000000000006', 'Hanna', 'Hansen', 1992, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000064', '5eed0002-0000-0000-0000-000000000006', 'Mia', 'Brodersen', 1993, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000065', '5eed0002-0000-0000-0000-000000000006', 'Merle', 'Ingwersen', 1994, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000066', '5eed0002-0000-0000-0000-000000000006', 'Lena', 'Rathjen', 1995, 'F', false, now(), null, now(), null);

insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('5eed0001-0020-0000-0000-00000000001f', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000049'),
    ('5eed0001-0020-0000-0000-00000000001f', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000004a'),
    ('5eed0001-0020-0000-0000-00000000001f', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000004b'),
    ('5eed0001-0020-0000-0000-00000000001f', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000004c'),
    ('5eed0001-0020-0000-0000-00000000001f', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000004d'),
    ('5eed0001-0020-0000-0000-000000000020', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000004e'),
    ('5eed0001-0020-0000-0000-000000000020', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000004f'),
    ('5eed0001-0020-0000-0000-000000000020', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000050'),
    ('5eed0001-0020-0000-0000-000000000020', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000051'),
    ('5eed0001-0020-0000-0000-000000000020', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000052'),
    ('5eed0001-0020-0000-0000-000000000021', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000053'),
    ('5eed0001-0020-0000-0000-000000000021', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000054'),
    ('5eed0001-0020-0000-0000-000000000021', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000055'),
    ('5eed0001-0020-0000-0000-000000000021', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000056'),
    ('5eed0001-0020-0000-0000-000000000021', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000057'),
    ('5eed0001-0020-0000-0000-000000000022', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000058'),
    ('5eed0001-0020-0000-0000-000000000022', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000059'),
    ('5eed0001-0020-0000-0000-000000000022', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000005a'),
    ('5eed0001-0020-0000-0000-000000000022', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000005b'),
    ('5eed0001-0020-0000-0000-000000000022', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000005c'),
    ('5eed0001-0020-0000-0000-000000000023', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000005d'),
    ('5eed0001-0020-0000-0000-000000000023', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000005e'),
    ('5eed0001-0020-0000-0000-000000000023', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000005f'),
    ('5eed0001-0020-0000-0000-000000000023', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000060'),
    ('5eed0001-0020-0000-0000-000000000023', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000061'),
    ('5eed0001-0020-0000-0000-000000000024', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000062'),
    ('5eed0001-0020-0000-0000-000000000024', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000063'),
    ('5eed0001-0020-0000-0000-000000000024', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000064'),
    ('5eed0001-0020-0000-0000-000000000024', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000065'),
    ('5eed0001-0020-0000-0000-000000000024', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000066');

-- ----------------------------------------------------------------------------------------
-- Rennen 7 - CM 1x (Männer-Einer), 6 Boote
-- ----------------------------------------------------------------------------------------
insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0010-0000-0000-000000000007', '5eed0001-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('5eed0001-0001-0000-0000-000000000003', '5eed0001-0010-0000-0000-000000000007', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name, description, competition_category)
values ('5eed0001-0011-0000-0000-000000000007', '5eed0001-0010-0000-0000-000000000007', null, '7', 'Männer-Einer', 'CM 1x', null, null);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0011-0000-0000-000000000007', now(), null, now(), null);

-- Runden rückwärts angelegt (next_round zeigt vorwärts).
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000007', '5eed0001-0011-0000-0000-000000000007', null, null, 'Finale', true, true, 'EQUAL', false);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000007', '5eed0001-0012-0000-0000-000000000007', null, 1, null, 'Finale DM', 1, null);

insert into competition_registration (id, event_registration, competition, club, name, team_number, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0020-0000-0000-000000000025', '5eed000a-0000-0000-0000-000000000001', '5eed0001-0010-0000-0000-000000000007', '5eed0002-0000-0000-0000-000000000001', 'Bremerhaven CM 1x', 1, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000026', '5eed000a-0000-0000-0000-000000000002', '5eed0001-0010-0000-0000-000000000007', '5eed0002-0000-0000-0000-000000000002', 'ARV Kiel CM 1x', 2, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000027', '5eed000a-0000-0000-0000-000000000003', '5eed0001-0010-0000-0000-000000000007', '5eed0002-0000-0000-0000-000000000003', 'Bergedorf CM 1x', 3, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000028', '5eed000a-0000-0000-0000-000000000004', '5eed0001-0010-0000-0000-000000000007', '5eed0002-0000-0000-0000-000000000004', 'Bremen HANSA CM 1x', 4, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000029', '5eed000a-0000-0000-0000-000000000005', '5eed0001-0010-0000-0000-000000000007', '5eed0002-0000-0000-0000-000000000005', 'München CM 1x', 5, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000002a', '5eed000a-0000-0000-0000-000000000006', '5eed0001-0010-0000-0000-000000000007', '5eed0002-0000-0000-0000-000000000006', 'Schleswig CM 1x', 6, now(), null, now(), null);

insert into participant (id, club, firstname, lastname, year, gender, external, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0030-0000-0000-000000000067', '5eed0002-0000-0000-0000-000000000001', 'Malte', 'Andresen', 1996, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000068', '5eed0002-0000-0000-0000-000000000002', 'Ole', 'Asmussen', 1997, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000069', '5eed0002-0000-0000-0000-000000000003', 'Hauke', 'Struve', 1998, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000006a', '5eed0002-0000-0000-0000-000000000004', 'Thies', 'Boysen', 1999, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000006b', '5eed0002-0000-0000-0000-000000000005', 'Arne', 'Kjeldsen', 2000, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000006c', '5eed0002-0000-0000-0000-000000000006', 'Sönke', 'Clausen', 2001, 'M', false, now(), null, now(), null);

insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('5eed0001-0020-0000-0000-000000000025', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000067'),
    ('5eed0001-0020-0000-0000-000000000026', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000068'),
    ('5eed0001-0020-0000-0000-000000000027', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000069'),
    ('5eed0001-0020-0000-0000-000000000028', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000006a'),
    ('5eed0001-0020-0000-0000-000000000029', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000006b'),
    ('5eed0001-0020-0000-0000-00000000002a', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000006c');

-- ----------------------------------------------------------------------------------------
-- Rennen 8 - CMix 4x+ (Mixed-Doppelvierer mit Steuerfrau/-mann), 6 Boote
-- ----------------------------------------------------------------------------------------
insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0010-0000-0000-000000000008', '5eed0001-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('5eed0001-0001-0000-0000-000000000003', '5eed0001-0010-0000-0000-000000000008', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name, description, competition_category)
values ('5eed0001-0011-0000-0000-000000000008', '5eed0001-0010-0000-0000-000000000008', null, '8', 'Mixed-Doppelvierer mit Steuerfrau/-mann', 'CMix 4x+', null, null);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0011-0000-0000-000000000008', now(), null, now(), null);

-- Runden rückwärts angelegt (next_round zeigt vorwärts).
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000009', '5eed0001-0011-0000-0000-000000000008', null, null, 'Finale', true, false, 'CUSTOM', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000008', '5eed0001-0011-0000-0000-000000000008', null, '5eed0001-0012-0000-0000-000000000009', 'Vorlauf', true, true, 'ASCENDING', true);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000008', '5eed0001-0012-0000-0000-000000000008', null, 1, 3, 'Vorlauf 1 DM', 1, null),
       ('5eed0001-0013-0000-0000-000000000009', '5eed0001-0012-0000-0000-000000000008', null, 2, 3, 'Vorlauf 2 DM', 2, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-00000000000a', '5eed0001-0012-0000-0000-000000000009', null, 1, 6, 'Finale DM', 1, null);

-- Setzung: seed = Ausgang der vorigen Runde, ranking = Position im Lauf.
insert into competition_setup_participant (id, competition_setup_match, competition_setup_group, seed, ranking)
values
    ('5eed0001-0014-0000-0000-000000000001', '5eed0001-0013-0000-0000-00000000000a', null, 1, 1),
    ('5eed0001-0014-0000-0000-000000000002', '5eed0001-0013-0000-0000-00000000000a', null, 2, 2),
    ('5eed0001-0014-0000-0000-000000000003', '5eed0001-0013-0000-0000-00000000000a', null, 3, 3),
    ('5eed0001-0014-0000-0000-000000000004', '5eed0001-0013-0000-0000-00000000000a', null, 4, 4),
    ('5eed0001-0014-0000-0000-000000000005', '5eed0001-0013-0000-0000-00000000000a', null, 5, 5),
    ('5eed0001-0014-0000-0000-000000000006', '5eed0001-0013-0000-0000-00000000000a', null, 6, 6);

insert into competition_registration (id, event_registration, competition, club, name, team_number, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0020-0000-0000-00000000002b', '5eed000a-0000-0000-0000-000000000001', '5eed0001-0010-0000-0000-000000000008', '5eed0002-0000-0000-0000-000000000001', 'Bremerhaven CMix 4x+', 1, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000002c', '5eed000a-0000-0000-0000-000000000002', '5eed0001-0010-0000-0000-000000000008', '5eed0002-0000-0000-0000-000000000002', 'ARV Kiel CMix 4x+', 2, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000002d', '5eed000a-0000-0000-0000-000000000003', '5eed0001-0010-0000-0000-000000000008', '5eed0002-0000-0000-0000-000000000003', 'Bergedorf CMix 4x+', 3, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000002e', '5eed000a-0000-0000-0000-000000000004', '5eed0001-0010-0000-0000-000000000008', '5eed0002-0000-0000-0000-000000000004', 'Bremen HANSA CMix 4x+', 4, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000002f', '5eed000a-0000-0000-0000-000000000005', '5eed0001-0010-0000-0000-000000000008', '5eed0002-0000-0000-0000-000000000005', 'München CMix 4x+', 5, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000030', '5eed000a-0000-0000-0000-000000000006', '5eed0001-0010-0000-0000-000000000008', '5eed0002-0000-0000-0000-000000000006', 'Schleswig CMix 4x+', 6, now(), null, now(), null);

insert into participant (id, club, firstname, lastname, year, gender, external, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0030-0000-0000-00000000006d', '5eed0002-0000-0000-0000-000000000001', 'Tjark', 'Carstensen', 2002, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000006e', '5eed0002-0000-0000-0000-000000000001', 'Inken', 'Callsen', 2003, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000006f', '5eed0002-0000-0000-0000-000000000001', 'Mads', 'Sievers', 2004, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000070', '5eed0002-0000-0000-0000-000000000001', 'Astrid', 'Lauritzen', 2005, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000071', '5eed0002-0000-0000-0000-000000000001', 'Sofie', 'Clausen', 1990, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000072', '5eed0002-0000-0000-0000-000000000002', 'Nils', 'Godbersen', 1991, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000073', '5eed0002-0000-0000-0000-000000000002', 'Bente', 'Hinrichsen', 1992, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000074', '5eed0002-0000-0000-0000-000000000002', 'Jann', 'Kjeldsen', 1993, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000075', '5eed0002-0000-0000-0000-000000000002', 'Ida', 'Lorenzen', 1994, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000076', '5eed0002-0000-0000-0000-000000000002', 'Tomke', 'Struve', 1995, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000077', '5eed0002-0000-0000-0000-000000000003', 'Timo', 'Boysen', 1996, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000078', '5eed0002-0000-0000-0000-000000000003', 'Mia', 'Skovgaard', 1997, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000079', '5eed0002-0000-0000-0000-000000000003', 'Jonas', 'Asmussen', 1998, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000007a', '5eed0002-0000-0000-0000-000000000003', 'Lena', 'Iversen', 1999, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000007b', '5eed0002-0000-0000-0000-000000000003', 'Svea', 'Volquardsen', 2000, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000007c', '5eed0002-0000-0000-0000-000000000004', 'Ole', 'Andresen', 2001, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000007d', '5eed0002-0000-0000-0000-000000000004', 'Annika', 'Hansen', 2002, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000007e', '5eed0002-0000-0000-0000-000000000004', 'Thies', 'Brodersen', 2003, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000007f', '5eed0002-0000-0000-0000-000000000004', 'Gesa', 'Ingwersen', 2004, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000080', '5eed0002-0000-0000-0000-000000000004', 'Nele', 'Rathjen', 2005, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000081', '5eed0002-0000-0000-0000-000000000005', 'Tjark', 'Petersen', 1990, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000082', '5eed0002-0000-0000-0000-000000000005', 'Inken', 'Matthiesen', 1991, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000083', '5eed0002-0000-0000-0000-000000000005', 'Mads', 'Nissen', 1992, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000084', '5eed0002-0000-0000-0000-000000000005', 'Astrid', 'Paulsen', 1993, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000085', '5eed0002-0000-0000-0000-000000000005', 'Sofie', 'Østergaard', 1994, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000086', '5eed0002-0000-0000-0000-000000000006', 'Nils', 'Carstensen', 1995, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000087', '5eed0002-0000-0000-0000-000000000006', 'Bente', 'Callsen', 1996, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000088', '5eed0002-0000-0000-0000-000000000006', 'Jann', 'Sievers', 1997, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000089', '5eed0002-0000-0000-0000-000000000006', 'Ida', 'Lauritzen', 1998, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000008a', '5eed0002-0000-0000-0000-000000000006', 'Tomke', 'Clausen', 1999, 'F', false, now(), null, now(), null);

insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('5eed0001-0020-0000-0000-00000000002b', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000006d'),
    ('5eed0001-0020-0000-0000-00000000002b', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000006e'),
    ('5eed0001-0020-0000-0000-00000000002b', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000006f'),
    ('5eed0001-0020-0000-0000-00000000002b', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000070'),
    ('5eed0001-0020-0000-0000-00000000002b', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000071'),
    ('5eed0001-0020-0000-0000-00000000002c', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000072'),
    ('5eed0001-0020-0000-0000-00000000002c', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000073'),
    ('5eed0001-0020-0000-0000-00000000002c', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000074'),
    ('5eed0001-0020-0000-0000-00000000002c', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000075'),
    ('5eed0001-0020-0000-0000-00000000002c', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000076'),
    ('5eed0001-0020-0000-0000-00000000002d', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000077'),
    ('5eed0001-0020-0000-0000-00000000002d', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000078'),
    ('5eed0001-0020-0000-0000-00000000002d', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000079'),
    ('5eed0001-0020-0000-0000-00000000002d', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000007a'),
    ('5eed0001-0020-0000-0000-00000000002d', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000007b'),
    ('5eed0001-0020-0000-0000-00000000002e', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000007c'),
    ('5eed0001-0020-0000-0000-00000000002e', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000007d'),
    ('5eed0001-0020-0000-0000-00000000002e', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000007e'),
    ('5eed0001-0020-0000-0000-00000000002e', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000007f'),
    ('5eed0001-0020-0000-0000-00000000002e', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000080'),
    ('5eed0001-0020-0000-0000-00000000002f', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000081'),
    ('5eed0001-0020-0000-0000-00000000002f', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000082'),
    ('5eed0001-0020-0000-0000-00000000002f', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000083'),
    ('5eed0001-0020-0000-0000-00000000002f', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000084'),
    ('5eed0001-0020-0000-0000-00000000002f', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000085'),
    ('5eed0001-0020-0000-0000-000000000030', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000086'),
    ('5eed0001-0020-0000-0000-000000000030', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000087'),
    ('5eed0001-0020-0000-0000-000000000030', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000088'),
    ('5eed0001-0020-0000-0000-000000000030', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000089'),
    ('5eed0001-0020-0000-0000-000000000030', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000008a');

-- ----------------------------------------------------------------------------------------
-- Rennen 11 - CF 1x (Frauen-Einer Sprint), 8 Boote
-- ----------------------------------------------------------------------------------------
insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0010-0000-0000-000000000009', '5eed0001-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('5eed0001-0001-0000-0000-000000000001', '5eed0001-0010-0000-0000-000000000009', now(), null),
       ('5eed0001-0001-0000-0000-000000000002', '5eed0001-0010-0000-0000-000000000009', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name, description, competition_category)
values ('5eed0001-0011-0000-0000-000000000009', '5eed0001-0010-0000-0000-000000000009', null, '11', 'Frauen-Einer Sprint', 'CF 1x', null, null);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0011-0000-0000-000000000009', now(), null, now(), null);

-- Runden rückwärts angelegt (next_round zeigt vorwärts).
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-00000000000d', '5eed0001-0011-0000-0000-000000000009', null, null, 'Finale', true, false, 'CUSTOM', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-00000000000c', '5eed0001-0011-0000-0000-000000000009', null, '5eed0001-0012-0000-0000-00000000000d', 'Halbfinale', false, true, 'EQUAL', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-00000000000b', '5eed0001-0011-0000-0000-000000000009', null, '5eed0001-0012-0000-0000-00000000000c', 'Viertelfinale', false, true, 'EQUAL', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-00000000000a', '5eed0001-0011-0000-0000-000000000009', null, '5eed0001-0012-0000-0000-00000000000b', 'Zeitfahren', true, true, 'ASCENDING', true);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-00000000000b', '5eed0001-0012-0000-0000-00000000000a', null, 1, null, 'Zeitfahren', 1, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-00000000000c', '5eed0001-0012-0000-0000-00000000000b', null, 1, 2, 'VF1', 1, null),
       ('5eed0001-0013-0000-0000-00000000000d', '5eed0001-0012-0000-0000-00000000000b', null, 2, 2, 'VF2', 2, null),
       ('5eed0001-0013-0000-0000-00000000000e', '5eed0001-0012-0000-0000-00000000000b', null, 3, 2, 'VF3', 3, null),
       ('5eed0001-0013-0000-0000-00000000000f', '5eed0001-0012-0000-0000-00000000000b', null, 4, 2, 'VF4', 4, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000010', '5eed0001-0012-0000-0000-00000000000c', null, 1, 2, 'HF1', 1, null),
       ('5eed0001-0013-0000-0000-000000000011', '5eed0001-0012-0000-0000-00000000000c', null, 2, 2, 'HF2', 2, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000012', '5eed0001-0012-0000-0000-00000000000d', null, 1, 2, 'Finale A', 1, null),
       ('5eed0001-0013-0000-0000-000000000013', '5eed0001-0012-0000-0000-00000000000d', null, 2, 2, 'Finale B', 2, null);

-- Setzung: seed = Ausgang der vorigen Runde, ranking = Position im Lauf.
insert into competition_setup_participant (id, competition_setup_match, competition_setup_group, seed, ranking)
values
    ('5eed0001-0014-0000-0000-000000000007', '5eed0001-0013-0000-0000-00000000000c', null, 1, 1),
    ('5eed0001-0014-0000-0000-000000000008', '5eed0001-0013-0000-0000-00000000000c', null, 8, 2),
    ('5eed0001-0014-0000-0000-000000000009', '5eed0001-0013-0000-0000-00000000000d', null, 4, 1),
    ('5eed0001-0014-0000-0000-00000000000a', '5eed0001-0013-0000-0000-00000000000d', null, 5, 2),
    ('5eed0001-0014-0000-0000-00000000000b', '5eed0001-0013-0000-0000-00000000000e', null, 2, 1),
    ('5eed0001-0014-0000-0000-00000000000c', '5eed0001-0013-0000-0000-00000000000e', null, 7, 2),
    ('5eed0001-0014-0000-0000-00000000000d', '5eed0001-0013-0000-0000-00000000000f', null, 3, 1),
    ('5eed0001-0014-0000-0000-00000000000e', '5eed0001-0013-0000-0000-00000000000f', null, 6, 2),
    ('5eed0001-0014-0000-0000-00000000000f', '5eed0001-0013-0000-0000-000000000010', null, 1, 1),
    ('5eed0001-0014-0000-0000-000000000010', '5eed0001-0013-0000-0000-000000000010', null, 2, 2),
    ('5eed0001-0014-0000-0000-000000000011', '5eed0001-0013-0000-0000-000000000011', null, 3, 1),
    ('5eed0001-0014-0000-0000-000000000012', '5eed0001-0013-0000-0000-000000000011', null, 4, 2),
    ('5eed0001-0014-0000-0000-000000000013', '5eed0001-0013-0000-0000-000000000012', null, 1, 1),
    ('5eed0001-0014-0000-0000-000000000014', '5eed0001-0013-0000-0000-000000000012', null, 2, 2),
    ('5eed0001-0014-0000-0000-000000000015', '5eed0001-0013-0000-0000-000000000013', null, 3, 1),
    ('5eed0001-0014-0000-0000-000000000016', '5eed0001-0013-0000-0000-000000000013', null, 4, 2);

insert into competition_registration (id, event_registration, competition, club, name, team_number, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0020-0000-0000-000000000031', '5eed000a-0000-0000-0000-000000000001', '5eed0001-0010-0000-0000-000000000009', '5eed0002-0000-0000-0000-000000000001', 'Bremerhaven CF 1x', 1, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000032', '5eed000a-0000-0000-0000-000000000002', '5eed0001-0010-0000-0000-000000000009', '5eed0002-0000-0000-0000-000000000002', 'ARV Kiel CF 1x', 2, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000033', '5eed000a-0000-0000-0000-000000000003', '5eed0001-0010-0000-0000-000000000009', '5eed0002-0000-0000-0000-000000000003', 'Bergedorf CF 1x', 3, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000034', '5eed000a-0000-0000-0000-000000000004', '5eed0001-0010-0000-0000-000000000009', '5eed0002-0000-0000-0000-000000000004', 'Bremen HANSA CF 1x', 4, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000035', '5eed000a-0000-0000-0000-000000000005', '5eed0001-0010-0000-0000-000000000009', '5eed0002-0000-0000-0000-000000000005', 'München CF 1x', 5, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000036', '5eed000a-0000-0000-0000-000000000006', '5eed0001-0010-0000-0000-000000000009', '5eed0002-0000-0000-0000-000000000006', 'Schleswig CF 1x', 6, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000037', '5eed000a-0000-0000-0000-000000000007', '5eed0001-0010-0000-0000-000000000009', '5eed0002-0000-0000-0000-000000000007', 'Flensburg CF 1x', 7, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000038', '5eed000a-0000-0000-0000-000000000008', '5eed0001-0010-0000-0000-000000000009', '5eed0002-0000-0000-0000-000000000008', 'Kiel 1862 CF 1x', 8, now(), null, now(), null);

insert into participant (id, club, firstname, lastname, year, gender, external, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0030-0000-0000-00000000008b', '5eed0002-0000-0000-0000-000000000001', 'Hanna', 'Carstensen', 2000, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000008c', '5eed0002-0000-0000-0000-000000000002', 'Mia', 'Nissen', 2001, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000008d', '5eed0002-0000-0000-0000-000000000003', 'Merle', 'Rathjen', 2002, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000008e', '5eed0002-0000-0000-0000-000000000004', 'Lena', 'Petersen', 2003, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000008f', '5eed0002-0000-0000-0000-000000000005', 'Svea', 'Brodersen', 2004, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000090', '5eed0002-0000-0000-0000-000000000006', 'Frieda', 'Volquardsen', 2005, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000091', '5eed0002-0000-0000-0000-000000000007', 'Annika', 'Andresen', 1990, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000092', '5eed0002-0000-0000-0000-000000000008', 'Maren', 'Asmussen', 1991, 'F', false, now(), null, now(), null);

insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('5eed0001-0020-0000-0000-000000000031', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000008b'),
    ('5eed0001-0020-0000-0000-000000000032', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000008c'),
    ('5eed0001-0020-0000-0000-000000000033', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000008d'),
    ('5eed0001-0020-0000-0000-000000000034', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000008e'),
    ('5eed0001-0020-0000-0000-000000000035', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000008f'),
    ('5eed0001-0020-0000-0000-000000000036', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000090'),
    ('5eed0001-0020-0000-0000-000000000037', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000091'),
    ('5eed0001-0020-0000-0000-000000000038', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000092');

-- ----------------------------------------------------------------------------------------
-- Rennen 12 - CM 1x (Männer-Einer Sprint), 3 Boote
-- ----------------------------------------------------------------------------------------
insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0010-0000-0000-00000000000a', '5eed0001-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('5eed0001-0001-0000-0000-000000000001', '5eed0001-0010-0000-0000-00000000000a', now(), null),
       ('5eed0001-0001-0000-0000-000000000002', '5eed0001-0010-0000-0000-00000000000a', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name, description, competition_category)
values ('5eed0001-0011-0000-0000-00000000000a', '5eed0001-0010-0000-0000-00000000000a', null, '12', 'Männer-Einer Sprint', 'CM 1x', null, null);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0011-0000-0000-00000000000a', now(), null, now(), null);

-- Runden rückwärts angelegt (next_round zeigt vorwärts).
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000010', '5eed0001-0011-0000-0000-00000000000a', null, null, 'Finale', true, false, 'CUSTOM', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-00000000000f', '5eed0001-0011-0000-0000-00000000000a', null, '5eed0001-0012-0000-0000-000000000010', 'Halbfinale', false, true, 'EQUAL', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-00000000000e', '5eed0001-0011-0000-0000-00000000000a', null, '5eed0001-0012-0000-0000-00000000000f', 'Zeitfahren', true, true, 'ASCENDING', true);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000014', '5eed0001-0012-0000-0000-00000000000e', null, 1, null, 'Zeitfahren', 1, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000015', '5eed0001-0012-0000-0000-00000000000f', null, 1, 2, 'HF1', 1, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000016', '5eed0001-0012-0000-0000-000000000010', null, 1, 2, 'Finale A', 1, null),
       ('5eed0001-0013-0000-0000-000000000017', '5eed0001-0012-0000-0000-000000000010', null, 2, 1, 'Finale B', 2, null);

-- Setzung: seed = Ausgang der vorigen Runde, ranking = Position im Lauf.
insert into competition_setup_participant (id, competition_setup_match, competition_setup_group, seed, ranking)
values
    ('5eed0001-0014-0000-0000-000000000017', '5eed0001-0013-0000-0000-000000000015', null, 2, 1),
    ('5eed0001-0014-0000-0000-000000000018', '5eed0001-0013-0000-0000-000000000015', null, 3, 2);

insert into competition_registration (id, event_registration, competition, club, name, team_number, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0020-0000-0000-000000000039', '5eed000a-0000-0000-0000-000000000001', '5eed0001-0010-0000-0000-00000000000a', '5eed0002-0000-0000-0000-000000000001', 'Bremerhaven CM 1x', 1, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000003a', '5eed000a-0000-0000-0000-000000000002', '5eed0001-0010-0000-0000-00000000000a', '5eed0002-0000-0000-0000-000000000002', 'ARV Kiel CM 1x', 2, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000003b', '5eed000a-0000-0000-0000-000000000003', '5eed0001-0010-0000-0000-00000000000a', '5eed0002-0000-0000-0000-000000000003', 'Bergedorf CM 1x', 3, now(), null, now(), null);

insert into participant (id, club, firstname, lastname, year, gender, external, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0030-0000-0000-000000000093', '5eed0002-0000-0000-0000-000000000001', 'Arne', 'Asmussen', 1992, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000094', '5eed0002-0000-0000-0000-000000000002', 'Sönke', 'Struve', 1993, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000095', '5eed0002-0000-0000-0000-000000000003', 'Tjark', 'Boysen', 1994, 'M', false, now(), null, now(), null);

insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('5eed0001-0020-0000-0000-000000000039', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000093'),
    ('5eed0001-0020-0000-0000-00000000003a', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000094'),
    ('5eed0001-0020-0000-0000-00000000003b', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000095');

-- ----------------------------------------------------------------------------------------
-- Rennen 13 - CF 2x (Frauen-Doppelzweier Sprint), 4 Boote
-- ----------------------------------------------------------------------------------------
insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0010-0000-0000-00000000000b', '5eed0001-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('5eed0001-0001-0000-0000-000000000001', '5eed0001-0010-0000-0000-00000000000b', now(), null),
       ('5eed0001-0001-0000-0000-000000000002', '5eed0001-0010-0000-0000-00000000000b', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name, description, competition_category)
values ('5eed0001-0011-0000-0000-00000000000b', '5eed0001-0010-0000-0000-00000000000b', null, '13', 'Frauen-Doppelzweier Sprint', 'CF 2x', null, null);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0011-0000-0000-00000000000b', now(), null, now(), null);

-- Runden rückwärts angelegt (next_round zeigt vorwärts).
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000013', '5eed0001-0011-0000-0000-00000000000b', null, null, 'Finale', true, false, 'CUSTOM', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000012', '5eed0001-0011-0000-0000-00000000000b', null, '5eed0001-0012-0000-0000-000000000013', 'Halbfinale', false, true, 'EQUAL', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000011', '5eed0001-0011-0000-0000-00000000000b', null, '5eed0001-0012-0000-0000-000000000012', 'Zeitfahren', true, true, 'ASCENDING', true);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000018', '5eed0001-0012-0000-0000-000000000011', null, 1, null, 'Zeitfahren', 1, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000019', '5eed0001-0012-0000-0000-000000000012', null, 1, 2, 'HF1', 1, null),
       ('5eed0001-0013-0000-0000-00000000001a', '5eed0001-0012-0000-0000-000000000012', null, 2, 2, 'HF2', 2, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-00000000001b', '5eed0001-0012-0000-0000-000000000013', null, 1, 2, 'Finale A', 1, null),
       ('5eed0001-0013-0000-0000-00000000001c', '5eed0001-0012-0000-0000-000000000013', null, 2, 2, 'Finale B', 2, null);

-- Setzung: seed = Ausgang der vorigen Runde, ranking = Position im Lauf.
insert into competition_setup_participant (id, competition_setup_match, competition_setup_group, seed, ranking)
values
    ('5eed0001-0014-0000-0000-000000000019', '5eed0001-0013-0000-0000-000000000019', null, 1, 1),
    ('5eed0001-0014-0000-0000-00000000001a', '5eed0001-0013-0000-0000-000000000019', null, 4, 2),
    ('5eed0001-0014-0000-0000-00000000001b', '5eed0001-0013-0000-0000-00000000001a', null, 2, 1),
    ('5eed0001-0014-0000-0000-00000000001c', '5eed0001-0013-0000-0000-00000000001a', null, 3, 2),
    ('5eed0001-0014-0000-0000-00000000001d', '5eed0001-0013-0000-0000-00000000001b', null, 1, 1),
    ('5eed0001-0014-0000-0000-00000000001e', '5eed0001-0013-0000-0000-00000000001b', null, 2, 2),
    ('5eed0001-0014-0000-0000-00000000001f', '5eed0001-0013-0000-0000-00000000001c', null, 3, 1),
    ('5eed0001-0014-0000-0000-000000000020', '5eed0001-0013-0000-0000-00000000001c', null, 4, 2);

insert into competition_registration (id, event_registration, competition, club, name, team_number, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0020-0000-0000-00000000003c', '5eed000a-0000-0000-0000-000000000001', '5eed0001-0010-0000-0000-00000000000b', '5eed0002-0000-0000-0000-000000000001', 'Bremerhaven CF 2x', 1, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000003d', '5eed000a-0000-0000-0000-000000000002', '5eed0001-0010-0000-0000-00000000000b', '5eed0002-0000-0000-0000-000000000002', 'ARV Kiel CF 2x', 2, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000003e', '5eed000a-0000-0000-0000-000000000003', '5eed0001-0010-0000-0000-00000000000b', '5eed0002-0000-0000-0000-000000000003', 'Bergedorf CF 2x', 3, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000003f', '5eed000a-0000-0000-0000-000000000004', '5eed0001-0010-0000-0000-00000000000b', '5eed0002-0000-0000-0000-000000000004', 'Bremen HANSA CF 2x', 4, now(), null, now(), null);

insert into participant (id, club, firstname, lastname, year, gender, external, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0030-0000-0000-000000000096', '5eed0002-0000-0000-0000-000000000001', 'Inken', 'Thomsen', 1995, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000097', '5eed0002-0000-0000-0000-000000000001', 'Femke', 'Petersen', 1996, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000098', '5eed0002-0000-0000-0000-000000000002', 'Astrid', 'Brodersen', 1997, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000099', '5eed0002-0000-0000-0000-000000000002', 'Sofie', 'Ingwersen', 1998, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000009a', '5eed0002-0000-0000-0000-000000000003', 'Karen', 'Thomsen', 1999, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000009b', '5eed0002-0000-0000-0000-000000000003', 'Bente', 'Petersen', 2000, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000009c', '5eed0002-0000-0000-0000-000000000004', 'Keike', 'Brodersen', 2001, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000009d', '5eed0002-0000-0000-0000-000000000004', 'Ida', 'Ingwersen', 2002, 'F', false, now(), null, now(), null);

insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('5eed0001-0020-0000-0000-00000000003c', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000096'),
    ('5eed0001-0020-0000-0000-00000000003c', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000097'),
    ('5eed0001-0020-0000-0000-00000000003d', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000098'),
    ('5eed0001-0020-0000-0000-00000000003d', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000099'),
    ('5eed0001-0020-0000-0000-00000000003e', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000009a'),
    ('5eed0001-0020-0000-0000-00000000003e', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000009b'),
    ('5eed0001-0020-0000-0000-00000000003f', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000009c'),
    ('5eed0001-0020-0000-0000-00000000003f', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000009d');

-- ----------------------------------------------------------------------------------------
-- Rennen 14 - CM 2x (Männer-Doppelzweier Sprint), 3 Boote
-- ----------------------------------------------------------------------------------------
insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0010-0000-0000-00000000000c', '5eed0001-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('5eed0001-0001-0000-0000-000000000001', '5eed0001-0010-0000-0000-00000000000c', now(), null),
       ('5eed0001-0001-0000-0000-000000000002', '5eed0001-0010-0000-0000-00000000000c', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name, description, competition_category)
values ('5eed0001-0011-0000-0000-00000000000c', '5eed0001-0010-0000-0000-00000000000c', null, '14', 'Männer-Doppelzweier Sprint', 'CM 2x', null, null);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0011-0000-0000-00000000000c', now(), null, now(), null);

-- Runden rückwärts angelegt (next_round zeigt vorwärts).
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000016', '5eed0001-0011-0000-0000-00000000000c', null, null, 'Finale', true, false, 'CUSTOM', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000015', '5eed0001-0011-0000-0000-00000000000c', null, '5eed0001-0012-0000-0000-000000000016', 'Halbfinale', false, true, 'EQUAL', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000014', '5eed0001-0011-0000-0000-00000000000c', null, '5eed0001-0012-0000-0000-000000000015', 'Zeitfahren', true, true, 'ASCENDING', true);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-00000000001d', '5eed0001-0012-0000-0000-000000000014', null, 1, null, 'Zeitfahren', 1, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-00000000001e', '5eed0001-0012-0000-0000-000000000015', null, 1, 2, 'HF1', 1, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-00000000001f', '5eed0001-0012-0000-0000-000000000016', null, 1, 2, 'Finale A', 1, null),
       ('5eed0001-0013-0000-0000-000000000020', '5eed0001-0012-0000-0000-000000000016', null, 2, 1, 'Finale B', 2, null);

-- Setzung: seed = Ausgang der vorigen Runde, ranking = Position im Lauf.
insert into competition_setup_participant (id, competition_setup_match, competition_setup_group, seed, ranking)
values
    ('5eed0001-0014-0000-0000-000000000021', '5eed0001-0013-0000-0000-00000000001e', null, 2, 1),
    ('5eed0001-0014-0000-0000-000000000022', '5eed0001-0013-0000-0000-00000000001e', null, 3, 2);

insert into competition_registration (id, event_registration, competition, club, name, team_number, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0020-0000-0000-000000000040', '5eed000a-0000-0000-0000-000000000001', '5eed0001-0010-0000-0000-00000000000c', '5eed0002-0000-0000-0000-000000000001', 'Bremerhaven CM 2x', 1, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000041', '5eed000a-0000-0000-0000-000000000002', '5eed0001-0010-0000-0000-00000000000c', '5eed0002-0000-0000-0000-000000000002', 'ARV Kiel CM 2x', 2, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000042', '5eed000a-0000-0000-0000-000000000003', '5eed0001-0010-0000-0000-00000000000c', '5eed0002-0000-0000-0000-000000000003', 'Bergedorf CM 2x', 3, now(), null, now(), null);

insert into participant (id, club, firstname, lastname, year, gender, external, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0030-0000-0000-00000000009e', '5eed0002-0000-0000-0000-000000000001', 'Sven', 'Hinrichsen', 2003, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-00000000009f', '5eed0002-0000-0000-0000-000000000001', 'Timo', 'Kjeldsen', 2004, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000a0', '5eed0002-0000-0000-0000-000000000002', 'Ove', 'Clausen', 2005, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000a1', '5eed0002-0000-0000-0000-000000000002', 'Jonas', 'Detlefsen', 1990, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000a2', '5eed0002-0000-0000-0000-000000000003', 'Finn', 'Hinrichsen', 1991, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000a3', '5eed0002-0000-0000-0000-000000000003', 'Malte', 'Kjeldsen', 1992, 'M', false, now(), null, now(), null);

insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('5eed0001-0020-0000-0000-000000000040', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000009e'),
    ('5eed0001-0020-0000-0000-000000000040', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-00000000009f'),
    ('5eed0001-0020-0000-0000-000000000041', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000a0'),
    ('5eed0001-0020-0000-0000-000000000041', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000a1'),
    ('5eed0001-0020-0000-0000-000000000042', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000a2'),
    ('5eed0001-0020-0000-0000-000000000042', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000a3');

-- ----------------------------------------------------------------------------------------
-- Rennen 15 - CMix 2x (Mixed-Doppelzweier Sprint), 4 Boote
-- ----------------------------------------------------------------------------------------
insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0010-0000-0000-00000000000d', '5eed0001-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('5eed0001-0001-0000-0000-000000000001', '5eed0001-0010-0000-0000-00000000000d', now(), null),
       ('5eed0001-0001-0000-0000-000000000002', '5eed0001-0010-0000-0000-00000000000d', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name, description, competition_category)
values ('5eed0001-0011-0000-0000-00000000000d', '5eed0001-0010-0000-0000-00000000000d', null, '15', 'Mixed-Doppelzweier Sprint', 'CMix 2x', null, null);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0011-0000-0000-00000000000d', now(), null, now(), null);

-- Runden rückwärts angelegt (next_round zeigt vorwärts).
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000019', '5eed0001-0011-0000-0000-00000000000d', null, null, 'Finale', true, false, 'CUSTOM', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000018', '5eed0001-0011-0000-0000-00000000000d', null, '5eed0001-0012-0000-0000-000000000019', 'Halbfinale', false, true, 'EQUAL', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000017', '5eed0001-0011-0000-0000-00000000000d', null, '5eed0001-0012-0000-0000-000000000018', 'Zeitfahren', true, true, 'ASCENDING', true);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000021', '5eed0001-0012-0000-0000-000000000017', null, 1, null, 'Zeitfahren', 1, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000022', '5eed0001-0012-0000-0000-000000000018', null, 1, 2, 'HF1', 1, null),
       ('5eed0001-0013-0000-0000-000000000023', '5eed0001-0012-0000-0000-000000000018', null, 2, 2, 'HF2', 2, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000024', '5eed0001-0012-0000-0000-000000000019', null, 1, 2, 'Finale A', 1, null),
       ('5eed0001-0013-0000-0000-000000000025', '5eed0001-0012-0000-0000-000000000019', null, 2, 2, 'Finale B', 2, null);

-- Setzung: seed = Ausgang der vorigen Runde, ranking = Position im Lauf.
insert into competition_setup_participant (id, competition_setup_match, competition_setup_group, seed, ranking)
values
    ('5eed0001-0014-0000-0000-000000000023', '5eed0001-0013-0000-0000-000000000022', null, 1, 1),
    ('5eed0001-0014-0000-0000-000000000024', '5eed0001-0013-0000-0000-000000000022', null, 4, 2),
    ('5eed0001-0014-0000-0000-000000000025', '5eed0001-0013-0000-0000-000000000023', null, 2, 1),
    ('5eed0001-0014-0000-0000-000000000026', '5eed0001-0013-0000-0000-000000000023', null, 3, 2),
    ('5eed0001-0014-0000-0000-000000000027', '5eed0001-0013-0000-0000-000000000024', null, 1, 1),
    ('5eed0001-0014-0000-0000-000000000028', '5eed0001-0013-0000-0000-000000000024', null, 2, 2),
    ('5eed0001-0014-0000-0000-000000000029', '5eed0001-0013-0000-0000-000000000025', null, 3, 1),
    ('5eed0001-0014-0000-0000-00000000002a', '5eed0001-0013-0000-0000-000000000025', null, 4, 2);

insert into competition_registration (id, event_registration, competition, club, name, team_number, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0020-0000-0000-000000000043', '5eed000a-0000-0000-0000-000000000001', '5eed0001-0010-0000-0000-00000000000d', '5eed0002-0000-0000-0000-000000000001', 'Bremerhaven CMix 2x', 1, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000044', '5eed000a-0000-0000-0000-000000000002', '5eed0001-0010-0000-0000-00000000000d', '5eed0002-0000-0000-0000-000000000002', 'ARV Kiel CMix 2x', 2, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000045', '5eed000a-0000-0000-0000-000000000003', '5eed0001-0010-0000-0000-00000000000d', '5eed0002-0000-0000-0000-000000000003', 'Bergedorf CMix 2x', 3, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000046', '5eed000a-0000-0000-0000-000000000004', '5eed0001-0010-0000-0000-00000000000d', '5eed0002-0000-0000-0000-000000000004', 'Bremen HANSA CMix 2x', 4, now(), null, now(), null);

insert into participant (id, club, firstname, lastname, year, gender, external, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0030-0000-0000-0000000000a4', '5eed0002-0000-0000-0000-000000000001', 'Ole', 'Hansen', 1993, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000a5', '5eed0002-0000-0000-0000-000000000001', 'Annika', 'Brodersen', 1994, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000a6', '5eed0002-0000-0000-0000-000000000002', 'Thies', 'Volquardsen', 1995, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000a7', '5eed0002-0000-0000-0000-000000000002', 'Gesa', 'Thomsen', 1996, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000a8', '5eed0002-0000-0000-0000-000000000003', 'Sönke', 'Hansen', 1997, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000a9', '5eed0002-0000-0000-0000-000000000003', 'Wiebke', 'Brodersen', 1998, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000aa', '5eed0002-0000-0000-0000-000000000004', 'Bjarne', 'Volquardsen', 1999, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000ab', '5eed0002-0000-0000-0000-000000000004', 'Femke', 'Thomsen', 2000, 'F', false, now(), null, now(), null);

insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('5eed0001-0020-0000-0000-000000000043', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000a4'),
    ('5eed0001-0020-0000-0000-000000000043', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000a5'),
    ('5eed0001-0020-0000-0000-000000000044', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000a6'),
    ('5eed0001-0020-0000-0000-000000000044', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000a7'),
    ('5eed0001-0020-0000-0000-000000000045', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000a8'),
    ('5eed0001-0020-0000-0000-000000000045', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000a9'),
    ('5eed0001-0020-0000-0000-000000000046', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000aa'),
    ('5eed0001-0020-0000-0000-000000000046', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000ab');

-- ----------------------------------------------------------------------------------------
-- Rennen 16 - CF 4x+ (Frauen-Doppelvierer mit Steuerfrau/-mann Sprint), 3 Boote
-- ----------------------------------------------------------------------------------------
insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0010-0000-0000-00000000000e', '5eed0001-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('5eed0001-0001-0000-0000-000000000001', '5eed0001-0010-0000-0000-00000000000e', now(), null),
       ('5eed0001-0001-0000-0000-000000000002', '5eed0001-0010-0000-0000-00000000000e', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name, description, competition_category)
values ('5eed0001-0011-0000-0000-00000000000e', '5eed0001-0010-0000-0000-00000000000e', null, '16', 'Frauen-Doppelvierer mit Steuerfrau/-mann Sprint', 'CF 4x+', null, null);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0011-0000-0000-00000000000e', now(), null, now(), null);

-- Runden rückwärts angelegt (next_round zeigt vorwärts).
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-00000000001c', '5eed0001-0011-0000-0000-00000000000e', null, null, 'Finale', true, false, 'CUSTOM', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-00000000001b', '5eed0001-0011-0000-0000-00000000000e', null, '5eed0001-0012-0000-0000-00000000001c', 'Halbfinale', false, true, 'EQUAL', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-00000000001a', '5eed0001-0011-0000-0000-00000000000e', null, '5eed0001-0012-0000-0000-00000000001b', 'Zeitfahren', true, true, 'ASCENDING', true);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000026', '5eed0001-0012-0000-0000-00000000001a', null, 1, null, 'Zeitfahren', 1, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000027', '5eed0001-0012-0000-0000-00000000001b', null, 1, 2, 'HF1', 1, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000028', '5eed0001-0012-0000-0000-00000000001c', null, 1, 2, 'Finale A', 1, null),
       ('5eed0001-0013-0000-0000-000000000029', '5eed0001-0012-0000-0000-00000000001c', null, 2, 1, 'Finale B', 2, null);

-- Setzung: seed = Ausgang der vorigen Runde, ranking = Position im Lauf.
insert into competition_setup_participant (id, competition_setup_match, competition_setup_group, seed, ranking)
values
    ('5eed0001-0014-0000-0000-00000000002b', '5eed0001-0013-0000-0000-000000000027', null, 2, 1),
    ('5eed0001-0014-0000-0000-00000000002c', '5eed0001-0013-0000-0000-000000000027', null, 3, 2);

insert into competition_registration (id, event_registration, competition, club, name, team_number, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0020-0000-0000-000000000047', '5eed000a-0000-0000-0000-000000000001', '5eed0001-0010-0000-0000-00000000000e', '5eed0002-0000-0000-0000-000000000001', 'Bremerhaven CF 4x+', 1, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000048', '5eed000a-0000-0000-0000-000000000002', '5eed0001-0010-0000-0000-00000000000e', '5eed0002-0000-0000-0000-000000000002', 'ARV Kiel CF 4x+', 2, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000049', '5eed000a-0000-0000-0000-000000000003', '5eed0001-0010-0000-0000-00000000000e', '5eed0002-0000-0000-0000-000000000003', 'Bergedorf CF 4x+', 3, now(), null, now(), null);

insert into participant (id, club, firstname, lastname, year, gender, external, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0030-0000-0000-0000000000ac', '5eed0002-0000-0000-0000-000000000001', 'Astrid', 'Lauritzen', 2001, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000ad', '5eed0002-0000-0000-0000-000000000001', 'Sofie', 'Clausen', 2002, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000ae', '5eed0002-0000-0000-0000-000000000001', 'Karen', 'Detlefsen', 2003, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000af', '5eed0002-0000-0000-0000-000000000001', 'Bente', 'Boysen', 2004, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000b0', '5eed0002-0000-0000-0000-000000000001', 'Keike', 'Skovgaard', 2005, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000b1', '5eed0002-0000-0000-0000-000000000002', 'Ida', 'Lorenzen', 1990, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000b2', '5eed0002-0000-0000-0000-000000000002', 'Tomke', 'Struve', 1991, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000b3', '5eed0002-0000-0000-0000-000000000002', 'Hanna', 'Feddersen', 1992, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000b4', '5eed0002-0000-0000-0000-000000000002', 'Mia', 'Andresen', 1993, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000b5', '5eed0002-0000-0000-0000-000000000002', 'Merle', 'Hansen', 1994, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000b6', '5eed0002-0000-0000-0000-000000000003', 'Lena', 'Iversen', 1995, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000b7', '5eed0002-0000-0000-0000-000000000003', 'Svea', 'Volquardsen', 1996, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000b8', '5eed0002-0000-0000-0000-000000000003', 'Frieda', 'Thomsen', 1997, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000b9', '5eed0002-0000-0000-0000-000000000003', 'Annika', 'Petersen', 1998, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000ba', '5eed0002-0000-0000-0000-000000000003', 'Maren', 'Matthiesen', 1999, 'F', false, now(), null, now(), null);

insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('5eed0001-0020-0000-0000-000000000047', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000ac'),
    ('5eed0001-0020-0000-0000-000000000047', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000ad'),
    ('5eed0001-0020-0000-0000-000000000047', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000ae'),
    ('5eed0001-0020-0000-0000-000000000047', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000af'),
    ('5eed0001-0020-0000-0000-000000000047', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000b0'),
    ('5eed0001-0020-0000-0000-000000000048', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000b1'),
    ('5eed0001-0020-0000-0000-000000000048', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000b2'),
    ('5eed0001-0020-0000-0000-000000000048', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000b3'),
    ('5eed0001-0020-0000-0000-000000000048', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000b4'),
    ('5eed0001-0020-0000-0000-000000000048', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000b5'),
    ('5eed0001-0020-0000-0000-000000000049', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000b6'),
    ('5eed0001-0020-0000-0000-000000000049', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000b7'),
    ('5eed0001-0020-0000-0000-000000000049', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000b8'),
    ('5eed0001-0020-0000-0000-000000000049', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000b9'),
    ('5eed0001-0020-0000-0000-000000000049', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000ba');

-- ----------------------------------------------------------------------------------------
-- Rennen 17 - CM 4x+ (Männer-Doppelvierer mit Steuerfrau/-mann Sprint), 4 Boote
-- ----------------------------------------------------------------------------------------
insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0010-0000-0000-00000000000f', '5eed0001-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('5eed0001-0001-0000-0000-000000000001', '5eed0001-0010-0000-0000-00000000000f', now(), null),
       ('5eed0001-0001-0000-0000-000000000002', '5eed0001-0010-0000-0000-00000000000f', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name, description, competition_category)
values ('5eed0001-0011-0000-0000-00000000000f', '5eed0001-0010-0000-0000-00000000000f', null, '17', 'Männer-Doppelvierer mit Steuerfrau/-mann Sprint', 'CM 4x+', null, null);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0011-0000-0000-00000000000f', now(), null, now(), null);

-- Runden rückwärts angelegt (next_round zeigt vorwärts).
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-00000000001f', '5eed0001-0011-0000-0000-00000000000f', null, null, 'Finale', true, false, 'CUSTOM', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-00000000001e', '5eed0001-0011-0000-0000-00000000000f', null, '5eed0001-0012-0000-0000-00000000001f', 'Halbfinale', false, true, 'EQUAL', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-00000000001d', '5eed0001-0011-0000-0000-00000000000f', null, '5eed0001-0012-0000-0000-00000000001e', 'Zeitfahren', true, true, 'ASCENDING', true);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-00000000002a', '5eed0001-0012-0000-0000-00000000001d', null, 1, null, 'Zeitfahren', 1, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-00000000002b', '5eed0001-0012-0000-0000-00000000001e', null, 1, 2, 'HF1', 1, null),
       ('5eed0001-0013-0000-0000-00000000002c', '5eed0001-0012-0000-0000-00000000001e', null, 2, 2, 'HF2', 2, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-00000000002d', '5eed0001-0012-0000-0000-00000000001f', null, 1, 2, 'Finale A', 1, null),
       ('5eed0001-0013-0000-0000-00000000002e', '5eed0001-0012-0000-0000-00000000001f', null, 2, 2, 'Finale B', 2, null);

-- Setzung: seed = Ausgang der vorigen Runde, ranking = Position im Lauf.
insert into competition_setup_participant (id, competition_setup_match, competition_setup_group, seed, ranking)
values
    ('5eed0001-0014-0000-0000-00000000002d', '5eed0001-0013-0000-0000-00000000002b', null, 1, 1),
    ('5eed0001-0014-0000-0000-00000000002e', '5eed0001-0013-0000-0000-00000000002b', null, 4, 2),
    ('5eed0001-0014-0000-0000-00000000002f', '5eed0001-0013-0000-0000-00000000002c', null, 2, 1),
    ('5eed0001-0014-0000-0000-000000000030', '5eed0001-0013-0000-0000-00000000002c', null, 3, 2),
    ('5eed0001-0014-0000-0000-000000000031', '5eed0001-0013-0000-0000-00000000002d', null, 1, 1),
    ('5eed0001-0014-0000-0000-000000000032', '5eed0001-0013-0000-0000-00000000002d', null, 2, 2),
    ('5eed0001-0014-0000-0000-000000000033', '5eed0001-0013-0000-0000-00000000002e', null, 3, 1),
    ('5eed0001-0014-0000-0000-000000000034', '5eed0001-0013-0000-0000-00000000002e', null, 4, 2);

insert into competition_registration (id, event_registration, competition, club, name, team_number, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0020-0000-0000-00000000004a', '5eed000a-0000-0000-0000-000000000001', '5eed0001-0010-0000-0000-00000000000f', '5eed0002-0000-0000-0000-000000000001', 'Bremerhaven CM 4x+', 1, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000004b', '5eed000a-0000-0000-0000-000000000002', '5eed0001-0010-0000-0000-00000000000f', '5eed0002-0000-0000-0000-000000000002', 'ARV Kiel CM 4x+', 2, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000004c', '5eed000a-0000-0000-0000-000000000003', '5eed0001-0010-0000-0000-00000000000f', '5eed0002-0000-0000-0000-000000000003', 'Bergedorf CM 4x+', 3, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000004d', '5eed000a-0000-0000-0000-000000000004', '5eed0001-0010-0000-0000-00000000000f', '5eed0002-0000-0000-0000-000000000004', 'Bremen HANSA CM 4x+', 4, now(), null, now(), null);

insert into participant (id, club, firstname, lastname, year, gender, external, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0030-0000-0000-0000000000bb', '5eed0002-0000-0000-0000-000000000001', 'Arne', 'Godbersen', 2000, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000bc', '5eed0002-0000-0000-0000-000000000001', 'Sönke', 'Hinrichsen', 2001, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000bd', '5eed0002-0000-0000-0000-000000000001', 'Tjark', 'Kjeldsen', 2002, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000be', '5eed0002-0000-0000-0000-000000000001', 'Bjarne', 'Lorenzen', 2003, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000bf', '5eed0002-0000-0000-0000-000000000001', 'Mads', 'Struve', 2004, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000c0', '5eed0002-0000-0000-0000-000000000002', 'Emil', 'Boysen', 2005, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000c1', '5eed0002-0000-0000-0000-000000000002', 'Lasse', 'Skovgaard', 1990, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000c2', '5eed0002-0000-0000-0000-000000000002', 'Nils', 'Asmussen', 1991, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000c3', '5eed0002-0000-0000-0000-000000000002', 'Boye', 'Iversen', 1992, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000c4', '5eed0002-0000-0000-0000-000000000002', 'Jann', 'Volquardsen', 1993, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000c5', '5eed0002-0000-0000-0000-000000000003', 'Rune', 'Andresen', 1994, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000c6', '5eed0002-0000-0000-0000-000000000003', 'Sven', 'Hansen', 1995, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000c7', '5eed0002-0000-0000-0000-000000000003', 'Timo', 'Brodersen', 1996, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000c8', '5eed0002-0000-0000-0000-000000000003', 'Ove', 'Ingwersen', 1997, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000c9', '5eed0002-0000-0000-0000-000000000003', 'Jonas', 'Rathjen', 1998, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000ca', '5eed0002-0000-0000-0000-000000000004', 'Finn', 'Petersen', 1999, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000cb', '5eed0002-0000-0000-0000-000000000004', 'Malte', 'Matthiesen', 2000, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000cc', '5eed0002-0000-0000-0000-000000000004', 'Ole', 'Nissen', 2001, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000cd', '5eed0002-0000-0000-0000-000000000004', 'Hauke', 'Paulsen', 2002, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000ce', '5eed0002-0000-0000-0000-000000000004', 'Thies', 'Østergaard', 2003, 'M', false, now(), null, now(), null);

insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('5eed0001-0020-0000-0000-00000000004a', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000bb'),
    ('5eed0001-0020-0000-0000-00000000004a', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000bc'),
    ('5eed0001-0020-0000-0000-00000000004a', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000bd'),
    ('5eed0001-0020-0000-0000-00000000004a', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000be'),
    ('5eed0001-0020-0000-0000-00000000004a', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000bf'),
    ('5eed0001-0020-0000-0000-00000000004b', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000c0'),
    ('5eed0001-0020-0000-0000-00000000004b', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000c1'),
    ('5eed0001-0020-0000-0000-00000000004b', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000c2'),
    ('5eed0001-0020-0000-0000-00000000004b', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000c3'),
    ('5eed0001-0020-0000-0000-00000000004b', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000c4'),
    ('5eed0001-0020-0000-0000-00000000004c', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000c5'),
    ('5eed0001-0020-0000-0000-00000000004c', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000c6'),
    ('5eed0001-0020-0000-0000-00000000004c', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000c7'),
    ('5eed0001-0020-0000-0000-00000000004c', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000c8'),
    ('5eed0001-0020-0000-0000-00000000004c', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000c9'),
    ('5eed0001-0020-0000-0000-00000000004d', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000ca'),
    ('5eed0001-0020-0000-0000-00000000004d', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000cb'),
    ('5eed0001-0020-0000-0000-00000000004d', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000cc'),
    ('5eed0001-0020-0000-0000-00000000004d', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000cd'),
    ('5eed0001-0020-0000-0000-00000000004d', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000ce');

-- ----------------------------------------------------------------------------------------
-- Rennen 18 - CMix 4x+ (Mixed-Doppelvierer mit Steuerfrau/-mann Sprint), 4 Boote
-- ----------------------------------------------------------------------------------------
insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0010-0000-0000-000000000010', '5eed0001-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('5eed0001-0001-0000-0000-000000000001', '5eed0001-0010-0000-0000-000000000010', now(), null),
       ('5eed0001-0001-0000-0000-000000000002', '5eed0001-0010-0000-0000-000000000010', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name, description, competition_category)
values ('5eed0001-0011-0000-0000-000000000010', '5eed0001-0010-0000-0000-000000000010', null, '18', 'Mixed-Doppelvierer mit Steuerfrau/-mann Sprint', 'CMix 4x+', null, null);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0011-0000-0000-000000000010', now(), null, now(), null);

-- Runden rückwärts angelegt (next_round zeigt vorwärts).
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000022', '5eed0001-0011-0000-0000-000000000010', null, null, 'Finale', true, false, 'CUSTOM', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000021', '5eed0001-0011-0000-0000-000000000010', null, '5eed0001-0012-0000-0000-000000000022', 'Halbfinale', false, true, 'EQUAL', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000020', '5eed0001-0011-0000-0000-000000000010', null, '5eed0001-0012-0000-0000-000000000021', 'Zeitfahren', true, true, 'ASCENDING', true);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-00000000002f', '5eed0001-0012-0000-0000-000000000020', null, 1, null, 'Zeitfahren', 1, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000030', '5eed0001-0012-0000-0000-000000000021', null, 1, 2, 'HF1', 1, null),
       ('5eed0001-0013-0000-0000-000000000031', '5eed0001-0012-0000-0000-000000000021', null, 2, 2, 'HF2', 2, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000032', '5eed0001-0012-0000-0000-000000000022', null, 1, 2, 'Finale A', 1, null),
       ('5eed0001-0013-0000-0000-000000000033', '5eed0001-0012-0000-0000-000000000022', null, 2, 2, 'Finale B', 2, null);

-- Setzung: seed = Ausgang der vorigen Runde, ranking = Position im Lauf.
insert into competition_setup_participant (id, competition_setup_match, competition_setup_group, seed, ranking)
values
    ('5eed0001-0014-0000-0000-000000000035', '5eed0001-0013-0000-0000-000000000030', null, 1, 1),
    ('5eed0001-0014-0000-0000-000000000036', '5eed0001-0013-0000-0000-000000000030', null, 4, 2),
    ('5eed0001-0014-0000-0000-000000000037', '5eed0001-0013-0000-0000-000000000031', null, 2, 1),
    ('5eed0001-0014-0000-0000-000000000038', '5eed0001-0013-0000-0000-000000000031', null, 3, 2),
    ('5eed0001-0014-0000-0000-000000000039', '5eed0001-0013-0000-0000-000000000032', null, 1, 1),
    ('5eed0001-0014-0000-0000-00000000003a', '5eed0001-0013-0000-0000-000000000032', null, 2, 2),
    ('5eed0001-0014-0000-0000-00000000003b', '5eed0001-0013-0000-0000-000000000033', null, 3, 1),
    ('5eed0001-0014-0000-0000-00000000003c', '5eed0001-0013-0000-0000-000000000033', null, 4, 2);

insert into competition_registration (id, event_registration, competition, club, name, team_number, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0020-0000-0000-00000000004e', '5eed000a-0000-0000-0000-000000000001', '5eed0001-0010-0000-0000-000000000010', '5eed0002-0000-0000-0000-000000000001', 'Bremerhaven CMix 4x+', 1, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-00000000004f', '5eed000a-0000-0000-0000-000000000002', '5eed0001-0010-0000-0000-000000000010', '5eed0002-0000-0000-0000-000000000002', 'ARV Kiel CMix 4x+', 2, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000050', '5eed000a-0000-0000-0000-000000000003', '5eed0001-0010-0000-0000-000000000010', '5eed0002-0000-0000-0000-000000000003', 'Bergedorf CMix 4x+', 3, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000051', '5eed000a-0000-0000-0000-000000000004', '5eed0001-0010-0000-0000-000000000010', '5eed0002-0000-0000-0000-000000000004', 'Bremen HANSA CMix 4x+', 4, now(), null, now(), null);

insert into participant (id, club, firstname, lastname, year, gender, external, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0030-0000-0000-0000000000cf', '5eed0002-0000-0000-0000-000000000001', 'Arne', 'Asmussen', 2004, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000d0', '5eed0002-0000-0000-0000-000000000001', 'Nele', 'Iversen', 2005, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000d1', '5eed0002-0000-0000-0000-000000000001', 'Tjark', 'Volquardsen', 1990, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000d2', '5eed0002-0000-0000-0000-000000000001', 'Inken', 'Thomsen', 1991, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000d3', '5eed0002-0000-0000-0000-000000000001', 'Femke', 'Petersen', 1992, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000d4', '5eed0002-0000-0000-0000-000000000002', 'Emil', 'Brodersen', 1993, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000d5', '5eed0002-0000-0000-0000-000000000002', 'Sofie', 'Ingwersen', 1994, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000d6', '5eed0002-0000-0000-0000-000000000002', 'Nils', 'Rathjen', 1995, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000d7', '5eed0002-0000-0000-0000-000000000002', 'Bente', 'Møller', 1996, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000d8', '5eed0002-0000-0000-0000-000000000002', 'Keike', 'Carstensen', 1997, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000d9', '5eed0002-0000-0000-0000-000000000003', 'Rune', 'Nissen', 1998, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000da', '5eed0002-0000-0000-0000-000000000003', 'Tomke', 'Paulsen', 1999, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000db', '5eed0002-0000-0000-0000-000000000003', 'Timo', 'Østergaard', 2000, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000dc', '5eed0002-0000-0000-0000-000000000003', 'Mia', 'Johannsen', 2001, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000dd', '5eed0002-0000-0000-0000-000000000003', 'Merle', 'Godbersen', 2002, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000de', '5eed0002-0000-0000-0000-000000000004', 'Finn', 'Sievers', 2003, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000df', '5eed0002-0000-0000-0000-000000000004', 'Svea', 'Lauritzen', 2004, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000e0', '5eed0002-0000-0000-0000-000000000004', 'Ole', 'Clausen', 2005, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000e1', '5eed0002-0000-0000-0000-000000000004', 'Annika', 'Detlefsen', 1990, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000e2', '5eed0002-0000-0000-0000-000000000004', 'Maren', 'Boysen', 1991, 'F', false, now(), null, now(), null);

insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('5eed0001-0020-0000-0000-00000000004e', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000cf'),
    ('5eed0001-0020-0000-0000-00000000004e', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000d0'),
    ('5eed0001-0020-0000-0000-00000000004e', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000d1'),
    ('5eed0001-0020-0000-0000-00000000004e', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000d2'),
    ('5eed0001-0020-0000-0000-00000000004e', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000d3'),
    ('5eed0001-0020-0000-0000-00000000004f', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000d4'),
    ('5eed0001-0020-0000-0000-00000000004f', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000d5'),
    ('5eed0001-0020-0000-0000-00000000004f', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000d6'),
    ('5eed0001-0020-0000-0000-00000000004f', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000d7'),
    ('5eed0001-0020-0000-0000-00000000004f', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000d8'),
    ('5eed0001-0020-0000-0000-000000000050', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000d9'),
    ('5eed0001-0020-0000-0000-000000000050', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000da'),
    ('5eed0001-0020-0000-0000-000000000050', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000db'),
    ('5eed0001-0020-0000-0000-000000000050', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000dc'),
    ('5eed0001-0020-0000-0000-000000000050', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000dd'),
    ('5eed0001-0020-0000-0000-000000000051', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000de'),
    ('5eed0001-0020-0000-0000-000000000051', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000df'),
    ('5eed0001-0020-0000-0000-000000000051', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000e0'),
    ('5eed0001-0020-0000-0000-000000000051', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000e1'),
    ('5eed0001-0020-0000-0000-000000000051', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000e2');

-- ----------------------------------------------------------------------------------------
-- Rennen 16-NC - CF 4x+ NC (Frauen-Doppelvierer mit Steuerfrau/-mann Studierende), 2 Boote
-- ----------------------------------------------------------------------------------------
insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0010-0000-0000-000000000011', '5eed0001-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('5eed0001-0001-0000-0000-000000000001', '5eed0001-0010-0000-0000-000000000011', now(), null),
       ('5eed0001-0001-0000-0000-000000000002', '5eed0001-0010-0000-0000-000000000011', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name, description, competition_category)
values ('5eed0001-0011-0000-0000-000000000011', '5eed0001-0010-0000-0000-000000000011', null, '16-NC', 'Frauen-Doppelvierer mit Steuerfrau/-mann Studierende', 'CF 4x+ NC', null, null);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0011-0000-0000-000000000011', now(), null, now(), null);

-- Runden rückwärts angelegt (next_round zeigt vorwärts).
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000024', '5eed0001-0011-0000-0000-000000000011', null, null, 'Finale', true, false, 'CUSTOM', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000023', '5eed0001-0011-0000-0000-000000000011', null, '5eed0001-0012-0000-0000-000000000024', 'Zeitfahren', true, true, 'ASCENDING', true);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000034', '5eed0001-0012-0000-0000-000000000023', null, 1, null, 'Zeitfahren', 1, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000035', '5eed0001-0012-0000-0000-000000000024', null, 1, 2, 'Finale A', 1, null);

-- Setzung: seed = Ausgang der vorigen Runde, ranking = Position im Lauf.
insert into competition_setup_participant (id, competition_setup_match, competition_setup_group, seed, ranking)
values
    ('5eed0001-0014-0000-0000-00000000003d', '5eed0001-0013-0000-0000-000000000035', null, 1, 1),
    ('5eed0001-0014-0000-0000-00000000003e', '5eed0001-0013-0000-0000-000000000035', null, 2, 2);

insert into competition_registration (id, event_registration, competition, club, name, team_number, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0020-0000-0000-000000000052', '5eed000a-0000-0000-0000-000000000001', '5eed0001-0010-0000-0000-000000000011', '5eed0002-0000-0000-0000-000000000001', 'Bremerhaven CF 4x+ NC', 1, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000053', '5eed000a-0000-0000-0000-000000000002', '5eed0001-0010-0000-0000-000000000011', '5eed0002-0000-0000-0000-000000000002', 'ARV Kiel CF 4x+ NC', 2, now(), null, now(), null);

insert into participant (id, club, firstname, lastname, year, gender, external, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0030-0000-0000-0000000000e3', '5eed0002-0000-0000-0000-000000000001', 'Gesa', 'Rathjen', 1992, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000e4', '5eed0002-0000-0000-0000-000000000001', 'Nele', 'Møller', 1993, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000e5', '5eed0002-0000-0000-0000-000000000001', 'Wiebke', 'Carstensen', 1994, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000e6', '5eed0002-0000-0000-0000-000000000001', 'Inken', 'Callsen', 1995, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000e7', '5eed0002-0000-0000-0000-000000000001', 'Femke', 'Sievers', 1996, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000e8', '5eed0002-0000-0000-0000-000000000002', 'Astrid', 'Østergaard', 1997, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000e9', '5eed0002-0000-0000-0000-000000000002', 'Sofie', 'Johannsen', 1998, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000ea', '5eed0002-0000-0000-0000-000000000002', 'Karen', 'Godbersen', 1999, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000eb', '5eed0002-0000-0000-0000-000000000002', 'Bente', 'Hinrichsen', 2000, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000ec', '5eed0002-0000-0000-0000-000000000002', 'Keike', 'Kjeldsen', 2001, 'F', false, now(), null, now(), null);

insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('5eed0001-0020-0000-0000-000000000052', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000e3'),
    ('5eed0001-0020-0000-0000-000000000052', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000e4'),
    ('5eed0001-0020-0000-0000-000000000052', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000e5'),
    ('5eed0001-0020-0000-0000-000000000052', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000e6'),
    ('5eed0001-0020-0000-0000-000000000052', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000e7'),
    ('5eed0001-0020-0000-0000-000000000053', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000e8'),
    ('5eed0001-0020-0000-0000-000000000053', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000e9'),
    ('5eed0001-0020-0000-0000-000000000053', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000ea'),
    ('5eed0001-0020-0000-0000-000000000053', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000eb'),
    ('5eed0001-0020-0000-0000-000000000053', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000ec');

-- ----------------------------------------------------------------------------------------
-- Rennen 17-NC - CM 4x+ NC (Männer-Doppelvierer mit Steuerfrau/-mann Studierende), 1 Boote
-- ----------------------------------------------------------------------------------------
insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0010-0000-0000-000000000012', '5eed0001-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('5eed0001-0001-0000-0000-000000000001', '5eed0001-0010-0000-0000-000000000012', now(), null),
       ('5eed0001-0001-0000-0000-000000000002', '5eed0001-0010-0000-0000-000000000012', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name, description, competition_category)
values ('5eed0001-0011-0000-0000-000000000012', '5eed0001-0010-0000-0000-000000000012', null, '17-NC', 'Männer-Doppelvierer mit Steuerfrau/-mann Studierende', 'CM 4x+ NC', null, null);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0011-0000-0000-000000000012', now(), null, now(), null);

-- Runden rückwärts angelegt (next_round zeigt vorwärts).
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000026', '5eed0001-0011-0000-0000-000000000012', null, null, 'Finale', true, false, 'CUSTOM', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000025', '5eed0001-0011-0000-0000-000000000012', null, '5eed0001-0012-0000-0000-000000000026', 'Zeitfahren', true, true, 'ASCENDING', true);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000036', '5eed0001-0012-0000-0000-000000000025', null, 1, null, 'Zeitfahren', 1, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000037', '5eed0001-0012-0000-0000-000000000026', null, 1, 1, 'Finale A', 1, null);

-- Setzung: seed = Ausgang der vorigen Runde, ranking = Position im Lauf.
insert into competition_setup_participant (id, competition_setup_match, competition_setup_group, seed, ranking)
values
    ('5eed0001-0014-0000-0000-00000000003f', '5eed0001-0013-0000-0000-000000000037', null, 1, 1);

insert into competition_registration (id, event_registration, competition, club, name, team_number, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0020-0000-0000-000000000054', '5eed000a-0000-0000-0000-000000000001', '5eed0001-0010-0000-0000-000000000012', '5eed0002-0000-0000-0000-000000000001', 'Bremerhaven CM 4x+ NC', 1, now(), null, now(), null);

insert into participant (id, club, firstname, lastname, year, gender, external, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0030-0000-0000-0000000000ed', '5eed0002-0000-0000-0000-000000000001', 'Rune', 'Asmussen', 2002, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000ee', '5eed0002-0000-0000-0000-000000000001', 'Sven', 'Iversen', 2003, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000ef', '5eed0002-0000-0000-0000-000000000001', 'Timo', 'Volquardsen', 2004, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000f0', '5eed0002-0000-0000-0000-000000000001', 'Ove', 'Thomsen', 2005, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000f1', '5eed0002-0000-0000-0000-000000000001', 'Jonas', 'Petersen', 1990, 'M', false, now(), null, now(), null);

insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('5eed0001-0020-0000-0000-000000000054', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000ed'),
    ('5eed0001-0020-0000-0000-000000000054', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000ee'),
    ('5eed0001-0020-0000-0000-000000000054', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000ef'),
    ('5eed0001-0020-0000-0000-000000000054', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000f0'),
    ('5eed0001-0020-0000-0000-000000000054', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000f1');

-- ----------------------------------------------------------------------------------------
-- Rennen 18-NC - CMix 4x+ NC (Mixed-Doppelvierer mit Steuerfrau/-mann Studierende), 3 Boote
-- ----------------------------------------------------------------------------------------
insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0010-0000-0000-000000000013', '5eed0001-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values ('5eed0001-0001-0000-0000-000000000001', '5eed0001-0010-0000-0000-000000000013', now(), null),
       ('5eed0001-0001-0000-0000-000000000002', '5eed0001-0010-0000-0000-000000000013', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name, description, competition_category)
values ('5eed0001-0011-0000-0000-000000000013', '5eed0001-0010-0000-0000-000000000013', null, '18-NC', 'Mixed-Doppelvierer mit Steuerfrau/-mann Studierende', 'CMix 4x+ NC', null, null);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values ('5eed0001-0011-0000-0000-000000000013', now(), null, now(), null);

-- Runden rückwärts angelegt (next_round zeigt vorwärts).
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000029', '5eed0001-0011-0000-0000-000000000013', null, null, 'Finale', true, false, 'CUSTOM', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000028', '5eed0001-0011-0000-0000-000000000013', null, '5eed0001-0012-0000-0000-000000000029', 'Halbfinale', false, true, 'EQUAL', false);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required, use_default_seeding, places_option, is_qualification)
values ('5eed0001-0012-0000-0000-000000000027', '5eed0001-0011-0000-0000-000000000013', null, '5eed0001-0012-0000-0000-000000000028', 'Zeitfahren', true, true, 'ASCENDING', true);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000038', '5eed0001-0012-0000-0000-000000000027', null, 1, null, 'Zeitfahren', 1, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-000000000039', '5eed0001-0012-0000-0000-000000000028', null, 1, 2, 'HF1', 1, null);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name, execution_order, start_time_offset)
values ('5eed0001-0013-0000-0000-00000000003a', '5eed0001-0012-0000-0000-000000000029', null, 1, 2, 'Finale A', 1, null),
       ('5eed0001-0013-0000-0000-00000000003b', '5eed0001-0012-0000-0000-000000000029', null, 2, 1, 'Finale B', 2, null);

-- Setzung: seed = Ausgang der vorigen Runde, ranking = Position im Lauf.
insert into competition_setup_participant (id, competition_setup_match, competition_setup_group, seed, ranking)
values
    ('5eed0001-0014-0000-0000-000000000040', '5eed0001-0013-0000-0000-000000000039', null, 2, 1),
    ('5eed0001-0014-0000-0000-000000000041', '5eed0001-0013-0000-0000-000000000039', null, 3, 2);

insert into competition_registration (id, event_registration, competition, club, name, team_number, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0020-0000-0000-000000000055', '5eed000a-0000-0000-0000-000000000001', '5eed0001-0010-0000-0000-000000000013', '5eed0002-0000-0000-0000-000000000001', 'Bremerhaven CMix 4x+ NC', 1, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000056', '5eed000a-0000-0000-0000-000000000002', '5eed0001-0010-0000-0000-000000000013', '5eed0002-0000-0000-0000-000000000002', 'ARV Kiel CMix 4x+ NC', 2, now(), null, now(), null),
    ('5eed0001-0020-0000-0000-000000000057', '5eed000a-0000-0000-0000-000000000003', '5eed0001-0010-0000-0000-000000000013', '5eed0002-0000-0000-0000-000000000003', 'Bergedorf CMix 4x+ NC', 3, now(), null, now(), null);

insert into participant (id, club, firstname, lastname, year, gender, external, created_at, created_by, updated_at, updated_by)
values
    ('5eed0001-0030-0000-0000-0000000000f2', '5eed0002-0000-0000-0000-000000000001', 'Finn', 'Matthiesen', 1991, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000f3', '5eed0002-0000-0000-0000-000000000001', 'Svea', 'Nissen', 1992, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000f4', '5eed0002-0000-0000-0000-000000000001', 'Ole', 'Paulsen', 1993, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000f5', '5eed0002-0000-0000-0000-000000000001', 'Annika', 'Østergaard', 1994, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000f6', '5eed0002-0000-0000-0000-000000000001', 'Maren', 'Johannsen', 1995, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000f7', '5eed0002-0000-0000-0000-000000000002', 'Arne', 'Callsen', 1996, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000f8', '5eed0002-0000-0000-0000-000000000002', 'Nele', 'Sievers', 1997, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000f9', '5eed0002-0000-0000-0000-000000000002', 'Tjark', 'Lauritzen', 1998, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000fa', '5eed0002-0000-0000-0000-000000000002', 'Inken', 'Clausen', 1999, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000fb', '5eed0002-0000-0000-0000-000000000002', 'Femke', 'Detlefsen', 2000, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000fc', '5eed0002-0000-0000-0000-000000000003', 'Emil', 'Hinrichsen', 2001, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000fd', '5eed0002-0000-0000-0000-000000000003', 'Sofie', 'Kjeldsen', 2002, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000fe', '5eed0002-0000-0000-0000-000000000003', 'Nils', 'Lorenzen', 2003, 'M', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-0000000000ff', '5eed0002-0000-0000-0000-000000000003', 'Bente', 'Struve', 2004, 'F', false, now(), null, now(), null),
    ('5eed0001-0030-0000-0000-000000000100', '5eed0002-0000-0000-0000-000000000003', 'Keike', 'Feddersen', 2005, 'F', false, now(), null, now(), null);

insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('5eed0001-0020-0000-0000-000000000055', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000f2'),
    ('5eed0001-0020-0000-0000-000000000055', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000f3'),
    ('5eed0001-0020-0000-0000-000000000055', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000f4'),
    ('5eed0001-0020-0000-0000-000000000055', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000f5'),
    ('5eed0001-0020-0000-0000-000000000055', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000f6'),
    ('5eed0001-0020-0000-0000-000000000056', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000f7'),
    ('5eed0001-0020-0000-0000-000000000056', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000f8'),
    ('5eed0001-0020-0000-0000-000000000056', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000f9'),
    ('5eed0001-0020-0000-0000-000000000056', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000fa'),
    ('5eed0001-0020-0000-0000-000000000056', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000fb'),
    ('5eed0001-0020-0000-0000-000000000057', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000fc'),
    ('5eed0001-0020-0000-0000-000000000057', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000fd'),
    ('5eed0001-0020-0000-0000-000000000057', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000fe'),
    ('5eed0001-0020-0000-0000-000000000057', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-0000000000ff'),
    ('5eed0001-0020-0000-0000-000000000057', '5eed0001-00c0-0000-0000-000000000001', '5eed0001-0030-0000-0000-000000000100');
