set search_path to ready2race, pg_catalog, public;

-- Rückbau von V202608121200 (Nutzerentscheid vom 12.08.2026): Die Reset-Mechanik der
-- Lauf-Benennung ist zurückgenommen, die Benennungs-Anwendung bei der Rundenerzeugung entspricht
-- wieder unverändert dem Original (nur die Läufe, deren weighting im Satz für das aktuelle n
-- vorkommt, werden überschrieben). Die beiden Sicherungs-Spalten waren nie produktiv (crf-2026
-- ist nicht in main gemergt) und wurden nur von Entwicklungs-Datenbanken angewendet - deshalb
-- "if exists": Eine Datenbank, die V202608121200 nie gesehen hat, hat die Spalten auch nicht.
alter table competition_setup_match
    drop column if exists base_name,
    drop column if exists base_execution_order;

-- Materialisierter Freilos-Name (Anforderung vom 12.08.2026): Ein Lauf, in dem nur ein Boot
-- fährt (nicht verpflichtende Runde, dieselbe Regel wie MatchStatusLogic.deriveBye), soll überall
-- als "Freilos <Setzungszahl>" lesbar sein, statt einen Pseudo-Namen aus dem Benennungs-Satz oder
-- der Setup-Vorlage zu tragen.
--
-- Der Name steht bewusst an der LAUF-INSTANZ (competition_match), nicht an der Setup-Vorlage
-- (competition_setup_match): Die Vorlage überlebt das Löschen der Runde und würde den
-- Freilos-Namen in die nächste Erzeugung verschleppen; die Instanz stirbt mit der Runde, und das
-- Neu-Erzeugen nach einer (zurückgenommenen) Abmeldung heilt damit alles von selbst - genau der
-- Arbeitsfluss des Nutzers. Gesetzt wird sie in createNewRound NACH der unveränderten
-- Benennungs-Anwendung; gelesen wird überall coalesce(competition_match.bye_name,
-- competition_setup_match.name).
alter table competition_match
    add column bye_name text;
