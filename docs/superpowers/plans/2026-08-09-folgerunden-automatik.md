# Folgerunden-Automatik — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sobald eine Runde vollständig abgeschlossen ist, erzeugt Ready2Race die Paarungen der
fachlich folgenden Runde von selbst — steuerbar pro Veranstaltung und pro Wettkampf.

**Architecture:** `CompetitionExecutionService.createNewRound` rechnet bereits formatunabhängig
über die Kette aus `competition_setup_round`. Neu ist nur ein Auslöser: eine reine Funktion
entscheidet, ob eine Runde abgeschlossen ist, ein schmaler Service verdrahtet sie mit den fünf
Stellen, an denen sich der Zustand eines Laufs ändert, und ruft `createNewRound`. Aktiviert wird
nichts — das bleibt bei `ScheduleChainService`.

**Tech Stack:** Kotlin, Ktor, KIO (tailwind-core), JOOQ mit Codegen gegen eine echte Datenbank,
Flyway, kotlin.test + Testcontainers; Frontend React/TypeScript mit MUI, generierter API-Client aus
OpenAPI, i18n über i18next.

## Global Constraints

- **Vorgehen ist TDD.** Erst der scheiternde Test, dann die minimale Implementierung.
- **Deutsche KDoc/Kommentare in ganzen Sätzen**, wie im Bestand. Kommentare erklären, *warum*
  etwas so ist — nicht, was der Code tut. Echte Umlaute (ä, ö, ü, ß), niemals `ae`/`oe`/`ue`/`ss`.
- **Kein Hinweis auf Claude oder KI** in Commits, Code oder Dokumenten.
- **Build braucht Java und eine laufende Datenbank.** In jeder Shell:
  `JAVA_HOME=/opt/homebrew/opt/openjdk@21`. Der Build-Datenbank-Container muss laufen
  (`docker ps` zeigt `backend-build-db-1` auf Port 7652); sonst scheitert der JOOQ-Codegen.
  Falls er fehlt: `cd backend && docker compose up -d`.
- **Eigene Build-Datenbank für diesen Worktree.** Der Container wird von allen Worktrees geteilt,
  und die Standard-Datenbank `ready2race-build` trägt Spalten einer parallelen Sitzung
  (`event.execution_auto_refresh*`), die unsere Migrationen nicht anlegen. JOOQ generiert sie mit,
  das Testcontainers-Postgres kennt sie nicht — jeder Test, der ein Event einfügt, stirbt daran.
  Deshalb hängt **jeder** Maven-Aufruf an:
  `-Ddatabase.url=jdbc:postgresql://localhost:7652/r2r-follow-rounds`. Die Datenbank existiert
  bereits. Wechselt man die Datenbank, muss einmal `clean` mitlaufen, sonst schlagen Tests mit
  `NoSuchMethod` auf `EventRecord.<init>` fehl (Klassen aus dem alten Schema im `target`).
- **JOOQ-Klassen sind generiert und nicht eingecheckt.** Nach jeder Migration muss der Build
  laufen, damit `target/generated-sources/jooq` neu entsteht. Das passiert im normalen
  `./mvnw test-compile` automatisch (Flyway migriert die Build-DB, dann generiert JOOQ).
- **Views gehören in `afterMigrate.sql`**, nie in eine Migrationsdatei. Dort wird jede View bei
  jedem Lauf gedroppt und neu erzeugt.
- **Migrationsnamen** folgen `V{yyyyMMddHHmm}__{beschreibung}.sql`. Die höchste vorhandene Nummer
  ist `V202608091410`. Dieser Plan benutzt `V202608091501`.
- **i18n immer in allen drei Sprachen** pflegen: `frontend/src/i18n/de/translations.json`,
  `.../en/translations.json`, `.../da/translations.json`.
- **`KIO.fail` ohne `!` ist ein No-Op.** Jeder `KIO.fail`/`onNullFail`-Aufruf in einer
  `KIO.comprehension` braucht entweder `!` davor oder ein `return@comprehension`.
- **Nach jeder Aufgabe committen.** Kleine, sprechende deutsche Commit-Nachrichten.

### Befehle

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o -Ddatabase.url=jdbc:postgresql://localhost:7652/r2r-follow-rounds test-compile
```

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o -Ddatabase.url=jdbc:postgresql://localhost:7652/r2r-follow-rounds test -Dtest=AutoRoundProgressionLogicTest
```

```bash
cd frontend && npm run lint
```

---

## Dateiübersicht

**Neu:**

| Datei | Verantwortung |
| --- | --- |
| `backend/src/main/resources/db/migration/V202608091501__auto_create_following_rounds.sql` | Vier Spalten |
| `backend/src/main/kotlin/.../app/competitionExecution/boundary/AutoRoundProgressionLogic.kt` | Reine Entscheidung: Runde abgeschlossen? Einstellung wirksam? |
| `backend/src/main/kotlin/.../app/competitionExecution/boundary/AutoRoundProgressionService.kt` | Auslöser, liest Einstellung, ruft `createNewRound` |
| `backend/src/main/kotlin/.../app/competitionExecution/entity/RoundProgressionConfigDto.kt` | DTO + Request der Wettkampf-Übersteuerung |
| `backend/src/main/kotlin/.../app/competitionExecution/boundary/roundProgression.kt` | Route `GET/PUT .../roundProgression` |
| `backend/src/test/kotlin/.../app/competitionExecution/AutoRoundProgressionLogicTest.kt` | Reine Tests, alle Formate |
| `backend/src/test/kotlin/.../app/competitionExecution/RoundProgressionFixture.kt` | Datenbank-Vorrichtung: Veranstaltung mit zweistufiger Kette |
| `backend/src/test/kotlin/.../app/competitionExecution/AutoRoundProgressionServiceTest.kt` | Service gegen echtes Postgres |
| `frontend/src/components/event/competition/excecution/roundProgressionForm.ts` | Reine Abbildung Formular ↔ Request + Vermerk-Regel |
| `frontend/src/components/event/competition/excecution/roundProgressionForm.test.ts` | Tests dazu |
| `frontend/src/components/event/competition/excecution/RoundProgressionSetting.tsx` | Dreier-Auswahl im Durchführungs-Tab |

**Geändert:** `afterMigrate.sql`, `CompetitionSetupRoundWithMatches.kt`, `CompetitionMatchWithTeams.kt`,
`CompetitionMatchDto.kt`, `competitionExecution/control/Conversions.kt`, `CompetitionMatchRepo.kt`,
`CompetitionSetupRoundRepo.kt`, `CompetitionExecutionService.kt`, `competitionExecution.kt`,
`LiveDashboardService.kt`, `LiveDashboardDto.kt`, `EventScheduleService.kt`, `EventRepo.kt`,
`CompetitionRepo.kt`, `event/entity/EventDto.kt`, `CreateEventRequest.kt`, `UpdateEventRequest.kt`,
`event/control/Conversions.kt`, `EventService.kt`, `competition/boundary/competition.kt`,
`openapi/documentation.yaml`, `EventDialog.tsx`, `CompetitionExecution.tsx`,
`CompetitionExecutionRound.tsx`, `LiveDashboardMatchCard.tsx`, drei `translations.json`.

---

## Task 1: Schema, Views und Entitäten

**Files:**
- Create: `backend/src/main/resources/db/migration/V202608091501__auto_create_following_rounds.sql`
- Modify: `backend/src/main/resources/db/migration/afterMigrate.sql` (Views
  `competition_match_with_teams` ab Zeile 839 und `competition_setup_round_with_matches` ab Zeile 885)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/entity/CompetitionMatchWithTeams.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/entity/CompetitionSetupRoundWithMatches.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/control/Conversions.kt` (Funktion `toCompetitionSetupRoundWithMatches`, Zeile 147)

**Interfaces:**
- Produces: `CompetitionSetupRoundWithMatches.materializedAt: LocalDateTime?` und
  `CompetitionMatchWithTeams.pairingsRecalculatedAt: LocalDateTime?`. Alle folgenden Aufgaben lesen
  diese beiden Felder.

- [ ] **Step 1: Migration schreiben**

Neue Datei `backend/src/main/resources/db/migration/V202608091501__auto_create_following_rounds.sql`:

```sql
set search_path to ready2race, pg_catalog, public;

-- Folgerunden automatisch erzeugen (Entwurf 2026-08-09).
--
-- Bewusst standardmäßig aus: Die Automatik erzeugt Paarungen ohne Rückfrage, und wer seinen Ablauf
-- noch einrichtet, will das nicht. Eingeschaltet wird sie pro Veranstaltung.
alter table event
    add column auto_create_following_rounds boolean not null default false;

-- Die Übersteuerung des einzelnen Wettkampfs, nullable statt not-null: null heißt "erben", true und
-- false heißen "ausdrücklich". Dasselbe Muster wie competition.timing_system über
-- event.timing_system (V202608062100) — die Lesestelle entscheidet mit coalesce.
--
-- Auf `competition` und nicht auf `competition_properties`, weil letztere laut Check-Constraint
-- auch an einer Wettkampf-Vorlage hängen kann. Eine Vorlage trägt keine Ablaufsteuerung einer
-- konkreten Regatta.
alter table competition
    add column auto_create_following_rounds boolean;

-- Merkt, dass diese Runde schon einmal gesetzt war. Steht auf der SETUP-Runde und nicht auf den
-- Läufen, weil es genau deren Löschen überleben muss: Erst daran ist zu erkennen, ob eine erzeugte
-- Runde die erste ihrer Art ist oder die Wiederholung nach einer Ergebniskorrektur.
-- deleteCurrentRound räumt die Spalte deshalb NICHT ab.
alter table competition_setup_round
    add column materialized_at timestamp;

-- Der sichtbare Vermerk am Lauf, wenn seine Paarung aus einer Wiederholung stammt. Gegenstück zu
-- raceclocker_auto_paused_at: ein Zeitstempel, den die Orga-Ansichten als Hinweis zeigen. Die
-- öffentliche Anzeige und die Athleten-Anzeige lesen ihn nicht.
alter table competition_match
    add column pairings_recalculated_at timestamp;

-- Beide Views hängen an den geänderten Tabellen; afterMigrate.sql erzeugt sie ohnehin bei jedem
-- Lauf neu. Vorab droppen hält bestehende Datenbanken sauber (gleiches Vorgehen wie V202608091400).
drop view if exists competition_setup_round_with_matches;
drop view if exists competition_match_with_teams;
```

- [ ] **Step 2: Views ergänzen**

In `afterMigrate.sql`, View `competition_match_with_teams`: nach der Zeile
`cm.raceclocker_auto_paused_at,` die neue Spalte einfügen:

```sql
       cm.raceclocker_auto_paused_at,
       cm.pairings_recalculated_at,
