set search_path to ready2race, pg_catalog, public;

-- Ein Verein kann mehrere Verwalter (app_user) haben, an die dieselbe Rechnung per Mail
-- verschickt wird. Bei mehreren Verwaltern waere die Auswahl eines einzelnen Namens fuer das
-- "Rechnung an"-Feld willkuerlich, deshalb bleibt das Feld in diesem Fall leer.
alter table invoice
    alter column billed_to_name drop not null;
