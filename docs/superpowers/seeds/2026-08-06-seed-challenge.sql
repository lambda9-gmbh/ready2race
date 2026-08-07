-- Winter-Challenge 2026: Seed für die Teilnahmeurkunde (Testkatalog G15, G16, G18, G22).
-- Bisher ist keine Veranstaltung in der Dev-DB ein Challenge-Event, deshalb waren diese vier
-- Fälle ungeprüft: CertificateService.downloadCertificateOfParticipation bricht ohne
-- Challenge-Event mit "Event is not a challenge event" ab.
-- Folgt denselben Konventionen wie 2026-08-06-seed-auflagen.sql (a4f1): eigener UUID-Präfix für
-- ALLE Zeilen (hier c4a1), Cleanup-Block ganz vorn (nur c4a1%, fremde Daten bleiben unangetastet,
-- FK-Reihenfolge Kinder vor Eltern), set time zone 'Europe/Berlin'.
--
-- ============================================================================================
-- Die Prüfkette der Teilnahmeurkunde (CertificateService.downloadCertificateOfParticipation)
-- ============================================================================================
--   1. Zugewiesene Vorlage vom Typ CERTIFICATE_OF_PARTICIPATION -- liegt bereits in der Dev-DB
--      (vorlage-teilnahme-2s.pdf, zweiseitig, Platzhalter auf Seite 1 und 2). Der Seed fasst
--      weder gap_document_template noch gap_document_template_usage an.
--   2. event.challenge_event = true, sonst NotAChallengeEvent.
--   3. ALLE Wettkämpfe der Veranstaltung brauchen ein challenge_end_at in der Vergangenheit,
--      sonst ChallengeStillInProgress. Der Code liest c.challengeEndAt!!, ein fehlender Wert
--      wäre also eine NullPointerException -- challenge_end_at hängt an
--      competition_properties_challenge_config.end_at und ist hier für beide Wettkämpfe gesetzt.
--   4. event.challenge_match_result_type muss gesetzt sein; MatchResultType kennt nur DISTANCE
--      mit der Einheit "m". Die Urkunde schreibt "<Summe> m".
--   5. Die Ergebnisse kommen aus challenge_result_participant_view. Die Sicht verbindet
--      participant -> competition_registration_named_participant -> competition_match_team ->
--      competition_registration -> event_registration. Der Wert je Boot ist
--      competition_match_team.result_value; die Urkunde summiert über ALLE Zeilen einer Person,
--      also über mehrere Wettkämpfe hinweg.
--   6. Abgefragt wird mit verifiedIfNeededOnly = true. ChallengeResultParticipantViewRepo macht
--      daraus: result_verified_at is not null ODER event.submission_needs_verification is false.
--      Steht das Flag am Event auf false, zählt jedes Ergebnis; steht es auf true, zählen nur
--      bestätigte.
--
-- ============================================================================================
-- Bewusste Entscheidungen an den beiden Event-Flags
-- ============================================================================================
--   submission_needs_verification = TRUE. Nur so ist die Verifizierung überhaupt wirksam: bei
--     false schließt der oder-Zweig oben kurz und result_verified_at spielt keine Rolle mehr --
--     man sähe nicht, ob der Filter greift. Mit true trennen sich die beiden Wege sichtbar:
--     die drei bestätigten Ergebnisse liefern Urkunden, das unbestätigte von Antje Duschek
--     fällt heraus. Wer den anderen Zweig sehen will, setzt das Flag in der Veranstaltungs-
--     verwaltung (oder per update) auf false -- dann kommt Antje mit 27600 m dazu, ohne dass
--     am Seed etwas geändert werden muss.
--   self_submission = TRUE. Das ist die Voraussetzung dafür, dass das unbestätigte Ergebnis
--     überhaupt plausibel ist: CompetitionExecutionChallengeService schreibt result_verified_at
--     nur dann null, wenn die Meldung aus der Selbstmeldung (Scope OWN bzw. Teilnehmer-Token)
--     kommt. Trägt die Geschäftsstelle das Ergebnis ein (Scope GLOBAL), ist es sofort bestätigt.
--     Mit self_submission = false wäre eine unbestätigte Zeile also ein Datenstand, den die
--     Anwendung nie erzeugen könnte.
--
-- ============================================================================================
-- Aufbau
-- ============================================================================================
--   Event "Winter-Challenge 2026" (Flensburger Förde, published, challenge_event = true,
--   challenge_match_result_type = 'DISTANCE'), Meldezeitraum und Wertungszeitraum in der
--   Vergangenheit. EIN event_day, damit die Veranstaltung in den Listen ein Datum hat.
--
--   Zwei Wettkämpfe. Ein Challenge-Wettkampf hat genau EINE Runde mit EINEM Lauf -- so legt
--   CompetitionExecutionChallengeService.createChallengeSetup ihn an, und saveChallengeResult
--   scheitert mit CorruptedSetup, sobald es mehr sind. Der Seed hält sich exakt daran:
--     K1 "Winter-Kilometer Einer"         (1x), Wertung 01.12.2025 bis 31.01.2026
--     K2 "Winter-Kilometer Doppelzweier"  (2x), Wertung 01.12.2025 bis 15.02.2026
--   Die unterschiedlichen Enden sind Absicht: wer ChallengeStillInProgress sehen will, zieht
--   das end_at von K2 in die Zukunft, dann sperrt die Urkunde für die GANZE Veranstaltung.
--
--   Zwei Vereine, fünf Personen, fünf Meldungen. Wer welches Ergebnis hat:
--     Hendrik Bargholz  (Winterwacht)  K1  41200 m  bestätigt    -> Urkunde  41200 m
--     Mette Kjærgaard   (Sønderborg)   K1  38500 m  bestätigt \
--                                      K2  52400 m  bestätigt  } -> Urkunde  90900 m (Summe!)
--     Zofia Wiśniewska  (Sønderborg)   K2  52400 m  bestätigt    -> Urkunde  52400 m
--     Antje Duschek     (Winterwacht)  K1  27600 m  UNbestätigt  -> fällt bei
--                                                                  submission_needs_verification
--                                                                  = true heraus
--     Ruben Ostermann   (Winterwacht)  gemeldet, KEIN Ergebnis   -> Fehlerfall NoResults
--   Mette und Zofia sitzen in K2 im selben Doppelzweier, teilen sich also eine Meldung und
--   damit denselben result_value -- genau so entsteht in der Sicht eine Zeile je Person.
--
--   Namen mit Sonderzeichen sind Absicht: æ (Kjærgaard), ø (Sønderborg), ś (Wiśniewska). Die
--   Urkunden laufen über den PDF-Sanitizer, Helvetica kann diese Zeichen teils nicht darstellen
--   -- der Download muss trotzdem durchlaufen (siehe G20). Die E-Mail-Adressen sind erfunden
--   (@example.org) und nur dafür da, dass getForCertificates (EMAIL.isNotNull) und damit der
--   Sammelversand überhaupt Kandidaten findet.
--
-- ============================================================================================
-- Was bewusst fehlt
-- ============================================================================================
--   - Kein Zeitplan (event_schedule_slot), keine Startzeiten, kein currently_running: ein
--     Challenge-Event hat keine Läufe am Steg. competition_match trägt wie bei
--     createChallengeSetup start_time = null.
--   - Keine Rundenkette, keine Plätze, kein places_calculated: die Teilnahmeurkunde liest nur
--     result_value und result_verified_at.
--   - Keine Auflagen, keine QR-Codes, keine event_participant-Zugangstoken. Letztere bräuchte
--     man erst, um über die Teilnehmer-Selbstmeldung ein weiteres Ergebnis zu erfassen; für die
--     Urkunde ist das nicht nötig. Der Cleanup räumt sie trotzdem weg, falls sie in der
--     Oberfläche entstanden sind.
--   - Keine Bestätigungsbilder: result_confirmation_image_required = false an beiden
--     Wettkämpfen, sonst blockiert die Anwendung jede weitere Meldung ohne Dateianhang.
--   - result_verified_by bleibt null. Die Spalte zeigt auf app_user; ein fester Verweis würde
--     den Seed an einen Benutzer binden, den es in einer frischen Datenbank nicht gibt.
--
-- ============================================================================================
-- Zeitstempel
-- ============================================================================================
--   Anders als beim a4f1-Seed gibt es hier KEINEN Versatz-Block am Ende, sondern ausschließlich
--   feste Werte in der Vergangenheit. Grund: die Prüfkette fragt nur, ob challenge_end_at vor
--   jetzt liegt -- ein fester Wert aus dem Februar 2026 bleibt das auf Dauer. Es gibt keinen
--   laufenden Lauf, dessen Abstand zu now() stimmen müsste, also auch nichts zu verschieben.
--   Die reinen Verwaltungsspalten (created_at/updated_at von Event, Verein, Person, Meldung)
--   stehen auf now(); die fachlich gemeinten Zeitpunkte -- Wertungszeitraum, Meldeschluss,
--   Meldezeitpunkt und Bestätigung eines Ergebnisses -- stehen fest im Skript, damit nachlesbar
--   bleibt, was gemeint ist.
--
-- ============================================================================================
-- Beobachtung am Rand (nicht vom Seed verursacht)
-- ============================================================================================
--   CertificateError.NoResults ist derzeit unerreichbar: ChallengeResultParticipantViewRepo
--   .getByEventIdAndParticipantId liefert über database.Extensions.select eine List, und
--   .onNullFail greift auf einer nicht-nullbaren Liste nie. Für Ruben Ostermann und (bei
--   aktiver Verifizierungspflicht) Antje Duschek entsteht deshalb keine Fehlermeldung, sondern
--   eine Urkunde mit "0 m". Genau das lässt sich mit diesem Seed zeigen.
-- ============================================================================================