```

In derselben Datei, View `competition_setup_round_with_matches`: nach der Zeile
`sr.places_option,` einfügen:

```sql
       sr.places_option,
       sr.materialized_at,
```

Die `group by`-Klauseln bleiben unverändert: `competition_match_with_teams` gruppiert bereits nach
`cm.competition_setup_match` (Primärschlüssel), `competition_setup_round_with_matches` nach `sr.id`
— funktional abhängige Spalten darf Postgres dann mitführen.

- [ ] **Step 3: Entitäten erweitern**

In `CompetitionMatchWithTeams.kt` nach `raceClockerAutoPausedAt`:

```kotlin
    /**
     * Gesetzt, wenn die Paarung dieses Laufs aus einer Wiederholung stammt — die Runde war schon
     * einmal gesetzt, wurde gelöscht und nach einer Ergebniskorrektur neu gerechnet.
     */
    val pairingsRecalculatedAt: LocalDateTime?,
```

In `CompetitionSetupRoundWithMatches.kt` nach `placesOption`:

```kotlin
    /**
     * Wann diese Runde zum ersten Mal gesetzt wurde. Überlebt das Löschen der Runde und ist damit
     * die einzige Auskunft darüber, ob eine Erzeugung die erste ihrer Art ist.
     */
    val materializedAt: LocalDateTime?,
```

- [ ] **Step 4: Conversions anpassen**

In `competitionExecution/control/Conversions.kt`, Funktion `toCompetitionSetupRoundWithMatches`:
nach `placesOption = placesOption!!,` einfügen `materializedAt = materializedAt,` und im inneren
`CompetitionMatchWithTeams(...)` nach `raceClockerAutoPausedAt = match.raceclockerAutoPausedAt,`
einfügen `pairingsRecalculatedAt = match.pairingsRecalculatedAt,`.

Die Datei enthält eine zweite Stelle, die `CompetitionMatchWithTeams` baut (Zeile ~104 und ~112 im
`toCompetitionRoundDto`) — dort werden Felder eines bereits gebauten Objekts gelesen, nicht neu
gesetzt. Nach dem Compile-Fehler-Lauf in Step 5 ist sicher, welche Stellen anzupassen sind.

- [ ] **Step 5: Compile laufen lassen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o -Ddatabase.url=jdbc:postgresql://localhost:7652/r2r-follow-rounds test-compile
```

Erwartet: BUILD SUCCESS. Der Lauf migriert die Build-Datenbank und erzeugt die JOOQ-Klassen neu, so
dass `CompetitionMatchRecord.pairingsRecalculatedAt` und
`CompetitionSetupRoundRecord.materializedAt` existieren. Schlägt der Compile mit „No value passed
for parameter" fehl, fehlt eine der Zuweisungen aus Step 4.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration backend/src/main/kotlin
git commit -m "Spalten und Views für die Folgerunden-Automatik"
```

---

## Task 2: Reine Entscheidungslogik

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/boundary/AutoRoundProgressionLogic.kt`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/AutoRoundProgressionLogicTest.kt`

**Interfaces:**
- Consumes: `CompetitionSetupRoundWithMatches` und `CompetitionMatchWithTeams` aus Task 1.
- Produces:
  - `AutoRoundProgressionLogic.effectiveAutoCreate(eventDefault: Boolean, competitionOverride: Boolean?): Boolean`
  - `AutoRoundProgressionLogic.roundIsComplete(round: CompetitionSetupRoundWithMatches): Boolean`

- [ ] **Step 1: Den scheiternden Test schreiben**

Neue Datei
`backend/src/test/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/AutoRoundProgressionLogicTest.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.competitionExecution.boundary.AutoRoundProgressionLogic
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionMatchTeamWithRegistration
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionMatchWithTeams
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionSetupRoundWithMatches
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Die eine Entscheidung, an der die Automatik hängt: Ist diese Runde durch?
 *
 * Ohne Datenbank geprüft, weil die Frage ausschließlich an Feldern hängt, die die Ansicht ohnehin
 * schon liefert — und weil nur so jedes Wettkampfformat als eigener Fall dastehen kann, ohne für
 * jedes eine Regatta aufbauen zu müssen.
 */
class AutoRoundProgressionLogicTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 14, 10, 0)

    /**
     * Eine Mannschaft in einem Lauf. Die Vorgabewerte beschreiben den Normalfall: gemeldet,
     * angetreten, gewertet.
     */
    private fun team(
        place: Int? = null,
        failed: Boolean = false,
        out: Boolean = false,
        deregistered: Boolean = false,
        startNumber: Int = 1,
    ) = CompetitionMatchTeamWithRegistration(
        id = UUID.randomUUID(),
        competitionMatch = UUID.randomUUID(),
        startNumber = startNumber,
        place = place,
        timeString = null,
        placesCalculated = false,
        competitionRegistration = UUID.randomUUID(),
        clubId = UUID.randomUUID(),
        clubName = "Testverein",
        registrationName = null,
        teamNumber = 1,
        participants = emptyList(),
        deregistered = deregistered,
        deregistrationReason = null,
        out = out,
        failed = failed,
        failedReason = null,
        penaltySeconds = null,
        penaltyNote = null,
        ratingCategory = null,
        mixedTeamTerm = null,
    )

    private fun match(
        finishedAt: LocalDateTime? = now,
        skipped: Boolean = false,
        teams: List<CompetitionMatchTeamWithRegistration>,
    ) = CompetitionMatchWithTeams(
        competitionSetupMatch = UUID.randomUUID(),
        startTime = now,
        activatedAt = null,
        startedAt = null,
        finishedAt = finishedAt,
        skipped = skipped,
        raceClockerPolledAt = null,
        raceClockerPollError = null,
        raceClockerAutoPausedAt = null,
        pairingsRecalculatedAt = null,
        teams = teams,
    )

    private fun round(
        required: Boolean = true,
        isQualification: Boolean = false,
        matches: List<CompetitionMatchWithTeams>,
    ) = CompetitionSetupRoundWithMatches(
        setupRoundId = UUID.randomUUID(),
        competitionSetup = UUID.randomUUID(),
        nextRound = UUID.randomUUID(),
        setupRoundName = "Runde",
        required = required,
        isQualification = isQualification,
        placesOption = CompetitionSetupPlacesOption.EQUAL.name,
        materializedAt = null,
        places = emptyList(),
        setupMatches = emptyList(),
        matches = matches,
        substitutions = emptyList(),
    )

    // --- Vererbung ---

    @Test
    fun theCompetitionInheritsWhenItSaysNothing() {
        assertTrue(AutoRoundProgressionLogic.effectiveAutoCreate(eventDefault = true, competitionOverride = null))
        assertFalse(AutoRoundProgressionLogic.effectiveAutoCreate(eventDefault = false, competitionOverride = null))
    }

    @Test
    fun anExplicitCompetitionSettingBeatsTheEvent() {
        assertTrue(AutoRoundProgressionLogic.effectiveAutoCreate(eventDefault = false, competitionOverride = true))
        assertFalse(AutoRoundProgressionLogic.effectiveAutoCreate(eventDefault = true, competitionOverride = false))
    }

    // --- Abschluss ---

    @Test
    fun aRoundWithoutMatchesIsNotComplete() {
        assertFalse(AutoRoundProgressionLogic.roundIsComplete(round(matches = emptyList())))
    }

    /**
     * Der Alltagsfall am Renntag: Ein Lauf mit DNF, einer Disqualifikation, einem Nichtantritt und
     * einer Abmeldung ist vollständig gewertet. Für all diese Boote kommt kein Ergebnis mehr, und
     * die Platzfolge zählt sie nicht mit — sonst erreichte kein Lauf mit Ausfall je "durch".
     */
    @Test
    fun specialStatusesCountAsScored() {
        val complete = round(
            matches = listOf(
                match(
                    teams = listOf(
                        team(place = 1, startNumber = 1),
                        team(place = 2, startNumber = 2),
                        team(failed = true, startNumber = 3),
                        team(deregistered = true, startNumber = 4),
                        team(out = true, startNumber = 5),
                    )
                )
            )
        )

        assertTrue(AutoRoundProgressionLogic.roundIsComplete(complete))
    }

    /** Ein Freilos wird nie gefahren und nie beendet — es darf die Runde trotzdem nicht aufhalten. */
    @Test
    fun aByeDoesNotHoldTheRoundBack() {
        val withBye = round(
            required = false,
            matches = listOf(
                match(teams = listOf(team(place = 1, startNumber = 1), team(place = 2, startNumber = 2))),
                match(finishedAt = null, teams = listOf(team(place = 1, startNumber = 1))),
            )
        )

        assertTrue(AutoRoundProgressionLogic.roundIsComplete(withBye))
    }

    /** Ein abgesagter Lauf ist erledigt, auch ohne Ergebnis. */
    @Test
    fun aSkippedMatchIsDone() {
        val withSkipped = round(
            matches = listOf(
                match(teams = listOf(team(place = 1, startNumber = 1), team(place = 2, startNumber = 2))),
                match(finishedAt = null, skipped = true, teams = listOf(team(startNumber = 1), team(startNumber = 2))),
            )
        )

        assertTrue(AutoRoundProgressionLogic.roundIsComplete(withSkipped))
    }

    /**
     * Vollständige Ergebnisse ohne Beenden-Klick reichen nicht. Bis zum Klick kann noch eine
     * Zeitstrafe kommen — das ist die Entscheidung C1 aus dem Bestand, und die Automatik darf sie
     * nicht unterlaufen.
     */
    @Test
    fun scoredButNotFinishedIsNotComplete() {
        val awaitingFinish = round(
            matches = listOf(
                match(
                    finishedAt = null,
                    teams = listOf(team(place = 1, startNumber = 1), team(place = 2, startNumber = 2)),
                )
            )
        )

        assertFalse(AutoRoundProgressionLogic.roundIsComplete(awaitingFinish))
    }

    @Test
    fun aMissingPlaceKeepsTheRoundOpen() {
        val missingPlace = round(
            matches = listOf(
                match(teams = listOf(team(place = 1, startNumber = 1), team(place = null, startNumber = 2)))
            )
        )

        assertFalse(AutoRoundProgressionLogic.roundIsComplete(missingPlace))
    }

    /**
     * Die Platzfolge muss lückenlos sein. Zwei Boote mit den Plätzen 1 und 3 sind kein
     * abgeschlossener Lauf, sondern ein Eingabefehler.
     */
    @Test
    fun placesMustBeContinuous() {
        val gap = round(
            matches = listOf(
                match(teams = listOf(team(place = 1, startNumber = 1), team(place = 3, startNumber = 2)))
            )
        )

        assertFalse(AutoRoundProgressionLogic.roundIsComplete(gap))
    }

    // --- Formate ---

    /** K.-o.-Runde: vier Läufe zu zwei Booten, jeder beendet und gewertet. */
    @Test
    fun aKnockoutRoundIsComplete() {
        val quarterFinals = round(
            matches = (1..4).map {
                match(teams = listOf(team(place = 1, startNumber = 1), team(place = 2, startNumber = 2)))
            }
        )

        assertTrue(AutoRoundProgressionLogic.roundIsComplete(quarterFinals))
    }

    /** Vorrunde: zwei Läufe zu sechs Booten. */
    @Test
    fun aHeatRoundIsComplete() {
        val heats = round(
            matches = (1..2).map {
                match(teams = (1..6).map { rank -> team(place = rank, startNumber = rank) })
            }
        )

        assertTrue(AutoRoundProgressionLogic.roundIsComplete(heats))
    }

    /** Zwischenrunde: derselbe Aufbau, ein Lauf noch offen — die Runde ist nicht durch. */
    @Test
    fun anUnfinishedSemiFinalHoldsTheRound() {
        val semiFinals = round(
            matches = listOf(
                match(teams = (1..6).map { rank -> team(place = rank, startNumber = rank) }),
                match(finishedAt = null, teams = (1..6).map { rank -> team(startNumber = rank) }),
            )
        )

        assertFalse(AutoRoundProgressionLogic.roundIsComplete(semiFinals))
    }

    /** Qualifikation mit einem Freilos und einem gefahrenen Lauf. */
    @Test
    fun aQualificationWithAByeIsComplete() {
        val qualification = round(
            required = false,
            isQualification = true,
            matches = listOf(
                match(teams = listOf(team(place = 1, startNumber = 1), team(place = 2, startNumber = 2))),
                match(finishedAt = null, teams = listOf(team(place = 1, startNumber = 1))),
            )
        )

        assertTrue(AutoRoundProgressionLogic.roundIsComplete(qualification))
    }

    /**
     * Ein einzelner Lauf mit nur einem Boot in einer ERFORDERLICHEN Runde ist kein Freilos: Dort
     * wird gefahren, und ohne Beenden ist die Runde nicht durch.
     */
    @Test
    fun aSingleTeamInARequiredRoundIsNoBye() {
        val timeTrial = round(
            required = true,
            matches = listOf(match(finishedAt = null, teams = listOf(team(place = 1, startNumber = 1))))
        )

        assertFalse(AutoRoundProgressionLogic.roundIsComplete(timeTrial))
    }
}
```

- [ ] **Step 2: Test laufen lassen und Scheitern sehen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o -Ddatabase.url=jdbc:postgresql://localhost:7652/r2r-follow-rounds test -Dtest=AutoRoundProgressionLogicTest
```

