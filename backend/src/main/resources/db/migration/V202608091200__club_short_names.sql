set search_path to ready2race, pg_catalog, public;

-- Gepflegte Kurzformen von Vereinsnamen (Entwurf 2026-08-09).
--
-- Bewusst KEIN Fremdschluessel auf club: der Verein, den ein Athlet traegt, ist bei Gastruderern
-- reiner Freitext an der Person (participant.external_club_name). Von den 46 Schreibweisen, die im
-- Produktivstand der CRF 2026 tatsaechlich an Personen stehen, liessen sich nur 17 einem
-- Vereins-Datensatz zuordnen -- ein Schluessel auf die Vereinstabelle wuerde also die Mehrzahl der
-- Namen ausschliessen, die gepflegt werden sollen.
create table club_short_name
(
    -- Normalisierte Form des Vereinsnamens (siehe ClubNameKey), nicht der Name selbst: der
    -- Schluessel fasst die Schreibvarianten desselben Vereins zusammen, damit
    -- "Rostocker Ruderclub" und "Rostocker Ruder-Club von 1885 e.V." nur einmal gepflegt werden.
    name_key    text      not null primary key,
    -- Eine vorgefundene Original-Schreibweise. Steht hier, damit die Pflegeseite Lesbares zeigen
    -- kann statt des zusammengezogenen Schluessels.
    sample_name text      not null,
    short_name  text      not null,
    created_at  timestamp not null,
    created_by  uuid references app_user on delete set null,
    updated_at  timestamp not null,
    updated_by  uuid references app_user on delete set null
);
