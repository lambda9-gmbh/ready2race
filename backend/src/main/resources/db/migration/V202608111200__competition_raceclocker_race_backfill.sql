set search_path to ready2race, pg_catalog, public;

-- Die Rennen-Zuordnung wandert vollständig auf den Wettkampf (Wunsch vom 11.08.2026): Ein
-- Wettkampf zeigt ab jetzt nur noch auf sein EIGENES RaceClocker-Rennen, die Voreinstellung der
-- Veranstaltung (event.raceclocker_race_*) wird nicht mehr gelesen und nirgends mehr geschrieben.
--
-- Bestehende Regatten hatten die Rennen aber oft NUR auf Veranstaltungsebene gesetzt und die
-- Wettkämpfe erbten sie still. Ohne diese Migration stünde ein solcher Wettkampf nach dem Umbau
-- plötzlich ohne Rennen da -- die Oberfläche meldete "Zeitnahme noch nicht einsatzbereit", und der
-- Abruf fände keine Ergebnisse, obwohl an der Zuordnung nie jemand etwas geändert hat.
--
-- Deshalb wird die bisher effektive Zuordnung einmalig explizit gemacht: Wo ein Wettkampf selbst
-- kein Rennen gewählt hat, erbt er es hier dauerhaft von seiner Veranstaltung. Wettkämpfe mit
-- eigener Anwahl bleiben unberührt -- genau die coalesce-Regel, die vorher zur Laufzeit galt.
update competition c
set raceclocker_race_qualification = e.raceclocker_race_qualification,
    updated_at                     = now()
from event e
where c.event = e.id
  and c.raceclocker_race_qualification is null
  and e.raceclocker_race_qualification is not null;

update competition c
set raceclocker_race_rounds = e.raceclocker_race_rounds,
    updated_at              = now()
from event e
where c.event = e.id
  and c.raceclocker_race_rounds is null
  and e.raceclocker_race_rounds is not null;

-- Die Veranstaltungsspalten bleiben als tote Spalten stehen: Sie zu entfernen hieße, den jOOQ-
-- Code neu zu erzeugen, und ein leeres, nicht mehr gelesenes Feld schadet nicht. Ein späterer
-- Aufräum-MR kann sie fallen lassen.