Erwartet: Compile-Fehler „Unresolved reference: AutoRoundProgressionLogic".

Die Feldnamen von `CompetitionMatchTeamWithRegistration` sind gegen den Bestand geprüft
(`registrationName`, `deregistrationReason`, dazu `ratingCategory` und `mixedTeamTerm`) — schlägt
der Konstruktor trotzdem fehl, hat eine parallele Sitzung die Entität geändert; dann die Datei
lesen und nur die Hilfsfunktion `team(...)` anpassen, nicht die Testfälle.

- [ ] **Step 3: Die Logik schreiben**

Neue Datei
`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/boundary/AutoRoundProgressionLogic.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionMatchWithTeams
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionSetupRoundWithMatches

/**
 * Die beiden Entscheidungen der Folgerunden-Automatik, ohne Datenbank: Gilt sie für diesen
 * Wettkampf, und ist die laufende Runde durch?
 *
 * Bewusst rein und getrennt vom Service. An dieser Stelle hängt jedes Wettkampfformat — K.-o.,
 * Vor-, Zwischen- und Finalrunde, Qualifikation mit Freilosen —, und jedes davon lässt sich hier
 * als eigener Fall festnageln, ohne dafür eine Regatta in einer Datenbank aufzubauen.
 *
 * Was hier NICHT steht: wie die Folgerunde aussieht. Das rechnet unverändert
 * [CompetitionExecutionService.createNewRound] aus der Kette der Setup-Runden — formatunabhängig,
 * getestet und von dieser Automatik unberührt.
 */
object AutoRoundProgressionLogic {

    /**
     * Welche Einstellung für diesen Wettkampf gilt. `null` am Wettkampf heißt „der Veranstaltung
     * folgen"; das ist der Vorgabezustand und derselbe Vererbungsweg wie bei der
     * Zeitnahme-Konfiguration (`competition.timing_system` über `event.timing_system`).
     */
    fun effectiveAutoCreate(eventDefault: Boolean, competitionOverride: Boolean?): Boolean =
        competitionOverride ?: eventDefault

    /**
     * Ob ein einzelner Lauf erledigt ist.
     *
     * Ein Freilos ([bye]) wird nie gefahren und bekommt nie ein `finished_at` — es hält die Runde
     * trotzdem nicht auf. Ein abgesagter Lauf ist ebenfalls erledigt. Sonst zählt ausschließlich
     * der Beenden-Stempel: Vollständige Ergebnisse allein reichen nicht, weil bis zum
     * Beenden-Klick noch eine Zeitstrafe kommen kann (Entscheidung C1).
     */
    private fun matchIsDone(match: CompetitionMatchWithTeams, roundRequired: Boolean): Boolean =
        bye(match, roundRequired) || match.skipped || match.finishedAt != null

    /**
     * Ein Freilos: ein einziges Boot in einer nicht erforderlichen Runde. In einer erforderlichen
     * Runde wird auch allein gefahren (Zeitfahren) — dort ist ein einzelnes Boot kein Freilos.
     */
    private fun bye(match: CompetitionMatchWithTeams, roundRequired: Boolean): Boolean =
        !roundRequired && match.teams.size == 1

    /**
     * Ob in einem Lauf alle Plätze vergeben sind. Wörtlich dieselbe Bedingung wie in
     * `CompetitionExecutionService.checkRoundCreation`, der Prüfung hinter dem Knopf „Nächste Runde
     * erstellen": die Plätze müssen die Folge `1..n` über alle Boote enthalten, die überhaupt
     * gewertet werden. Abgemeldete, ausgeschiedene und ausgefallene Boote (DNF, Disqualifikation,
     * Nichtantritt) zählen nicht mit — für sie kommt kein Ergebnis mehr.
     */
    private fun placesAreSet(match: CompetitionMatchWithTeams): Boolean {
        val scoring = match.teams.filter { !it.deregistered && !it.failed && !it.out }
        return match.teams.map { it.place }.containsAll((1..scoring.size).toList())
    }

    /**
     * Ob die Runde als Ganzes durch ist — die Bedingung, die die Folgerunde auslöst.
     *
     * Eine Runde ohne Läufe ist ausdrücklich nicht abgeschlossen, sondern noch gar nicht gesetzt.
     * Ohne diesen Fall erklärte die Automatik jede leere Runde für fertig und liefe die ganze
     * Kette in einem Rutsch durch.
     */
    fun roundIsComplete(round: CompetitionSetupRoundWithMatches): Boolean =
        round.matches.isNotEmpty() &&
            round.matches.all { matchIsDone(it, round.required) && placesAreSet(it) }
}
```

- [ ] **Step 4: Test laufen lassen und grün sehen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o -Ddatabase.url=jdbc:postgresql://localhost:7652/r2r-follow-rounds test -Dtest=AutoRoundProgressionLogicTest
```

Erwartet: `Tests run: 13, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin backend/src/test/kotlin
git commit -m "Entscheiden, wann eine Runde abgeschlossen ist"
```

---

## Task 3: Einstellung lesen und schreiben (Backend)

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/event/control/EventRepo.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competition/control/CompetitionRepo.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/event/entity/EventDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/event/entity/CreateEventRequest.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/event/entity/UpdateEventRequest.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/event/control/Conversions.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/event/boundary/EventService.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/entity/RoundProgressionConfigDto.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/boundary/roundProgression.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/boundary/CompetitionExecutionService.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competition/boundary/competition.kt`
- Modify: `backend/src/main/resources/openapi/documentation.yaml`

**Interfaces:**
- Consumes: die Spalten aus Task 1.
- Produces:
  - `EventRepo.getAutoCreateFollowingRounds(eventId: UUID): JIO<Boolean>`
  - `CompetitionRepo.getAutoCreateFollowingRounds(competitionId: UUID): JIO<Boolean?>`
  - `CompetitionExecutionService.getRoundProgressionConfig(eventId: UUID, competitionId: UUID): App<ServiceError, ApiResponse.Dto<RoundProgressionConfigDto>>`
  - `CompetitionExecutionService.updateRoundProgressionConfig(competitionId: UUID, userId: UUID, request: RoundProgressionConfigRequest): App<ServiceError, ApiResponse.NoData>`
  - DTO-Felder `RoundProgressionConfigDto(autoCreateFollowingRounds: Boolean?, eventAutoCreateFollowingRounds: Boolean, effective: Boolean)`

- [ ] **Step 1: Repo-Lesestellen ergänzen**

In `EventRepo.kt`, direkt unter `getChainProgressionMode`:

```kotlin
    /** Nie null: die Spalte ist not-null mit Default, ein fehlendes Event fällt sicher auf "aus" zurück. */
    fun getAutoCreateFollowingRounds(eventId: UUID) = Jooq.query {
        select(EVENT.AUTO_CREATE_FOLLOWING_ROUNDS)
            .from(EVENT)
            .where(EVENT.ID.eq(eventId))
            .fetchOne(EVENT.AUTO_CREATE_FOLLOWING_ROUNDS) ?: false
    }
```

In `CompetitionRepo.kt` (Import `org.jooq.impl.DSL.select` bzw. `Jooq.query` wie im Bestand):

```kotlin
    /**
     * Die Übersteuerung dieses Wettkampfs. `null` heißt "der Veranstaltung folgen" und ist damit
     * etwas anderes als `false` ("ausdrücklich aus") — die Unterscheidung darf hier nicht verloren
     * gehen, sonst kann ein Wettkampf die eingeschaltete Veranstaltung nicht mehr abwählen.
     */
    fun getAutoCreateFollowingRounds(competitionId: UUID) = Jooq.query {
        select(COMPETITION.AUTO_CREATE_FOLLOWING_ROUNDS)
            .from(COMPETITION)
            .where(COMPETITION.ID.eq(competitionId))
            .fetchOne(COMPETITION.AUTO_CREATE_FOLLOWING_ROUNDS)
    }

    fun updateAutoCreateFollowingRounds(competitionId: UUID, value: Boolean?, userId: UUID) =
        COMPETITION.update(
            f = {
                autoCreateFollowingRounds = value
                updatedBy = userId
                updatedAt = LocalDateTime.now()
            },
            condition = { ID.eq(competitionId) },
        )
```