set search_path to ready2race, pg_catalog, public;
set time zone 'Europe/Berlin';

-- ============================================================================================
-- Cleanup: vorherige c4a1-Zeilen entfernen, FK-Reihenfolge beachten (Kinder vor Eltern).
-- Nur c4a1% -- 5eed%, f0de%, fee1% und a4f1% bleiben unangetastet.
-- competition_match.competition_setup_match ist zugleich der PK, deshalb tragen auch die Läufe
-- den c4a1-Präfix ihrer Setup-Matches.
-- ============================================================================================

-- Bestätigungsbilder, die in der Oberfläche zu einem Ergebnis hochgeladen wurden.
-- competition_match_team_document_data hängt per CASCADE am Dokument, das Dokument per CASCADE
-- am competition_match_team -- der Delete unten reicht also; hier steht nur, dass daran gedacht
-- wurde.

delete from timecode
where id in (
    select timecode from competition_match_team
    where competition_match in (select id from competition_setup_match where id::text like 'c4a1%')
       or competition_registration in (select id from competition_registration where id::text like 'c4a1%')
);

delete from competition_match_team
where competition_match in (select id from competition_setup_match where id::text like 'c4a1%')
   or competition_registration in (select id from competition_registration where id::text like 'c4a1%');

-- Auswechslungen entstehen bei einem Challenge-Event nicht von selbst, könnten aber in der
-- Oberfläche angelegt worden sein. inherited_from ist eine Selbstreferenz ohne ON DELETE CASCADE.
update substitution set inherited_from = null
where competition_registration::text like 'c4a1%'
   or participant_out::text like 'c4a1%'
   or participant_in::text like 'c4a1%';

