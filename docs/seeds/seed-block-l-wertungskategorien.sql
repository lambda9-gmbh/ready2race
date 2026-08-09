-- Testdaten fuer Block L (Ergebnisse nach Wertungskategorien).
-- Zielveranstaltung: Coastal-Regatta Flensburg 2026, Wettkampf 11 "Coastal Frauen Einer".
-- Wettkampf 8 bleibt bewusst ohne jede Kategorie -> Regressionsfall L5.
set search_path to ready2race, pg_catalog, public;

begin;

-- Vier Kategorien. Die Sortierung ist absichtlich NICHT alphabetisch, damit L2 etwas beweist:
-- alphabetisch waere Breitensport, Junioren, Masters, Meisterschaften.
insert into rating_category (id, name, description, created_at, created_by, updated_at, updated_by)
values ('1a000000-0000-0000-0000-000000000001', 'Meisterschaften', 'Offene Wertung', now(), null, now(), null),
       ('1a000000-0000-0000-0000-000000000002', 'Breitensport', null, now(), null, now(), null),
       ('1a000000-0000-0000-0000-000000000003', 'Masters', 'ab Jahrgang 1985', now(), null, now(), null),
       ('1a000000-0000-0000-0000-000000000004', 'Junioren', 'bleibt leer - Fall L20', now(), null, now(), null)
on conflict (id) do nothing;

insert into event_rating_category (event, rating_category, year_restriction_from, year_restriction_to,
                                   sort_order, created_at, created_by, updated_at, updated_by)
values ('9d27f0ee-6622-48d5-82a4-1975baa18d13', '1a000000-0000-0000-0000-000000000001', null, null, 0, now(), null, now(), null),
       ('9d27f0ee-6622-48d5-82a4-1975baa18d13', '1a000000-0000-0000-0000-000000000002', null, null, 1, now(), null, now(), null),
       ('9d27f0ee-6622-48d5-82a4-1975baa18d13', '1a000000-0000-0000-0000-000000000003', 1985, null, 2, now(), null, now(), null),
       ('9d27f0ee-6622-48d5-82a4-1975baa18d13', '1a000000-0000-0000-0000-000000000004', null, null, 3, now(), null, now(), null)
on conflict (event, rating_category) do nothing;

-- Zuordnung der Boote. Cassel bleibt bewusst OHNE Kategorie -> Fall L4.
update competition_registration set rating_category = '1a000000-0000-0000-0000-000000000001'
where id in ('3f02cdf2-20d9-469e-bf92-670d73c8fb4f',  -- Hochschulrudern Flensburg, Zeitfahren 1
             'cd3d31d3-0c4e-4c2f-be6e-7174cb38dcf5',  -- Frankfurter, Zeitfahren 4 -> gleich auf 1 gesetzt
             '52ecdd44-0189-4514-b009-8c79abf42d80'); -- Ratzeburger, Zeitfahren 6

update competition_registration set rating_category = '1a000000-0000-0000-0000-000000000002'
where id in ('eca9cf01-d6da-4b20-b8e2-e0a8b0d02797',  -- Koelner, Zeitfahren 2
             'a1f147e6-0d02-4a07-b9e4-6aa3d16eea77'); -- Allemannia, Zeitfahren 7

update competition_registration set rating_category = '1a000000-0000-0000-0000-000000000003'
where id in ('e71b8260-ba38-4b64-899a-a51832022d13',  -- Muenchen, Zeitfahren 5
             'eb34040c-5a93-46dc-908f-9061476c82ab'); -- Heidelberg, im Zeitfahren gescheitert -> Fall L7

-- KEIN Gleichstand im Lauf moeglich: place_unique_in_match (V202507040930) verbietet zwei
-- Boote mit demselben Platz im selben Lauf. Der Gleichstandsfall gehoert damit zu den
-- Wettkampf-Platzierungen, wo CompetitionSetupPlacesOption.EQUAL mehrere Boote gleich wertet.

commit;

-- Kontrollausgabe
select rc.name as kategorie, erc.sort_order, count(cr.id) as boote
from event_rating_category erc
         join rating_category rc on rc.id = erc.rating_category
         left join competition_registration cr on cr.rating_category = rc.id
where erc.event = '9d27f0ee-6622-48d5-82a4-1975baa18d13'
group by rc.name, erc.sort_order
order by erc.sort_order;
