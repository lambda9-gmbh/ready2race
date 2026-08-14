set search_path to ready2race, pg_catalog, public;

-- Eine Teilnahmebedingung galt bisher genau einmal je Person und Veranstaltung: der
-- Primärschlüssel von participant_has_requirement_for_event kannte nur (Person,
-- Veranstaltung, Bedingung), eine Zeitachse gab es außer created_at nicht. Nach dem ersten
-- Regattatag 2026 ist das als Verfahrensfehler erkannt worden: ein Aktivenpass wird einmal
-- gesehen und gilt, eine Waage oder eine Bootsabnahme dagegen an jedem Tag - und manches
-- nur für einen bestimmten Wettkampf - neu.
--
-- Bewusst ZWEI Schalter und keine Aufzählung ("je Veranstaltung | je Tag | je Wettkampf"):
-- aus zwei Schaltern entstehen alle vier Kombinationen von selbst (je Veranstaltung, je Tag,
-- je Wettkampf, je Wettkampf und Tag). Eine Aufzählung müsste für die vierte um einen
-- weiteren Wert erweitert werden, und mit ihr jede Stelle, die sie auswertet.
--
-- Der Standard ist für beide "aus" und damit exakt das bisherige Verhalten - eine Migration
-- darf keiner bestehenden Bedingung stillschweigend eine neue Gültigkeitsdauer verpassen.
alter table participant_requirement
    add column per_event_day   boolean not null default false,
    add column per_competition boolean not null default false;

-- Die Dimensionen der Erfüllung. Beide nullbar, weil beide fehlen dürfen: eine Bedingung,
-- die für die ganze Veranstaltung gilt, hat weder Tag noch Wettkampf. Ein erfundener
-- Ersatzwert (Sentinel-Tag, Sentinel-Wettkampf) wäre eine Lüge in den Daten und müsste
-- außerdem als echte Zeile in event_day bzw. competition existieren.
--
-- on delete cascade, nicht set null: verschwindet ein Wettkampftag oder ein Wettkampf, ist
-- die daran hängende Erfüllung gegenstandslos. Mit "set null" bliebe eine Zeile stehen, die
-- plötzlich "gilt für alles" behauptete - und zwei solche Zeilen desselben Menschen liefen
-- unter dem eindeutigen Index unten sofort in eine Kollision, die dann ausgerechnet das
-- Löschen des Tages scheitern ließe.
alter table participant_has_requirement_for_event
    add column event_day   uuid references event_day on delete cascade,
    add column competition uuid references competition on delete cascade;

-- Bestandsdaten bekommen den ERSTEN Wettkampftag ihrer Veranstaltung eingetragen: was vor
-- dieser Migration abgehakt wurde, wurde zum Beginn der Veranstaltung abgehakt.
--
-- Ausdrücklich in ALLE Zeilen, nicht nur in die zu tagesabhängigen Bedingungen: welcher
-- Schalter später an welcher Bedingung steht, ist zum Migrationszeitpunkt nicht bekannt, und
-- bei ausgeschaltetem per_event_day liest die Auswertung die Spalte ohnehin nicht. So kann
-- der Veranstalter den Schalter später umlegen, ohne dass jemand Altdaten nachpflegen muss.
--
-- Veranstaltungen ohne event_day bleiben null: die Unterabfrage liefert dann nichts, die
-- Zeile behält ihr null. Ein Scheitern wäre hier die falsche Antwort - eine Veranstaltung
-- ohne angelegten Tag ist zulässig, und die Zeile ist mit null so gültig wie zuvor.
-- Der Tie-Break über die id hält das Ergebnis auch dann eindeutig, wenn zwei Tage
-- versehentlich dasselbe Datum tragen.
update participant_has_requirement_for_event phrfe
set event_day = (select ed.id
                 from event_day ed
                 where ed.event = phrfe.event
                 order by ed.date, ed.id
                 limit 1);

-- Der bisherige Primärschlüssel kann die Dimensionen nicht aufnehmen: ein Primärschlüssel
-- duldet keine null-Spalten, und genau null ist hier die Aussage "gilt ohne Tag/Wettkampf".
alter table participant_has_requirement_for_event
    drop constraint participant_has_requirement_for_event_pkey;

-- "nulls not distinct" ist der Kern dieser Migration (Postgres 15+, hier 17). Ohne den
-- Zusatz gilt in einem eindeutigen Index jedes null als von jedem anderen verschieden -
-- dieselbe veranstaltungsweite Erfüllung (event_day null, competition null) ließe sich dann
-- beliebig oft eintragen, und der Schutz, den der alte Primärschlüssel gab, wäre ersatzlos
-- weg. Die beiden Alternativen sind bewusst verworfen: ein Sentinel-Wert bräuchte erfundene
-- Fremdschlüsselzeilen, ein Index über coalesce(...) funktionierte zwar, verlangte aber
-- denselben Ausdruck in jeder einzelnen Abfrage und im Upsert.
alter table participant_has_requirement_for_event
    add constraint participant_has_requirement_for_event_uq
        unique nulls not distinct (participant, event, participant_requirement, event_day, competition);

-- Für das Aufräumen beim Löschen eines Tages bzw. eines Wettkampfs (on delete cascade oben
-- sucht ohne Index sequenziell).
create index on participant_has_requirement_for_event (event_day);
create index on participant_has_requirement_for_event (competition);
