set search_path to ready2race, pg_catalog, public;

-- Startlisten-Export und Rennergebnisse-Import als Veranstaltungs-Voreinstellung (Ergänzung zu
-- V202608062100, die dasselbe für System und die beiden RaceClocker-Adressen getan hat).
--
-- Ursprünglich blieben diese beiden bewusst pro Wettkampf, weil sie an den Spalten der konkreten
-- Startliste hängen. In der Praxis einer Regatta ist das die Ausnahme: alle Wettkämpfe exportieren
-- dieselben Spalten, weil sie in dasselbe Rennen im Fremdsystem importiert werden. Sie zwanzigmal
-- einzeln zu setzen ist derselbe Fehlerweg wie bei den Adressen — nur fällt er noch später auf,
-- nämlich erst beim Spaltenmapping am Renntag.
--
-- Wie zuvor: `competition` behält seine Spalten und hat Vorrang (coalesce an den Lesestellen),
-- ein Wettkampf mit abweichender Bootsklasse schert also weiterhin gezielt aus.
alter table event
    add column startlist_config_qualification uuid references startlist_export_config on delete set null,
    add column startlist_config_rounds        uuid references startlist_export_config on delete set null,
    add column result_import_config           uuid references match_result_import_config on delete set null;

-- Alle Spalten nullable ohne Default und ohne Backfill: bestehende Veranstaltungen bleiben
-- unkonfiguriert, die Wettkampf-Werte gelten unverändert weiter. `on delete set null` wie beim
-- Wettkampf — wird ein Export in der Konfiguration gelöscht, verliert die Veranstaltung nur ihre
-- Vorbelegung.
