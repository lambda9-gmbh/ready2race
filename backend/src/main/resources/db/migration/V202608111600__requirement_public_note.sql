set search_path to ready2race, pg_catalog, public;

-- Athletengerechter, ÖFFENTLICHER Text zu einer Bedingung (z. B. "Es sind 2,5 kg
-- Zusatzgewicht mitzuführen"). Bewusst eine eigene Spalte neben `description`: die
-- Beschreibung ist die interne Arbeitsanweisung für die Meldestelle und wird seit dieser
-- Migration nicht mehr an "Mein Event" ausgeliefert. Die Freitext-Notiz aus
-- checked_participant_requirement bleibt davon unberührt und wird weiterhin niemals
-- öffentlich (siehe Kommentar in V202608101000).
-- Die View participant_requirement_for_event selektiert pr.* und übernimmt die Spalte
-- daher ohne eigene Anpassung; afterMigrate.sql erzeugt sie im selben Lauf neu.
alter table participant_requirement
    add column public_note text;
