set search_path to ready2race, pg_catalog, public;

-- Der Backfill in V202608101100 hat die alten Adressen wörtlich übernommen -- und die trugen
-- durchweg `www.`. Neue Eingaben faltet `RaceClockerFeed.normalizeUrl` dagegen auf den Apex
-- (https, Host ohne www.), und an genau dieser Zeichenkette hängen die Eindeutigkeit je
-- Veranstaltung und die Entdopplung im Abruf. Eine www-Zeile neben einer Apex-Zeile ist derselbe
-- Feed zweimal je Takt -- genau die Verschwendung, die der Umbau auf benannte Rennen abstellen
-- sollte. Diese Migration zieht den Bestand auf dieselbe Form nach; V202608101100 selbst bleibt
-- unangetastet, ihre Checksum ist in mehreren Datenbanken verankert.
--
-- Nur Adressen, die erkennbar auf raceclocker.com zeigen, werden angefasst. Alles andere -- das
-- dürfte es über `normalizeUrl` nie in die Spalte geschafft haben -- bleibt wörtlich stehen: Eine
-- Datenmigration, die an einem unerwarteten Wert scheitert oder ihn "repariert", ist schlimmer als
-- eine, die ihn liegen lässt.
create temporary table rc_url_normalized on commit drop as
select id,
       event,
       position,
       results_url,
       case
           when results_url ~* '^(https?://)?(www\.)?raceclocker\.com($|[/?#])'
               then regexp_replace(results_url, '^(https?://)?(www\.)?raceclocker\.com',
                                   'https://raceclocker.com', 'i')
           else results_url
           end as normalized_url
from raceclocker_race;

-- Der Kollisionsfall zuerst: Trägt eine Veranstaltung dieselbe Adresse in www- UND Apex-Form,
-- liefe die Normalisierung in den Unique-Constraint (event, results_url). Solche Paare werden
-- vorher auf EINE Zeile zusammengeführt. Es überlebt die mit der kleinsten position -- die steht
-- in der Auswahlliste oben und ist damit die, die der Bediener kennt; die id bricht den
-- theoretischen Gleichstand, damit die Migration überall dieselbe Entscheidung trifft.
create temporary table rc_url_survivor on commit drop as
select distinct on (event, normalized_url) event, normalized_url, id
from rc_url_normalized
order by event, normalized_url, position, id;

create temporary table rc_url_duplicate on commit drop as
select n.id as duplicate_id, s.id as survivor_id
from rc_url_normalized n
         join rc_url_survivor s on s.event = n.event and s.normalized_url = n.normalized_url
where n.id <> s.id;

-- Die Anwahl zeigt womöglich auf das Duplikat. Stumpf gelöscht stünde sie über `on delete set
-- null` plötzlich leer, und der betroffene Wettkampf erbte still die Voreinstellung der
-- Veranstaltung -- ein falscher Feed ohne Fehlermeldung, mitten in einer laufenden Regatta.
-- Deshalb werden alle vier Zeiger erst auf die Überlebende umgehängt.
update event e
set raceclocker_race_qualification = d.survivor_id
from rc_url_duplicate d
where e.raceclocker_race_qualification = d.duplicate_id;

update event e
set raceclocker_race_rounds = d.survivor_id
from rc_url_duplicate d
where e.raceclocker_race_rounds = d.duplicate_id;

update competition c
set raceclocker_race_qualification = d.survivor_id
from rc_url_duplicate d
where c.raceclocker_race_qualification = d.duplicate_id;

update competition c
set raceclocker_race_rounds = d.survivor_id
from rc_url_duplicate d
where c.raceclocker_race_rounds = d.duplicate_id;

delete
from raceclocker_race r using rc_url_duplicate d
where r.id = d.duplicate_id;

-- Erst jetzt, nach der Entdopplung, ist die Normalisierung kollisionsfrei: Je (event,
-- normalized_url) existiert nur noch eine Zeile, und eine Zeile, deren Rohform der Zielform einer
-- anderen gliche, wäre oben in deren Gruppe gefallen. updated_by bleibt unberührt -- es war keine
-- Person, und der Systembenutzer stünde hier für eine Herkunft, die er nicht hat.
update raceclocker_race r
set results_url = n.normalized_url,
    updated_at  = now()
from rc_url_normalized n
where n.id = r.id
  and n.normalized_url <> r.results_url;
