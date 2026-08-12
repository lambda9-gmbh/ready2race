set search_path to ready2race, pg_catalog, public;

-- Freilos "muss gefahren werden" (Nutzerwunsch vom 11.08.2026): Manche Regelwerke verlangen, dass
-- auch ein Boot ohne Gegner die Strecke fährt. Das Flag steht am Lauf (competition_match), nicht am
-- Setup-Lauf: Ein Freilos ist eine Eigenschaft des KONKRETEN Laufs einer Durchführung -- es entsteht
-- erst aus der Besetzung (eine fahrende Mannschaft in einer nicht verpflichtenden Runde), und beim
-- Löschen und Neu-Auslosen einer Runde soll die Entscheidung mit dem Lauf verschwinden, statt am
-- wiederverwendeten Turnierbaum-Knoten zu kleben.
--
-- Wirkung: Der Lauf gilt operativ als echtes Rennen (Startlisten-Export, RaceClocker-Abruf, die
-- Kette wartet auf sein Beenden). Das Weiterkommen bleibt Freilos-Semantik -- die eine fahrende
-- Mannschaft steigt unabhängig von Zeit und Platz auf, die gemessene Zeit läuft "außer Konkurrenz"
-- nur in die Anzeige.
alter table competition_match
    add column bye_must_race boolean not null default false;