Prüfe die im Bestand vorhandenen Import- und Hilfsformen (`COMPETITION.update { }` gibt es in
`CompetitionRepo` bereits in ähnlicher Form) und passe die Schreibweise daran an.

- [ ] **Step 2: Veranstaltungseinstellung durch DTO und Requests ziehen**

In `EventDto.kt` nach `chainProgressionMode`:

```kotlin
    val autoCreateFollowingRounds: Boolean,
```

Dasselbe Feld in `CreateEventRequest.kt` und `UpdateEventRequest.kt` ergänzen — jeweils auch im
`example`-Block am Dateiende, wo `chainProgressionMode = ChainProgressionMode.DEAKTIVIERT` steht,
mit `autoCreateFollowingRounds = false`.

In `event/control/Conversions.kt` beide Richtungen ergänzen (Zeile ~42 schreibt in den Record,
Zeile ~81 liest heraus): `autoCreateFollowingRounds = autoCreateFollowingRounds`.

In `EventService.kt` an der Stelle, an der `chainProgressionMode = request.chainProgressionMode.name`
steht (Zeile ~98), ergänzen: `autoCreateFollowingRounds = request.autoCreateFollowingRounds`.

- [ ] **Step 3: DTO und Request der Wettkampf-Übersteuerung anlegen**

Neue Datei
`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/entity/RoundProgressionConfigDto.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

/**
 * Wie dieser Wettkampf zur Folgerunden-Automatik steht — und was daraus tatsächlich folgt.
 *
 * Alle drei Angaben zusammen, weil die Oberfläche sie zusammen braucht: Sie zeigt die eigene Wahl,
 * daneben den geerbten Wert ("Veranstaltung: an") und stellt sicher, dass niemand raten muss, was
 * am Ende gilt.
 */
data class RoundProgressionConfigDto(
    /** Die eigene Wahl. null = der Veranstaltung folgen. */
    val autoCreateFollowingRounds: Boolean?,
    /** Was die Veranstaltung vorgibt — nur zur Anzeige. */
    val eventAutoCreateFollowingRounds: Boolean,
    /** Was daraus folgt. Vom Backend gerechnet, damit die Regel nicht im Frontend zweitgeschrieben wird. */
    val effective: Boolean,
)

data class RoundProgressionConfigRequest(
    val autoCreateFollowingRounds: Boolean?,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example get() = RoundProgressionConfigRequest(autoCreateFollowingRounds = null)
    }
}
```

Prüfe die Signatur von `Validatable`/`ValidationResult` an einem Bestandsbeispiel, etwa
`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/timingConfig/entity/TimingConfigRequest.kt`,
und übernimm dessen Form.

- [ ] **Step 4: Service-Funktionen ergänzen**

In `CompetitionExecutionService.kt`, direkt hinter `getProgress`:

```kotlin
    /**
     * Wie dieser Wettkampf zur Folgerunden-Automatik steht. Der wirksame Wert wird hier gerechnet
     * und nicht im Frontend, damit die Vererbungsregel an genau einer Stelle steht.
     */
    fun getRoundProgressionConfig(
        eventId: UUID,
        competitionId: UUID,
    ): App<ServiceError, ApiResponse.Dto<RoundProgressionConfigDto>> = KIO.comprehension {
        val eventDefault = !EventRepo.getAutoCreateFollowingRounds(eventId).orDie()
        val override = !CompetitionRepo.getAutoCreateFollowingRounds(competitionId).orDie()

        KIO.ok(
            ApiResponse.Dto(
                RoundProgressionConfigDto(
                    autoCreateFollowingRounds = override,
                    eventAutoCreateFollowingRounds = eventDefault,
                    effective = AutoRoundProgressionLogic.effectiveAutoCreate(eventDefault, override),
                )
            )
        )
    }

    fun updateRoundProgressionConfig(
        competitionId: UUID,
        userId: UUID,
        request: RoundProgressionConfigRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !CompetitionRepo.updateAutoCreateFollowingRounds(
            competitionId,
            request.autoCreateFollowingRounds,
            userId,
        ).orDie().onNullFail { CompetitionError.NotFound }

        noData
    }
```

- [ ] **Step 5: Route anlegen und einhängen**

Neue Datei
`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/boundary/roundProgression.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionExecution.entity.RoundProgressionConfigRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

/**
 * Die Folgerunden-Automatik eines Wettkampfs — unterhalb der Wettkampf-Route zu mounten, nach dem
 * Vorbild von `timingConfig()`.
 */
fun Route.roundProgression() {
    route("/roundProgression") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val competitionId = !pathParam("competitionId", uuid)

                CompetitionExecutionService.getRoundProgressionConfig(eventId, competitionId)
            }
        }
        put {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val competitionId = !pathParam("competitionId", uuid)

                val body = !receiveKIO(RoundProgressionConfigRequest.example)
                CompetitionExecutionService.updateRoundProgressionConfig(
                    competitionId = competitionId,
                    userId = user.id!!,
                    request = body,
                )
            }
        }
    }
}
```

In `competition/boundary/competition.kt` direkt neben dem vorhandenen `timingConfig()` (Zeile 118)
ergänzen: `roundProgression()` samt Import.

- [ ] **Step 6: OpenAPI ergänzen**

In `backend/src/main/resources/openapi/documentation.yaml`:

1. Bei allen drei Event-Schemata, in denen `chainProgressionMode` steht (Zeilen ~9311, ~9462,
   ~9515 — `EventDto`, `EventRequest`-Varianten), direkt darunter einfügen:

```yaml
        autoCreateFollowingRounds:
          type: boolean
          description: "Creates the pairings of the following round automatically once a round is fully finished"
```

   Steht das Feld in der jeweiligen `required`-Liste der Nachbarn (`chainProgressionMode` taucht in
   `required` bei Zeilen ~13921 und ~14174 auf), dort ebenfalls `- autoCreateFollowingRounds`
   ergänzen.

2. Neuen Pfad direkt hinter `/event/{eventId}/competition/{competitionId}/timing-config` (endet bei
   Zeile ~1681) einfügen:

```yaml
  /event/{eventId}/competition/{competitionId}/roundProgression:
    parameters:
      - $ref: '#/components/parameters/eventId'
      - $ref: '#/components/parameters/competitionId'
    get:
      operationId: getRoundProgressionConfig
      description: Whether this competition creates the pairings of following rounds automatically - its own setting, the event default it inherits from, and what actually applies.
      responses:
        200:
          description: Round progression configuration successfully retrieved
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/RoundProgressionConfigDto'
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
    put:
      operationId: updateRoundProgressionConfig
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/RoundProgressionConfigRequest'
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
        422:
          $ref: '#/components/responses/422'
        500:
          $ref: '#/components/responses/500'
```

3. Neben `TimingConfigDto` in `components.schemas` einfügen:

```yaml
    RoundProgressionConfigDto:
      type: object
      required:
        - eventAutoCreateFollowingRounds
        - effective
      properties:
        autoCreateFollowingRounds:
          type: boolean
          nullable: true
          description: "The competition's own choice. null means it follows the event setting."
        eventAutoCreateFollowingRounds:
          type: boolean
        effective:
          type: boolean
          description: "What actually applies - computed by the backend so the inheritance rule lives in one place."

    RoundProgressionConfigRequest:
      type: object
      properties:
        autoCreateFollowingRounds:
          type: boolean
          nullable: true
```

- [ ] **Step 7: Compile laufen lassen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o -Ddatabase.url=jdbc:postgresql://localhost:7652/r2r-follow-rounds test-compile
```

Erwartet: BUILD SUCCESS. Bricht es an `EventDto`/`CreateEventRequest`-Aufrufstellen ab, fehlt das
neue Feld an einer der `example`-Definitionen oder in `Conversions.kt`.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/kotlin backend/src/main/resources/openapi/documentation.yaml
git commit -m "Folgerunden-Automatik pro Veranstaltung und pro Wettkampf einstellen"
```

---

## Task 4: Vermerk bei der Wiedererzeugung

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionSetup/control/CompetitionSetupRoundRepo.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/control/CompetitionMatchRepo.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/boundary/CompetitionExecutionService.kt` (Funktion `createNewRound`, ab Zeile 115)
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/RoundProgressionFixture.kt` (neu)
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/AutoRoundProgressionServiceTest.kt` (neu, hier nur der erste Fall)

**Interfaces:**
- Produces:
  - `CompetitionSetupRoundRepo.markMaterialized(roundId: UUID, at: LocalDateTime): JIO<Int>`
  - `CompetitionMatchRepo.markPairingsRecalculated(setupMatchIds: List<UUID>, at: LocalDateTime): JIO<Int>`
  - Die Vorrichtung `seedTwoRoundCompetition(...)` (Signatur unten in Step 2), die auch Task 5 und 6
    benutzen.

- [ ] **Step 1: Repo-Schreibstellen ergänzen**

In `CompetitionSetupRoundRepo.kt`:

```kotlin
    /**
     * Hält fest, dass diese Runde gesetzt wurde. Nur beim ersten Mal — der Zeitstempel ist die
     * Auskunft "es gab sie schon einmal" und darf beim Wiederholen nicht vorrücken, sonst ginge
     * genau die Unterscheidung verloren, für die er existiert.
     */
    fun markMaterialized(roundId: UUID, at: LocalDateTime) = COMPETITION_SETUP_ROUND.update(
        f = { materializedAt = at },
        condition = { ID.eq(roundId).and(MATERIALIZED_AT.isNull) },
    )
```

In `CompetitionMatchRepo.kt`:

```kotlin
    /** Setzt den Vermerk "Paarung neu berechnet" auf die angegebenen Läufe. */
    fun markPairingsRecalculated(setupMatchIds: List<UUID>, at: LocalDateTime) =
        COMPETITION_MATCH.updateMany(
            f = { pairingsRecalculatedAt = at },
            condition = { COMPETITION_SETUP_MATCH.`in`(setupMatchIds) },
        )

    /**
     * Der Wettkampf, zu dem dieser Lauf gehört — die Kette Lauf → Setup-Lauf → Runde →
     * Eigenschaften → Wettkampf. Die Aufrufer der Automatik kennen nur den Lauf.
     */
    fun getCompetitionId(setupMatchId: UUID) = Jooq.query {
        select(COMPETITION_PROPERTIES.COMPETITION)
            .from(COMPETITION_SETUP_MATCH)
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_ROUND.ID.eq(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_PROPERTIES.ID.eq(COMPETITION_SETUP_ROUND.COMPETITION_SETUP))
            .where(COMPETITION_SETUP_MATCH.ID.eq(setupMatchId))
            .fetchOne(COMPETITION_PROPERTIES.COMPETITION)
    }
