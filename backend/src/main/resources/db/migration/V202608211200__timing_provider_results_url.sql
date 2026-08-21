set search_path to ready2race, pg_catalog, public;

-- Die Nennung der Zeitnahme verweist auf die Ergebnisseite des Rennens statt auf die Startseite
-- des Anbieters (Wunsch vom 21.08.2026).
--
-- V202608201200 hat für alle Läufe aus dem RaceClocker-Abruf `https://raceclocker.com` eingetragen
-- - die Startseite, von der aus der Leser die Regatta selbst suchen müsste. Gemeint ist die
-- Adresse, aus der die Zeiten tatsächlich geholt werden: `raceclocker_race.results_url` des
-- Rennens, das am Wettkampf hängt. Neue Ergebnisse schreiben sie ab jetzt selbst mit; hier
-- kommt der Bestand nach.
--
-- Angefasst wird nur, was noch wörtlich auf der Startseite steht. Eine von Hand gepflegte
-- Nennung aus einer Import-Konfiguration bleibt damit unberührt, auch wenn sie „RaceClocker"
-- heißt: Wer dort eine eigene Adresse eingetragen hat, meint sie auch so.
update competition_match cm
set timing_provider_url = r.results_url
from competition_setup_match csm
    join competition_setup_round csr on csm.competition_setup_round = csr.id
    join competition_properties cp on csr.competition_setup = cp.id
    join competition c on cp.competition = c.id
    join raceclocker_race r on c.raceclocker_race = r.id
where cm.competition_setup_match = csm.id
  and cm.timing_provider_name = 'RaceClocker'
  and cm.timing_provider_url = 'https://raceclocker.com'
  and r.results_url is not null;
