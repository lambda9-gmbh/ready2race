set search_path to ready2race, pg_catalog, public;

-- RaceClocker-Integration (Issue #94). Rein additiv neben dem bestehenden Webscorer-Weg: der
-- xlsx-Import und die Webscorer-Presets bleiben unveraendert.
--
-- RaceClocker kennt keine Import-API; Startlisten kommen weiterhin per CSV-Upload dorthin. Der
-- Rueckweg laeuft ueber den oeffentlichen Ergebnis-Feed, den jede Ergebnis-URL mit ?json=1 liefert.
--
-- RaceClocker braucht pro Wettkampf zwei Rennen, weil eine gemappte "Lauf"-Spalte ein Rennen
-- automatisch in den Wave-Modus schaltet und nur Einzelstarts einen echten Countdown haben:
-- eines vom Typ "Einzelstarts (Zeitfahren)" fuer die Qualifikationsrunde, eines vom Typ "Start in
-- mehreren Laeufen" fuer alle uebrigen Runden. Welche der beiden URLs fuer ein Match gilt, ergibt
-- sich aus competition_setup_round.is_qualification -- es muss nichts manuell zugeordnet werden.
alter table competition
    add column raceclocker_tt_results_url    text,
    add column raceclocker_heats_results_url text;

-- RaceClocker zeigt genau eine "Name"-Spalte pro Teilnehmer. Der Startlisten-Export konnte Vor- und
-- Nachname bisher nur getrennt schreiben (col_participant_firstname / col_participant_lastname);
-- diese Spalte liefert beides zusammen. Bei Mannschaftsbooten werden alle Aktiven kommagetrennt
-- aufgefuehrt, wie bei den bestehenden Teilnehmerspalten auch.
alter table startlist_export_config
    add column col_participant_fullname text;

-- Die beiden mitgelieferten Presets. Der Unterschied ist allein die Lauf-Spalte: das Zeitfahren-Rennen
-- darf sie nicht enthalten (sonst kippt RaceClocker es in den Wave-Modus), das Laeufe-Rennen muss.
--
-- col_team_registration_id traegt die competition_registration-UUID und ist der Schluessel fuer den
-- Ergebnis-Rueckweg. Im RaceClocker-Spaltenmapper wird diese Spalte auf "Extra info" gezogen -- nicht
-- auf das Custom-Feld: dessen JSON-Key wandert mit dem vergebenen Label mit, waehrend "Extra info"
-- immer als ExtraInfo mit selbstbeschreibenden [Label, Wert]-Paaren im Feed steht. Ausserdem bleibt
-- das Custom-Feld so fuer den Bootsnamen frei.
insert into startlist_export_config
    (id, name, col_participant_fullname, col_team_start_number, col_team_club,
     col_competition_short_name, col_match_name, col_team_registration_id,
     created_at, created_by, updated_at, updated_by)
values
    (gen_random_uuid(), 'RaceClocker Zeitfahren', 'Name', 'Bib', 'Verein',
     'Kategorie', null, 'R2R-ID', now(), null, now(), null),
    (gen_random_uuid(), 'RaceClocker Laeufe', 'Name', 'Bib', 'Verein',
     'Kategorie', 'Lauf', 'R2R-ID', now(), null, now(), null)
;
