set search_path to ready2race, pg_catalog, public;

-- Nennung der externen Zeitnahme auf den öffentlichen Ergebnissen.
--
-- Anbieter von Zeitnahme verlangen einen sichtbaren Hinweis samt Link, wenn ihre Daten
-- veröffentlicht werden (RaceClocker, Nutzungsbedingungen Nr. 6). Woher die Ergebnisse eines
-- Laufs zuletzt kamen, steht deshalb am Lauf selbst -- als Abzug (Name + Website) und nicht als
-- Verweis auf die Import-Konfiguration: Der RaceClocker-Abruf holt seine Ergebnisse ohne eine
-- solche Konfiguration (eigene `raceclocker_race`-Tabellen), und ein veröffentlichter Hinweis
-- soll auch dann stehen bleiben, wenn die Konfiguration später umbenannt oder gelöscht wird.

alter table match_result_import_config
    add column attribution_name text,
    add column attribution_url text;

update match_result_import_config
    set attribution_name = 'RaceClocker',
        attribution_url = 'https://raceclocker.com'
    where name ilike '%raceclocker%';

update match_result_import_config
    set attribution_name = 'Webscorer',
        attribution_url = 'https://www.webscorer.com'
    where name ilike '%webscorer%';

alter table competition_match
    add column timing_provider_name text,
    add column timing_provider_url  text;

-- Bestandsdaten: Läufe, deren Ergebnisse schon aus RaceClocker kamen, bekommen die Nennung
-- nachgetragen - sonst stünde sie ausgerechnet unter den bereits veröffentlichten Ergebnissen
-- nicht. Woher ein Ergebnis kam, ist rückwirkend nicht aufgezeichnet; erkannt wird es an drei
-- Zeichen zusammen: Der Wettkampf misst mit RaceClocker, hat ein Rennen angewählt, und der Lauf
-- wurde entweder abgerufen (`raceclocker_polled_at`, der Automatik-Stempel) oder trägt
-- Ergebnisse. Ausgenommen sind Läufe mit angehaltener Automatik: Genau dort hat jemand von Hand
-- eingetragen oder eine Datei hochgeladen, die Zeiten stammen also nicht aus RaceClocker.
update competition_match cm
set timing_provider_name = 'RaceClocker',
    timing_provider_url  = 'https://raceclocker.com'
from competition_setup_match csm
    join competition_setup_round csr on csm.competition_setup_round = csr.id
    join competition_properties cp on csr.competition_setup = cp.id
    join competition c on cp.competition = c.id
    join event e on c.event = e.id
where cm.competition_setup_match = csm.id
  and c.raceclocker_race is not null
  and coalesce(c.timing_system, e.timing_system) = 'RACECLOCKER'
  and cm.raceclocker_auto_paused_at is null
  and (
    cm.raceclocker_polled_at is not null
        or exists (select 1
                   from competition_match_team cmt
                   where cmt.competition_match = cm.competition_setup_match
                     and cmt.place is not null)
    );
