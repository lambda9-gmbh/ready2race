set search_path to ready2race, pg_catalog, public;

-- Der Abbau zu V202608091600: Die vier Adress-Spalten, die von den benannten Rennen abgelöst
-- wurden, verschwinden.
--
-- Bewusst eine eigene Migration und nicht der Schluss der vorigen. Kotlin übersetzt alle
-- Hauptquellen als eine Einheit, und jOOQ erzeugt seine Klassen aus dem migrierten Schema: Wären
-- die Spalten schon beim Anlegen der Rennen gefallen, hätte der Zweig von da an bis zum letzten
-- umgestellten Aufrufer nicht mehr übersetzt -- kein Test wäre lauffähig gewesen, auch keiner, der
-- mit der Zeitnahme nichts zu tun hat. Getrennt bleibt jeder Zwischenstand prüfbar.
--
-- Der Backfill in V202608091600 hat die Anwahl-Spalten bereits gefüllt; hier geht keine Zuordnung
-- verloren, nur die abgelöste Schreibweise.
alter table event
    drop column raceclocker_tt_results_url,
    drop column raceclocker_heats_results_url;

alter table competition
    drop column raceclocker_tt_results_url,
    drop column raceclocker_heats_results_url;
