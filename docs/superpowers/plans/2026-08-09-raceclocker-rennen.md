# Benannte RaceClocker-Rennen — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eine Veranstaltung führt beliebig viele benannte RaceClocker-Rennen; Wettkämpfe wählen sie an statt Adressen zu wiederholen, und der automatische Abruf holt je Takt nur die Rennen, die gerade gebraucht werden.

**Architecture:** Neue Tabelle `raceclocker_race` (Kind der Veranstaltung) ersetzt die vier Adress-Spalten auf `event` und `competition`. An ihre Stelle treten je zwei nullable FK-Spalten (Quali-Rennen / Runden-Rennen) mit demselben `coalesce`-Vererbungsmuster wie bisher. `RaceClockerPollService.pollEvent` wird in Phasen zerlegt — auflösen, zuordnen, schreiben —, sodass der Rückfall auf das jeweils andere Rennen erst geholt wird, wenn das angewählte den Lauf nicht enthält.

**Tech Stack:** Kotlin/Ktor, jOOQ (generiert), Flyway, Postgres 17, Maven; React/TypeScript, MUI, react-hook-form-mui, Vite, Vitest.

**Spec:** [docs/superpowers/specs/2026-08-09-raceclocker-rennen-design.md](../specs/2026-08-09-raceclocker-rennen-design.md)

## Global Constraints

- **Arbeitsverzeichnis:** `/Users/thomas/Developer/privat/ready2race/.claude/worktrees/urkunden-vorlagen-speicherort-350ad0`, Branch `claude/raceclocker-polling-optimization-d69666`.
- **`JAVA_HOME` fehlt in dieser Shell, und `/usr/libexec/java_home` findet nichts** — das JDK kommt aus Homebrew und ist nicht nach `/Library/Java/JavaVirtualMachines` verlinkt. Vor jedem Maven-Aufruf exakt so setzen (verifiziert am 2026-08-09, Maven 3.9.6 / OpenJDK 21.0.11):
  ```bash
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
  ```
  `/usr/libexec/java_home -v 21` schlägt hier mit „Unable to locate a Java Runtime" fehl — nicht darauf ausweichen.
- **Datenbanken müssen laufen**, sonst schlägt jede Generierung fehl: `cd backend && docker compose up -d`. Port 7652 ist die Build-Datenbank (jOOQ/Flyway), 7653 die Entwicklungsdatenbank.
- **jOOQ-Klassen sind nicht eingecheckt.** Sie entstehen unter `backend/target/generated-sources/jooq` in der Maven-Phase `generate-sources`, nachdem Flyway die Build-Datenbank migriert hat. Nach jeder Migrationsänderung: `./mvnw generate-sources`.
- **Migrationsnummer:** `V202608091600`. Höchste im Zweig ist `V202608091410`; `V202608091500` ist auf einem parallelen Zweig vergeben. Vor dem Commit von Task 1 mit `ls backend/src/main/resources/db/migration | tail -5` erneut prüfen.
- **Deutsche Texte immer mit echten Umlauten** (ä, ö, ü, ß), nie mit ae/oe/ue/ss. Gilt für Kommentare, Migrationstexte und i18n.
- **Drei Sprachdateien**, alle drei pflegen: `frontend/src/i18n/de/translations.json`, `.../en/translations.json`, `.../da/translations.json`.
- **Kommentare erklären das Warum**, nicht das Was — der Bestand in `app/raceclocker/` ist der Maßstab. Keine Kommentare, die nur den Code nacherzählen.
- **Kein Hinweis auf Claude oder KI** in Commit-Nachrichten. Commit-Nachrichten auf Deutsch, im Stil der Historie (`git log --oneline -10`).
- **`KIO.fail` ohne vorangestelltes `!` ist ein No-Op** und wirkt wie ein stiller Erfolg. Jede neue Fehlerstelle prüfen.
- **Kein `orDie` in Job-Pfaden**, die einen einzelnen Lauf betreffen — ein kaputter Lauf darf den Takt nicht beenden.

---

## Dateiübersicht

**Neu (Backend):**

| Datei | Verantwortung |
|---|---|
| `backend/src/main/resources/db/migration/V202608091600__raceclocker_races.sql` | Tabelle, Anwahl-Spalten, Backfill, Abbau der alten Spalten |
| `.../app/raceclocker/entity/RaceClockerRace.kt` | `RaceClockerRaceDto`, `RaceClockerRaceRequest`, `RaceClockerStartMode`, `RaceClockerRaceRef` |
| `.../app/raceclocker/entity/RaceClockerRaceError.kt` | Fehler der Rennen-Verwaltung |
| `.../app/raceclocker/control/RaceClockerRaceRepo.kt` | Lesen/Schreiben der Rennen |
| `.../app/raceclocker/boundary/RaceClockerRaceService.kt` | Anlegen, Ändern, Löschen, Liste |
| `.../app/raceclocker/boundary/raceClockerRace.kt` | Ktor-Routen unterhalb der Event-Route |
| `.../app/raceclocker/boundary/RaceClockerFeedAssignment.kt` | Reine Zuordnung Feed → Lauf, ohne DB und HTTP |

**Neu (Test):**

| Datei | Verantwortung |
|---|---|
| `backend/src/test/kotlin/.../raceclocker/RaceClockerRaceRepoTest.kt` | Rennen und Anwahl gegen echtes Postgres |
| `backend/src/test/kotlin/.../raceclocker/RaceClockerFeedAssignmentTest.kt` | Zuordnung als reine Funktion |
| `backend/src/test/kotlin/.../raceclocker/RaceClockerFetchPlanTest.kt` | Welche Adressen in Runde 1, welche in Runde 2 |

**Geändert (Backend):** `RaceClockerMatchTarget.kt`, `RaceClockerError.kt`, `RaceClockerPollRepo.kt`, `RaceClockerPollService.kt`, `CompetitionMatchRepo.kt`, `TimingConfigRepo.kt`, `TimingConfigService.kt`, `TimingConfigDto.kt`, `EventTimingConfigDto.kt`, `CompetitionTimingDeviationDto.kt`, `TimingConfigRequest.kt`, `EventTimingConfigRequest.kt`, `event.kt`, `documentation.yaml`.

**Geändert (Frontend):** `EventTimingConfig.tsx`, `eventTimingConfigForm.ts` (+ Test), `CompetitionTimingConfig.tsx`, `timingConfigForm.ts` (+ Test), `executionError.ts`, drei `translations.json`, `api/*.gen.ts` (generiert).

---

## Task 1: Migration und jOOQ-Neugenerierung

**Files:**
- Create: `backend/src/main/resources/db/migration/V202608091600__raceclocker_races.sql`

**Interfaces:**
- Consumes: nichts
- Produces: Tabelle `raceclocker_race` (Spalten `id, event, name, results_url, start_mode, captures_laps, position, created_at, created_by, updated_at, updated_by`); Spalten `raceclocker_race_qualification` und `raceclocker_race_rounds` auf `event` und `competition`. Nach `generate-sources` existieren `RACECLOCKER_RACE` und `RaceclockerRaceRecord` im Paket `de.lambda9.ready2race.backend.database.generated.tables.*`. Die vier alten Adress-Spalten existieren **nicht mehr** — nachfolgende Tasks dürfen sie nicht referenzieren.

- [ ] **Step 1: Migrationsnummer prüfen**

```bash
ls backend/src/main/resources/db/migration | tail -5
```

Erwartet: höchste Nummer ist `V202608091410__check_type_not_in_arena.sql`. Ist bereits eine `V2026080916*` da, die nächste freie Nummer nehmen und in allen folgenden Schritten verwenden.

- [ ] **Step 2: Migration schreiben**

Datei `backend/src/main/resources/db/migration/V202608091600__raceclocker_races.sql`:

```sql
set search_path to ready2race, pg_catalog, public;

-- Benannte RaceClocker-Rennen je Veranstaltung (Entwurf 2026-08-09).
--
-- Bisher gab es genau zwei Adressen: eine für Zeitfahren, eine für Läufe (V202607211200 auf dem
-- Wettkampf, V202608062100 als Voreinstellung auf der Veranstaltung). Eine Regatta fährt aber mehr
-- Rennen als zwei -- bei der Coastal-Rowing-Regatta sind es Timetrials, Langstrecke und
-- Kurzstrecke. Im Zwei-Slot-Modell ließ sich das nur über Wettkampf-Overrides abbilden, und dann
-- trug jeder Wettkampf sein eigenes Adresspaar bei: die Zahl der Abrufe je Takt wuchs mit der Zahl
-- der WETTKÄMPFE statt mit der Zahl der RENNEN.
--
-- Ab hier ist ein Rennen eine eigene Zeile, und Veranstaltung wie Wettkampf zeigen nur noch darauf.
create table raceclocker_race
(
    id            uuid primary key,
    event         uuid      not null references event on delete cascade,
    name          text      not null,
    results_url   text      not null,
    -- INDIVIDUAL = Einzelstarts (Zeitfahren), WAVE = Start in mehreren Läufen. Der Unterschied ist
    -- nicht kosmetisch: Nur Einzelstarts haben in RaceClocker einen echten Countdown, und eine
    -- gemappte Lauf-Spalte kippt ein Rennen selbsttätig in den Wave-Modus. Als Spalte hinterlegt,
    -- damit sich das künftig prüfen lässt, statt es am Renntag am fehlenden Countdown zu merken.
    start_mode    text      not null,
    -- Noch von niemandem gelesen: der Andockpunkt für die Rundenzeiten der Langstrecke, die in
    -- einem eigenen Zyklus kommen. Wie RaceClocker sie im Feed ausliefert, ist noch unbekannt.
    captures_laps boolean   not null default false,
    position      int       not null,
    created_at    timestamp not null,
    created_by    uuid references app_user on delete set null,
    updated_at    timestamp not null,
    updated_by    uuid references app_user on delete set null,
    constraint chk_raceclocker_race_start_mode check (start_mode in ('INDIVIDUAL', 'WAVE')),
    -- Zwei Rennen gleichen Namens wären in einem Auswahlfeld nicht unterscheidbar, und genau am
    -- Renntag ist das der Fehler, der weh tut.
    constraint uq_raceclocker_race_event_name unique (event, name),
    -- Zwei Rennen mit derselben Adresse wären zwei Abrufe für dieselbe Antwort -- genau die
    -- Verschwendung, die diese Änderung beseitigt. Die Eindeutigkeit trägt zusätzlich den Backfill
    -- unten: er verbindet die alten Spalten über (event, results_url) mit den neuen Zeilen.
    constraint uq_raceclocker_race_event_url unique (event, results_url)
);

create index on raceclocker_race (event);

-- Die Anwahl: dasselbe Vererbungsmuster wie zuvor bei den Adressen -- Wettkampf vor Veranstaltung,
-- gelesen per coalesce. `on delete set null`, damit ein gelöschtes Rennen die Anwahl entwertet,
-- statt das Löschen zu blockieren.
alter table event
    add column raceclocker_race_qualification uuid references raceclocker_race on delete set null,
    add column raceclocker_race_rounds        uuid references raceclocker_race on delete set null;

alter table competition
    add column raceclocker_race_qualification uuid references raceclocker_race on delete set null,
    add column raceclocker_race_rounds        uuid references raceclocker_race on delete set null;

-- Backfill. Ziel: Für eine laufende Regatta ändert sich nichts.
--
-- Entdoppelt über die Adresse: Zwei Wettkämpfe mit derselben Override-Adresse teilen sich EIN
-- Rennen, statt zwei gleichlautende zu erzeugen. Das ist der Punkt, an dem die Migration die
-- Schulden tilgt, statt sie ins neue Modell zu übertragen.
with sources as (
    select e.id                          as event_id,
           e.raceclocker_tt_results_url  as url,
           'INDIVIDUAL'                  as start_mode,
           true                          as event_level,
           null::text                    as label
    from event e
    where e.raceclocker_tt_results_url is not null
    union all
    select e.id, e.raceclocker_heats_results_url, 'WAVE', true, null::text
    from event e
    where e.raceclocker_heats_results_url is not null
    union all
    select c.event, c.raceclocker_tt_results_url, 'INDIVIDUAL', false,
           coalesce(cp.short_name, cp.identifier)
    from competition c
             join competition_properties cp on cp.competition = c.id
    where c.raceclocker_tt_results_url is not null
      and c.event is not null
    union all
    select c.event, c.raceclocker_heats_results_url, 'WAVE', false,
           coalesce(cp.short_name, cp.identifier)
    from competition c
             join competition_properties cp on cp.competition = c.id
    where c.raceclocker_heats_results_url is not null
      and c.event is not null
),
deduped as (
    select event_id,
           url,
           -- Dieselbe Adresse in beiden alten Spalten kann nur EIN Rennen sein. WAVE gewinnt, weil
           -- das der Modus ist, in den RaceClocker beim Import mit Lauf-Spalte selbst kippt.
           case when bool_or(start_mode = 'WAVE') then 'WAVE' else 'INDIVIDUAL' end as start_mode,
           bool_or(event_level)                                                     as event_level,
           min(label)                                                               as label
    from sources
    group by event_id, url
),
named as (
    select event_id,
           url,
           start_mode,
           case
               when event_level or label is null
                   then case when start_mode = 'WAVE' then 'Läufe' else 'Zeitfahren' end
               else case when start_mode = 'WAVE' then 'Läufe ' else 'Zeitfahren ' end || label
               end as base_name
    from deduped
),
numbered as (
    select event_id,
           url,
           start_mode,
           base_name,
           -- Zwei Wettkämpfe mit gleichem Kürzel, aber verschiedenen Adressen kollidierten im Namen.
           -- Eine Migration darf nicht an einem Datenzufall scheitern.
           row_number() over (partition by event_id, base_name order by url) as dup,
           row_number() over (partition by event_id order by start_mode, base_name, url) as pos
    from named
)
insert into raceclocker_race (id, event, name, results_url, start_mode, captures_laps, position,
                              created_at, updated_at)
select gen_random_uuid(),
       event_id,
       case when dup = 1 then base_name else base_name || ' (' || dup || ')' end,
       url,
       start_mode,
       false,
       pos::int,
       now(),
       now()
from numbered;

-- Anwahl setzen. Die Verbindung läuft über (event, results_url), das ist oben eindeutig.
update event e
set raceclocker_race_qualification = r.id
from raceclocker_race r
where r.event = e.id
  and r.results_url = e.raceclocker_tt_results_url;

update event e
set raceclocker_race_rounds = r.id
from raceclocker_race r
where r.event = e.id
  and r.results_url = e.raceclocker_heats_results_url;

update competition c
set raceclocker_race_qualification = r.id
from raceclocker_race r
where r.event = c.event
  and r.results_url = c.raceclocker_tt_results_url;

update competition c
set raceclocker_race_rounds = r.id
from raceclocker_race r
where r.event = c.event
  and r.results_url = c.raceclocker_heats_results_url;

-- Erst jetzt: Die alten Spalten haben ihre Schuldigkeit getan.
alter table event
    drop column raceclocker_tt_results_url,
    drop column raceclocker_heats_results_url;

alter table competition
    drop column raceclocker_tt_results_url,
    drop column raceclocker_heats_results_url;
```