```

Die genauen Hilfsformen (`update`, `updateMany`, `Jooq.query`) sind im Bestand beider Dateien
vorhanden — übernimm die dortige Schreibweise samt Importen.

- [ ] **Step 2: Die Datenbank-Vorrichtung schreiben**

Neue Datei
`backend/src/test/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/RoundProgressionFixture.kt`.
Vorbild ist `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/club/ClubChainFixture.kt` —
lies sie zuerst und übernimm Aufbau und Importstil.

Aufzubauen ist eine Veranstaltung mit einem Wettkampf und einer **zweistufigen** Kette:

- `event` (Name „Testregatta", `autoCreateFollowingRounds` als Parameter)
- `competition` (`autoCreateFollowingRounds` als Parameter, Vorgabe `null`)
- `competition_properties`, `competition_setup`
- Runde A „Vorlauf": `required = true`, `useDefaultSeeding = true`,
  `placesOption = CompetitionSetupPlacesOption.EQUAL.name`, `nextRound` zeigt auf Runde B
- Runde B „Finale": `required = true`, `nextRound = null`
- Runde A: zwei `competition_setup_match` (`weighting` 1 und 2, `teams = 2`, `executionOrder` 1/2)
- Runde B: ein `competition_setup_match` (`weighting = 1`, `teams = 2`, `executionOrder = 1`)
- Für Runde B zwei `competition_setup_participant`: `seed = 1, ranking = 1` und `seed = 2,
  ranking = 2`, beide auf den Setup-Lauf von Runde B
- ein `club`, eine `event_registration`, vier `competition_registration` mit `teamNumber` 1..4
- **nur für Runde A** je Setup-Lauf ein `competition_match` und je zwei `competition_match_team`
  (Startnummern 1 und 2, Plätze zunächst `null`)

Die Vorrichtung hat genau diese Signatur — Task 5 und Task 6 rufen sie so auf:

```kotlin
fun TestComprehensionScope<JEnv>.seedTwoRoundCompetition(
    eventAutoCreate: Boolean = false,
    competitionAutoCreate: Boolean? = null,
): SeededRoundProgression
```

Sie gibt dieses Datenobjekt zurück:

```kotlin
data class SeededRoundProgression(
    val eventId: UUID,
    val competitionId: UUID,
    val firstRoundId: UUID,
    val secondRoundId: UUID,
    val firstRoundMatchIds: List<UUID>,
    val secondRoundSetupMatchId: UUID,
    val registrationIds: List<UUID>,
    val userId: UUID,
)
```

`userId` ist eine feste `UUID.randomUUID()`; `competition_match.created_by` ist nullable mit
`on delete set null`, ein App-Benutzer muss also nicht angelegt werden — setze `createdBy`/
`updatedBy` in der Vorrichtung auf `null` und gib die `userId` nur als Aufrufparameter für die
Services zurück.

Dazu eine Hilfsfunktion in derselben Datei, die eine Runde fertig wertet:

```kotlin
/**
 * Trägt Plätze ein und beendet beide Läufe der ersten Runde — der Zustand, in dem die Automatik
 * greifen muss.
 */
fun TestComprehensionScope<JEnv>.finishFirstRound(seed: SeededRoundProgression, at: LocalDateTime) {
    seed.firstRoundMatchIds.forEach { matchId ->
        val teams = !COMPETITION_MATCH_TEAM.select { COMPETITION_MATCH.eq(matchId) }
        teams.sortedBy { it.startNumber }.forEachIndexed { index, team ->
            !COMPETITION_MATCH_TEAM.update(
                f = { place = index + 1 },
                condition = { ID.eq(team.id) },
            )
        }
        !COMPETITION_MATCH.update(
            f = { finishedAt = at },
            condition = { COMPETITION_SETUP_MATCH.eq(matchId) },
        )
    }
}
```

Die genauen Repo-Hilfsformen (`select`, `update` auf Tabellenreferenzen) stehen in
`backend/src/main/kotlin/de/lambda9/ready2race/backend/database/` — nutze dieselben wie
`ClubChainFixture.kt`.

- [ ] **Step 3: Den scheiternden Test schreiben**

Neue Datei
`backend/src/test/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/AutoRoundProgressionServiceTest.kt`
mit zunächst diesem einen Fall:

```kotlin
    /**
     * Die erste Erzeugung ist keine Wiederholung: Sie merkt sich die Runde, setzt aber keinen
     * Vermerk. Ein Hinweis „Paarung neu berechnet" am allerersten Finale wäre schlicht falsch.
     */
    @Test
    fun theFirstCreationLeavesNoNotice() = testComprehension {
        val seed = seedTwoRoundCompetition()
        finishFirstRound(seed, at = LocalDateTime.of(2026, 8, 14, 10, 30))

        !CompetitionExecutionService.createNewRound(seed.eventId, seed.competitionId, seed.userId)

        val finalMatch = !COMPETITION_MATCH.selectOne { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) }
        assertNotNull(finalMatch, "Das Finale hätte gesetzt werden müssen")
        assertNull(finalMatch.pairingsRecalculatedAt)

        val round = !COMPETITION_SETUP_ROUND.selectOne { ID.eq(seed.secondRoundId) }
        assertNotNull(round?.materializedAt, "Die Runde hätte als gesetzt vermerkt werden müssen")
    }

    /**
     * Nach Löschen und Neuerzeugung trägt jeder Lauf den Vermerk. Genau daran erkennen Admins und
     * Schiedsrichter, dass sich unter ihnen etwas verschoben hat — die Runde sieht sonst aus wie
     * jede andere.
     */
    @Test
    fun aSecondCreationCarriesTheNotice() = testComprehension {
        val seed = seedTwoRoundCompetition()
        finishFirstRound(seed, at = LocalDateTime.of(2026, 8, 14, 10, 30))

        !CompetitionExecutionService.createNewRound(seed.eventId, seed.competitionId, seed.userId)
        !CompetitionExecutionService.deleteCurrentRound(seed.competitionId, seed.eventId)
        !CompetitionExecutionService.createNewRound(seed.eventId, seed.competitionId, seed.userId)

        val finalMatch = !COMPETITION_MATCH.selectOne { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) }
        assertNotNull(finalMatch?.pairingsRecalculatedAt, "Der Vermerk hätte gesetzt sein müssen")
    }
```

Die Klassenhülle, Importe und `testComprehension` nach dem Vorbild von
`backend/src/test/kotlin/de/lambda9/ready2race/backend/app/raceclocker/RaceClockerPollRepoTest.kt`
aufbauen.

- [ ] **Step 4: Test laufen lassen und Scheitern sehen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o -Ddatabase.url=jdbc:postgresql://localhost:7652/r2r-follow-rounds test -Dtest=AutoRoundProgressionServiceTest
```

Erwartet: `aSecondCreationCarriesTheNotice` scheitert mit „Der Vermerk hätte gesetzt sein müssen".
`theFirstCreationLeavesNoNotice` scheitert an `materializedAt == null`.

- [ ] **Step 5: Vermerk in `createNewRound` setzen**

In `CompetitionExecutionService.createNewRound` **an genau einer Stelle** — nicht in beiden
Zweigen. Die Schleife erzeugt je Durchlauf eine Runde; der Vermerk gehört ans Ende eines
Durchlaufs, dorthin, wo auch der Namens-Übernahme-Block schon steht (ab Zeile 298). Beide Zweige
liefern ihm nur ihre Lauf-Ids zu.

Schritt 1 — innerhalb der `while`-Schleife, vor dem `if (currentRound == null)`, eine Variable
anlegen:

```kotlin
            // Die Setup-Lauf-Ids der Runde, die dieser Durchlauf erzeugt - Grundlage des Vermerks
            // unten. `createdSetupMatchIds` sammelt über alle Durchläufe hinweg und taugt dafür nicht.
            var createdThisRound: List<UUID> = emptyList()
```

Schritt 2 — in **beiden** Zweigen direkt hinter der jeweils vorhandenen Zeile
`createdSetupMatchIds += matchRecords.map { it.competitionSetupMatch!! }` (Zeile 166 bzw. 241)
ergänzen:

```kotlin
                createdThisRound = matchRecords.map { it.competitionSetupMatch!! }
```

Schritt 3 — am Ende des Schleifenkörpers, direkt hinter dem `if (nextRound != null && !nextRound.isQualification) { … }`-Block:

```kotlin
            // Stand die Runde schon einmal, sind diese Paarungen eine Neuberechnung - und die
            // Orga-Ansichten sollen das sehen. Dass es sie schon einmal gab, weiß nur die
            // Setup-Runde: Sie überlebt das Löschen der Runde, die Läufe tun es nicht
            // (siehe V202608091501).
            if (nextRound != null && createdThisRound.isNotEmpty()) {
                val markedAt = LocalDateTime.now()
                if (nextRound.materializedAt != null) {
                    !CompetitionMatchRepo.markPairingsRecalculated(createdThisRound, markedAt).orDie()
                } else {
                    !CompetitionSetupRoundRepo.markMaterialized(nextRound.setupRoundId, markedAt).orDie()
                }
            }
```

`nextRound` ist im Schleifenkörper nullable deklariert und wird in beiden Zweigen mit `!!`
benutzt — die Prüfung `nextRound != null` oben macht den Zugriff hier ohne weiteres `!!` möglich
(Kotlin verengt den Typ, weil es eine lokale `val` ist).

- [ ] **Step 6: Test laufen lassen und grün sehen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o -Ddatabase.url=jdbc:postgresql://localhost:7652/r2r-follow-rounds test -Dtest=AutoRoundProgressionServiceTest
```

Erwartet: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin backend/src/test/kotlin
git commit -m "Neu berechnete Paarungen am Lauf vermerken"
```

---

## Task 5: Der Auslöser

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/boundary/AutoRoundProgressionService.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/LiveDashboardService.kt` (Funktion `finishMatchInternal`, ab Zeile 447)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/boundary/EventScheduleService.kt` (Funktion `setSlotSkipped`, ab Zeile 215)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/boundary/CompetitionExecutionService.kt` (`updateMatchResult` ab Zeile 623, `updateMatchResultByFile` ab Zeile 695, `applyRaceClockerRows` ab Zeile 1032)
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/AutoRoundProgressionServiceTest.kt` (erweitern)

