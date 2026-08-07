set search_path to ready2race, pg_catalog, public;

-- Vierter Anzeigetyp: die Athleten-Anzeige an Start und Ziel.
-- `alter type ... add value` läuft ab PostgreSQL 12 innerhalb einer Transaktion,
-- solange der neue Wert nicht in derselben Transaktion verwendet wird.
alter type info_view_type add value 'ATHLETE_BOARD';