delete from substitution
where competition_registration::text like 'c4a1%'
   or participant_out::text like 'c4a1%'
   or participant_in::text like 'c4a1%';

delete from competition_deregistration
where competition_registration::text like 'c4a1%';

delete from competition_match
where competition_setup_match::text like 'c4a1%';

delete from event_schedule_slot
where id::text like 'c4a1%' or event::text like 'c4a1%';

-- Was die Oberfläche nach dem Einspielen erzeugt haben kann (Check-in per QR, Anwesenheits-Scans,
-- Urkunden-Jobs, Zugangstoken für die Selbstmeldung): ohne diese Zeilen scheitert der zweite
-- Lauf am Personen-Delete unten.
delete from participant_tracking
where event::text like 'c4a1%' or participant::text like 'c4a1%';

delete from qr_codes
where event::text like 'c4a1%' or participant::text like 'c4a1%';

delete from certificate_of_event_participation_sending_job
where event::text like 'c4a1%' or participant::text like 'c4a1%';

delete from event_participant
where event::text like 'c4a1%' or participant::text like 'c4a1%';

delete from participant_has_requirement_for_event
where event::text like 'c4a1%' or participant::text like 'c4a1%';

delete from event_has_participant_requirement
where event::text like 'c4a1%';

delete from competition_registration_named_participant
where competition_registration::text like 'c4a1%' or participant::text like 'c4a1%';

