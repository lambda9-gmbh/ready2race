set search_path to ready2race, pg_catalog, public;

-- Eine Person, mehrere Vereine (Entwurf 2026-08-14).
--
-- Ausgangslage: `participant.club` ist seit V202502140000 ein Pflicht-Fremdschluessel, und
-- die Meldeseite prueft ihn hart. Wer in zwei Vereinen rudert -- im Kuestenrudern der
-- Normalfall, nicht die Ausnahme -- konnte deshalb nur von einem davon gemeldet werden.
-- Die Vereine haben sich beholfen, indem sie dieselbe Person ein zweites Mal angelegt
-- haben. Damit zerfaellt alles, was an der Person haengt: Baendchen/QR-Code, geprüfte
-- Bedingungen (Aktivenpass, Schwimmnachweis), Check-in/-out, Ergebnisse.
--
-- Der Zuschnitt loest den Pflicht-Fremdschluessel bewusst NICHT auf:
--   * `participant.club` bleibt not null und heisst ab jetzt ausdruecklich STAMMVEREIN.
--     Nur er darf die Stammdaten aendern (siehe ParticipantRepo.update/delete -- die
--     filtern weiterhin auf `participant.club`, nicht auf diese Tabelle).
--   * Diese Tabelle traegt die WEITEREN Vereine. Der Stammverein gehoert hier nicht noch
--     einmal hinein; er steht schon in `participant.club`. Eine Datenbank-Bedingung kann
--     das nicht pruefen (das Feld liegt in einer anderen Tabelle), deshalb steht die
--     Regel im Service (ParticipantClubService.add) und hier als Kommentar.
--   * Die Rechnung folgt weiterhin dem meldenden Verein (`event_registration.club`).
--     Daran aendert diese Migration nichts.
--
-- Abgrenzung zu `participant.external` + `external_club_name`: das ist der Gastruderer-Pfad
-- fuer Vereine, die es im System gar nicht gibt -- ein Freitext ohne Datensatz. Diese
-- Tabelle verbindet zwei existierende Vereine. Kein zweiter Weg fuer dasselbe.
create table participant_additional_club
(
    participant uuid      not null references participant (id) on delete cascade,
    club        uuid      not null references club (id) on delete cascade,
    created_at  timestamp not null,
    created_by  uuid references app_user (id) on delete set null,
    -- Der zusammengesetzte Primaerschluessel ist zugleich die Sperre gegen den doppelten
    -- Eintrag desselben Vereins -- ein zweites "Ruderklub Flensburg" an derselben Person
    -- laesst die Datenbank gar nicht erst zu, unabhaengig davon, ueber welchen Weg
    -- geschrieben wird.
    primary key (participant, club)
);

-- Der Primaerschluessel deckt die Richtung "welche Vereine hat diese Person?" ab. Die
-- Personenliste eines Vereins fragt umgekehrt -- "welche Personen hat dieser Verein?" --
-- und braucht dafuer einen eigenen Index.
create index idx_participant_additional_club_club on participant_additional_club (club);

-- Der Schalter an der Veranstaltung: duerfen Meldende Personen anderer Vereine ueberhaupt
-- suchen und melden?
--
-- Vorbelegung aus, und zwar fuer Bestand wie fuer neue Veranstaltungen: die
-- vereinsuebergreifende Meldung ist eine Absprache des Ausrichters mit seinen Vereinen,
-- keine stille Voreinstellung. Steht der Schalter aus, verhaelt sich die Meldeseite exakt
-- wie vor dieser Migration.
--
-- Er wirkt nur auf die SUCHE ueber fremde Vereine und auf die Vereinspruefung beim Melden.
-- Die oben angelegte Zugehoerigkeit ist etwas anderes: sie ist eine dauerhafte Eigenschaft
-- der Person und gilt unabhaengig von der Veranstaltung.
alter table event
    add column cross_club_registration boolean not null default false;