- [ ] **Step 3: Datenbanken starten und migrieren**

```bash
cd backend && docker compose up -d && export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./mvnw -q generate-sources
```

Erwartet: Durchlauf ohne Fehler. Bei `Migration checksum mismatch` oder Resten früherer Versuche die Build-Datenbank zurücksetzen: `./mvnw flyway:clean && ./mvnw -q generate-sources`.

- [ ] **Step 4: Generierte Klassen prüfen**

```bash
grep -rn "RACECLOCKER_RACE\b" backend/target/generated-sources/jooq/de/lambda9/ready2race/backend/database/generated/tables/references/Tables.kt | head -3
grep -rn "raceclockerTtResultsUrl" backend/target/generated-sources/jooq | head -3
```

Erwartet: Der erste Befehl findet `RACECLOCKER_RACE`. Der zweite findet **nichts** — die alten Spalten sind weg.

- [ ] **Step 5: Backfill an einem Beispiel prüfen**

```bash
docker exec -i $(docker compose -f backend/docker-compose.yaml ps -q build-db) psql -U developer -d ready2race-build -c "set search_path to ready2race; select name, start_mode, position, results_url from raceclocker_race order by event, position;"
```

Erwartet: Auf einer leeren Build-Datenbank null Zeilen — das ist in Ordnung und belegt nur, dass die Anweisungen syntaktisch laufen. Der echte Beleg folgt in Task 9 gegen den Prod-Abzug.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V202608091600__raceclocker_races.sql
git commit -m "Rennen als eigene Zeile statt zweier fester Adressen"
```

---

## Task 2: Rennen lesen und schreiben (Entity + Repo + DB-Test)

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/entity/RaceClockerRace.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/control/RaceClockerRaceRepo.kt`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/raceclocker/RaceClockerRaceRepoTest.kt`

**Interfaces:**
- Consumes: `RACECLOCKER_RACE`, `RaceclockerRaceRecord` aus Task 1.
- Produces:
  - `enum class RaceClockerStartMode { INDIVIDUAL, WAVE }`
  - `data class RaceClockerRaceDto(id: UUID, name: String, resultsUrl: String, startMode: RaceClockerStartMode, capturesLaps: Boolean, position: Int)`
  - `data class RaceClockerRaceRequest(name: String, resultsUrl: String, startMode: RaceClockerStartMode, capturesLaps: Boolean)` mit `Validatable`-Umsetzung und `companion object { val example }`
  - `data class RaceClockerRaceRef(id: UUID, name: String, resultsUrl: String)`
  - `RaceClockerRaceRepo.getForEvent(eventId: UUID): JIO<List<RaceClockerRaceDto>>`
  - `RaceClockerRaceRepo.nextPosition(eventId: UUID): JIO<Int>`
  - `RaceClockerRaceRepo.belongsToEvent(raceId: UUID, eventId: UUID): JIO<Boolean>`

- [ ] **Step 1: Entity schreiben**

Datei `.../app/raceclocker/entity/RaceClockerRace.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.raceclocker.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidator.notBlank
import java.util.UUID

/**
 * Wie ein RaceClocker-Rennen gestartet wird.
 *
 * Nur [INDIVIDUAL] hat in RaceClocker einen echten Countdown; [WAVE] ist der Modus, in den ein
 * Rennen selbsttätig kippt, sobald beim Import eine Spalte auf „Lauf" gemappt wird. Der Unterschied
 * steht hier, weil der Bediener in ihm denkt — und weil sich daran künftig prüfen lässt, ob das
 * gewählte Startlisten-Preset zum Rennen passt.
 */
enum class RaceClockerStartMode { INDIVIDUAL, WAVE }

data class RaceClockerRaceDto(
    val id: UUID,
    val name: String,
    val resultsUrl: String,
    val startMode: RaceClockerStartMode,
    val capturesLaps: Boolean,
    val position: Int,
)

/**
 * Ein Rennen, so wie ein Lauf es braucht: Adresse zum Holen, Name für die Fehlermeldung.
 *
 * Der Name ist kein Schmuck. „Lauf im Rennen Kurzstrecke nicht gefunden" ist am Renntag brauchbar,
 * eine nackte URL nicht.
 */
data class RaceClockerRaceRef(
    val id: UUID,
    val name: String,
    val resultsUrl: String,
)

data class RaceClockerRaceRequest(
    val name: String,
    val resultsUrl: String,
    val startMode: RaceClockerStartMode,
    val capturesLaps: Boolean,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::name validate notBlank,
        this::resultsUrl validate notBlank,
    )

    companion object {
        val example
            get() = RaceClockerRaceRequest(
                name = "Kurzstrecke",
                resultsUrl = "https://www.raceclocker.com/7c854955",
                startMode = RaceClockerStartMode.WAVE,
                capturesLaps = false,
            )
    }
}
```

Falls die Importpfade der Validierung abweichen: `grep -rn "validate notBlank" backend/src/main/kotlin --include="*.kt" | head -3` zeigt ein funktionierendes Beispiel, dessen Importe übernommen werden.

- [ ] **Step 2: Failing test schreiben**

Datei `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/raceclocker/RaceClockerRaceRepoTest.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.raceclocker

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerRaceRepo
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerStartMode
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceclockerRaceRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.RACECLOCKER_RACE
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Rennen einer Veranstaltung gegen eine echte Datenbank. Reine Funktionen decken die
 * Anwahl-Logik ab; was hier geprüft wird, ist alles, was nur Postgres beantworten kann —
 * Reihenfolge, Abgrenzung zwischen Veranstaltungen, und dass ein gelöschtes Rennen die Anwahl
 * entwertet statt das Löschen zu blockieren.
 */
class RaceClockerRaceRepoTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 14, 10, 0)

    private fun TestComprehensionScope<JEnv>.seedEvent(): UUID {
        val eventId = UUID.randomUUID()
        !EVENT.insert(
            EventRecord(id = eventId, name = "Testregatta", createdAt = now, updatedAt = now)
        )
        return eventId
    }

    private fun TestComprehensionScope<JEnv>.seedRace(
        eventId: UUID,
        name: String,
        url: String,
        startMode: RaceClockerStartMode = RaceClockerStartMode.WAVE,
        position: Int = 1,
    ): UUID {
        val raceId = UUID.randomUUID()
        !RACECLOCKER_RACE.insert(
            RaceclockerRaceRecord(
                id = raceId,
                event = eventId,
                name = name,
                resultsUrl = url,
                startMode = startMode.name,
                capturesLaps = false,
                position = position,
                createdAt = now,
                updatedAt = now,
            )
        )
        return raceId
    }

    @Test
    fun `liefert die Rennen einer Veranstaltung nach Position sortiert`() = testComprehension {
        val eventId = seedEvent()
        seedRace(eventId, "Kurzstrecke", "https://www.raceclocker.com/kurz", position = 2)
        seedRace(eventId, "Timetrials", "https://www.raceclocker.com/tt", RaceClockerStartMode.INDIVIDUAL, position = 1)

        val races = !RaceClockerRaceRepo.getForEvent(eventId)

        assertEquals(listOf("Timetrials", "Kurzstrecke"), races.map { it.name })
        assertEquals(RaceClockerStartMode.INDIVIDUAL, races.first().startMode)
    }

    @Test
    fun `liefert keine Rennen einer fremden Veranstaltung`() = testComprehension {
        val eventId = seedEvent()
        val otherEventId = seedEvent()
        seedRace(otherEventId, "Kurzstrecke", "https://www.raceclocker.com/kurz")

        assertEquals(emptyList(), !RaceClockerRaceRepo.getForEvent(eventId))
    }

    @Test
    fun `nextPosition zaehlt hinter dem letzten Rennen weiter`() = testComprehension {
        val eventId = seedEvent()
        assertEquals(1, !RaceClockerRaceRepo.nextPosition(eventId))

        seedRace(eventId, "Kurzstrecke", "https://www.raceclocker.com/kurz", position = 7)
        assertEquals(8, !RaceClockerRaceRepo.nextPosition(eventId))
    }

    @Test
    fun `belongsToEvent trennt die Veranstaltungen`() = testComprehension {
        val eventId = seedEvent()
        val otherEventId = seedEvent()
        val raceId = seedRace(eventId, "Kurzstrecke", "https://www.raceclocker.com/kurz")

        assertEquals(true, !RaceClockerRaceRepo.belongsToEvent(raceId, eventId))
        assertEquals(false, !RaceClockerRaceRepo.belongsToEvent(raceId, otherEventId))
    }
}
```

Falls `testComprehension` eine andere Signatur hat, `RaceClockerPollRepoTest.kt` als Vorlage lesen und angleichen — es liegt im selben Verzeichnis und nutzt genau dieses Gerüst.

- [ ] **Step 3: Test laufen lassen, Fehlschlag bestätigen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && cd backend && ./mvnw -q test -Dtest=RaceClockerRaceRepoTest
```

