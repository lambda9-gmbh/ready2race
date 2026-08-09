-- Kürzungsregeln für den Rudersport, nachträglich einzuspielen (Nachtrag zum Entwurf 2026-08-09).
--
-- Bis zur Migration V202608091300 standen diese Kürzel als `CLUB_TYPE_ABBREVIATIONS` im
-- Kotlin-Code. Sie sind Wissen über eine Sportart und stehen deshalb nicht mehr im Produktkern:
-- ausgeliefert wird nur das Sportartübergreifende (Rechtsformen, Gründungsjahre, Klammerzusätze).
--
-- Diese Datei ist für Installationen gedacht, die die Kürzel bisher aus dem Code bekommen haben -
-- für die CRF 2026 sieht damit alles aus wie vorher. Eine Segelregatta spielt sie nicht ein.
--
-- Anders als die alten regulären Ausdrücke greifen Regeln wortgenau und literal. Schreibvarianten
-- brauchen deshalb je eine Zeile: "Ruderclub" und "Ruder-Club" sind zwei Einträge. Das schließt
-- die Fälle ein, die die alten Muster verfehlt haben - "Ruder-Gesellschaft" wurde nie abgekürzt,
-- weil dem Muster das optionale Trennzeichen fehlte.
--
-- Die Reihenfolge (sort_order) ist inhaltlich: die längere Zusammensetzung steht vor der kürzeren.
-- Die Zahlen setzen bei 100 an, damit die mitgelieferten Regeln (10-50: Rechtsformen,
-- Klammerzusätze, Gründungsjahre) vorher greifen.
--
-- Mehrfaches Einspielen ist gefahrlos: schon vorhandene Bestandteile werden übersprungen.

set search_path to ready2race, pg_catalog, public;

begin;

insert into club_name_rule (id, kind, term, replacement, sort_order, created_at, updated_at)
select gen_random_uuid(), 'ABBREVIATION', seed.term, seed.replacement, seed.sort_order, now(), now()
from (values ('Rudergesellschaft', 'RG', 100),
             ('Ruder-Gesellschaft', 'RG', 110),
             ('Rudervereinigung', 'RVg', 120),
             ('Ruder-Vereinigung', 'RVg', 130),
             ('Ruderverein', 'RV', 140),
             ('Ruder-Verein', 'RV', 150),
             ('Ruderclub', 'RC', 160),
             ('Ruder-Club', 'RC', 170),
             ('Ruderklub', 'RK', 180),
             ('Ruder-Klub', 'RK', 190),
             ('Segelverein', 'SV', 200),
             ('Segel-Verein', 'SV', 210),
             ('Segelclub', 'SC', 220),
             ('Segel-Club', 'SC', 230),
             ('Sportvereinigung', 'SVg', 240),
             ('Sportverein', 'SV', 250),
             ('Turnverein', 'TV', 260),
             ('Akademischer', 'Akad.', 270)) as seed(term, replacement, sort_order)
where not exists (select 1
                  from club_name_rule existing
                  where existing.kind = 'ABBREVIATION'
                    and lower(existing.term) = lower(seed.term));

commit;