**Interfaces:**
- Consumes: `AutoRoundProgressionLogic` (Task 2), `EventRepo.getAutoCreateFollowingRounds` und
  `CompetitionRepo.getAutoCreateFollowingRounds` (Task 3), `CompetitionMatchRepo.getCompetitionId`
  (Task 4).
- Produces:
  - `AutoRoundProgressionService.progressIfRoundComplete(eventId: UUID, competitionId: UUID, userId: UUID): App<Nothing, Unit>`
  - `AutoRoundProgressionService.progressAfterMatch(eventId: UUID, matchId: UUID, userId: UUID): App<Nothing, Unit>`

- [ ] **Step 1: Die scheiternden Tests schreiben**

In `AutoRoundProgressionServiceTest.kt` ergänzen:

```kotlin
    /** Der Regelfall: Ist die Runde durch, steht die nächste ohne Zutun. */
    @Test
    fun aFinishedRoundBringsTheNextOne() = testComprehension {
        val seed = seedTwoRoundCompetition(eventAutoCreate = true)
        finishFirstRound(seed, at = LocalDateTime.of(2026, 8, 14, 10, 30))

        !AutoRoundProgressionService.progressIfRoundComplete(seed.eventId, seed.competitionId, seed.userId)

        val finalMatch = !COMPETITION_MATCH.selectOne { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) }
        assertNotNull(finalMatch, "Das Finale hätte gesetzt werden müssen")
    }

    /**
     * Zweimal prüfen erzeugt nicht zweimal. Die Automatik hängt an fünf Auslösern, von denen
     * mehrere kurz hintereinander feuern können — doppelte Paarungen wären am Renntag nicht mehr
     * einzufangen.
     */
    @Test
    fun checkingTwiceCreatesTheRoundOnce() = testComprehension {
        val seed = seedTwoRoundCompetition(eventAutoCreate = true)
        finishFirstRound(seed, at = LocalDateTime.of(2026, 8, 14, 10, 30))

        !AutoRoundProgressionService.progressIfRoundComplete(seed.eventId, seed.competitionId, seed.userId)
        !AutoRoundProgressionService.progressIfRoundComplete(seed.eventId, seed.competitionId, seed.userId)

        val matches = !COMPETITION_MATCH.select { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) }
        assertEquals(1, matches.size)
    }

    /**
     * Erzeugen heißt nicht aufrufen. Ob und wann ein Lauf an den Start geht, entscheidet weiter
     * die Zeitstrahl-Kette — die Automatik darf dem nicht vorgreifen.
     */
    @Test
    fun theNewRoundIsNotActivated() = testComprehension {
        val seed = seedTwoRoundCompetition(eventAutoCreate = true)
        finishFirstRound(seed, at = LocalDateTime.of(2026, 8, 14, 10, 30))

        !AutoRoundProgressionService.progressIfRoundComplete(seed.eventId, seed.competitionId, seed.userId)

        val finalMatch = !COMPETITION_MATCH.selectOne { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) }
        assertNull(finalMatch?.activatedAt)
        assertNull(finalMatch?.startedAt)
    }

    /** Eine halbe Runde reicht nicht: ein unbeendeter Lauf hält alles an. */
    @Test
    fun anUnfinishedMatchHoldsEverything() = testComprehension {
        val seed = seedTwoRoundCompetition(eventAutoCreate = true)
        finishFirstRound(seed, at = LocalDateTime.of(2026, 8, 14, 10, 30))
        // Den zweiten Lauf wieder öffnen - so sieht ein Zwischenstand aus.
        !COMPETITION_MATCH.update(
            f = { finishedAt = null },
            condition = { COMPETITION_SETUP_MATCH.eq(seed.firstRoundMatchIds.last()) },
        )

        !AutoRoundProgressionService.progressIfRoundComplete(seed.eventId, seed.competitionId, seed.userId)

        val matches = !COMPETITION_MATCH.select { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) }
        assertEquals(0, matches.size)
    }

    /** Ausgeschaltete Veranstaltung heißt: gar nichts passiert. */
    @Test
    fun theSettingOffCreatesNothing() = testComprehension {
        val seed = seedTwoRoundCompetition(eventAutoCreate = false)
        finishFirstRound(seed, at = LocalDateTime.of(2026, 8, 14, 10, 30))

        !AutoRoundProgressionService.progressIfRoundComplete(seed.eventId, seed.competitionId, seed.userId)

        val matches = !COMPETITION_MATCH.select { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) }
        assertEquals(0, matches.size)
    }

    /** Der Wettkampf schlägt die Veranstaltung — in beide Richtungen. */
    @Test
    fun theCompetitionOverridesTheEvent() = testComprehension {
        val off = seedTwoRoundCompetition(eventAutoCreate = true, competitionAutoCreate = false)
        finishFirstRound(off, at = LocalDateTime.of(2026, 8, 14, 10, 30))
        !AutoRoundProgressionService.progressIfRoundComplete(off.eventId, off.competitionId, off.userId)
        assertEquals(0, (!COMPETITION_MATCH.select { COMPETITION_SETUP_MATCH.eq(off.secondRoundSetupMatchId) }).size)

        val on = seedTwoRoundCompetition(eventAutoCreate = false, competitionAutoCreate = true)
        finishFirstRound(on, at = LocalDateTime.of(2026, 8, 14, 10, 30))
        !AutoRoundProgressionService.progressIfRoundComplete(on.eventId, on.competitionId, on.userId)
        assertEquals(1, (!COMPETITION_MATCH.select { COMPETITION_SETUP_MATCH.eq(on.secondRoundSetupMatchId) }).size)
    }
```

Die Parameter `eventAutoCreate` und `competitionAutoCreate` trägt `seedTwoRoundCompetition` bereits
seit Task 4 — sie schreiben `event.auto_create_following_rounds` bzw.
`competition.auto_create_following_rounds`.

- [ ] **Step 2: Tests laufen lassen und Scheitern sehen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o -Ddatabase.url=jdbc:postgresql://localhost:7652/r2r-follow-rounds test -Dtest=AutoRoundProgressionServiceTest
```

Erwartet: Compile-Fehler „Unresolved reference: AutoRoundProgressionService".

- [ ] **Step 3: Den Service schreiben**

Neue Datei
`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/boundary/AutoRoundProgressionService.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.recover
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

/**
 * Der Auslöser der Folgerunden-Automatik: Nach jeder Änderung am Zustand eines Laufs wird gefragt,
 * ob dessen Runde damit durch ist — und wenn ja, die nächste gesetzt.
 *
 * Die Rechnung selbst steht unverändert in [CompetitionExecutionService.createNewRound] und ist
 * formatunabhängig. Dieser Service entscheidet ausschließlich, WANN sie läuft.
 *
 * Er aktiviert bewusst nichts: `createNewRound` stößt am Ende die Zeitstrahl-Kette an
 * (`ScheduleChainService.resumeIfParked`), und die entscheidet nach `chain_progression_mode`, ob
 * überhaupt ein Lauf an den Start gerufen wird. Steht die Veranstaltung auf DEAKTIVIERT, passiert
 * genau nichts — die Automatik fügt keine Aktivierung hinzu, die es vorher nicht gab.
 */
object AutoRoundProgressionService {

    private val logger = KotlinLogging.logger {}

    /**
     * Derselbe Ablauf, wenn nur der Lauf bekannt ist. Die Aufrufer aus dem Schiedsrichter-Dashboard
     * und aus dem Zeitplan kennen den Wettkampf nicht; ihn dort zu ermitteln hieße, dieselbe
     * Join-Kette an drei Stellen zu schreiben.
     */
    fun progressAfterMatch(eventId: UUID, matchId: UUID, userId: UUID): App<Nothing, Unit> =
        KIO.comprehension {
            val competitionId = !CompetitionMatchRepo.getCompetitionId(matchId).orDie()
                ?: return@comprehension KIO.unit

            progressIfRoundComplete(eventId, competitionId, userId)
        }

    /**
     * Prüft und erzeugt. Jeder Abbruchgrund ist ein stilles `unit` — die Automatik ist eine
     * Bequemlichkeit obendrauf und darf den Aufrufer nie scheitern lassen.
     *
     * Die Idempotenz fällt strukturell an: `getCurrentAndNextRound` erklärt jede Runde, die schon
     * Läufe hat, zur aktuellen. Ein zweiter Aufruf betrachtet also die eben erzeugte Runde, findet
     * sie unbeendet und tut nichts. Es gibt keinen Pfad, auf dem dieselbe Runde zweimal entsteht.
     */
    fun progressIfRoundComplete(eventId: UUID, competitionId: UUID, userId: UUID): App<Nothing, Unit> =
        KIO.comprehension {
            val eventDefault = !EventRepo.getAutoCreateFollowingRounds(eventId).orDie()
            val override = !CompetitionRepo.getAutoCreateFollowingRounds(competitionId).orDie()
            if (!AutoRoundProgressionLogic.effectiveAutoCreate(eventDefault, override)) {
                return@comprehension KIO.unit
            }

            // Wettkämpfe einer Challenge-Veranstaltung kennen keine Runden; createNewRound
            // verweigert dort ohnehin.
            val isChallenge = !EventService.checkIsChallengeEvent(eventId).orDie()
            if (isChallenge) {
                return@comprehension KIO.unit
            }

            // Ein Wettkampf ohne Ablauf ist kein Fehler dieser Automatik, sondern schlicht keiner,
            // der Runden kennt.
            val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)
                .recoverDefault { emptyList() }
            val (currentRound, nextRound) = CompetitionExecutionService.getCurrentAndNextRound(setupRounds)

            // Ohne aktuelle Runde gäbe es keine abgeschlossene Runde, aus der sich etwas ergeben
            // könnte: Die ERSTE Runde erzeugt weiterhin ein Mensch. Sie hängt an finalisierten
            // Meldungen, nicht an einem Rundenabschluss - und der Dialog dort macht bewusst darauf
            // aufmerksam, dass Meldungen ohne Startnummer unter den Tisch fallen.
            if (currentRound == null || nextRound == null) {
                return@comprehension KIO.unit
            }

            if (!AutoRoundProgressionLogic.roundIsComplete(currentRound)) {
                return@comprehension KIO.unit
            }

            // Scheitert die Erzeugung - etwa weil der Ablauf der nächsten Runde zu wenig Bahnen hat
            // -, darf das den Aufrufer nicht mitreißen. Der Lauf IST gefahren und beendet; das ist
            // eine Tatsache, die nicht an einer Setup-Lücke hängen darf. Der Knopf im
            // Durchführungs-Tab meldet denselben Fehler weiterhin sichtbar.
            !CompetitionExecutionService.createNewRound(eventId, competitionId, userId)
                .recoverDefault { error ->
                    logger.warn { "Folgerunde für Wettkampf $competitionId konnte nicht erzeugt werden: $error" }
                    ApiResponse.NoData
                }

