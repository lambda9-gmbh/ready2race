set search_path to ready2race, pg_catalog, public;

-- Zeitnahme-Konfiguration pro Wettkampf (Design 2026-08-05).
--
-- Die beiden RaceClocker-Ergebnis-URLs liegen bereits auf dieser Tabelle (V202607211200). Hier kommt
-- dazu, mit welchem Fremdsystem der Wettkampf ueberhaupt arbeitet und welche Spalten-Presets dafuer
-- gelten -- bisher wurde das Preset bei jedem CSV-Download von Hand aus der globalen Liste gewaehlt.
--
-- Bewusst auf `competition` und nicht auf `competition_properties`: letztere haengt laut
-- Check-Constraint entweder an einem Wettkampf ODER an einer Wettkampf-Vorlage. Eine Vorlage kann
-- diese Werte nicht tragen, denn sie zeigen auf konkrete Rennen in einem fremden System, angelegt
-- fuer eine konkrete Regatta.
alter table competition
    add column timing_system                  text,
    -- RaceClocker braucht pro Wettkampf zwei Rennen und damit zwei Presets: das Zeitfahren-Rennen
    -- darf die Lauf-Spalte nicht enthalten (sonst kippt RaceClocker es in den Wave-Modus und der
    -- Countdown fehlt), das Laeufe-Rennen muss sie enthalten. Welches gilt, entscheidet dieselbe
    -- competition_setup_round.is_qualification, die auch die URL-Auswahl steuert.
    --
    -- Ist der Qualifikations-Slot leer, greift der Runden-Slot. Genau deshalb braucht Webscorer nur
    -- ein Preset: dort gibt es diese Zweiteilung nicht.
    add column startlist_config_qualification uuid references startlist_export_config on delete set null,
    add column startlist_config_rounds        uuid references startlist_export_config on delete set null,
    -- Ergebnis-Import per xlsx: bei Webscorer der Hauptweg, bei RaceClocker der Notausgang, wenn der
    -- Ergebnis-Feed am Renntag klemmt.
    add column result_import_config           uuid references match_result_import_config on delete set null;

-- Alle Spalten nullable ohne Default: bestehende Wettkaempfe bleiben unkonfiguriert, es gibt keinen
-- Datenmigrations-Schritt. `on delete set null` laesst das Loeschen eines Presets in der
-- Konfigurationsverwaltung zu -- der Wettkampf verliert dann nur seine Vorbelegung.