delete from competition_registration
where id::text like 'c4a1%';

delete from event_registration
where id::text like 'c4a1%';

delete from participant
where id::text like 'c4a1%';

-- Cascades to competition_setup / competition_setup_round / competition_setup_match /
-- competition_setup_participant / competition_properties_has_named_participant /
-- competition_properties_challenge_config.
-- next_round bleibt hier immer null (eine Runde je Wettkampf), deshalb ist anders als beim
-- a4f1-Seed kein vorheriges Entkoppeln der Selbstreferenz nötig.
delete from competition_properties
where id::text like 'c4a1%';

-- Cascades to event_day_has_competition.
delete from competition
where id::text like 'c4a1%';

-- Erst hier: named_participant hängt an den Meldungen oben.
delete from named_participant
where id::text like 'c4a1%';

delete from club
where id::text like 'c4a1%';

-- Cascades to event_day / event_day_has_competition (already gone).
delete from event
where id::text like 'c4a1%';

-- ============================================================================================
-- Event
--
-- challenge_event schaltet die Teilnahmeurkunde frei und die Siegerurkunde ab (G18).
-- challenge_match_result_type = 'DISTANCE' ist der einzige Wert, den MatchResultType kennt;
-- seine Einheit "m" hängt die Urkunde an die Summe.
-- chain_progression_mode bleibt auf dem Standard 'DEAKTIVIERT': eine Challenge hat keine
-- Lauf-Kette, die weitergeschaltet werden könnte.
-- ============================================================================================

insert into event (id, name, description, location, published,
                    registration_available_from, registration_available_to,
                    challenge_event, challenge_match_result_type,
                    self_submission, submission_needs_verification,
                    chain_progression_mode, created_at, created_by, updated_at, updated_by)
values ('c4a10001-0000-0000-0000-000000000001', 'Winter-Challenge 2026',
        'Seed für die Teilnahmeurkunde (G15, G16, G18, G22): Challenge-Event mit abgelaufenem Wertungszeitraum',
        'Flensburger Förde', true,
        '2025-11-01 00:00:00', '2025-11-30 23:59:00',
        true, 'DISTANCE',
        true, true,
        'DEAKTIVIERT', now(), null, now(), null);

-- Ein Renntag, damit die Veranstaltung in den Übersichten ein Datum trägt. Fachlich ist das der
-- Wertungsschluss der Challenge, kein Renntag am Steg.
insert into event_day (id, event, date, name, description, created_at, created_by, updated_at, updated_by)
values ('c4a10002-0000-0000-0000-000000000001', 'c4a10001-0000-0000-0000-000000000001', '2026-02-15',
        'Wertungsschluss', 'Letzter Tag des Wertungszeitraums', now(), null, now(), null);

-- ============================================================================================
-- Rolle im Boot
--
-- Eine einzige Rolle reicht: die Sicht braucht nur den Weg
-- participant -> competition_registration_named_participant -> competition_registration.
-- ============================================================================================

insert into named_participant (id, name, description, created_at, created_by, updated_at, updated_by)
values ('c4a10003-0000-0000-0000-000000000001', 'Ruderer:in', null, now(), null, now(), null);

-- ============================================================================================
-- Vereine + Meldungen
--
-- Der zweite Verein trägt bewusst ein ø im Namen: der Vereinsname landet zwar nicht auf der
-- Teilnahmeurkunde, wohl aber in den Listen und im ZIP-Download je Verein.
-- ============================================================================================

insert into club (id, name, created_at, created_by, updated_at, updated_by)
values
    ('c4a10004-0000-0000-0000-000000000001', 'Ruderclub Winterwacht Flensburg e.V.', now(), null, now(), null),
    ('c4a10004-0000-0000-0000-000000000002', 'Roklub Sønderborg Vinter', now(), null, now(), null);

insert into event_registration (id, event, club, message, created_at, created_by, updated_at, updated_by)
values
    ('c4a10005-0000-0000-0000-000000000001', 'c4a10001-0000-0000-0000-000000000001',
     'c4a10004-0000-0000-0000-000000000001', null, '2025-11-12 17:20:00', null, '2025-11-12 17:20:00', null),
    ('c4a10005-0000-0000-0000-000000000002', 'c4a10001-0000-0000-0000-000000000001',
     'c4a10004-0000-0000-0000-000000000002', null, '2025-11-18 09:05:00', null, '2025-11-18 09:05:00', null);