            KIO.unit
        }
}
```

Importe dafür: `de.lambda9.tailwind.core.extensions.kio.recoverDefault` und
`de.lambda9.ready2race.backend.calls.responses.ApiResponse`. `recoverDefault` ist die im Bestand
benutzte Form (siehe `Application.kt` und `calls/requests/Extensions.kt`); es nimmt den Fehler und
gibt einen Ersatzwert desselben Ergebnistyps zurück — hier `ApiResponse.NoData`, weil
`createNewRound` ein `App<ServiceError, ApiResponse.NoData>` ist.

`EventService.checkIsChallengeEvent(eventId)` ist `App<EventError, Boolean>` — `.orDie()` ist
deshalb richtig: Eine fehlende Veranstaltung ist hier ein Programmfehler, kein Betriebsfall.

- [ ] **Step 4: Tests laufen lassen und grün sehen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o -Ddatabase.url=jdbc:postgresql://localhost:7652/r2r-follow-rounds test -Dtest=AutoRoundProgressionServiceTest
```

Erwartet: `Tests run: 8, Failures: 0, Errors: 0`.

- [ ] **Step 5: Die fünf Auslöser verdrahten**

In `LiveDashboardService.finishMatchInternal`, ganz am Ende direkt vor `KIO.unit`:

```kotlin
        // Ist die Runde mit diesem Lauf durch, steht die nächste ohne Zutun. Bewusst NACH der
        // Kette: createNewRound stößt sie selbst noch einmal an, wenn sie an einem wartenden Slot
        // geparkt war.
        !AutoRoundProgressionService.progressAfterMatch(eventId, matchId, userId)
```

In `EventScheduleService.setSlotSkipped`, nach dem bestehenden Schreibvorgang und vor der Antwort —
ein abgesagter Lauf kann der letzte fehlende der Runde sein:

```kotlin
        !AutoRoundProgressionService.progressAfterMatch(eventId, setupMatchId, userId)
```

Der Name der Variable, die dort den Setup-Lauf trägt, steht in der Funktion; nimm sie und nicht die
Slot-Id. Hat der Slot keinen Lauf (`competition_setup_match is null`), überspringe den Aufruf.

In `CompetitionExecutionService` am Ende von `updateMatchResult`, `updateMatchResultByFile` und
`applyRaceClockerRows` jeweils vor der Rückgabe:

```kotlin
        // Ein Lauf kann beendet sein und erst mit dieser Eingabe vollständig gewertet werden -
        // dann ist das Ergebnis der letzte fehlende Baustein der Runde.
        !AutoRoundProgressionService.progressIfRoundComplete(eventId, competitionId, userId)
```

`applyRaceClockerRows` bekommt Event- und Wettkampf-Id möglicherweise nicht als Parameter; nutze
dort `AutoRoundProgressionService.progressAfterMatch(eventId, matchId, userId)`, falls nur der Lauf
zur Verfügung steht.

- [ ] **Step 6: Gesamten Backend-Testlauf**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o -Ddatabase.url=jdbc:postgresql://localhost:7652/r2r-follow-rounds test
```

Erwartet: BUILD SUCCESS, keine neuen Fehlschläge.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin backend/src/test/kotlin
git commit -m "Folgerunde nach abgeschlossener Runde von selbst erzeugen"
```

---

## Task 6: Korrektur und Schutz gestarteter Läufe

**Files:**
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/AutoRoundProgressionServiceTest.kt` (erweitern)

Diese Aufgabe schreibt **keinen** Produktionscode. Sie belegt, dass der Schutz aus dem Entwurf
tatsächlich greift — und schlägt fehl, falls eine der vorherigen Aufgaben ihn aufgeweicht hat.

**Interfaces:**
- Consumes: alles aus Task 4 und 5.

- [ ] **Step 1: Die Tests schreiben**

```kotlin
    /**
     * Der Weg zur Korrektur: Folgerunde löschen, Ergebnis richtigstellen, Automatik rechnet neu.
     * Die neuen Paarungen tragen den Vermerk, an dem die Orga sieht, dass sich etwas verschoben hat.
     */
    @Test
    fun aCorrectionAfterDeletingRecreatesThePairingsWithANotice() = testComprehension {
        val seed = seedTwoRoundCompetition(eventAutoCreate = true)
        finishFirstRound(seed, at = LocalDateTime.of(2026, 8, 14, 10, 30))
        !AutoRoundProgressionService.progressIfRoundComplete(seed.eventId, seed.competitionId, seed.userId)

        !CompetitionExecutionService.deleteCurrentRound(seed.competitionId, seed.eventId)

        // Die Plätze des ersten Laufs tauschen - so sieht eine Korrektur aus.
        val firstMatchId = seed.firstRoundMatchIds.first()
        val teams = (!COMPETITION_MATCH_TEAM.select { COMPETITION_MATCH.eq(firstMatchId) }).sortedBy { it.startNumber }
        !CompetitionExecutionService.updateMatchResult(
            eventId = seed.eventId,
            competitionId = seed.competitionId,
            matchId = firstMatchId,
            userId = seed.userId,
            request = UpdateCompetitionMatchResultRequest(
                teamResults = listOf(
                    UpdateCompetitionMatchTeamResultRequest(
                        registrationId = teams[0].competitionRegistration!!,
                        place = 2,
                        timeString = null,
                        failed = false,
                        failedReason = null,
                        penaltySeconds = null,
                        penaltyNote = null,
                    ),
                    UpdateCompetitionMatchTeamResultRequest(
                        registrationId = teams[1].competitionRegistration!!,
                        place = 1,
                        timeString = null,
                        failed = false,
                        failedReason = null,
                        penaltySeconds = null,
                        penaltyNote = null,
                    ),
                )
            ),
        )

        val finalMatch = !COMPETITION_MATCH.selectOne { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) }
        assertNotNull(finalMatch, "Die Folgerunde hätte neu gesetzt werden müssen")
        assertNotNull(finalMatch.pairingsRecalculatedAt, "Die Neuberechnung hätte vermerkt werden müssen")
    }

    /**
     * Steht die Folgerunde bereits, ist die davor gesperrt — dieselbe Antwort wie vor der
     * Automatik. Genau darauf beruht der Schutz gestarteter Läufe: Die Automatik kann eine
     * bestehende Runde nicht überschreiben, weil an ihr Ergebnis gar nicht mehr heranzukommen ist.
     */
    @Test
    fun aStartedFollowingRoundLocksTheRoundBefore() = testComprehension {
        val seed = seedTwoRoundCompetition(eventAutoCreate = true)
        finishFirstRound(seed, at = LocalDateTime.of(2026, 8, 14, 10, 30))
        !AutoRoundProgressionService.progressIfRoundComplete(seed.eventId, seed.competitionId, seed.userId)

        !COMPETITION_MATCH.update(
            f = { startedAt = LocalDateTime.of(2026, 8, 14, 11, 0) },
            condition = { COMPETITION_SETUP_MATCH.eq(seed.secondRoundSetupMatchId) },
        )

        // Über den echten Schreibweg geprüft, nicht über die interne Prüffunktion: Was zählt, ist
        // dass die Korrektur nicht durchgeht - nicht, dass eine private Hilfsfunktion nein sagt.
        val firstMatchId = seed.firstRoundMatchIds.first()
        val teams = (!COMPETITION_MATCH_TEAM.select { COMPETITION_MATCH.eq(firstMatchId) }).sortedBy { it.startNumber }
        assertKIOFails(CompetitionExecutionError.MatchResultsLocked) {
            CompetitionExecutionService.updateMatchResult(
                eventId = seed.eventId,
                competitionId = seed.competitionId,
                matchId = firstMatchId,
                userId = seed.userId,
                request = UpdateCompetitionMatchResultRequest(
                    teamResults = listOf(
                        UpdateCompetitionMatchTeamResultRequest(
                            registrationId = teams[0].competitionRegistration!!,
                            place = 2,
                            timeString = null,
                            failed = false,
                            failedReason = null,
                            penaltySeconds = null,
                            penaltyNote = null,
                        ),
                        UpdateCompetitionMatchTeamResultRequest(
                            registrationId = teams[1].competitionRegistration!!,
                            place = 1,
                            timeString = null,
                            failed = false,
                            failedReason = null,
                            penaltySeconds = null,
                            penaltyNote = null,
                        ),
                    )
                ),
            )
        }

        // Und der Beleg, dass wirklich nichts passiert ist: die Plätze stehen wie vorher.
        val after = (!COMPETITION_MATCH_TEAM.select { COMPETITION_MATCH.eq(firstMatchId) }).sortedBy { it.startNumber }
        assertEquals(listOf(1, 2), after.map { it.place })
    }
```

Die Sichtbarkeit von `checkUpdateMatchResult` bleibt unverändert `private` — der Test kommt über
den öffentlichen Schreibweg an dieselbe Sperre.

Die Felder von `UpdateCompetitionMatchTeamResultRequest` sind gegen den Bestand geprüft
(`registrationId`, `place`, `timeString`, `failed`, `failedReason`, `penaltySeconds`,
`penaltyNote`). Achte auf dessen Validierung: Entweder haben **alle** Boote einen Platz oder
**keines** — die beiden Einträge oben setzen deshalb beide einen.

- [ ] **Step 2: Tests laufen lassen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o -Ddatabase.url=jdbc:postgresql://localhost:7652/r2r-follow-rounds test -Dtest=AutoRoundProgressionServiceTest
```

Erwartet: `Tests run: 10, Failures: 0, Errors: 0`.

