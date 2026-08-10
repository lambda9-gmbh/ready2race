set search_path to ready2race, pg_catalog, public;

-- Gibt eine Bedingung für das persönliche Dashboard "Mein Event" frei. Der Standard ist
-- bewusst "aus": die Freigabe ist eine Entscheidung des Veranstalters darüber, welche
-- Aussage über eine Person öffentlich sichtbar sein darf, und darf nicht als Nebeneffekt
-- einer Migration entstehen. Die Freitext-Notiz aus checked_participant_requirement wird
-- unabhängig von diesem Schalter niemals öffentlich ausgeliefert.
-- Die View participant_requirement_for_event selektiert pr.* und übernimmt die Spalte
-- daher ohne eigene Anpassung; afterMigrate.sql erzeugt sie im selben Lauf neu.
alter table participant_requirement
    add column publicly_visible boolean not null default false;