-- ============================================================================================
-- Wettkämpfe
--
-- Beide Wertungszeiträume liegen abgeschlossen in der Vergangenheit -- das ist die Bedingung
-- aus Punkt 3 der Prüfkette. K2 endet später als K1; die Urkunde prüft das Maximum, also alle
-- Wettkämpfe der Veranstaltung.
-- ============================================================================================

insert into competition (id, event, created_at, created_by, updated_at, updated_by)
values
    ('c4a10006-0000-0000-0000-000000000001', 'c4a10001-0000-0000-0000-000000000001', now(), null, now(), null),
    ('c4a10006-0000-0000-0000-000000000002', 'c4a10001-0000-0000-0000-000000000001', now(), null, now(), null);

insert into event_day_has_competition (event_day, competition, created_at, created_by)
values
    ('c4a10002-0000-0000-0000-000000000001', 'c4a10006-0000-0000-0000-000000000001', now(), null),
    ('c4a10002-0000-0000-0000-000000000001', 'c4a10006-0000-0000-0000-000000000002', now(), null);

insert into competition_properties (id, competition, competition_template, identifier, name, short_name,
                                     description, competition_category)
values
    ('c4a10007-0000-0000-0000-000000000001', 'c4a10006-0000-0000-0000-000000000001', null,
     '1', 'Winter-Kilometer Einer', 'WK 1x',
     'Gesamtstrecke im Einer über den Wertungszeitraum', null),
    ('c4a10007-0000-0000-0000-000000000002', 'c4a10006-0000-0000-0000-000000000002', null,
     '2', 'Winter-Kilometer Doppelzweier', 'WK 2x',
     'Gesamtstrecke im Doppelzweier über den Wertungszeitraum', null);

-- Hier hängt challenge_end_at dran (competition_view.challenge_end_at). Beide Enden liegen in
-- der Vergangenheit; wer ChallengeStillInProgress prüfen will, setzt eines davon in die Zukunft.
insert into competition_properties_challenge_config (competition_properties,
                                                      result_confirmation_image_required,
                                                      start_at, end_at)
values
    ('c4a10007-0000-0000-0000-000000000001', false, '2025-12-01 00:00:00', '2026-01-31 23:59:00'),
    ('c4a10007-0000-0000-0000-000000000002', false, '2025-12-01 00:00:00', '2026-02-15 23:59:00');

insert into competition_properties_has_named_participant (competition_properties, named_participant,
                                                           count_males, count_females,
                                                           count_non_binary, count_mixed)
values
    ('c4a10007-0000-0000-0000-000000000001', 'c4a10003-0000-0000-0000-000000000001', 0, 0, 0, 1),
    ('c4a10007-0000-0000-0000-000000000002', 'c4a10003-0000-0000-0000-000000000001', 0, 0, 0, 2);

insert into competition_setup (competition_properties, created_at, created_by, updated_at, updated_by)
values
    ('c4a10007-0000-0000-0000-000000000001', now(), null, now(), null),
    ('c4a10007-0000-0000-0000-000000000002', now(), null, now(), null);

-- Genau eine Runde je Wettkampf, benannt wie der Wettkampf -- so legt createChallengeSetup sie
-- an. Mehr als eine Runde oder mehr als ein Lauf darin, und die Ergebniserfassung antwortet mit
-- CorruptedSetup.
insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name,
                                      required, use_default_seeding, places_option, is_qualification)
values
    ('c4a10008-0000-0000-0000-000000000001', 'c4a10007-0000-0000-0000-000000000001', null, null,
     'Winter-Kilometer Einer', true, true, 'ASCENDING', false),
    ('c4a10008-0000-0000-0000-000000000002', 'c4a10007-0000-0000-0000-000000000002', null, null,
     'Winter-Kilometer Doppelzweier', true, true, 'ASCENDING', false);

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting,
                                      teams, name, execution_order, start_time_offset)
values
    ('c4a10009-0000-0000-0000-000000000001', 'c4a10008-0000-0000-0000-000000000001', null, 1, null,
     'Winter-Kilometer Einer', 1, null),
    ('c4a10009-0000-0000-0000-000000000002', 'c4a10008-0000-0000-0000-000000000002', null, 1, null,
     'Winter-Kilometer Doppelzweier', 1, null);

