set search_path to ready2race, pg_catalog, public;

-- Ausgangszustand der Lauf-Benennung (Bugfix 12.08.2026, Nutzer-Screenshots: zweimal "VF1",
-- kein "VF2" nach Freilos-bedingtem Aufrücken).
--
-- Die Benennungs-Sätze (competition_setup_match_naming) sind ABWEICHUNGEN vom Ausgangszustand
-- je Bracket-Größe n; angewandt werden sie bei der Rundenerzeugung, indem sie name und
-- execution_order des Setup-Laufs ÜBERSCHREIBEN. Damit war der Ausgangszustand nach der ersten
-- Anwendung unwiederbringlich weg - und ein Lauf, dessen weighting im Satz für das NEUE n nicht
-- vorkam (Runde gelöscht, Abmeldung, Neu-Erzeugen mit kleinerem n), behielt den Namen der ALTEN
-- Anwendung: Duplikate und Lücken in den Anzeigen.
--
-- Deshalb sichern base_name/base_execution_order den Ausgangszustand beim ersten Überschreiben.
-- base_execution_order trägt zugleich den Marker "schon gesichert": execution_order ist NOT NULL,
-- base_execution_order IS NULL heißt also eindeutig "noch nie überschrieben - der aktuelle Wert
-- IST der Ausgangszustand". base_name allein könnte das nicht, ein Ausgangsname darf null sein
-- (Anzeige fällt dann auf den Rundennamen zurück).
--
-- Kein Backfill: Für Läufe, deren Ausgangszustand schon vor dieser Migration überschrieben wurde,
-- ist er nicht mehr rekonstruierbar - dort wird beim nächsten Überschreiben der dann aktuelle
-- (bereits benannte) Stand als Ausgangszustand gesichert. Das ist der bestmögliche Stand, und er
-- betrifft nur Runden, die vor der Migration bereits erzeugt und benannt waren.
alter table competition_setup_match
    add column base_name text,
    add column base_execution_order integer;
