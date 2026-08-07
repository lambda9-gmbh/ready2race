set search_path to ready2race, pg_catalog, public;

-- Pausen/Programmpunkte aus dem Zeitplan erscheinen damit auch auf Kiosk und Athleten-Anzeige -
-- standardmäßig aus, weil öffentliche Anzeigen sparsam bleiben sollen.
alter table event
    add column show_breaks_on_public_boards boolean not null default false;