-- start_time bleibt null: der "Lauf" ist nur das Gefäß, in dem die gemeldeten Ergebnisse hängen.
insert into competition_match (competition_setup_match, start_time, currently_running, started_at,
                                finished_at, created_at, created_by, updated_at, updated_by)
values
    ('c4a10009-0000-0000-0000-000000000001', null, false, null, null, now(), null, now(), null),
    ('c4a10009-0000-0000-0000-000000000002', null, false, null, null, now(), null, now(), null);

-- ============================================================================================
-- Personen
--
-- Alle fünf mit E-Mail-Adresse, damit auch der Sammelversand (getForCertificates verlangt
-- email is not null) Kandidaten findet. Die Adressen sind erfunden.
-- ============================================================================================

insert into participant (id, club, firstname, lastname, year, gender, external, external_club_name,
                          email, created_at, created_by, updated_at, updated_by)
values
    ('c4a1000b-0000-0000-0000-000000000001', 'c4a10004-0000-0000-0000-000000000001',
     'Hendrik', 'Bargholz', 1989, 'M', false, null, 'hendrik.bargholz@example.org', now(), null, now(), null),
    ('c4a1000b-0000-0000-0000-000000000002', 'c4a10004-0000-0000-0000-000000000001',
     'Antje', 'Duschek', 1994, 'F', false, null, 'antje.duschek@example.org', now(), null, now(), null),
    ('c4a1000b-0000-0000-0000-000000000003', 'c4a10004-0000-0000-0000-000000000001',
     'Ruben', 'Ostermann', 2001, 'M', false, null, 'ruben.ostermann@example.org', now(), null, now(), null),
    ('c4a1000b-0000-0000-0000-000000000004', 'c4a10004-0000-0000-0000-000000000002',
     'Mette', 'Kjærgaard', 1992, 'F', false, null, 'mette.kjaergaard@example.org', now(), null, now(), null),
    ('c4a1000b-0000-0000-0000-000000000005', 'c4a10004-0000-0000-0000-000000000002',
     'Zofia', 'Wiśniewska', 1997, 'F', false, null, 'zofia.wisniewska@example.org', now(), null, now(), null);

-- ============================================================================================
-- Meldungen
--
-- team_number vergibt die Anwendung erst beim Melden des Ergebnisses, fortlaufend je Wettkampf
-- (CompetitionRegistrationRepo.getHighestTeamNumber). Ruben Ostermann hat kein Ergebnis und
-- deshalb auch keine Nummer -- genau der Zustand, den die Anwendung erzeugt hätte.
-- ============================================================================================

insert into competition_registration (id, event_registration, competition, club, name, team_number,
                                       created_at, created_by, updated_at, updated_by)
values
    -- K1 "Winter-Kilometer Einer"
    ('c4a1000a-0000-0000-0000-000000000001', 'c4a10005-0000-0000-0000-000000000001',
     'c4a10006-0000-0000-0000-000000000001', 'c4a10004-0000-0000-0000-000000000001',
     'Winterwacht Einer 1', 1, '2025-11-12 17:22:00', null, '2026-01-28 18:42:00', null),
    ('c4a1000a-0000-0000-0000-000000000002', 'c4a10005-0000-0000-0000-000000000001',
     'c4a10006-0000-0000-0000-000000000001', 'c4a10004-0000-0000-0000-000000000001',
     'Winterwacht Einer 2', 2, '2025-11-12 17:23:00', null, '2026-01-30 20:11:00', null),
    ('c4a1000a-0000-0000-0000-000000000003', 'c4a10005-0000-0000-0000-000000000001',
     'c4a10006-0000-0000-0000-000000000001', 'c4a10004-0000-0000-0000-000000000001',
     'Winterwacht Einer 3', null, '2025-11-12 17:24:00', null, '2025-11-12 17:24:00', null),
    ('c4a1000a-0000-0000-0000-000000000004', 'c4a10005-0000-0000-0000-000000000002',
     'c4a10006-0000-0000-0000-000000000001', 'c4a10004-0000-0000-0000-000000000002',
     'Sønderborg Einer 1', 3, '2025-11-18 09:07:00', null, '2026-01-29 07:55:00', null),
    -- K2 "Winter-Kilometer Doppelzweier"
    ('c4a1000a-0000-0000-0000-000000000005', 'c4a10005-0000-0000-0000-000000000002',
     'c4a10006-0000-0000-0000-000000000002', 'c4a10004-0000-0000-0000-000000000002',
     'Sønderborg Doppelzweier 1', 1, '2025-11-18 09:08:00', null, '2026-02-14 16:30:00', null);