Erwartet: Übersetzungsfehler „Unresolved reference: RaceClockerRaceRepo".

- [ ] **Step 4: Repo schreiben**

Datei `.../app/raceclocker/control/RaceClockerRaceRepo.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.raceclocker.control

import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceDto
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerStartMode
import de.lambda9.ready2race.backend.database.generated.tables.references.RACECLOCKER_RACE
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.UUID

object RaceClockerRaceRepo {

    fun getForEvent(eventId: UUID) = Jooq.query {
        select(
            RACECLOCKER_RACE.ID,
            RACECLOCKER_RACE.NAME,
            RACECLOCKER_RACE.RESULTS_URL,
            RACECLOCKER_RACE.START_MODE,
            RACECLOCKER_RACE.CAPTURES_LAPS,
            RACECLOCKER_RACE.POSITION,
        )
            .from(RACECLOCKER_RACE)
            .where(RACECLOCKER_RACE.EVENT.eq(eventId))
            .orderBy(RACECLOCKER_RACE.POSITION, RACECLOCKER_RACE.NAME)
            .fetch {
                RaceClockerRaceDto(
                    // Im Schema not null; die Projektion verliert nur die Garantie.
                    id = it[RACECLOCKER_RACE.ID]!!,
                    name = it[RACECLOCKER_RACE.NAME]!!,
                    resultsUrl = it[RACECLOCKER_RACE.RESULTS_URL]!!,
                    startMode = RaceClockerStartMode.valueOf(it[RACECLOCKER_RACE.START_MODE]!!),
                    capturesLaps = it[RACECLOCKER_RACE.CAPTURES_LAPS]!!,
                    position = it[RACECLOCKER_RACE.POSITION]!!,
                )
            }
    }

    /** Ein neues Rennen landet hinten. Reihenfolge ändern ist ein eigener Vorgang, kein Nebeneffekt. */
    fun nextPosition(eventId: UUID) = Jooq.query {
        (select(DSL.max(RACECLOCKER_RACE.POSITION))
            .from(RACECLOCKER_RACE)
            .where(RACECLOCKER_RACE.EVENT.eq(eventId))
            .fetchOne()
            ?.value1() ?: 0) + 1
    }

    /**
     * Ob dieses Rennen zu dieser Veranstaltung gehört.
     *
     * Der Fremdschlüssel allein verhindert nicht, dass ein Wettkampf ein Rennen einer ANDEREN
     * Veranstaltung anwählt — dafür bräuchte es einen zusammengesetzten Schlüssel, der den übrigen
     * Tabellen dieses Projekts fremd wäre. Also prüft der Service, und das ist seine Frage.
     */
    fun belongsToEvent(raceId: UUID, eventId: UUID) = Jooq.query {
        fetchExists(
            selectOne()
                .from(RACECLOCKER_RACE)
                .where(RACECLOCKER_RACE.ID.eq(raceId))
                .and(RACECLOCKER_RACE.EVENT.eq(eventId))
        )
    }
}
```

- [ ] **Step 5: Test laufen lassen, Erfolg bestätigen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && cd backend && ./mvnw -q test -Dtest=RaceClockerRaceRepoTest
```

Erwartet: 4 Tests, alle grün.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/entity/RaceClockerRace.kt \
        backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/control/RaceClockerRaceRepo.kt \
        backend/src/test/kotlin/de/lambda9/ready2race/backend/app/raceclocker/RaceClockerRaceRepoTest.kt
git commit -m "Rennen einer Veranstaltung lesen"
```

---

## Task 3: Rennen verwalten (Service, Routen, OpenAPI, SDK)

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/entity/RaceClockerRaceError.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/boundary/RaceClockerRaceService.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/boundary/raceClockerRace.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/event/boundary/event.kt:99`
- Modify: `backend/src/main/resources/openapi/documentation.yaml`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/calls/responses/ErrorCode.kt`

**Interfaces:**
- Consumes: `RaceClockerRaceRepo`, `RaceClockerRaceDto`, `RaceClockerRaceRequest`, `RaceClockerStartMode` aus Task 2; `RaceClockerFeed.normalizeUrl` aus `.../raceclocker/control/RaceClockerFeed.kt`.
- Produces: Endpunkte `GET|POST /event/{eventId}/raceclocker-race` und `PUT|DELETE /event/{eventId}/raceclocker-race/{raceId}`; generierte Frontend-Funktionen `getRaceClockerRaces`, `addRaceClockerRace`, `updateRaceClockerRace`, `deleteRaceClockerRace` und der Typ `RaceClockerRaceDto` in `frontend/src/api/types.gen.ts`.

- [ ] **Step 1: Fehler schreiben**

Datei `.../app/raceclocker/entity/RaceClockerRaceError.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.raceclocker.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.*

sealed interface RaceClockerRaceError : ServiceError {

    data object NotFound : RaceClockerRaceError

    /** Zwei Rennen gleichen Namens wären in einem Auswahlfeld nicht unterscheidbar. */
    data object NameTaken : RaceClockerRaceError

    /**
     * Zwei Rennen mit derselben Adresse wären zwei Abrufe für dieselbe Antwort — genau die
     * Verschwendung, die diese Änderung beseitigt.
     */
    data object UrlTaken : RaceClockerRaceError

    override fun respond(): ApiError = when (this) {
        NotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "RaceClocker race not found",
        )

        NameTaken -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "A RaceClocker race with this name already exists for this event",
            errorCode = ErrorCode.RACECLOCKER_RACE_NAME_TAKEN,
        )

        UrlTaken -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "A RaceClocker race with this results URL already exists for this event",
            errorCode = ErrorCode.RACECLOCKER_RACE_URL_TAKEN,
        )
    }
}
```

- [ ] **Step 2: ErrorCodes ergänzen**

In `backend/src/main/kotlin/de/lambda9/ready2race/backend/calls/responses/ErrorCode.kt` neben den bestehenden `RACECLOCKER_*`-Einträgen ergänzen:

```kotlin
RACECLOCKER_RACE_NAME_TAKEN,
RACECLOCKER_RACE_URL_TAKEN,
```

Vorher `grep -n "RACECLOCKER" backend/src/main/kotlin/de/lambda9/ready2race/backend/calls/responses/ErrorCode.kt` lesen und die genaue Schreibweise/Trennzeichen der Aufzählung übernehmen.

- [ ] **Step 3: Service schreiben**

Datei `.../app/raceclocker/boundary/RaceClockerRaceService.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.raceclocker.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerFeed
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerRaceRepo
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceDto
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceError
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceRequest
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceclockerRaceRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.RACECLOCKER_RACE
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.UUID

object RaceClockerRaceService {

    fun getRaces(eventId: UUID): App<ServiceError, ApiResponse.ListDto<RaceClockerRaceDto>> =
        KIO.comprehension {
            val races = !RaceClockerRaceRepo.getForEvent(eventId).orDie()
            KIO.ok(ApiResponse.ListDto(races))
        }

    fun addRace(
        eventId: UUID,
        userId: UUID,
        request: RaceClockerRaceRequest,
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {
        // Normalisiert gespeichert (Schema ergänzt, http auf https gehoben), damit der Tab hinterher
        // zeigt, was der Abruf tatsächlich anfragt — dieselbe Behandlung wie zuvor an den beiden
        // Adressfeldern. Die Host-Allowlist darin ist zugleich das, was diesen Endpunkt davon
        // abhält, ein SSRF-Hebel zu sein: die Adresse kommt vom Bediener.
        val url = (!RaceClockerFeed.normalizeUrl(request.resultsUrl.trim())).toString()
        val name = request.name.trim()

        !ensureFree(eventId, name, url, exceptRaceId = null)

        val position = !RaceClockerRaceRepo.nextPosition(eventId).orDie()
        val raceId = UUID.randomUUID()
        val now = LocalDateTime.now()

        !RACECLOCKER_RACE.insert(
            RaceclockerRaceRecord(
                id = raceId,
                event = eventId,
                name = name,
                resultsUrl = url,
                startMode = request.startMode.name,
                capturesLaps = request.capturesLaps,
                position = position,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId,
            )
        ).orDie()

        KIO.ok(ApiResponse.Created(raceId))
    }

    fun updateRace(
        eventId: UUID,
        raceId: UUID,
        userId: UUID,
        request: RaceClockerRaceRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        val url = (!RaceClockerFeed.normalizeUrl(request.resultsUrl.trim())).toString()
        val name = request.name.trim()

        !ensureFree(eventId, name, url, exceptRaceId = raceId)

        !RACECLOCKER_RACE.update({
            this.name = name
            resultsUrl = url
            startMode = request.startMode.name
            capturesLaps = request.capturesLaps
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }) {
            ID.eq(raceId).and(EVENT.eq(eventId))
        }.orDie().onNullFail { RaceClockerRaceError.NotFound }

        noData
    }

    /**
     * Löschen entwertet die Anwahl, statt sie zu blockieren (`on delete set null` in der Migration).
     * Ein Wettkampf, der auf das gelöschte Rennen zeigte, erbt danach wieder die Voreinstellung der
     * Veranstaltung — und ein Lauf ohne jedes Rennen wird vom Abruf still übersprungen.
     */
    fun deleteRace(eventId: UUID, raceId: UUID): App<ServiceError, ApiResponse.NoData> =
        KIO.comprehension {
            val deleted = !RACECLOCKER_RACE.delete { ID.eq(raceId).and(EVENT.eq(eventId)) }.orDie()
            if (deleted == 0) return@comprehension KIO.fail(RaceClockerRaceError.NotFound)
            noData
        }

    /** Name und Adresse sind je Veranstaltung eindeutig; beides fällt hier auf, nicht erst als 500er. */
    private fun ensureFree(
        eventId: UUID,
        name: String,
        url: String,
        exceptRaceId: UUID?,
    ): App<ServiceError, Unit> = KIO.comprehension {
        val races = !RaceClockerRaceRepo.getForEvent(eventId).orDie()
        val others = races.filter { it.id != exceptRaceId }

        if (others.any { it.name.equals(name, ignoreCase = true) }) {
            return@comprehension KIO.fail(RaceClockerRaceError.NameTaken)
        }
        if (others.any { it.resultsUrl == url }) {
            return@comprehension KIO.fail(RaceClockerRaceError.UrlTaken)
        }
        KIO.ok(Unit)
    }
}
```

Falls `ApiResponse.ListDto` oder `ApiResponse.Created` anders heißen: `grep -n "class ListDto\|class Created" backend/src/main/kotlin/de/lambda9/ready2race/backend/calls/responses/ApiResponse.kt` und die vorhandenen Namen verwenden.

- [ ] **Step 4: Routen schreiben**

Datei `.../app/raceclocker/boundary/raceClockerRace.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.raceclocker.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

/** Die RaceClocker-Rennen einer Veranstaltung — unterhalb der Event-Route zu mounten. */
fun Route.raceClockerRace() {
    route("/raceclocker-race") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)

                RaceClockerRaceService.getRaces(eventId)
            }
        }
        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)

                val body = !receiveKIO(RaceClockerRaceRequest.example)
                RaceClockerRaceService.addRace(eventId, user.id!!, body)
            }
        }
        route("/{raceId}") {
            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val raceId = !pathParam("raceId", uuid)

                    val body = !receiveKIO(RaceClockerRaceRequest.example)
                    RaceClockerRaceService.updateRace(eventId, raceId, user.id!!, body)
                }
            }
            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val raceId = !pathParam("raceId", uuid)

                    RaceClockerRaceService.deleteRace(eventId, raceId)
                }
            }
        }
    }
}
```

