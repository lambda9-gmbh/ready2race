set search_path to ready2race, pg_catalog, public;

-- Nacharbeiten zur RaceClocker-Integration (Issue #94) aus einem vollstaendigen Round-Trip-Test.

-- Lauf-Team-ID als Round-Trip-Schluessel.
--
-- Die Registrierungs-UUID ist pro Team eindeutig, aber nicht pro Lauf: im Laeufe-Rennen liegen alle
-- Runden in einem einzigen RaceClocker-Rennen, dieselbe UUID taucht also je Runde einmal auf. Bisher
-- loeste der Wave-Name (= Match-Name) das auf -- ein Feld, das am Renntag umbenannt oder
-- zusammengelegt wird ("AF4 & AF2" fuer einen gemeinsamen Start) und den Pull dann brechen laesst.
-- competition_match_team.id ist pro Team UND Runde eindeutig, damit entfaellt der Wave-Filter.
--
-- Preis dafuer: die ID wechselt, wenn eine Runde neu angelegt wird, die Startliste in RaceClocker ist
-- dann veraltet. Der Pull faellt deshalb auf die Registrierungs-UUID zurueck, wenn ueber die
-- Lauf-Team-ID keine Zeile passt.
alter table startlist_export_config
    add column col_team_match_id text;

-- Welche der beiden ID-Spalten exportiert wird, haengt am Zielsystem: die Registrierungs-UUID fuer den
-- Webscorer-Weg, die Lauf-Team-ID fuer RaceClocker. Mindestens eine von beiden verlangt die Validierung
-- des Requests, damit keine Config ohne Round-Trip-Schluessel entsteht.
alter table startlist_export_config
    alter column col_team_registration_id drop not null;

-- Kopfzeile abschaltbar.
--
-- RaceClocker uebernimmt die Kopfzeile beim Import als (Bogus-)Teilnehmer, sofern der "Kopfzeile"-
-- Toggle dort nicht aktiv gesetzt wird. Fuer den Webscorer-Weg bleibt sie noetig, der mappt die Spalten
-- ueber die Header.
alter table startlist_export_config
    add column no_header boolean not null default false;

-- Wertungskategorie in die Kategorie-Spalte.
--
-- RaceClocker gruppiert Ergebnisse nach genau einem Feld. Zwei getrennte Spalten helfen dort nicht,
-- deshalb haengt dieses Flag die Wertungskategorie an den Wettkampf-Kurznamen: "CMix2x-Int - Senior"
-- statt nur "CMix2x-Int". Ohne Wertungskategorie bleibt es beim Kurznamen allein.
alter table startlist_export_config
    add column append_rating_to_short_name boolean not null default false;

update startlist_export_config
set col_team_match_id           = 'R2R-Lauf-ID',
    col_team_registration_id    = null,
    no_header                   = true,
    append_rating_to_short_name = true
where name in ('RaceClocker Zeitfahren', 'RaceClocker Laeufe');
