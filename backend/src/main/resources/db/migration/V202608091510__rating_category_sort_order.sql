set search_path to ready2race, pg_catalog, public;

-- Die Reihenfolge, in der Wertungskategorien in Ergebnislisten als Abschnitte erscheinen. Sie
-- haengt an der Veranstaltung und nicht an der Kategorie: dieselbe Kategorie darf bei zwei
-- Regatten unterschiedlich einsortiert sein, und eine global gepflegte Reihenfolge waere fuer
-- Veranstalter, die nur eine Teilmenge der Kategorien benutzen, ohne Bedeutung.
alter table event_rating_category
    add column sort_order int not null default 0;

-- Backfill: die bisher ueberall gezeigte alphabetische Reihenfolge festschreiben, damit die
-- Migration allein noch keine Anzeige veraendert.
update event_rating_category erc
set sort_order = numbered.position
from (select erc2.event,
             erc2.rating_category,
             row_number() over (partition by erc2.event order by rc.name) - 1 as position
      from event_rating_category erc2
               join rating_category rc on rc.id = erc2.rating_category) numbered
where erc.event = numbered.event
  and erc.rating_category = numbered.rating_category;