- [ ] **Step 5: Route einhängen**

In `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/event/boundary/event.kt` direkt neben `eventTimingConfig()` (Zeile 99) ergänzen:

```kotlin
            eventTimingConfig()
            raceClockerRace()
```

Import ergänzen: `import de.lambda9.ready2race.backend.app.raceclocker.boundary.raceClockerRace`.

- [ ] **Step 6: OpenAPI ergänzen**

In `backend/src/main/resources/openapi/documentation.yaml` direkt **vor** `  /event/{eventId}/timing-config:` (Zeile 1590) einfügen:

```yaml
  /event/{eventId}/raceclocker-race:
    parameters:
      - $ref: '#/components/parameters/eventId'
    get:
      operationId: getRaceClockerRaces
      description: The named RaceClocker races of this event - one per race in the timing system.
      responses:
        200:
          description: RaceClocker races successfully retrieved
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/RaceClockerRaceDto'
        400:
          $ref: '#/components/responses/400'
        401:
          $ref: '#/components/responses/401'
        403:
          $ref: '#/components/responses/403'
        404:
          $ref: '#/components/responses/404'
        500:
          $ref: '#/components/responses/500'
    post:
      operationId: addRaceClockerRace
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/RaceClockerRaceRequest'
      responses:
        201:
          $ref: '#/components/responses/201'
        400:
          $ref: '#/components/responses/400'
        401:
          $ref: '#/components/responses/401'
        403:
          $ref: '#/components/responses/403'
        409:
          $ref: '#/components/responses/409'
        422:
          $ref: '#/components/responses/422'
        500:
          $ref: '#/components/responses/500'
  /event/{eventId}/raceclocker-race/{raceId}:
    parameters:
      - $ref: '#/components/parameters/eventId'
      - name: raceId
        in: path
        required: true
        schema:
          type: string
          format: uuid
    put:
      operationId: updateRaceClockerRace
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/RaceClockerRaceRequest'
      responses:
        204:
          $ref: '#/components/responses/204'
        400:
          $ref: '#/components/responses/400'
        401:
          $ref: '#/components/responses/401'
        403:
          $ref: '#/components/responses/403'
        404:
          $ref: '#/components/responses/404'
        409:
          $ref: '#/components/responses/409'
        422:
          $ref: '#/components/responses/422'
        500:
          $ref: '#/components/responses/500'
    delete:
      operationId: deleteRaceClockerRace
      responses:
        204:
          $ref: '#/components/responses/204'
        400:
          $ref: '#/components/responses/400'
        401:
          $ref: '#/components/responses/401'
        403:
          $ref: '#/components/responses/403'
        404:
          $ref: '#/components/responses/404'
        500:
          $ref: '#/components/responses/500'
```

Existiert `#/components/responses/409` nicht, mit `grep -n "    409:" backend/src/main/resources/openapi/documentation.yaml | head -2` prüfen und andernfalls die 409-Zeilen weglassen.

Im `components.schemas`-Block neben `CompetitionTimingDeviationDto` (etwa Zeile 12660) ergänzen:

```yaml
    RaceClockerRaceDto:
      type: object
      required: [id, name, resultsUrl, startMode, capturesLaps, position]
      properties:
        id:
          type: string
          format: uuid
        name:
          type: string
        resultsUrl:
          type: string
        startMode:
          $ref: '#/components/schemas/RaceClockerStartMode'
        capturesLaps:
          type: boolean
        position:
          type: integer
    RaceClockerRaceRequest:
      type: object
      required: [name, resultsUrl, startMode, capturesLaps]
      properties:
        name:
          type: string
        resultsUrl:
          type: string
        startMode:
          $ref: '#/components/schemas/RaceClockerStartMode'
        capturesLaps:
          type: boolean
    RaceClockerStartMode:
      type: string
      enum: [INDIVIDUAL, WAVE]
```

- [ ] **Step 7: Übersetzen und SDK erzeugen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && cd backend && ./mvnw -q compile
```

Erwartet: Übersetzung ohne Fehler.

```bash
cd frontend && npm run generate
```

Erwartet: `frontend/src/api/sdk.gen.ts` enthält danach `getRaceClockerRaces`. Prüfen:

```bash
grep -n "getRaceClockerRaces\|RaceClockerRaceDto" frontend/src/api/sdk.gen.ts frontend/src/api/types.gen.ts | head -5
```

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/ \
        backend/src/main/kotlin/de/lambda9/ready2race/backend/app/event/boundary/event.kt \
        backend/src/main/kotlin/de/lambda9/ready2race/backend/calls/responses/ErrorCode.kt \
        backend/src/main/resources/openapi/documentation.yaml \
        frontend/src/api/
git commit -m "Rennen einer Veranstaltung anlegen, ändern und löschen"
```

---

## Task 4: Anwahl statt Adressen im Abruf-Ziel

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/entity/RaceClockerMatchTarget.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/entity/RaceClockerError.kt:33`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/control/RaceClockerPollRepo.kt:57-129`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/control/CompetitionMatchRepo.kt:54-82`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/boundary/CompetitionExecutionService.kt:1043`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/raceclocker/RaceClockerMatchTargetTest.kt`

**Interfaces:**
- Consumes: `RaceClockerRaceRef` aus Task 2.
- Produces: `RaceClockerMatchTarget(waveName: String?, isQualification: Boolean, qualificationRace: RaceClockerRaceRef?, roundsRace: RaceClockerRaceRef?)` mit den abgeleiteten Werten `race`, `alternateRace`, `resultsUrl`, `alternateResultsUrl`, `candidateUrls`, `candidateRaceNames`. `RaceClockerError.MatchNotInFeed(urls: List<String>, raceNames: List<String>)`.

- [ ] **Step 1: Failing test schreiben**

`backend/src/test/kotlin/de/lambda9/ready2race/backend/app/raceclocker/RaceClockerMatchTargetTest.kt` vollständig ersetzen:

```kotlin
package de.lambda9.ready2race.backend.app.raceclocker

import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceRef
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Welches Rennen für einen Lauf gilt — und welches der Rückfall ist.
 *
 * Die Runde entscheidet, welches Rennen ZUERST versucht wird. Sie ist eine Angabe, keine Garantie:
 * Eine als Zeitfahren gefahrene, aber nicht als Qualifikation markierte Runde findet ihren Lauf im
 * anderen Rennen. Deshalb bleibt der Rückfall — nur wird er ab jetzt erst bei Bedarf geholt.
 */
class RaceClockerMatchTargetTest {

    private val timeTrials = RaceClockerRaceRef(UUID.randomUUID(), "Timetrials", "https://raceclocker.com/tt")
    private val shortCourse = RaceClockerRaceRef(UUID.randomUUID(), "Kurzstrecke", "https://raceclocker.com/kurz")

    private fun target(
        isQualification: Boolean,
        qualificationRace: RaceClockerRaceRef? = timeTrials,
        roundsRace: RaceClockerRaceRef? = shortCourse,
    ) = RaceClockerMatchTarget(
        waveName = "Lauf 1",
        isQualification = isQualification,
        qualificationRace = qualificationRace,
        roundsRace = roundsRace,
    )

    @Test
    fun `eine Qualifikationsrunde beginnt beim Zeitfahren-Rennen`() {
        val t = target(isQualification = true)
        assertEquals(timeTrials.resultsUrl, t.resultsUrl)
        assertEquals(shortCourse.resultsUrl, t.alternateResultsUrl)
        assertEquals(listOf(timeTrials.resultsUrl, shortCourse.resultsUrl), t.candidateUrls)
        assertEquals(listOf("Timetrials", "Kurzstrecke"), t.candidateRaceNames)
    }

    @Test
    fun `jede andere Runde beginnt beim Laeufe-Rennen`() {
        val t = target(isQualification = false)
        assertEquals(shortCourse.resultsUrl, t.resultsUrl)
        assertEquals(timeTrials.resultsUrl, t.alternateResultsUrl)
    }

    @Test
    fun `ohne Quali-Anwahl bleibt nur das Laeufe-Rennen`() {
        val t = target(isQualification = true, qualificationRace = null)
        assertEquals(shortCourse.resultsUrl, t.resultsUrl)
        assertNull(t.alternateResultsUrl)
        assertEquals(listOf(shortCourse.resultsUrl), t.candidateUrls)
    }

    @Test
    fun `ohne jede Anwahl gibt es nichts zu holen`() {
        val t = target(isQualification = false, qualificationRace = null, roundsRace = null)
        assertNull(t.resultsUrl)
        assertEquals(emptyList(), t.candidateUrls)
    }

