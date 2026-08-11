set search_path to ready2race, pg_catalog, public;

-- "Auf dem Wasser" ist ruderspezifisch. ready2race soll auch Sportarten ohne Wasser bedienen, und
-- die neutrale Entsprechung fuer den Ort, an dem gefahren wird, ist die Arena.
--
-- Die Tabelle ist bewusst duenn besetzt (nur Abweichungen vom eingebauten Standard), das sind
-- wenige Zeilen. chk_ccs_requirement_matches_check_type nennt nur die beiden REQUIREMENT-Typen und
-- bleibt unveraendert gueltig.
update competition_check_severity
set check_type = 'NOT_IN_ARENA'
where check_type = 'NOT_ON_WATER';