-- Aufstellung. Mette Kjærgaard steht in ZWEI Meldungen (K1 einzeln, K2 zusammen mit Zofia
-- Wiśniewska) -- daraus entstehen für sie zwei Zeilen in challenge_result_participant_view, die
-- die Urkunde addiert.
insert into competition_registration_named_participant (competition_registration, named_participant, participant)
values
    ('c4a1000a-0000-0000-0000-000000000001', 'c4a10003-0000-0000-0000-000000000001', 'c4a1000b-0000-0000-0000-000000000001'),
    ('c4a1000a-0000-0000-0000-000000000002', 'c4a10003-0000-0000-0000-000000000001', 'c4a1000b-0000-0000-0000-000000000002'),
    ('c4a1000a-0000-0000-0000-000000000003', 'c4a10003-0000-0000-0000-000000000001', 'c4a1000b-0000-0000-0000-000000000003'),
    ('c4a1000a-0000-0000-0000-000000000004', 'c4a10003-0000-0000-0000-000000000001', 'c4a1000b-0000-0000-0000-000000000004'),
    ('c4a1000a-0000-0000-0000-000000000005', 'c4a10003-0000-0000-0000-000000000001', 'c4a1000b-0000-0000-0000-000000000004'),
    ('c4a1000a-0000-0000-0000-000000000005', 'c4a10003-0000-0000-0000-000000000001', 'c4a1000b-0000-0000-0000-000000000005');

-- ============================================================================================
-- Ergebnisse
--
-- result_value ist die Strecke in Metern (MatchResultType.DISTANCE, Einheit "m").
-- start_number vergibt die Anwendung fortlaufend je Lauf; die Meldung von Ruben Ostermann hat
-- keine Zeile, weil nie ein Ergebnis eingegangen ist.
-- place, out, failed bleiben unberührt: eine Challenge kennt keine Platzierung am Steg.
-- ============================================================================================

insert into competition_match_team (id, competition_match, competition_registration, start_number,
                                     place, out, failed, result_value,
                                     result_verified_at, result_verified_by,
                                     created_at, created_by, updated_at, updated_by)
values
    -- K1: bestätigt (Hendrik Bargholz)
    ('c4a1000c-0000-0000-0000-000000000001', 'c4a10009-0000-0000-0000-000000000001',
     'c4a1000a-0000-0000-0000-000000000001', 1, null, false, false, 41200,
     '2026-02-02 09:15:00', null, '2026-01-28 18:42:00', null, '2026-02-02 09:15:00', null),
    -- K1: UNbestätigt (Antje Duschek) -- Selbstmeldung, noch niemand hat sie bestätigt
    ('c4a1000c-0000-0000-0000-000000000002', 'c4a10009-0000-0000-0000-000000000001',
     'c4a1000a-0000-0000-0000-000000000002', 2, null, false, false, 27600,
     null, null, '2026-01-30 20:11:00', null, '2026-01-30 20:11:00', null),
    -- K1: bestätigt (Mette Kjærgaard), erster Teil ihrer Summe
    ('c4a1000c-0000-0000-0000-000000000003', 'c4a10009-0000-0000-0000-000000000001',
     'c4a1000a-0000-0000-0000-000000000004', 3, null, false, false, 38500,
     '2026-02-02 09:20:00', null, '2026-01-29 07:55:00', null, '2026-02-02 09:20:00', null),
    -- K2: bestätigt, ein Boot mit zwei Personen (Mette Kjærgaard + Zofia Wiśniewska).
    -- Beide erben denselben Wert; für Mette ist es der zweite Teil ihrer Summe.
    ('c4a1000c-0000-0000-0000-000000000004', 'c4a10009-0000-0000-0000-000000000002',
     'c4a1000a-0000-0000-0000-000000000005', 1, null, false, false, 52400,
     '2026-02-17 10:05:00', null, '2026-02-14 16:30:00', null, '2026-02-17 10:05:00', null);