    @Test
    fun `dasselbe Rennen fuer beides wird nur einmal geholt`() {
        val t = target(isQualification = true, qualificationRace = shortCourse, roundsRace = shortCourse)
        assertEquals(listOf(shortCourse.resultsUrl), t.candidateUrls)
        assertEquals(listOf("Kurzstrecke"), t.candidateRaceNames)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && cd backend && ./mvnw -q test -Dtest=RaceClockerMatchTargetTest
```

Erwartet: Übersetzungsfehler „No value passed for parameter 'timeTrialUrl'" oder „Unresolved reference: qualificationRace".

- [ ] **Step 3: `RaceClockerMatchTarget` umstellen**

Datei vollständig ersetzen:

```kotlin
package de.lambda9.ready2race.backend.app.raceclocker.entity

/**
 * Wo ein Lauf in RaceClocker zu finden ist.
 *
 * Eine Veranstaltung führt benannte Rennen; Veranstaltung und Wettkampf wählen daraus je eines für
 * die Qualifikationsrunden und eines für alle übrigen Runden ([qualificationRace] /
 * [roundsRace]). [isQualification] entscheidet, welches für DIESEN Lauf gilt.
 *
 * Diese Angabe ist eine Angabe, keine Garantie: Nichts hindert daran, eine als Zeitfahren gefahrene
 * Runde nicht als Qualifikation zu markieren. Deshalb bleibt das jeweils andere Rennen der Rückfall
 * — geholt wird es allerdings erst, wenn der Lauf im angewählten nicht auftaucht.
 */
data class RaceClockerMatchTarget(
    /**
     * Der Laufname plus die geplante Startzeit (siehe
     * [de.lambda9.ready2race.backend.app.competitionExecution.entity.WaveName]), exportiert als
     * RaceClocker-Wellenname. Nur für Startlisten ohne Lauf-Kennung nötig.
     */
    val waveName: String?,
    val isQualification: Boolean,
    val qualificationRace: RaceClockerRaceRef?,
    val roundsRace: RaceClockerRaceRef?,
) {
    val race: RaceClockerRaceRef? get() = if (isQualification) qualificationRace else roundsRace

    val alternateRace: RaceClockerRaceRef? get() = if (isQualification) roundsRace else qualificationRace

    val resultsUrl: String? get() = race?.resultsUrl

    /** Null, wenn es kein anderes Rennen gibt — oder wenn beide Anwahlen dasselbe Rennen meinen. */
    val alternateResultsUrl: String? get() = alternateRace?.resultsUrl?.takeIf { it != resultsUrl }

    /**
     * Angewähltes Rennen zuerst, damit eine richtige Anwahl genau einen Abruf kostet. Entdoppelt:
     * Zeigen beide Anwahlen auf dasselbe Rennen, wird es nicht zweimal geholt.
     */
    val candidateUrls: List<String> get() = listOfNotNull(resultsUrl, alternateResultsUrl)

    /** Dieselbe Reihenfolge wie [candidateUrls], aber lesbar — für Fehlermeldungen am Renntag. */
    val candidateRaceNames: List<String>
        get() = listOfNotNull(race, alternateRace.takeIf { it?.resultsUrl != resultsUrl })
            .map { it.name }
}
```

- [ ] **Step 4: Fehlermeldung um die Namen erweitern**

In `RaceClockerError.kt` die Variante ändern:

```kotlin
    /**
     * None of the configured races contains a row for any team of this match. Either the start list
     * for this heat has not been imported into RaceClocker yet, or it was exported before the round
     * was re-created and carries identifiers that no longer exist.
     *
     * [raceNames] rides along because a race name is what an operator can act on at the regatta; a
     * bare URL is not.
     */
    data class MatchNotInFeed(val urls: List<String>, val raceNames: List<String>) : RaceClockerError
```

und den `respond()`-Zweig:

```kotlin
        is MatchNotInFeed -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No entries for this heat in the RaceClocker feed",
            details = mapOf("urls" to urls, "races" to raceNames),
            errorCode = ErrorCode.RACECLOCKER_MATCH_NOT_IN_FEED,
        )
```

In `CompetitionExecutionService.kt:1043` den Aufruf anpassen:

```kotlin
        if (rowsByTeam.isEmpty()) return@comprehension KIO.fail(
            RaceClockerError.MatchNotInFeed(target.candidateUrls, target.candidateRaceNames)
        )
```

In `RaceClockerErrorTest.kt` die beiden Vorkommen von `MatchNotInFeed(listOf("https://raceclocker.com/xxxx"))` auf `MatchNotInFeed(listOf("https://raceclocker.com/xxxx"), listOf("Kurzstrecke"))` ändern.

- [ ] **Step 5: `RaceClockerPollRepo.getCandidates` umstellen**

In `RaceClockerPollRepo.kt` den Block ab Zeile 57 ersetzen. Die beiden Rennen werden über zwei eigene Join-Aliase angebunden, weil dieselbe Tabelle zweimal gebraucht wird:

```kotlin
    fun getCandidates(eventId: UUID) = Jooq.query {
        // Die Anwahl erbt wie zuvor die Adressen: Wettkampf vor Veranstaltung. Alias-Fallen aus der
        // alten Fassung entfallen, weil hier keine coalesce-Ausdrücke mehr in der SELECT-Liste
        // stehen - die Rennen kommen aus zwei getrennten Joins.
        val qualiRace = RACECLOCKER_RACE.`as`("quali_race")
        val roundsRace = RACECLOCKER_RACE.`as`("rounds_race")
        val timingSystem = DSL.coalesce(COMPETITION.TIMING_SYSTEM, EVENT.TIMING_SYSTEM)
        val qualiRaceId = DSL.coalesce(
            COMPETITION.RACECLOCKER_RACE_QUALIFICATION,
            EVENT.RACECLOCKER_RACE_QUALIFICATION,
        )
        val roundsRaceId = DSL.coalesce(
            COMPETITION.RACECLOCKER_RACE_ROUNDS,
            EVENT.RACECLOCKER_RACE_ROUNDS,
        )

        select(
            COMPETITION_MATCH.COMPETITION_SETUP_MATCH,
            COMPETITION_MATCH.START_TIME,
            COMPETITION_MATCH.ACTIVATED_AT,
            COMPETITION_MATCH.STARTED_AT,
            COMPETITION_SETUP_MATCH.NAME,
            COMPETITION_SETUP_ROUND.IS_QUALIFICATION,
            COMPETITION.ID.`as`("competition_id"),
            qualiRace.ID,
            qualiRace.NAME,
            qualiRace.RESULTS_URL,
            roundsRace.ID,
            roundsRace.NAME,
            roundsRace.RESULTS_URL,
        )
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .join(EVENT).on(COMPETITION.EVENT.eq(EVENT.ID))
            .leftJoin(qualiRace).on(qualiRace.ID.eq(qualiRaceId))
            .leftJoin(roundsRace).on(roundsRace.ID.eq(roundsRaceId))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(COMPETITION_MATCH.FINISHED_AT.isNull)
            .and(COMPETITION_MATCH.RACECLOCKER_AUTO_PAUSED_AT.isNull)
            .and(timingSystem.eq(TimingSystem.RACECLOCKER.name))
            .and(DSL.or(qualiRaceId.isNotNull, roundsRaceId.isNotNull))
            .andNotExists(
                DSL.selectOne()
                    .from(EVENT_SCHEDULE_SLOT)
                    .where(EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH.eq(COMPETITION_MATCH.COMPETITION_SETUP_MATCH))
                    .and(EVENT_SCHEDULE_SLOT.SKIPPED_AT.isNotNull)
            )
            .fetch {
                RaceClockerPollCandidate(
                    matchId = it[COMPETITION_MATCH.COMPETITION_SETUP_MATCH]!!,
                    competitionId = it["competition_id", UUID::class.java],
                    startTime = it[COMPETITION_MATCH.START_TIME],
                    activatedAt = it[COMPETITION_MATCH.ACTIVATED_AT],
                    startedAt = it[COMPETITION_MATCH.STARTED_AT],
                    target = RaceClockerMatchTarget(
                        waveName = WaveName.format(it[COMPETITION_SETUP_MATCH.NAME], it[COMPETITION_MATCH.START_TIME]),
                        isQualification = it[COMPETITION_SETUP_ROUND.IS_QUALIFICATION] == true,
                        qualificationRace = it[qualiRace.ID]?.let { id ->
                            RaceClockerRaceRef(id, it[qualiRace.NAME]!!, it[qualiRace.RESULTS_URL]!!)
                        },
                        roundsRace = it[roundsRace.ID]?.let { id ->
                            RaceClockerRaceRef(id, it[roundsRace.NAME]!!, it[roundsRace.RESULTS_URL]!!)
                        },
                    ),
                )
            }
    }
```

Importe ergänzen: `RaceClockerRaceRef`. Der lange Kommentar über die Alias-Falle (Zeilen 58–67 der alten Fassung) entfällt mit den coalesce-Aliassen — er beschrieb ein Problem, das es nicht mehr gibt.

Sollte das innere `it` im `?.let { id -> ... }` den äußeren Record verdecken, den äußeren Record vorher an eine Variable binden (`.fetch { record -> ... }`).

- [ ] **Step 6: `CompetitionMatchRepo.getForRaceClockerPull` umstellen**

Dieselbe Umstellung in `CompetitionMatchRepo.kt:54-82`: zwei `RACECLOCKER_RACE`-Aliase, zwei `leftJoin` über die coalesce-Anwahl, Projektion auf `RaceClockerRaceRef`. Vorher `sed -n '40,90p' backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/control/CompetitionMatchRepo.kt` lesen, weil die umgebende Join-Kette dort eine andere ist.

- [ ] **Step 7: Alles übersetzen und die Tests laufen lassen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && cd backend && ./mvnw -q test
```

Erwartet: Alle Tests grün. `RaceClockerPollRepoTest` schlägt hier fehl, weil sein `seed` noch die alten Spalten setzt — das ist der nächste Schritt.

- [ ] **Step 8: `RaceClockerPollRepoTest` auf Rennen umstellen**

In `RaceClockerPollRepoTest.kt`:
- Die Parameter `competitionHeatsUrl`, `competitionTimeTrialUrl`, `eventHeatsResultsUrl`, `eventTimeTrialResultsUrl` des `seed` durch `eventQualiRaceId`/`eventRoundsRaceId`/`competitionQualiRaceId`/`competitionRoundsRaceId` ersetzen.
- Vor `EVENT.insert` die benötigten `RACECLOCKER_RACE`-Zeilen anlegen (Muster aus `RaceClockerRaceRepoTest.seedRace` in Task 2).
- In `EventRecord` und `CompetitionRecord` die alten URL-Felder durch `raceclockerRaceQualification` / `raceclockerRaceRounds` ersetzen.
- Zusätzlicher Testfall:

```kotlin
    @Test
    fun `ein geloeschtes Rennen entwertet die Anwahl, ohne den Lauf zu verlieren`() = testComprehension {
        val (eventId, _) = seed()
        !RACECLOCKER_RACE.delete { EVENT.eq(eventId) }

        // Ohne jede Anwahl fällt der Lauf aus der Kandidatenmenge - der Job überspringt ihn still,
        // statt am fehlenden Rennen zu scheitern.
        assertEquals(emptyList(), !RaceClockerPollRepo.getCandidates(eventId))
    }
```

- [ ] **Step 9: Tests laufen lassen, Erfolg bestätigen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && cd backend && ./mvnw -q test
```

Erwartet: alle grün.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/kotlin backend/src/test/kotlin
git commit -m "Läufe wählen ihr Rennen an, statt zwei Adressen zu erben"
```

---

## Task 5: Zuordnung als reine Funktion

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/boundary/RaceClockerFeedAssignment.kt`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/raceclocker/RaceClockerFetchPlanTest.kt`

**Interfaces:**
- Consumes: `RaceClockerMatchTarget` aus Task 4, `RaceClockerFeedRow`.
- Produces:
  - `RaceClockerFeedAssignment.primaryUrls(targets: List<RaceClockerMatchTarget>): List<String>`
  - `RaceClockerFeedAssignment.fallbackUrls(unresolved: List<RaceClockerMatchTarget>, alreadyFetched: Set<String>): List<String>`

Beides sind reine Funktionen ohne DB und HTTP — sie beantworten allein, **welche** Adressen ein Takt anfragt.

- [ ] **Step 1: Failing test schreiben**

Datei `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/raceclocker/RaceClockerFetchPlanTest.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.raceclocker

import de.lambda9.ready2race.backend.app.raceclocker.boundary.RaceClockerFeedAssignment
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceRef
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Was ein Takt tatsächlich anfragt.
 *
 * Das ist der Kern dieser Änderung: Bisher holte der Job für jeden beobachteten Lauf BEIDE Adressen
 * - die angewählte und den Rückfall. Bei einer Regatta, die nur Läufe fährt, war damit jeder zweite
 * Abruf überflüssig, dauerhaft, im Fünf-Sekunden-Takt. Diese Tests halten fest, dass die zweite
 * Runde nur noch für Läufe stattfindet, die in ihrem Rennen nicht gefunden wurden.
 */
class RaceClockerFetchPlanTest {

    private val timeTrials = RaceClockerRaceRef(UUID.randomUUID(), "Timetrials", "https://raceclocker.com/tt")
    private val shortCourse = RaceClockerRaceRef(UUID.randomUUID(), "Kurzstrecke", "https://raceclocker.com/kurz")
    private val longCourse = RaceClockerRaceRef(UUID.randomUUID(), "Langstrecke", "https://raceclocker.com/lang")

    private fun target(
        isQualification: Boolean = false,
        qualificationRace: RaceClockerRaceRef? = timeTrials,
        roundsRace: RaceClockerRaceRef? = shortCourse,
    ) = RaceClockerMatchTarget("Lauf 1", isQualification, qualificationRace, roundsRace)

    @Test
    fun `Runde 1 holt nur die angewaehlten Rennen`() {
        val targets = listOf(target(), target(), target(roundsRace = longCourse))

        assertEquals(
            listOf(shortCourse.resultsUrl, longCourse.resultsUrl),
            RaceClockerFeedAssignment.primaryUrls(targets),
        )
    }

    @Test
    fun `Runde 1 holt dieselbe Adresse nur einmal`() {
        val targets = List(8) { target() }

        assertEquals(listOf(shortCourse.resultsUrl), RaceClockerFeedAssignment.primaryUrls(targets))
    }

    @Test
    fun `Laeufe ohne Anwahl tragen nichts bei`() {
        val targets = listOf(target(qualificationRace = null, roundsRace = null))

        assertEquals(emptyList(), RaceClockerFeedAssignment.primaryUrls(targets))
    }

    @Test
    fun `Runde 2 holt nur den Rueckfall der nicht gefundenen Laeufe`() {
        val unresolved = listOf(target())

        assertEquals(
            listOf(timeTrials.resultsUrl),
            RaceClockerFeedAssignment.fallbackUrls(unresolved, setOf(shortCourse.resultsUrl)),
        )
    }

    @Test
    fun `Runde 2 holt nichts erneut, was Runde 1 schon hat`() {
        val unresolved = listOf(target())

        assertEquals(
            emptyList(),
            RaceClockerFeedAssignment.fallbackUrls(
                unresolved,
                setOf(shortCourse.resultsUrl, timeTrials.resultsUrl),
            ),
        )
    }

    @Test
    fun `ein Lauf ohne Rueckfall loest keine zweite Runde aus`() {
        val unresolved = listOf(target(qualificationRace = null))

        assertEquals(
            emptyList(),
            RaceClockerFeedAssignment.fallbackUrls(unresolved, setOf(shortCourse.resultsUrl)),
        )
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && cd backend && ./mvnw -q test -Dtest=RaceClockerFetchPlanTest
```

Erwartet: „Unresolved reference: RaceClockerFeedAssignment".

- [ ] **Step 3: Implementieren**

Datei `.../app/raceclocker/boundary/RaceClockerFeedAssignment.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.raceclocker.boundary

import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget

/**
 * Welche Adressen ein Takt anfragt — bewusst ohne Datenbank- und HTTP-Bezug, wie
 * [RaceClockerPollLogic] und aus demselben Grund.
 *
 * Der Abruf läuft in zwei Runden. Runde 1 holt die angewählten Rennen; Runde 2 holt den Rückfall,
 * aber nur für Läufe, die in ihrem Rennen nicht gefunden wurden. Vorher wurden beide Adressen jedes
 * beobachteten Laufs bedingungslos geholt — bei einer Regatta ohne Zeitfahren war damit jeder
 * zweite Abruf überflüssig, in jedem Takt der ganzen Veranstaltung.
 */
object RaceClockerFeedAssignment {

    /** Die angewählten Rennen, entdoppelt. Reihenfolge stabil, damit Protokolle vergleichbar bleiben. */
    fun primaryUrls(targets: List<RaceClockerMatchTarget>): List<String> =
        targets.mapNotNull { it.resultsUrl }.distinct()

    /**
     * Die Rückfall-Rennen der Läufe, die in Runde 1 leer ausgegangen sind — ohne das, was schon
     * geholt ist. Ist die Liste leer, entfällt die zweite Runde ganz, und das ist der Normalfall.
     */
    fun fallbackUrls(
        unresolved: List<RaceClockerMatchTarget>,
        alreadyFetched: Set<String>,
    ): List<String> =
        unresolved.mapNotNull { it.alternateResultsUrl }
            .filterNot { it in alreadyFetched }
            .distinct()
}
```

- [ ] **Step 4: Test laufen lassen, Erfolg bestätigen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && cd backend && ./mvnw -q test -Dtest=RaceClockerFetchPlanTest
```

Erwartet: 6 Tests, alle grün.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/boundary/RaceClockerFeedAssignment.kt \
        backend/src/test/kotlin/de/lambda9/ready2race/backend/app/raceclocker/RaceClockerFetchPlanTest.kt
git commit -m "Festhalten, welche Adressen ein Takt anfragt"
```

---

## Task 6: Zweistufiger Abruf in `pollEvent`

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/boundary/RaceClockerPollService.kt:115-336`

**Interfaces:**
- Consumes: `RaceClockerFeedAssignment.primaryUrls` / `.fallbackUrls` aus Task 5, `RaceClockerMatchTarget` aus Task 4.
- Produces: keine öffentliche Schnittstelle über `pollTick` und `forget` hinaus — beide bleiben unverändert.

**Achtung — die zwei Fallen dieser Umstellung:**

1. **`runIsolated` muss jede Phase je Lauf umschließen.** Heute klammert es den ganzen `pollMatch`. Nach der Aufteilung braucht jede Phase dieselbe Klammer, sonst reißt ein Defekt beim Auflösen des ersten Laufs alle noch nicht besuchten Läufe des Takts mit.
2. **`KIO.fail` ohne `!` ist ein No-Op.** Der neue Code hat mehr frühe Ausstiege als der alte.

- [ ] **Step 1: Die Hilfstypen anlegen**

Innerhalb von `RaceClockerPollService`, neben dem bestehenden `FeedResult`:

```kotlin
    /** Ein Lauf, dessen Turnierstruktur und Mannschaften bereits aufgelöst sind. */
    private data class ResolvedMatch(
        val candidate: RaceClockerPollCandidate,
        val match: CompetitionMatchWithTeams,
        val teams: List<CompetitionMatchTeamWithRegistration>,
    )

