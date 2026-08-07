set search_path to ready2race, pg_catalog, public;

-- Steuert, ob das Beenden eines Laufs im Schiedsrichter-Dashboard automatisch die Läufe der
-- nächsten Startzeit aktiviert.
--
-- Bewusst standardmäßig aus: Startzeiten lassen sich erst pflegen, wenn die Läufe einer Runde
-- gesetzt sind. Solange spätere Runden noch fehlen, hat der Zeitplan Lücken und die Kette würde
-- den falschen Lauf greifen (etwa ein Qualifying eines anderen Wettkampfs, das schon eine Zeit
-- hat). Wer einen durchgängig gepflegten Zeitplan hat, schaltet die Automatik pro Veranstaltung ein.
alter table event
    add column auto_activate_next_match boolean not null default false;
