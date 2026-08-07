set search_path to ready2race, pg_catalog, public;

-- Zeitnahme-Voreinstellung pro Veranstaltung (Ergänzung zu V202608051500).
--
-- Die RaceClocker-Rennen (und damit ihre Ergebnis-URLs) werden pro Veranstaltung angelegt, nicht
-- pro Wettkampf: eine Regatta läuft typischerweise über EIN Zeitfahren- und EIN Läufe-Rennen mit
-- vielen Waves. Die Werte pro Wettkampf einzutragen hieße, dieselben zwei URLs zwanzigmal zu
-- pflegen — und ein Tippfehler in einer Kopie fällt erst am Renntag auf.
--
-- Deshalb hier dieselben drei Felder noch einmal auf `event` als Voreinstellung. Die Spalten auf
-- `competition` bleiben und behalten Vorrang (coalesce an den Lesestellen:
-- CompetitionMatchRepo.getForRaceClockerPull und .getStartListConfigTarget sowie der
-- Zeitnahme-Tab) — ein Wettkampf mit eigenem Rennen, z. B. einem separaten Sprint-Rennen,
-- überschreibt die Veranstaltungswerte gezielt, alle anderen erben sie.
--
-- Die Startlisten-Presets und der Ergebnis-Import bleiben bewusst pro Wettkampf: sie hängen an
-- den Spalten der konkreten Startliste (Bootsklasse, Crew-Größe), nicht am Rennen im Fremdsystem.
alter table event
    add column timing_system                 text,
    add column raceclocker_tt_results_url    text,
    add column raceclocker_heats_results_url text;

-- Alle Spalten nullable ohne Default und ohne Backfill: bestehende Veranstaltungen bleiben
-- unkonfiguriert, die Wettkampf-Werte gelten unverändert weiter.