Schlägt `aCorrectionAfterDeletingRecreatesThePairingsWithANotice` fehl, weil die Folgerunde nicht
neu entsteht: Der Auslöser in `updateMatchResult` aus Task 5 Step 5 fehlt.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin backend/src/test/kotlin
git commit -m "Korrekturweg und Sperre gestarteter Runden belegen"
```

---

## Task 7: DTO-Feld und Oberfläche

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/entity/CompetitionMatchDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/entity/LiveDashboardDto.kt` (`LiveDashboardMatchDto`)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/control/Conversions.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/LiveDashboardService.kt` (`buildMatchDto`, ab Zeile 208)
- Modify: `backend/src/main/resources/openapi/documentation.yaml`
- Create: `frontend/src/components/event/competition/excecution/roundProgressionForm.ts`
- Create: `frontend/src/components/event/competition/excecution/roundProgressionForm.test.ts`
- Create: `frontend/src/components/event/competition/excecution/RoundProgressionSetting.tsx`
- Modify: `frontend/src/components/event/EventDialog.tsx`
- Modify: `frontend/src/components/event/competition/excecution/CompetitionExecution.tsx`
- Modify: `frontend/src/components/event/competition/excecution/CompetitionExecutionRound.tsx`
- Modify: `frontend/src/components/event/liveDashboard/LiveDashboardMatchCard.tsx`
- Modify: `frontend/src/i18n/de/translations.json`, `.../en/translations.json`, `.../da/translations.json`

**Interfaces:**
- Consumes: `pairingsRecalculatedAt` aus Task 1, die Route aus Task 3.
- Produces: `pairingsRecalculatedAt?: string | null` in beiden Match-DTOs des generierten Clients.

- [ ] **Step 1: Backend-DTOs erweitern**

In `CompetitionMatchDto.kt` nach `raceClockerAutoPausedAt`:

```kotlin
    /**
     * Gesetzt, wenn diese Paarung aus einer Neuberechnung stammt — und nur, solange der Lauf noch
     * nicht an den Start gerufen wurde. Ab da hat sich die Frage erledigt: Wer am Start steht,
     * fährt in dieser Aufstellung.
     */
    val pairingsRecalculatedAt: LocalDateTime?,
```

Dasselbe Feld in `LiveDashboardMatchDto` in `LiveDashboardDto.kt` ergänzen.

In `competitionExecution/control/Conversions.kt`, in der Funktion, die `CompetitionMatchDto` baut
(um Zeile 160), setzen:

```kotlin
                // Der Vermerk verschwindet mit der Aktivierung. Die Regel steht hier und nicht im
                // Frontend, weil sie sonst in zwei Oberflächen doppelt stünde.
                pairingsRecalculatedAt = match.pairingsRecalculatedAt?.takeIf { match.activatedAt == null },
```

In `LiveDashboardService.buildMatchDto` dieselbe Zuweisung ergänzen; das Feld kommt dort aus dem
`Record` (`COMPETITION_MATCH.PAIRINGS_RECALCULATED_AT`) — prüfe, ob die dortige Abfrage die Spalte
bereits mitliest, und ergänze sie andernfalls in `LiveDashboardRepo`.

- [ ] **Step 2: OpenAPI ergänzen und Client neu erzeugen**

In `documentation.yaml` in den Schemata `CompetitionMatchDto` und `LiveDashboardMatchDto` jeweils
ergänzen:

```yaml
        pairingsRecalculatedAt:
          type: string
          format: date-time
          nullable: true
          description: "Set while this match's pairing comes from a recalculation and the match has not been called to the start yet"
```

Dann:

```bash
cd frontend && npm run generate
```

Erwartet: `frontend/src/api/types.gen.ts` enthält danach `pairingsRecalculatedAt`.

- [ ] **Step 3: Reine Frontend-Logik mit Test**

Neue Datei `frontend/src/components/event/competition/excecution/roundProgressionForm.ts`:

```ts
import {RoundProgressionConfigDto, RoundProgressionConfigRequest} from '@api/types.gen.ts'

/** Die drei Zustände als Auswahlwert — `null` lässt sich in einem Radio nicht abbilden. */
export type RoundProgressionChoice = 'INHERIT' | 'ENABLED' | 'DISABLED'

export const choiceFromDto = (dto: RoundProgressionConfigDto): RoundProgressionChoice =>
    dto.autoCreateFollowingRounds === null || dto.autoCreateFollowingRounds === undefined
        ? 'INHERIT'
        : dto.autoCreateFollowingRounds
          ? 'ENABLED'
          : 'DISABLED'

export const requestFromChoice = (choice: RoundProgressionChoice): RoundProgressionConfigRequest => ({
    autoCreateFollowingRounds:
        choice === 'INHERIT' ? null : choice === 'ENABLED',
})

/**
 * Was aus der Wahl folgt. Das Backend rechnet denselben Wert und schickt ihn mit; diese Funktion
 * ist nur für die Vorschau, solange der Nutzer noch nicht gespeichert hat.
 */
export const effectiveFromChoice = (choice: RoundProgressionChoice, eventDefault: boolean): boolean =>
    choice === 'INHERIT' ? eventDefault : choice === 'ENABLED'
```

Neue Datei `frontend/src/components/event/competition/excecution/roundProgressionForm.test.ts`:

```ts
import {describe, expect, test} from 'vitest'
import {choiceFromDto, effectiveFromChoice, requestFromChoice} from './roundProgressionForm.ts'

describe('roundProgressionForm', () => {
    test('null am Wettkampf heißt: der Veranstaltung folgen', () => {
        expect(
            choiceFromDto({
                autoCreateFollowingRounds: null,
                eventAutoCreateFollowingRounds: true,
                effective: true,
            }),
        ).toBe('INHERIT')
    })

    test('die ausdrückliche Wahl bleibt erhalten', () => {
        expect(
            choiceFromDto({
                autoCreateFollowingRounds: false,
                eventAutoCreateFollowingRounds: true,
                effective: false,
            }),
        ).toBe('DISABLED')
    })

    test('erben schickt null, nicht false', () => {
        expect(requestFromChoice('INHERIT').autoCreateFollowingRounds).toBeNull()
        expect(requestFromChoice('DISABLED').autoCreateFollowingRounds).toBe(false)
        expect(requestFromChoice('ENABLED').autoCreateFollowingRounds).toBe(true)
    })

    test('die Vorschau folgt der Veranstaltung nur beim Erben', () => {
        expect(effectiveFromChoice('INHERIT', true)).toBe(true)
        expect(effectiveFromChoice('DISABLED', true)).toBe(false)
        expect(effectiveFromChoice('ENABLED', false)).toBe(true)
    })
})
```

Prüfe die Test-Schreibweise an `frontend/src/components/event/competition/timing/timingConfigForm.test.ts`
und übernimm deren Importstil.

- [ ] **Step 4: Frontend-Test laufen lassen**

```bash
cd frontend && npx vitest run src/components/event/competition/excecution/roundProgressionForm.test.ts
```

Erwartet: 4 passed.

- [ ] **Step 5: Veranstaltungs-Schalter**

In `EventDialog.tsx`:
- im Typ `EventForm` nach `chainProgressionMode: ChainProgressionMode` ergänzen:
  `autoCreateFollowingRounds: boolean`
- in `defaultValues` ergänzen: `autoCreateFollowingRounds: false`
- im Formular direkt nach dem `chainProgressionMode`-Block einfügen:

```tsx
                <FormInputCheckbox
                    name={`autoCreateFollowingRounds`}
                    label={t('event.autoCreateFollowingRounds.label')}
                />
                <Typography variant={'body2'} color={'text.secondary'} sx={{mt: -1}}>
                    {t('event.autoCreateFollowingRounds.hint')}
                </Typography>
```

Zusätzlich muss das Feld in den Abbildungsfunktionen `mapDtoToForm`, `mapFormToCreateRequest` und
`mapFormToUpdateRequest` auftauchen — sie liegen in derselben Datei oder in einer
`eventForm`-Hilfsdatei daneben; folge dem Muster von `showBreaksOnPublicBoards`.

- [ ] **Step 6: Wettkampf-Übersteuerung**

Neue Datei `frontend/src/components/event/competition/excecution/RoundProgressionSetting.tsx`: eine
kleine Komponente, die `getRoundProgressionConfig` lädt, die Dreier-Auswahl über
`FormInputRadioButtonGroup` anzeigt (Optionen `INHERIT`/`ENABLED`/`DISABLED` mit den i18n-Schlüsseln
aus Step 8), den geerbten Wert als Hinweistext darunter zeigt und beim Speichern
`updateRoundProgressionConfig` mit `requestFromChoice(...)` ruft. Vorbild für Laden, Speichern und
Rückmeldung ist `frontend/src/components/event/competition/timing/CompetitionTimingConfig.tsx`.

In `CompetitionExecution.tsx` die Komponente neben dem Knopf „Nächste Runde erstellen" einhängen.

- [ ] **Step 7: Der Vermerk-Chip**

In `CompetitionExecutionRound.tsx`, in derselben Zeile wie der RaceClocker-Chip (um Zeile 476):

```tsx
                                    {match.pairingsRecalculatedAt && (
                                        <Typography variant={'caption'} color={'warning.main'}>
                                            {t('event.competition.execution.pairingsRecalculated')}
                                        </Typography>
                                    )}
```

In `LiveDashboardMatchCard.tsx` denselben Hinweis ergänzen, direkt vor dem `pollStatus`-Block
(um Zeile 233). Das Backend liefert das Feld bereits `null`, sobald der Lauf am Start steht — im
Frontend ist keine zusätzliche Bedingung nötig.

- [ ] **Step 8: Übersetzungen**

In allen drei Dateien `frontend/src/i18n/{de,en,da}/translations.json` ergänzen. Deutsch:

```json
"autoCreateFollowingRounds": {
  "label": "Folgerunden automatisch erzeugen",
  "hint": "Sobald alle Läufe einer Runde beendet und gewertet sind, werden die Paarungen der nächsten Runde von selbst gesetzt. Aktiviert wird dadurch nichts - das bleibt beim Zeitplan.",
  "INHERIT": "Veranstaltung folgen",
  "ENABLED": "Ein",
  "DISABLED": "Aus",
  "inherited": "Veranstaltung: {{value}}"
}
```

und

```json
"pairingsRecalculated": "Paarung neu berechnet"
```

Die englischen und dänischen Fassungen sinngemäß. Die Schlüssel gehören unter `event` bzw. unter
`event.competition.execution` — halte dieselbe Verschachtelung wie die benachbarten Einträge
`chainProgressionMode` und `raceclocker`.

- [ ] **Step 9: Lint und Testlauf**

```bash
cd frontend && npm run lint
```

```bash
cd frontend && npx vitest run
```

Erwartet: keine Fehler, alle Tests grün.

- [ ] **Step 10: Commit**

```bash
git add backend frontend
git commit -m "Automatik einstellen und neu berechnete Paarungen anzeigen"
```

---

## Abschluss

- [ ] **Backend vollständig**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -o -Ddatabase.url=jdbc:postgresql://localhost:7652/r2r-follow-rounds test
```

- [ ] **Frontend vollständig**

```bash
cd frontend && npm run lint && npx vitest run && npm run build
```

- [ ] **Kurzbericht** an den Auftraggeber: was geändert wurde, welche Tests laufen, welche fachlich
  relevanten Randfälle offenblieben (erste Runde bleibt Handarbeit; `deleteCurrentRound` bleibt
  ungesperrt; ein Wettkampf ohne Folgerunde in der Kette löst nie aus).