    /**
     * Was die bisher geholten Rennen über einen Lauf hergeben.
     *
     * Die drei Fälle sind bewusst getrennt, weil sie am Renntag drei verschiedene Dinge bedeuten:
     * gefunden, „die Welle gibt es dort noch nicht" (vor dem Start der Normalfall) und „kein Rennen
     * hat geantwortet" (die einzige echte Störung). Nur der letzte gehört als Fehler in die
     * Oberfläche — eine Warnung, die immer leuchtet, bringt dem Büro bei, auch die eine zu
     * übersehen, auf die es ankommt.
     */
    private sealed interface MatchFeed {
        data class Found(
            /** Alle Zeilen des Rennens — `applyRaceClockerRows` braucht sie, nicht nur die eigenen. */
            val rows: List<RaceClockerFeedRow>,
            val assigned: List<RaceClockerFeedRow>,
        ) : MatchFeed

        data object NotInFeed : MatchFeed

        data class Failed(val errorCode: String?) : MatchFeed
    }
```

- [ ] **Step 2: `pollEvent` umbauen**

`pollEvent` (Zeile 115–178) durch die Phasenfassung ersetzen:

```kotlin
    /**
     * Ein Abruf für eine Veranstaltung, in vier Phasen.
     *
     * Der Umweg über Phasen ist nicht Ordnungsliebe: Der Rückfall soll erst geholt werden, wenn das
     * angewählte Rennen den Lauf nicht enthält - und ob es ihn enthält, weiß man erst, wenn die
     * Mannschaften des Laufs bekannt sind. Auflösen, Zuordnen und Schreiben müssen deshalb
     * auseinander.
     */
    private suspend fun CoroutineComprehensionScope<Nothing>.pollEvent(
        event: RaceClockerPollEvent,
        now: LocalDateTime,
    ) {
        val candidates = !RaceClockerPollRepo.getCandidates(event.eventId).orDie()
        val watched = candidates.filter {
            RaceClockerPollLogic.isWatched(
                activated = it.activatedAt != null,
                startTime = it.startTime,
                now = now,
                watchBeforeMinutes = event.watchBeforeMinutes,
                watchAfterMinutes = event.watchAfterMinutes,
            )
        }

        if (watched.isEmpty()) {
            eventStates[event.eventId] = EventState(now, RaceClockerPollLogic.modeFor(anyRunning = false))
            return
        }

        // Phase 1: Auflösen. `getSetupRoundsWithMatches` sind zwei Abfragen plus der ganze Baum aus
        // Runden, Läufen und Mannschaften - einmal je Wettkampf reicht, der Stand kann sich
        // innerhalb eines Taktes nicht ändern.
        val setupRoundsByCompetition = mutableMapOf<UUID, List<CompetitionSetupRoundWithMatches>>()
        val resolved = watched.mapNotNull { candidate ->
            val setupRounds = setupRoundsByCompetition.getOrPut(candidate.competitionId) {
                runIsolated(candidate.matchId, emptyList()) {
                    CompetitionSetupService.getSetupRoundsWithMatches(candidate.competitionId)
                        .recoverDefault { emptyList() }
                }
            }
            // Dieselbe Sperre wie beim Knopf: `checkUpdateMatchResult` löst die aktuelle Runde auf
            // und weist einen Lauf außerhalb davon ab. Ohne das würde der Job einen ersten Vorlauf,
            // den niemand beendet hat, für immer weiter beschreiben. Scheitert die Prüfung
            // (gesperrt, Freilos, Struktur leer), überspringt der Job den Lauf still - das ist kein
            // Abruf-Fehler, den die Oberfläche anzeigen müsste.
            val match = runIsolated(candidate.matchId, null) {
                CompetitionExecutionService.checkUpdateMatchResult(setupRounds, candidate.matchId)
                    .recoverDefault { null }
            } ?: return@mapNotNull null

            ResolvedMatch(candidate, match, match.teams.filter { !it.deregistered })
        }

        // Phase 2: Runde 1 - nur die angewählten Rennen.
        val feeds = mutableMapOf<String, FeedResult>()
        RaceClockerFeedAssignment.primaryUrls(resolved.map { it.candidate.target })
            .forEach { feeds[it] = fetchRows(it) }

        // Über die Lauf-Kennung verschlüsselt, nicht über das Objekt: `ResolvedMatch` trägt den
        // ganzen Lauf mitsamt Mannschaften, und dessen Gleichheit ist hier weder nötig noch billig.
        val firstPass: Map<UUID, MatchFeed> =
            resolved.associate { it.candidate.matchId to assign(it, feeds) }

        // Phase 3: Runde 2 - der Rückfall, aber nur für das, was leer ausgegangen ist. Im gesunden
        // Betrieb ist diese Runde leer, und genau darin liegt die Ersparnis.
        val unresolved = resolved.filter { firstPass[it.candidate.matchId] !is MatchFeed.Found }
        if (unresolved.isNotEmpty()) {
            RaceClockerFeedAssignment
                .fallbackUrls(unresolved.map { it.candidate.target }, feeds.keys.toSet())
                .forEach { feeds[it] = fetchRows(it) }
        }

        // Phase 4: Schreiben.
        var anyRunning = false
        resolved.forEach { entry ->
            // Wer in Runde 1 gefunden wurde, wird nicht erneut zugeordnet; für alle anderen sind
            // inzwischen die Rückfall-Rennen da.
            val first = firstPass.getValue(entry.candidate.matchId)
            val feed = if (first is MatchFeed.Found) first else assign(entry, feeds)

            val outcome = runIsolated(
                entry.candidate.matchId,
                MatchOutcome(errorCode = ErrorCode.INTERNAL_ERROR.name),
            ) {
                writeMatch(entry, feed, now)
            }
            // Der schnelle Takt hängt an der Aktivierung, nicht am Ist-Start: Ein Lauf am Start ist
            // genau der, dessen Startmeldung so früh wie möglich ankommen soll.
            anyRunning = anyRunning || entry.candidate.activatedAt != null || outcome.activated

            runIsolated(entry.candidate.matchId, Unit) {
                RaceClockerPollRepo.recordPoll(entry.candidate.matchId, now, outcome.errorCode).orDie().map { }
            }
        }

        // Der Takt wird erst hier bestimmt: In der Schleife kann ein Lauf aktiviert worden sein, und
        // genau der Takt, der den Start entdeckt, soll schon der schnelle sein. Sonst wartet ein
        // frisch gestarteter Lauf noch einen ganzen langsamen Takt (Vorgabe 60 s) auf seinen ersten
        // Ergebnisabruf.
        eventStates[event.eventId] = EventState(now, RaceClockerPollLogic.modeFor(anyRunning))
    }

    /**
     * Sucht diesen Lauf in den bereits geholten Rennen — angewähltes zuerst, dann der Rückfall.
     *
     * Entscheidend ist wie beim Knopf, ob die Welle im Feed STEHT, nicht bloß, ob die Adresse
     * geantwortet hat. Sonst gewönne bei einer als Zeitfahren gefahrenen, aber nicht als
     * Qualifikation markierten Runde immer das erste, falsche Rennen, und der Lauf bliebe die ganze
     * Regatta ohne Ergebnis.
     */
    private fun assign(entry: ResolvedMatch, feeds: Map<String, FeedResult>): MatchFeed {
        val target = entry.candidate.target
        val fetched = target.candidateUrls.mapNotNull { feeds[it] }
        val answered = fetched.filterIsInstance<FeedResult.Rows>()

        val found = answered.firstNotNullOfOrNull { feed ->
            CompetitionExecutionService.assignedRowsFor(feed.rows, entry.teams, target.waveName)
                .takeIf { it.isNotEmpty() }
                ?.let { MatchFeed.Found(feed.rows, it) }
        }
        if (found != null) return found

        // Hat gar kein Rennen mit Zeilen geantwortet, ist DAS der Fehler, den die Oberfläche zeigen
        // soll. Hat eines geantwortet und die Welle fehlt bloß, ist das vor dem Start der Normalfall.
        return if (answered.isEmpty()) {
            MatchFeed.Failed((fetched.firstOrNull() as? FeedResult.Failed)?.errorCode)
        } else {
            MatchFeed.NotInFeed
        }
    }
```

- [ ] **Step 3: `pollMatch` zu `writeMatch` umbauen**

`pollMatch` (Zeile 217–336) verliert seinen Sucheteil am Anfang und behält den ganzen Schreibteil. Vollständige neue Fassung — der Rumpf ab „Bevorstehender Lauf" ist bis auf `entry.match`, `found.assigned` und `found.rows` wörtlich der bisherige:

```kotlin
    /**
     * Ein einzelner Lauf, ab der fertigen Zuordnung.
     *
     * Ein Fehler bleibt hier: Ein Lauf mit doppelten Crews in RaceClocker darf die anderen Läufe
     * derselben Veranstaltung nicht mitreißen.
     */
    private fun writeMatch(
        entry: ResolvedMatch,
        feed: MatchFeed,
        now: LocalDateTime,
    ): App<Nothing, MatchOutcome> = KIO.comprehension {
        val candidate = entry.candidate

        val found = when (feed) {
            is MatchFeed.Failed -> return@comprehension KIO.ok(MatchOutcome(errorCode = feed.errorCode))
            // Eine Welle, die in RaceClocker noch nicht angelegt ist, ist vor dem Start der
            // Normalfall und keine Störung.
            MatchFeed.NotInFeed -> return@comprehension KIO.ok(MatchOutcome())
            is MatchFeed.Found -> feed
        }

        // Bevorstehender Lauf: nur hinsehen, nichts schreiben außer der Aktivierung. Ein
        // Umsortieren in RaceClocker vor dem Start schlägt erst durch, wenn der Lauf aktiv ist.
        if (candidate.activatedAt == null) {
            if (!RaceClockerPollLogic.startDetected(found.assigned)) {
                return@comprehension KIO.ok(MatchOutcome())
            }

            !CompetitionMatchRepo.update(candidate.matchId) {
                if (activatedAt == null) {
                    activatedAt = now
                }
                if (startedAt == null) {
                    startedAt = now
                }
                updatedBy = SYSTEM_USER
                updatedAt = now
            }.orDie()
            logger.info { "RaceClocker meldet den Start von Lauf ${candidate.matchId} - Lauf aktiviert." }
            return@comprehension KIO.ok(MatchOutcome(activated = true))
        }

        // Aktiviert, aber noch ohne Ist-Start: Der Lauf wurde von Hand oder von der Kette an den
        // Start gerufen, und der Feed weiß vielleicht schon, dass er losgegangen ist. Der Stempel
        // steht hier und nicht in `applyRaceClockerRows`: Dort bricht der NoResults-Zweig ab, bevor
        // die gemessene Startzeit übernommen wird, und er läuft innerhalb von `.transact()` - ein
        // dort gesetzter Zeitstempel fiele dem Rollback zum Opfer.
        val measuredStart = RaceClockerPollLogic.measuredStartFor(
            rows = found.assigned,
            existingStartedAt = candidate.startedAt,
            plannedStart = candidate.startTime,
            now = now,
        )
        if (measuredStart != null) {
            !CompetitionMatchRepo.update(candidate.matchId) {
                startedAt = measuredStart
                updatedBy = SYSTEM_USER
                updatedAt = now
            }.orDie()
            logger.info { "RaceClocker meldet den Ist-Start von Lauf ${candidate.matchId}." }
        }

        // Unverändert seit dem letzten Abruf: nichts schreiben.
        val fingerprint = RaceClockerPollLogic.fingerprint(found.assigned)
        if (fingerprints[candidate.matchId] == fingerprint) return@comprehension KIO.ok(MatchOutcome())

        // `transact()`, weil der Job im Gegensatz zum Endpunkt keine mitgebrachte Transaktion hat.
        // Ohne die Klammer bliebe ein Lauf halb geschrieben zurück, sobald `applyRaceClockerRows`
        // nach den ersten Schreibvorgängen scheitert - und weil die Bahnvergabe die Startnummern
        // zwischendurch negiert, sähe das Live-Dashboard kurzzeitig lauter negative Bahnen.
        val write = !KIO.comprehension<JEnv, ServiceError, WriteOutcome> {
            // Die Pause wird hier ein zweites Mal geprüft, in derselben Transaktion wie das
            // Schreiben. `getCandidates` hat sie am Anfang des Takts gelesen, dazwischen liegen bis
            // zu zwei HTTP-Abrufe mit je 10 s Zeitlimit.
            val paused = !RaceClockerPollRepo.isAutoPaused(candidate.matchId).orDie()
            if (paused) return@comprehension KIO.ok(WriteOutcome(rememberFingerprint = false))

            CompetitionExecutionService
                .applyRaceClockerRows(entry.match, candidate.matchId, candidate.target, found.rows, SYSTEM_USER)
                .map { WriteOutcome(rememberFingerprint = true) }
        }.transact().recoverDefault { error -> failedWrite(candidate.matchId, error) }

        if (write.rememberFingerprint) {
            fingerprints[candidate.matchId] = fingerprint
        }
        KIO.ok(MatchOutcome(errorCode = write.errorCode))
    }
```

`MatchOutcome`, `WriteOutcome`, `failedWrite`, `FeedResult`, `fetchRows` und `runIsolated` bleiben unverändert.

- [ ] **Step 4: Übersetzen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && cd backend && ./mvnw -q compile
```

Erwartet: keine Fehler.

- [ ] **Step 5: Alle Tests laufen lassen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && cd backend && ./mvnw -q test
```

Erwartet: alle grün.

- [ ] **Step 6: Isolation prüfen**

```bash
grep -n "runIsolated" backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/boundary/RaceClockerPollService.kt
```

Erwartet: mindestens vier Vorkommen — Turnierstruktur, `checkUpdateMatchResult`, `writeMatch`, `recordPoll`. Fehlt eines, ist die Isolation je Lauf durchbrochen.

```bash
grep -n "KIO.fail" backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/boundary/RaceClockerPollService.kt
```

Erwartet: jedes Vorkommen steht hinter `return@comprehension` oder `!`. Ein nacktes `KIO.fail(...)` in einer Anweisungszeile ist ein Fehler.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/boundary/RaceClockerPollService.kt
git commit -m "Den Rückfall erst holen, wenn das angewählte Rennen den Lauf nicht kennt"
```

---

## Task 7: Rennen im Zeitnahme-Tab der Veranstaltung

**Files:**
- Modify: `frontend/src/components/event/timing/EventTimingConfig.tsx`
- Modify: `frontend/src/components/event/timing/eventTimingConfigForm.ts`
- Modify: `frontend/src/components/event/timing/eventTimingConfigForm.test.ts`
- Modify: `frontend/src/i18n/{de,en,da}/translations.json`
- Modify: `backend/.../timingConfig/entity/EventTimingConfigDto.kt`, `EventTimingConfigRequest.kt`, `TimingConfigService.kt`, `documentation.yaml`

**Interfaces:**
- Consumes: `getRaceClockerRaces`, `addRaceClockerRace`, `updateRaceClockerRace`, `deleteRaceClockerRace`, `RaceClockerRaceDto` aus Task 3.
- Produces: `EventTimingConfigDto` und `EventTimingConfigRequest` tragen statt `timeTrialResultsUrl`/`heatsResultsUrl` die Felder `raceQualification: UUID?` und `raceRounds: UUID?`.

- [ ] **Step 1: Backend-DTO und -Request umstellen**

In `EventTimingConfigDto.kt` und `EventTimingConfigRequest.kt` die beiden URL-Felder durch `raceQualification: UUID?` und `raceRounds: UUID?` ersetzen; in `TimingConfigService.updateEventTimingConfig` die `normalizeUrl`-Aufrufe entfernen und stattdessen schreiben:

```kotlin
        // Beide Rennen müssen zu dieser Veranstaltung gehören. Der Fremdschlüssel allein lässt ein
        // Rennen einer anderen Veranstaltung durch.
        request.raceQualification?.let {
            !RaceClockerRaceRepo.belongsToEvent(it, eventId).orDie()
                .onFalseFail { RaceClockerRaceError.NotFound }
        }
        request.raceRounds?.let {
            !RaceClockerRaceRepo.belongsToEvent(it, eventId).orDie()
                .onFalseFail { RaceClockerRaceError.NotFound }
        }
```

und im `EventRepo.update`-Block `raceclockerRaceQualification = request.raceQualification` / `raceclockerRaceRounds = request.raceRounds`. Heißt die Hilfsfunktion nicht `onFalseFail`, mit `grep -rn "onTrueFail\|onFalseFail" backend/src/main/kotlin --include="*.kt" | head -3` die vorhandene ermitteln.

`documentation.yaml` entsprechend anpassen (`EventTimingConfigDto`, `EventTimingConfigRequest`: `timeTrialResultsUrl`/`heatsResultsUrl` raus, `raceQualification`/`raceRounds` als `type: string, format: uuid, nullable: true` rein), danach `cd frontend && npm run generate`.

- [ ] **Step 2: Formularmodell umstellen**

In `eventTimingConfigForm.ts` `timeTrialResultsUrl` und `heatsResultsUrl` durch `raceQualification: string | null` und `raceRounds: string | null` ersetzen — in `EventTimingForm`, `emptyEventTimingForm`, `mapDtoToEventTimingForm` und `mapEventTimingFormToRequest`. Die vorhandenen Tests in `eventTimingConfigForm.test.ts` entsprechend anpassen und je einen Fall ergänzen: leere Anwahl bleibt `null`, gesetzte Anwahl kommt unverändert durch.

```bash
cd frontend && npx vitest run src/components/event/timing/eventTimingConfigForm.test.ts
```

Erwartet: grün.

- [ ] **Step 3: Rennen-Liste in `EventTimingConfig.tsx`**

Die beiden `FormInputText` für `timeTrialResultsUrl`/`heatsResultsUrl` (Zeilen 158–165) durch zwei Blöcke ersetzen:

1. **Die Rennen-Liste**, außerhalb des Formulars (wie die Abweichungen unten, und aus demselben Grund: sie wird hier nicht mit dem Formular gespeichert, sondern über eigene Endpunkte). Je Zeile Name, Adresse, Startart und Rundenzeiten-Kennzeichen, dazu Bearbeiten und Löschen; darunter ein Knopf „Rennen hinzufügen", der einen Dialog mit `name`, `resultsUrl`, `startMode` (Radio: Einzelstarts / Läufe) und `capturesLaps` (Schalter) öffnet. Nach jedem Schreibvorgang neu laden.

2. **Zwei `FormInputAutocomplete`** über die geladenen Rennen (`options={races.map(r => ({id: r.id, label: r.name}))}`) für `raceQualification` und `raceRounds`, mit denselben Labels-Schlüsseln wie bisher, aber neuen Texten.

Vor dem Löschen prüfen, ob das Rennen noch angewählt ist — in der Voreinstellung oder in einer der `deviations` — und dann rückfragen, welche Wettkämpfe betroffen sind. `getRaceClockerRaces` und die Abweichungen liegen beide schon in der Komponente.

Für Dialog und Liste die im Projekt üblichen Bausteine verwenden; ein Muster mit Liste plus Dialog findet sich über:

```bash
grep -rln "DialogTitle" frontend/src/components/event | head -5
```

- [ ] **Step 4: Übersetzungen ergänzen**

In allen drei `translations.json` unterhalb von `event.timing` ergänzen — Schlüssel identisch, Texte je Sprache:

```
event.timing.races.title            "RaceClocker-Rennen"
event.timing.races.hint             "Ein Rennen je Startmodus. Wettkämpfe wählen daraus aus, statt Adressen zu wiederholen."
event.timing.races.add              "Rennen hinzufügen"
event.timing.races.name             "Name"
event.timing.races.url              "Ergebnis-Adresse"
event.timing.races.startMode        "Startart"
event.timing.races.startModes.individual "Einzelstarts (Zeitfahren)"
event.timing.races.startModes.wave       "Start in mehreren Läufen"
event.timing.races.capturesLaps     "Erfasst Rundenzeiten"
event.timing.races.none             "Noch kein Rennen angelegt."
event.timing.races.deleteConfirm    "„{{name}}" löschen? Wettkämpfe, die darauf zeigen, erben danach wieder die Voreinstellung."
event.timing.races.nameTaken        "Es gibt schon ein Rennen mit diesem Namen."
event.timing.races.urlTaken         "Es gibt schon ein Rennen mit dieser Adresse."
event.timing.raceQualification      "Rennen für Qualifikationsrunden"
event.timing.raceRounds             "Rennen für alle übrigen Runden"
```

Die alten Schlüssel `event.timing.timeTrialUrl` und `event.timing.heatsUrl` entfernen, sofern sie nirgends sonst mehr vorkommen:

```bash
grep -rn "timing.timeTrialUrl\|timing.heatsUrl" frontend/src
```

- [ ] **Step 5: Bauen und prüfen**

```bash
cd frontend && npm run lint && npm run build
```

Erwartet: beides ohne Fehler.

- [ ] **Step 6: Commit**

```bash
git add frontend/src backend/src/main/kotlin backend/src/main/resources/openapi/documentation.yaml
git commit -m "Rennen der Veranstaltung anlegen und als Voreinstellung anwählen"
```

---

## Task 8: Anwahl im Zeitnahme-Tab des Wettkampfs

**Files:**
- Modify: `frontend/src/components/event/competition/timing/CompetitionTimingConfig.tsx`
- Modify: `frontend/src/components/event/competition/timing/timingConfigForm.ts` (+ `.test.ts`)
- Modify: `frontend/src/components/event/competition/excecution/executionError.ts`
- Modify: `frontend/src/components/event/timing/EventTimingConfig.tsx` (Abweichungs-Anzeige)
- Modify: `backend/.../timingConfig/entity/TimingConfigDto.kt`, `TimingConfigRequest.kt`, `CompetitionTimingDeviationDto.kt`, `TimingConfigRepo.kt`, `TimingConfigService.kt`, `documentation.yaml`

**Interfaces:**
- Consumes: `RaceClockerRaceDto` und `getRaceClockerRaces` aus Task 3; `raceQualification`/`raceRounds` aus Task 7.
- Produces: `TimingConfigDto` trägt `raceQualification`, `raceRounds`, `eventRaceQualification`, `eventRaceRounds`; `CompetitionTimingDeviationDto` trägt `raceQualificationName: String?` und `raceRoundsName: String?` statt der beiden URL-Felder.

- [ ] **Step 1: Backend umstellen**

- `TimingConfigDto`: `timeTrialResultsUrl`/`heatsResultsUrl`/`eventTimeTrialResultsUrl`/`eventHeatsResultsUrl` → `raceQualification`/`raceRounds`/`eventRaceQualification`/`eventRaceRounds`, alle `UUID?`.
- `TimingConfigRequest`: dieselben zwei Felder wie beim Event, `normalizeUrl`-Aufrufe raus, Zugehörigkeitsprüfung wie in Task 7 Step 1 — die Veranstaltung dafür über `competition.event` besorgen.
- `CompetitionTimingDeviationDto`: `timeTrialResultsUrl`/`heatsResultsUrl` → `raceQualificationName`/`raceRoundsName`, beide `String?`.
- `TimingConfigRepo.getDeviations`: die beiden URL-Spalten im `select` und im `DSL.or(...)` durch `COMPETITION.RACECLOCKER_RACE_QUALIFICATION` / `COMPETITION.RACECLOCKER_RACE_ROUNDS` ersetzen und über zwei `leftJoin` auf `RACECLOCKER_RACE`-Aliase die Namen mitlesen (Muster aus Task 4 Step 5).
- `documentation.yaml` nachziehen, dann `cd frontend && npm run generate`.

- [ ] **Step 2: Wettkampf-Formular umstellen**

In `timingConfigForm.ts` dieselbe Ersetzung wie in Task 7 Step 2, plus die Anzeige des geerbten Werts: Der Tab zeigt heute, *was* geerbt würde — künftig den **Namen** des geerbten Rennens, nicht seine Adresse. Dafür die Rennen der Veranstaltung mit `getRaceClockerRaces` laden und die geerbte UUID darüber auflösen. Tests in `timingConfigForm.test.ts` anpassen.

- [ ] **Step 3: Abweichungs-Anzeige umstellen**

In `EventTimingConfig.tsx` die Funktion `describeDeviation` (Zeilen 33–45): die beiden Zeilen für `timeTrialResultsUrl` und `heatsResultsUrl` durch Einträge ersetzen, die das abweichende Rennen **beim Namen nennen** — statt eines festen Schlüssels also ein zusammengesetzter Text:

```tsx
        deviation.raceQualificationName
            ? t('event.timing.deviations.raceQualification', {name: deviation.raceQualificationName})
            : null,
        deviation.raceRoundsName
            ? t('event.timing.deviations.raceRounds', {name: deviation.raceRoundsName})
            : null,
```

Da `describeDeviation` bisher Schlüssel zurückgibt und der Aufrufer `t()` anwendet, die Funktion auf **fertige Texte** umstellen und den `.map(key => t(key))` im Aufrufer (Zeile 313–314) entfernen. `t` dafür als Parameter hineinreichen.

Neue Schlüssel in allen drei Sprachdateien:

```
event.timing.deviations.raceQualification "Eigenes Quali-Rennen: {{name}}"
event.timing.deviations.raceRounds        "Eigenes Rennen: {{name}}"
```

Die alten Schlüssel `event.timing.deviations.timeTrialUrl` und `.heatsUrl` entfernen.

- [ ] **Step 4: Fehlermeldung um den Rennennamen erweitern**

In `executionError.ts` beim Zweig für `RACECLOCKER_MATCH_NOT_IN_FEED` die neuen `details.races` verwenden, wenn vorhanden. Vorher die Stelle lesen:

```bash
grep -n "MATCH_NOT_IN_FEED" -A6 frontend/src/components/event/competition/excecution/executionError.ts
```

Neuer Text in allen drei Sprachdateien, Schlüssel nach dem dort üblichen Muster, etwa: „Der Lauf steht in {{races}} nicht im Feed." Den zugehörigen Fall in `executionError.test.ts` ergänzen.

- [ ] **Step 5: Frontend-Tests, Lint und Build**

```bash
cd frontend && npx vitest run && npm run lint && npm run build
```

Erwartet: alles grün.

- [ ] **Step 6: Backend-Tests**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && cd backend && ./mvnw -q test
```

Erwartet: alles grün.

- [ ] **Step 7: Commit**

```bash
git add frontend/src backend/src
git commit -m "Wettkämpfe wählen ihr Rennen beim Namen an"
```

---

## Task 9: Migrationsprobe gegen den Prod-Abzug und Handtest-Katalog

**Files:**
- Modify: `docs/superpowers/specs/2026-08-05-testkatalog-crf-2026.md`

**Interfaces:**
- Consumes: die vollständige Umsetzung aus Task 1–8.
- Produces: belegte Aussage darüber, was die Migration mit den echten Daten von CRF 2026 tut.

- [ ] **Step 1: Vollständigen Lauf gegen eine frische Datenbank**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && cd backend && ./mvnw flyway:clean && ./mvnw -q verify
```

Erwartet: Migrationen und alle Tests grün.

- [ ] **Step 2: Prod-Abzug einspielen und migrieren**

Den Abzug von CRF 2026 nach dem in `.claude/` hinterlegten Weg ziehen und in eine leere lokale Datenbank einspielen, dann Flyway darauf laufen lassen. **Vor** der Migration festhalten, was da ist:

```sql
select count(*) from event where raceclocker_tt_results_url is not null or raceclocker_heats_results_url is not null;
select count(*) from competition where raceclocker_tt_results_url is not null or raceclocker_heats_results_url is not null;
```

- [ ] **Step 3: Ergebnis prüfen**

Nach der Migration:

```sql
select e.name, r.name, r.start_mode, r.position, r.results_url
from raceclocker_race r join event e on e.id = r.event
order by e.name, r.position;

-- Kein Wettkampf darf seine Anwahl verloren haben, der vorher eine Adresse hatte.
select count(*) from competition
where raceclocker_race_qualification is null and raceclocker_race_rounds is null;
```

Erwartet: Je Veranstaltung so viele Rennen wie es dort verschiedene Adressen gab — nicht mehr. Keine Namensdubletten. Die zweite Zahl entspricht der Zahl der Wettkämpfe, die vorher keine eigene Adresse hatten.

Weicht etwas ab, ist das ein Fehler in Task 1 und wird dort behoben, nicht hier umgangen.

- [ ] **Step 4: Handtests in den Katalog aufnehmen**

In `docs/superpowers/specs/2026-08-05-testkatalog-crf-2026.md` fünf Fälle ergänzen, im dort vorhandenen Format und mit fortlaufenden Nummern:

1. Drei Rennen anlegen (Timetrials/INDIVIDUAL, Langstrecke/WAVE mit Rundenzeiten-Kennzeichen, Kurzstrecke/WAVE), einem Wettkampf zuweisen, automatischen Abruf einschalten und beobachten, dass Ergebnisse ankommen.
2. Wettkampf-Anwahl setzen und wieder leeren; prüfen, dass danach wieder die Voreinstellung der Veranstaltung greift und der Tab das auch anzeigt.
3. Absichtlich das falsche Rennen anwählen; prüfen, dass der Lauf trotzdem gefunden wird (Rückfall) und die Regatta weiterläuft.
4. Ein angewähltes Rennen löschen; prüfen, dass die Oberfläche vorher warnt und danach die betroffenen Wettkämpfe wieder erben.
5. Zwei Rennen mit derselben Adresse anlegen; prüfen, dass das mit einer verständlichen Meldung abgewiesen wird.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-08-05-testkatalog-crf-2026.md
git commit -m "Handtests für die Rennen-Anwahl aufnehmen"
```

---

## Nach Abschluss

Die Rundenzeiten der Langstrecke sind bewusst **nicht** Teil dieses Plans (§1 der Spec). Der erste Schritt dort ist kein Code, sondern ein echter Langstrecken-Feed mit Rundenzeiten zum Ansehen — ohne den wäre der Parser geraten.
