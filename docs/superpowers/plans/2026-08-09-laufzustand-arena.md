# Laufzustand „In Vorbereitung" / „Läuft" + Arena-Umbenennung — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Klick auf „aktiv" setzt einen Lauf nur noch auf **In Vorbereitung**; **Läuft** entsteht erst durch einen gemessenen oder ausdrücklich bestätigten Ist-Start — und dieser Zustand ist in allen Oberflächen identisch sichtbar.

**Architecture:** `competition_match.currently_running` (bool) wird zu `activated_at` (timestamp). Der Zustand bleibt **abgeleitet**: `LiveDashboardLogic.deriveMatchState` ist die einzige Stelle, die aus `activated_at` + `started_at` + `finished_at` + `skipped` + Teamergebnissen einen `MatchState` macht, und jede Oberfläche liest nur noch diesen Wert statt sich eine eigene Ableitung zu bauen. Parallel wird die ruderspezifische Sprache („auf dem Wasser", `NOT_ON_WATER`) auf „in der Arena" umgestellt, inklusive Datenmigration des gespeicherten Prüfungstyps.

**Tech Stack:** Kotlin/Ktor + KIO (tailwind-core) + jOOQ + Flyway/PostgreSQL im Backend; React + TypeScript + MUI + vitest im Frontend; `@hey-api/openapi-ts` erzeugt `src/api/*.gen.ts` aus `documentation.yaml`.

**Spec:** `docs/superpowers/specs/2026-08-09-laufzustand-arena-design.md`

## Global Constraints

- **Arbeitsverzeichnis:** `/Users/thomas/Developer/privat/ready2race/.claude/worktrees/laufzustand-arena`, Branch `claude/laufzustand-arena` (zweigt von `feature/crf-2026` ab). **Niemals nach `main` committen oder mergen. Nichts pushen.**
- **Keine AI-Attribution** in Commit-Nachrichten (kein `Co-Authored-By`, keine Claude-Erwähnung). Commit-Betreffzeilen im Stil des Repos: englischer Imperativ-Satz, der die *Wirkung* beschreibt („Let the referee board tell preparation from racing"), nicht `feat:`-Präfixe.
- **Kommentare und Doku auf Deutsch, mit echten Umlauten** (ä, ö, ü, ß) — außer in `.sql`-Migrationen, die dem Bestand folgend ASCII schreiben (`ae`, `oe`, `ue`, `ss`).
- **Backend-Befehle brauchen** `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
- **Dev-Datenbank** muss für jOOQ-Generierung laufen: `cd backend && docker compose up -d`.
- **`backend/.env` und `frontend/.env` fehlen in diesem Worktree** und dürfen nicht kopiert werden. Wenn ein Befehl sie braucht, den Schritt überspringen und im Abschlussbericht melden — nicht selbst anlegen.
- **Migrationsnummern:** höchste bestehende ist `V202608091300`. Dieser Plan belegt `V202608091400` und `V202608091410`. Keine anderen Nummern verwenden.
- **jOOQ-Klassen liegen unter `target/generated-sources/jooq`** und werden **nicht** committet. Nach jeder Migration `./mvnw generate-sources` laufen lassen, damit `COMPETITION_MATCH.ACTIVATED_AT` existiert.
- **`MatchState` ist ein Alias auf `LiveDashboardMatchState`** (`matchStatus/entity/MatchStatusDto.kt:14`). Der Enum-Name bleibt, wie er ist — nur der neue Wert `PREPARING` kommt dazu.
- **Ein neuer Enum-Wert fällt still in jeden `else`-Zweig.** Alle Verzweigungen über `MatchState` sind in diesem Plan namentlich aufgeführt; keine darf ausgelassen werden.

---

## Dateiübersicht

**Neu:**

| Datei | Verantwortung |
|---|---|
| `backend/src/main/resources/db/migration/V202608091400__match_activated_at.sql` | `currently_running` → `activated_at` |
| `backend/src/main/resources/db/migration/V202608091410__check_type_not_in_arena.sql` | `NOT_ON_WATER` → `NOT_IN_ARENA` |
| `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/raceclocker/RaceClockerStartStampTest.kt` | Ist-Start-Stempel für bereits aktivierte Läufe |

**Geändert (Backend, Kern):** `liveDashboard/boundary/LiveDashboardLogic.kt`, `matchStatus/boundary/MatchStatusLogic.kt`, `matchStatus/entity/MatchStatusDto.kt`, `liveDashboard/entity/LiveDashboardDto.kt`, `liveDashboard/boundary/LiveDashboardService.kt`, `liveDashboard/boundary/liveDashboard.kt`, `liveDashboard/control/LiveDashboardRepo.kt`, `eventSchedule/boundary/ScheduleChain.kt`, `eventSchedule/boundary/EventScheduleService.kt`, `eventSchedule/boundary/EventScheduleLogic.kt`, `eventSchedule/control/EventScheduleRepo.kt`, `competitionExecution/boundary/CompetitionExecutionService.kt`, `competitionExecution/boundary/competitionExecution.kt`, `competitionExecution/control/{CompetitionMatchRepo,Conversions}.kt`, `competitionExecution/entity/*.kt`, `raceclocker/**`, `eventInfo/**`, `event/boundary/event.kt`, `resources/openapi/documentation.yaml`, `resources/db/migration/afterMigrate.sql`.

**Geändert (Frontend, Kern):** `components/event/match/matchStatusChip.ts`, `components/event/liveDashboard/{common.ts,LiveDashboardMatchCard.tsx}`, `components/event/schedule/{timelineIndicator.ts,ScheduleTimelineIndicator.tsx,EventSchedule.tsx}`, `components/event/competition/excecution/{CompetitionExecution.tsx,CompetitionExecutionRound.tsx}`, `components/event/match/{DragDropMatchLists.tsx,ManageRunningMatchesDialog.tsx}`, `components/event/info/athleteBoard/AthleteBoardMatchCard.tsx`, `pages/event/LiveDashboardPage.tsx`, `i18n/{de,en,da}/translations.json`, `src/api/*.gen.ts` (generiert).

---

## Task 1: Die Spalte `activated_at`

**Files:**
- Create: `backend/src/main/resources/db/migration/V202608091400__match_activated_at.sql`
- Modify: `backend/src/main/resources/db/migration/afterMigrate.sql:836`

**Interfaces:**
- Produces: Spalte `competition_match.activated_at timestamp`; jOOQ-Feld `COMPETITION_MATCH.ACTIVATED_AT`. `COMPETITION_MATCH.CURRENTLY_RUNNING` existiert danach **nicht mehr** — der Compiler zeigt jede verbliebene Verwendung.

- [ ] **Step 1: Migration schreiben**

`backend/src/main/resources/db/migration/V202608091400__match_activated_at.sql`:

```sql
set search_path to ready2race, pg_catalog, public;

-- Aktivierung und Ist-Start sind zwei verschiedene Aussagen (Entwurf 2026-08-09).
--
-- currently_running hiess "laufend", bedeutete aber immer "vom Schiedsrichter an den Start
-- gerufen" -- der Ist-Start steht seit jeher getrennt in started_at. Solange niemand einen Start
-- messen konnte, war die Ungenauigkeit folgenlos; mit dem automatischen RaceClocker-Abruf gibt es
-- einen zuverlaessigen Sender dafuer, und der Name muss die Bedeutung tragen.
--
-- Als Zeitstempel statt als Flag, weil die Frage "seit wann steht der Lauf am Start" am Renntag
-- genauso zaehlt wie "steht er ueberhaupt".
alter table competition_match
    add column activated_at timestamp;

-- Naeherung fuer Bestandsdaten: der Aktivierungszeitpunkt wurde nie festgehalten. started_at ist
-- der beste Beleg, wo er existiert; sonst bleibt updated_at, das bei der Aktivierung mitgeschrieben
-- wurde. Bei einer Regatta, die zum Migrationszeitpunkt nicht laeuft, sind das null Zeilen.
update competition_match
set activated_at = coalesce(started_at, updated_at)
where currently_running;

alter table competition_match
    drop column currently_running;
```

- [ ] **Step 2: `afterMigrate.sql` mitziehen**

In `backend/src/main/resources/db/migration/afterMigrate.sql` die Zeile

```sql
       cm.currently_running,
```

ersetzen durch

```sql
       cm.activated_at,
```

- [ ] **Step 3: Dev-DB starten und Migration laufen lassen**

```bash
cd backend && docker compose up -d
```

Dann:

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw generate-sources
```

Erwartet: Flyway meldet `Migrating schema "ready2race" to version "202608091400"`, danach erzeugt jOOQ neu. Der Lauf endet mit BUILD SUCCESS — `generate-sources` kompiliert den Kotlin-Code noch nicht, die späteren Compilerfehler tauchen erst in Task 2 auf.

> Schlägt Flyway mit „Migration checksum mismatch" oder einer bereits vorhandenen Spalte fehl, ist die lokale Dev-DB aus einer früheren Session verdorben: `./mvnw flyway:clean flyway:migrate` (die Dev-DB trägt keine Nutzdaten).

- [ ] **Step 4: Bestätigen, dass jOOQ die neue Spalte kennt**

```bash
grep -rn "ACTIVATED_AT" backend/target/generated-sources/jooq/de/lambda9/ready2race/backend/database/generated/tables/CompetitionMatch.kt
```

Erwartet: mindestens eine Trefferzeile mit `val ACTIVATED_AT`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V202608091400__match_activated_at.sql backend/src/main/resources/db/migration/afterMigrate.sql
git commit -m "Give the match the moment it was called to the start"
```

---

## Task 2: `PREPARING` in der Zustandsableitung

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/entity/LiveDashboardDto.kt:21`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/LiveDashboardLogic.kt:72-85` und `:140-152`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/matchStatus/boundary/MatchStatusLogic.kt:37-65, 94-104`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/matchStatus/entity/MatchStatusDto.kt:69-75`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/LiveDashboardLogicTest.kt`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/matchStatus/MatchStatusLogicTest.kt`

**Interfaces:**
- Consumes: `COMPETITION_MATCH.ACTIVATED_AT` aus Task 1.
- Produces:
  - `LiveDashboardMatchState.PREPARING` (Enum-Wert, erster in der Liste).
  - `LiveDashboardLogic.deriveMatchState(activatedAt: LocalDateTime?, startTime: LocalDateTime?, finishedAt: LocalDateTime?, teamResults: List<Boolean>, skipped: Boolean = false): LiveDashboardMatchState` — Parameter `currentlyRunning: Boolean` **entfällt**, `activatedAt` tritt an seine Stelle **an derselben Position**.
  - `MatchStatusLogic.matchStatus(activatedAt: LocalDateTime?, startTime: LocalDateTime?, startedAt: LocalDateTime?, finishedAt: LocalDateTime?, skipped: Boolean, teams: List<MatchStatusTeam>, teamsOnWater: Int? = null): MatchStatusDto`
  - `RoundCountersDto(total, preparing, running, open, finished, skipped)` — neues Feld `preparing` **vor** `running`.

- [ ] **Step 1: Den fehlschlagenden Test schreiben**

`deriveMatchState` bekommt **zwei** neue Parameter: `activatedAt` tritt an die Stelle von `currentlyRunning`, und `startedAt` kommt dazu — ohne ihn kann die Ableitung „am Start" nicht von „unterwegs" unterscheiden. Die Zielsignatur:

```kotlin
fun deriveMatchState(
    activatedAt: LocalDateTime?,
    startedAt: LocalDateTime?,
    startTime: LocalDateTime?,
    finishedAt: LocalDateTime?,
    teamResults: List<Boolean>,
    skipped: Boolean = false,
): LiveDashboardMatchState
```

In `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/LiveDashboardLogicTest.kt` ergänzen (Import `java.time.LocalDateTime` prüfen, sonst hinzufügen):

```kotlin
@Test
fun `aktiviert ohne Ist-Start ist PREPARING`() {
    val state = LiveDashboardLogic.deriveMatchState(
        activatedAt = LocalDateTime.of(2026, 8, 14, 10, 0),
        startedAt = null,
        startTime = LocalDateTime.of(2026, 8, 14, 10, 5),
        finishedAt = null,
        teamResults = listOf(false, false),
        skipped = false,
    )
    assertEquals(LiveDashboardMatchState.PREPARING, state)
}

@Test
fun `aktiviert mit Ist-Start ist RUNNING`() {
    val state = LiveDashboardLogic.deriveMatchState(
        activatedAt = LocalDateTime.of(2026, 8, 14, 10, 0),
        startedAt = LocalDateTime.of(2026, 8, 14, 10, 6),
        startTime = LocalDateTime.of(2026, 8, 14, 10, 5),
        finishedAt = null,
        teamResults = listOf(false, false),
        skipped = false,
    )
    assertEquals(LiveDashboardMatchState.RUNNING, state)
}
```

Dazu ein Regressionstest, der festhält, dass die Zweige darunter unverändert greifen:

```kotlin
@Test
fun `ein beendeter Lauf bleibt FINISHED, auch wenn er noch aktiviert waere`() {
    val state = LiveDashboardLogic.deriveMatchState(
        activatedAt = null,
        startedAt = LocalDateTime.of(2026, 8, 14, 10, 6),
        startTime = LocalDateTime.of(2026, 8, 14, 10, 5),
        finishedAt = LocalDateTime.of(2026, 8, 14, 10, 20),
        teamResults = listOf(true, true),
        skipped = false,
    )
    assertEquals(LiveDashboardMatchState.FINISHED, state)
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test -Dtest=LiveDashboardLogicTest
```

Erwartet: Kompilierfehler „No value passed for parameter 'currentlyRunning'" bzw. „Cannot find parameter 'activatedAt'".

- [ ] **Step 3: Enum und Ableitung umbauen**

`liveDashboard/entity/LiveDashboardDto.kt:21`:

```kotlin
enum class LiveDashboardMatchState { PREPARING, RUNNING, FINISHED, SKIPPED, AWAITING_FINISH, UPCOMING, UNSCHEDULED }
```

`liveDashboard/boundary/LiveDashboardLogic.kt`, der `when`-Block ab Zeile 78 — die bestehende KDoc darüber um den neuen ersten Zweig ergänzen und die Zweige ersetzen durch:

```kotlin
    ): LiveDashboardMatchState = when {
        // Aktiviert, aber ohne Ist-Start: der Lauf ist an den Start gerufen und noch nicht
        // unterwegs. Die Trennung traegt erst, seit der RaceClocker-Abruf den echten Start meldet -
        // vorher war "laeuft" eine Behauptung, jetzt ist es ein Beleg.
        activatedAt != null && startedAt == null -> LiveDashboardMatchState.PREPARING
        activatedAt != null -> LiveDashboardMatchState.RUNNING
        finishedAt != null -> LiveDashboardMatchState.FINISHED
        skipped -> LiveDashboardMatchState.SKIPPED
        teamResults.isNotEmpty() && teamResults.all { it } -> LiveDashboardMatchState.AWAITING_FINISH
        startTime == null -> LiveDashboardMatchState.UNSCHEDULED
        else -> LiveDashboardMatchState.UPCOMING
    }
```

- [ ] **Step 4: `selectForScope` erweitern**

`liveDashboard/boundary/LiveDashboardLogic.kt:145-152`: Der LIVE-Ausschnitt muss `PREPARING` enthalten — ein Lauf am Start ist genau der, den der Schiedsrichter vor sich hat. Aus

```kotlin
                it.state == LiveDashboardMatchState.RUNNING ||
                    it.state == LiveDashboardMatchState.AWAITING_FINISH
```

wird

```kotlin
                it.state == LiveDashboardMatchState.PREPARING ||
                    it.state == LiveDashboardMatchState.RUNNING ||
                    it.state == LiveDashboardMatchState.AWAITING_FINISH
```

Die KDoc darüber (ab Zeile 134) um einen Satz ergänzen: dass ein Lauf in Vorbereitung dazugehört, weil auf ihm die nächste Handlung liegt.

- [ ] **Step 5: `MatchStatusLogic` durchreichen**

`matchStatus/boundary/MatchStatusLogic.kt`: Signatur von `matchStatus` um `activatedAt: LocalDateTime?` als **ersten** Parameter erweitern (`currentlyRunning` entfällt) und beim Aufruf von `deriveMatchState` `activatedAt = activatedAt, startedAt = startedAt` übergeben.

`roundCounters` (Zeile 94-104) ersetzen durch:

```kotlin
    fun roundCounters(statuses: List<MatchStatusDto>): RoundCountersDto = RoundCountersDto(
        total = statuses.size,
        preparing = statuses.count { it.state == MatchState.PREPARING },
        running = statuses.count { it.state == MatchState.RUNNING },
        open = statuses.count {
            it.state == MatchState.AWAITING_FINISH ||
                it.state == MatchState.UPCOMING ||
                it.state == MatchState.UNSCHEDULED
        },
        finished = statuses.count { it.state == MatchState.FINISHED },
        skipped = statuses.count { it.state == MatchState.SKIPPED },
    )
```

`matchStatus/entity/MatchStatusDto.kt:69`:

```kotlin
data class RoundCountersDto(
    val total: Int,
    /** Am Start gerufen, aber noch nicht unterwegs — zaehlt weder als „laeuft" noch als „offen". */
    val preparing: Int,
    val running: Int,
    val open: Int,
    val finished: Int,
    val skipped: Int,
)
```

Die KDoc von `RoundCountersDto` um den Hinweis ergänzen, dass jeder Lauf weiterhin in genau einen Topf zählt.

- [ ] **Step 6: Zähler-Test schreiben**

In `MatchStatusLogicTest.kt`:

```kotlin
@Test
fun `ein Lauf in Vorbereitung zaehlt weder als laufend noch als offen`() {
    val counters = MatchStatusLogic.roundCounters(
        listOf(
            MatchStatusDto(MatchState.PREPARING, startedAt = null, teamsTotal = 6, teamsScored = 0),
            MatchStatusDto(MatchState.RUNNING, startedAt = null, teamsTotal = 6, teamsScored = 0),
        )
    )
    assertEquals(1, counters.preparing)
    assertEquals(1, counters.running)
    assertEquals(0, counters.open)
    assertEquals(2, counters.total)
}
```

- [ ] **Step 7: Alle Aufrufer von `deriveMatchState`/`matchStatus` nachziehen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw compile 2>&1 | grep -E "^\[ERROR\].*\.kt" | sort -u
```

Jede gemeldete Stelle auf `activatedAt`/`startedAt` umstellen. Betroffen sind mindestens `LiveDashboardService.kt`, `LiveDashboardRepo.kt`, `EventScheduleService.kt`, `EventScheduleRepo.kt`, `CompetitionExecutionService.kt`, `competitionExecution/control/Conversions.kt`, `eventInfo/control/Conversions.kt`. Wo eine Repo-Abfrage `COMPETITION_MATCH.CURRENTLY_RUNNING` selektiert, wird daraus `COMPETITION_MATCH.ACTIVATED_AT`.

- [ ] **Step 8: Tests laufen lassen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test -Dtest='LiveDashboardLogicTest,MatchStatusLogicTest'
```

Erwartet: BUILD SUCCESS, alle Tests grün.

- [ ] **Step 9: Commit**

```bash
git add backend/src
git commit -m "Let the board tell a crew at the start from a crew racing"
```

---

## Task 3: Aktivieren, „Läuft", Deaktivieren, Beenden

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/LiveDashboardService.kt:493-533` (`setMatchRunning`, `markMatchStarted`, `setRunning`) und `finishMatch`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/liveDashboard.kt:63-80`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/boundary/CompetitionExecutionService.kt:1182-1199`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/entity/UpdateCompetitionMatchRunningStateRequest.kt` → umbenennen
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/boundary/competitionExecution.kt:75-85`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/boundary/ScheduleChain.kt:17-31, 65-80, 155-175`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/ScheduleChainTest.kt`

**Interfaces:**
- Consumes: `PREPARING`, `activated_at` aus Tasks 1–2.
- Produces:
  - `LiveDashboardService.setMatchActivated(eventId, matchId, activated: Boolean, userId): App<LiveDashboardError, ApiResponse.NoData>`
  - `LiveDashboardService.markMatchStarted(eventId, matchId, userId)` (Name bleibt, Verhalten leicht erweitert)
  - `ChainSlot(..., matchActivatedAt: LocalDateTime? = null, matchStartedAt: LocalDateTime? = null)` — `currentlyRunning: Boolean` entfällt.
  - `UpdateCompetitionMatchActivationRequest(activated: Boolean)`

- [ ] **Step 1: Den fehlschlagenden Ketten-Test schreiben**

In `ScheduleChainTest.kt` (bestehendem Stil und Fixture folgen):

```kotlin
@Test
fun `ein aktivierter, noch nicht gestarteter Nachbar blockiert die Gruppe nicht`() {
    val decision = ScheduleChain.decideNext(
        listOf(
            ChainSlot(
                slotId = UUID.randomUUID(),
                startTime = LocalDateTime.of(2026, 8, 14, 10, 0),
                state = EventScheduleSlotState.LINKED,
                matchId = UUID.randomUUID(),
                matchFinished = false,
                matchOpen = true,
                matchActivatedAt = LocalDateTime.of(2026, 8, 14, 9, 55),
                matchStartedAt = null,
            ),
        )
    )
    assertTrue(decision is ChainDecision.NothingToDo)
}

@Test
fun `ein wirklich gestarteter Nachbar blockiert die Gruppe`() {
    val running = ChainSlot(
        slotId = UUID.randomUUID(),
        startTime = LocalDateTime.of(2026, 8, 14, 10, 0),
        state = EventScheduleSlotState.LINKED,
        matchId = UUID.randomUUID(),
        matchFinished = false,
        matchOpen = true,
        matchActivatedAt = LocalDateTime.of(2026, 8, 14, 9, 55),
        matchStartedAt = LocalDateTime.of(2026, 8, 14, 10, 1),
    )
    val activatable = ChainSlot(
        slotId = UUID.randomUUID(),
        startTime = LocalDateTime.of(2026, 8, 14, 10, 0),
        state = EventScheduleSlotState.LINKED,
        matchId = UUID.randomUUID(),
        matchFinished = false,
        matchOpen = true,
    )
    assertTrue(ScheduleChain.decideNext(listOf(running, activatable)) is ChainDecision.NothingToDo)
}
```

Im ersten Test ist `NothingToDo` korrekt, weil der einzige Slot bereits aktiviert und damit nicht mehr `activatable` ist — geprüft wird, dass die Entscheidung **nicht** an `siblingStillRunning` hängen bleibt und die Suche in der nächsten Gruppe fortsetzt. Ergänze deshalb einen dritten Test mit einer zweiten, späteren Gruppe:

```kotlin
@Test
fun `die Kette rueckt vor, wenn die Gruppe nur vorbereitete Laeufe enthaelt`() {
    val prepared = ChainSlot(
        slotId = UUID.randomUUID(),
        startTime = LocalDateTime.of(2026, 8, 14, 10, 0),
        state = EventScheduleSlotState.LINKED,
        matchId = UUID.randomUUID(),
        matchFinished = false,
        matchOpen = true,
        matchActivatedAt = LocalDateTime.of(2026, 8, 14, 9, 55),
        matchStartedAt = null,
    )
    val nextMatchId = UUID.randomUUID()
    val next = ChainSlot(
        slotId = UUID.randomUUID(),
        startTime = LocalDateTime.of(2026, 8, 14, 10, 10),
        state = EventScheduleSlotState.LINKED,
        matchId = nextMatchId,
        matchFinished = false,
        matchOpen = true,
    )
    val decision = ScheduleChain.decideNext(listOf(prepared, next))
    assertEquals(ChainDecision.Activate(listOf(nextMatchId)), decision)
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test -Dtest=ScheduleChainTest
```

Erwartet: Kompilierfehler „Cannot find a parameter with this name: matchActivatedAt".

- [ ] **Step 3: `ChainSlot` und `decideNext` umbauen**

`eventSchedule/boundary/ScheduleChain.kt:17-31`:

```kotlin
data class ChainSlot(
    val slotId: UUID,
    val startTime: LocalDateTime,
    val state: EventScheduleSlotState,
    val matchId: UUID?,
    val matchFinished: Boolean,
    val matchOpen: Boolean,
    /**
     * Wann der Lauf an den Start gerufen wurde (competition_match.activated_at). Entscheidet in
     * [ScheduleChain.decideNext], ob er noch aktivierbar ist — ein bereits aktivierter Lauf soll
     * nicht ein zweites Mal aktiviert werden.
     */
    val matchActivatedAt: LocalDateTime? = null,
    /**
     * Der Ist-Start (competition_match.started_at). NUR er blockiert das Vorruecken: Ein Lauf, den
     * die Kette an den Start gerufen hat, dessen Boote aber noch am Steg liegen, haelt die naechste
     * Startgruppe nicht auf. Vor der Trennung von Aktivierung und Ist-Start stand hier
     * `currentlyRunning`, das beide Faelle zusammenwarf.
     */
    val matchStartedAt: LocalDateTime? = null,
)
```

In `decideNext` (Zeile ~69/76):

```kotlin
            val siblingStillRunning = group.any {
                it.state == EventScheduleSlotState.LINKED && !it.matchFinished && it.matchOpen &&
                    it.matchStartedAt != null
            }
            if (siblingStillRunning) {
                return ChainDecision.NothingToDo
            }

            val activatable = group.filter {
                it.state == EventScheduleSlotState.LINKED && !it.matchFinished && it.matchOpen &&
                    it.matchActivatedAt == null
            }
```

Die KDoc von `decideNext` (Zeile 50-53) um den Satz ergänzen, dass „noch laufend" jetzt den Ist-Start meint.

`ScheduleChain.setRunning` (Zeile ~169) umbenennen zu `activate` und schreiben:

```kotlin
    private fun activate(matchId: UUID, userId: UUID): App<Nothing, Unit> =
        CompetitionMatchRepo.update(matchId) {
            activatedAt = LocalDateTime.now()
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie().map { }
```

`buildChainSlots` (Zeile ~159) liest statt `COMPETITION_MATCH.CURRENTLY_RUNNING` jetzt `COMPETITION_MATCH.ACTIVATED_AT` und `COMPETITION_MATCH.STARTED_AT`; `EventScheduleRepo.getChainSlots` muss beide Spalten mitliefern.

- [ ] **Step 4: Die Schreibpfade umbauen**

`liveDashboard/boundary/LiveDashboardService.kt` — `setMatchRunning` wird zu:

```kotlin
    /**
     * Ruft einen Lauf an den Start oder nimmt das zurueck.
     *
     * Aktivieren setzt ausschliesslich [activatedAt]: Ein Klick des Schiedsrichters stellt fest,
     * dass der Lauf drankommt, nicht dass er faehrt. Der Ist-Start kommt aus der Zeitnahme oder aus
     * [markMatchStarted].
     *
     * Deaktivieren nimmt beides zurueck und pausiert zugleich den automatischen RaceClocker-Abruf.
     * Ohne die Pause waere die Ruecknahme wirkungslos: der Lauf steht im Beobachtungsfenster, der
     * Job findet die Startzeit im Feed und aktiviert ihn im naechsten Takt wieder — spaetestens
     * nach 60 Sekunden. Freigegeben wird er ueber denselben Weg wie ein von Hand eingetragener Lauf
     * (`CompetitionExecutionService.resumeRaceClockerAutoPull`).
     */
    fun setMatchActivated(
        eventId: UUID,
        matchId: UUID,
        activated: Boolean,
        userId: UUID,
    ): App<LiveDashboardError, ApiResponse.NoData> = KIO.comprehension {
        val exists = !EventRepo.exists(eventId).orDie()
        if (!exists) {
            return@comprehension KIO.fail(LiveDashboardError.EventNotFound(eventId))
        }

        val now = LocalDateTime.now()
        !CompetitionMatchRepo.update(matchId) {
            if (activated) {
                if (activatedAt == null) {
                    activatedAt = now
                }
            } else {
                activatedAt = null
                startedAt = null
                if (raceclockerAutoPausedAt == null) {
                    raceclockerAutoPausedAt = now
                }
            }
            updatedBy = userId
            updatedAt = now
        }.orDie()

        if (!activated) {
            RaceClockerPollService.forget(matchId)
        }

        noData
    }
```

`markMatchStarted` bleibt im Kern, nur `currentlyRunning = true` wird zu `if (activatedAt == null) activatedAt = LocalDateTime.now()`, und die KDoc bekommt einen Satz: der Knopf stellt fest, dass das Rennen unterwegs ist, er loest keine Zeitnahme aus.

`finishMatch`: `currentlyRunning = false` wird zu `activatedAt = null`. `started_at` bleibt stehen — der Ist-Start eines beendeten Laufs ist eine Tatsache.

`liveDashboard/boundary/liveDashboard.kt`: Route `put("/match/{matchId}/running-state")` wird zu `put("/match/{matchId}/activation")`, Query-Parameter `running` zu `activated`, Aufruf auf `setMatchActivated`.

`competitionExecution`: Datei `UpdateCompetitionMatchRunningStateRequest.kt` nach `UpdateCompetitionMatchActivationRequest.kt` umbenennen, Klasse und Feld (`activated: Boolean`) mit, `CompetitionExecutionService.updateMatchRunningState` zu `updateMatchActivation` mit derselben Aktivieren/Deaktivieren-Regel wie oben (die Pausen-Logik in eine private Hilfsfunktion ziehen, damit sie nicht zweimal steht — sie gehört in `CompetitionMatchRepo` oder in einen gemeinsamen Service; entscheide beim Umsetzen und begründe es im Kommentar). Route in `competitionExecution.kt` von `running-state` auf `activation`.

- [ ] **Step 5: Tests laufen lassen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test -Dtest='ScheduleChainTest,EventScheduleLogicTest'
```

Erwartet: alle grün. `EventScheduleLogicTest` prüft `matchUnderway` — der bleibt bei „Aktivierung genügt" und muss unverändert bestehen (er liest jetzt `activatedAt != null` statt `currentlyRunning`).

- [ ] **Step 6: Commit**

```bash
git add backend/src
git commit -m "Make activating a match call it to the start, nothing more"
```

---

## Task 4: Der Ist-Start eines bereits aktivierten Laufs

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/entity/RaceClockerPollCandidate.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/control/RaceClockerPollRepo.kt` (`getCandidates`)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/boundary/RaceClockerPollLogic.kt` (`isWatched`)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/boundary/RaceClockerPollService.kt:255-278`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/raceclocker/RaceClockerPollLogicTest.kt`

**Interfaces:**
- Consumes: `RaceClockerFeedRow.earliestStart(rows): LocalTime?` (existiert), `RaceClockerPollLogic.startDetected(rows): Boolean` (existiert, unverändert).
- Produces: `RaceClockerPollCandidate(matchId, competitionId, startTime, activatedAt, startedAt, target)`.

**Warum nicht in `applyRaceClockerRows`:** Dort steht der `NoResults`-Abbruch vor dem `earliestStart`-Block, und `pollMatch` führt `applyRaceClockerRows` innerhalb von `.transact()` aus — ein dort gesetzter `started_at` würde vom Rollback der fehlschlagenden Transaktion gleich wieder kassiert. Der eigene Zweig im Poll umgeht das Problem, statt es zu verwalten, und lässt den „nachziehen"-Knopf unangetastet.

- [ ] **Step 1: Den fehlschlagenden Test schreiben**

In `RaceClockerPollLogicTest.kt`:

```kotlin
@Test
fun `ein aktivierter Lauf ohne Ist-Start wird weiter beobachtet`() {
    val watched = RaceClockerPollLogic.isWatched(
        activated = true,
        startTime = LocalDateTime.of(2026, 8, 14, 10, 0),
        now = LocalDateTime.of(2026, 8, 14, 14, 0),
        watchBeforeMinutes = 15,
        watchAfterMinutes = 120,
    )
    // Aktiviert schlaegt das Zeitfenster: der Lauf kann laengst vor oder nach seinem Plan
    // stattfinden, und was tatsaechlich passiert, schlaegt den Plan.
    assertTrue(watched)
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test -Dtest=RaceClockerPollLogicTest
```

Erwartet: Kompilierfehler „Cannot find a parameter with this name: activated".

- [ ] **Step 3: Kandidat und Beobachtungsregel umstellen**

`RaceClockerPollCandidate.kt`:

```kotlin
data class RaceClockerPollCandidate(
    val matchId: UUID,
    val competitionId: UUID,
    val startTime: LocalDateTime?,
    /** Wann der Lauf an den Start gerufen wurde — null, solange ihn niemand aktiviert hat. */
    val activatedAt: LocalDateTime?,
    /** Der Ist-Start. Null bei einem Lauf, der aktiviert ist, aber noch am Steg liegt. */
    val startedAt: LocalDateTime?,
    val target: RaceClockerMatchTarget,
)
```

`RaceClockerPollLogic.isWatched`: Parameter `currentlyRunning: Boolean` → `activated: Boolean`, Rumpf und KDoc entsprechend (der erste Zweig heißt jetzt „aktiviert immer").

`RaceClockerPollRepo.getCandidates`: `COMPETITION_MATCH.ACTIVATED_AT` und `COMPETITION_MATCH.STARTED_AT` mitselektieren und in den Kandidaten füllen.

- [ ] **Step 4: Den Stempel im Poll setzen**

`RaceClockerPollService.pollMatch`, den Block ab Zeile 259 ersetzen. `candidate.currentlyRunning` wird zu `candidate.activatedAt != null`, und darunter kommt der neue Zweig:

```kotlin
        // Bevorstehender Lauf: nur hinsehen, nichts schreiben ausser der Aktivierung. Ein
        // Umsortieren in RaceClocker vor dem Start schlaegt erst durch, wenn der Lauf aktiv ist.
        if (candidate.activatedAt == null) {
            if (!RaceClockerPollLogic.startDetected(assigned)) return@comprehension KIO.ok(MatchOutcome())

            !CompetitionMatchRepo.update(candidate.matchId) {
                activatedAt = now
                if (startedAt == null) {
                    startedAt = now
                }
                updatedBy = SYSTEM_USER
                updatedAt = now
            }.orDie()
            logger.info { "RaceClocker meldet den Start von Lauf ${candidate.matchId} - Lauf aktiviert." }
            return@comprehension KIO.ok(MatchOutcome(activated = true))
        }

        // Aktiviert, aber noch ohne Ist-Start: der Lauf wurde von Hand oder von der Kette an den
        // Start gerufen, und der Feed weiss vielleicht schon, dass er losgegangen ist. Der Stempel
        // steht hier und nicht in `applyRaceClockerRows`: dort bricht der NoResults-Zweig ab, bevor
        // die gemessene Startzeit uebernommen wird, und er laeuft innerhalb von `.transact()` - ein
        // dort gesetzter Zeitstempel faellt dem Rollback zum Opfer. Ohne diesen Zweig bliebe ein von
        // der Kette aktivierter Lauf "in Vorbereitung", bis das erste Boot durchs Ziel ist.
        val measuredStart = RaceClockerFeedRow.earliestStart(assigned)
        if (candidate.startedAt == null && measuredStart != null) {
            val raceDay = candidate.startTime?.toLocalDate() ?: now.toLocalDate()
            !CompetitionMatchRepo.update(candidate.matchId) {
                startedAt = raceDay.atTime(measuredStart)
                updatedBy = SYSTEM_USER
                updatedAt = now
            }.orDie()
            logger.info { "RaceClocker meldet den Ist-Start von Lauf ${candidate.matchId}." }
        }
```

Bewusst ohne `?.let { … }`: Der `!`-Operator der Comprehension funktioniert nur direkt im Block, nicht in einem geschachtelten Lambda. `import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow` steht bereits in der Datei.

Zusätzlich in `pollEvent` (Zeile ~163): `anyRunning = anyRunning || candidate.currentlyRunning || outcome.activated` wird zu `candidate.activatedAt != null`.

- [ ] **Step 5: Tests laufen lassen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test -Dtest='RaceClockerPollLogicTest,RaceClockerPollRepoTest'
```

Erwartet: alle grün.

- [ ] **Step 6: Commit**

```bash
git add backend/src
git commit -m "Stamp the real start on a match the chain already called up"
```

---

## Task 5: DTOs, Endpunkte, OpenAPI

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/entity/LiveDashboardDto.kt:152`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/entity/{CompetitionMatchDto,CompetitionMatchWithTeams,MatchForRunningStatusDto}.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/entity/` (Slot-DTO mit `matchCurrentlyRunning`)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/AthleteBoardDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/event/boundary/event.kt:105-108`
- Modify: `backend/src/main/resources/openapi/documentation.yaml`

**Interfaces:**
- Produces (für Task 7 im Frontend):
  - `LiveDashboardMatchDto` **ohne** `currentlyRunning` (der Zustand steht in `state`).
  - `CompetitionMatchDto.activatedAt: LocalDateTime?` statt `currentlyRunning: Boolean`.
  - `EventScheduleSlotDto.matchActivatedAt: LocalDateTime?` statt `matchCurrentlyRunning: Boolean`.
  - `AthleteBoardMatch.state: MatchState` (neu) neben dem bestehenden `actualStartTime`.
  - `GET /event/{eventId}/matches?activated=<bool>`.

- [ ] **Step 1: DTO-Felder umstellen**

`LiveDashboardMatchDto`: `val currentlyRunning: Boolean` streichen — jede Frontend-Ablesung geht über `state`. `LiveDashboardRepo` und `LiveDashboardService` entsprechend.

`CompetitionMatchDto`, `CompetitionMatchWithTeams`, `MatchForRunningStatusDto`: `currentlyRunning: Boolean` → `activatedAt: LocalDateTime?`, dazu die KDoc („Wann der Lauf an den Start gerufen wurde").

`EventScheduleSlotDto`: `matchCurrentlyRunning: Boolean` → `matchActivatedAt: LocalDateTime?`; `EventScheduleRepo` mitziehen.

`AthleteBoardMatch`: Feld `state: MatchState` ergänzen, in `eventInfo/control/Conversions.kt` bzw. `EventInfoService` über `LiveDashboardLogic.deriveMatchState` füllen — **nicht** über eine zweite Ableitung. `actualStartTime` bleibt, es trägt die Uhrzeit für „gestartet 14:32".

`event/boundary/event.kt:105`: Query-Parameter `currentlyRunning` → `activated`, Weiterreichung an `CompetitionExecutionService.getMatchesByEvent` entsprechend.

- [ ] **Step 2: `documentation.yaml` nachziehen**

Alle betroffenen Schemata und Pfade anpassen:
- neuer Enum-Wert `PREPARING` in `LiveDashboardMatchState`
- `RoundCountersDto.preparing`
- `LiveDashboardMatchDto.currentlyRunning` entfernen
- `CompetitionMatchDto.activatedAt`, `EventScheduleSlotDto.matchActivatedAt`
- `AthleteBoardMatch.state`
- `PUT /event/{eventId}/liveDashboard/match/{matchId}/running-state` → `/activation`, Parameter `running` → `activated`
- `PUT …/competitionExecution/…/running-state` → `/activation` mit `UpdateCompetitionMatchActivationRequest`
- `GET /event/{eventId}/matches` Parameter `currentlyRunning` → `activated`

- [ ] **Step 3: Vollständigen Backend-Lauf machen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test
```

Erwartet: BUILD SUCCESS. `testComprehension` fährt Testcontainers hoch und prüft die Services gegen echtes Postgres — damit laufen beide Migrationen gegen eine frische Datenbank. Dauert ca. 6 s zusätzlich.

- [ ] **Step 4: Commit**

```bash
git add backend/src
git commit -m "Speak of activation instead of running across the API"
```

---

## Task 6: Arena statt Wasser (Backend)

**Files:**
- Create: `backend/src/main/resources/db/migration/V202608091410__check_type_not_in_arena.sql`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/entity/CheckSeverity.kt:13`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/LiveDashboardLogic.kt` (`teamOnWaterAt`)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/matchStatus/boundary/MatchStatusLogic.kt` (`teamsOnWaterPerMatch`)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/matchStatus/entity/MatchStatusDto.kt` (`teamsOnWater`, `CrewLastScans`-KDoc)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/entity/{LiveDashboardDto,CheckSeverityConfigDto}.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/LiveDashboardService.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/{participantTracking/boundary/ParticipantTrackingService.kt,competitionProperties/entity/CompetitionPropertiesRequest.kt}` (nur Kommentare)
- Modify: `backend/src/main/resources/openapi/documentation.yaml`

**Interfaces:**
- Produces: `CheckType.NOT_IN_ARENA`, `LiveDashboardLogic.teamInArenaAt`, `MatchStatusLogic.teamsInArenaPerMatch`, `MatchStatusDto.teamsInArena`, `LiveDashboardTeamDto.{inArenaRequired,inArenaSeverity,inArenaAt}`.

- [ ] **Step 1: Migration schreiben**

`backend/src/main/resources/db/migration/V202608091410__check_type_not_in_arena.sql`:

```sql
set search_path to ready2race, pg_catalog, public;

-- "Auf dem Wasser" ist ruderspezifisch. ready2race soll auch Sportarten ohne Wasser bedienen, und
-- die neutrale Entsprechung fuer den Ort, an dem gefahren wird, ist die Arena.
--
-- Die Tabelle ist bewusst duenn besetzt (nur Abweichungen vom eingebauten Standard), das sind
-- wenige Zeilen. chk_ccs_requirement_matches_check_type nennt nur die beiden REQUIREMENT-Typen und
-- bleibt unveraendert gueltig.
update competition_check_severity
set check_type = 'NOT_IN_ARENA'
where check_type = 'NOT_ON_WATER';
```

Außerdem den Kommentar in `V202608071200__referee_check_severity.sql:22` **nicht** anfassen — eine bereits gelaufene Migration zu ändern kippt Flywayss Prüfsumme.

- [ ] **Step 2: Enum und Logik umbenennen**

`CheckSeverity.kt:13`:

```kotlin
enum class CheckType { INVOICE_OPEN, NOT_IN_ARENA, REQUIREMENT, REQUIREMENT_TIME_WINDOW }
```

Dann die Umbenennungen aus der Tabelle in Abschnitt 4.2 der Spec durchführen. Vorgehen: erst `grep -rn "onWater\|OnWater\|NOT_ON_WATER" backend/src`, dann jede Stelle einzeln — Feldnamen, Funktionsnamen, KDocs und die deutschen Kommentartexte („auf dem Wasser" → „in der Arena", „vom Wasser" → „aus der Arena", „aufs Wasser" → „in die Arena").

`ParticipantTrackingService.kt:50-51` und `CompetitionPropertiesRequest.kt:18` sind reine Kommentare, werden aber mitgezogen: Sie erklären das Check-in/out und wären sonst die letzte Stelle, an der „Wasser" die Bedeutung trägt.

`competition_properties.check_in_out_required` bleibt, wie es heißt.

- [ ] **Step 3: `documentation.yaml` nachziehen**

`NOT_ON_WATER` → `NOT_IN_ARENA` im `CheckType`-Enum; `teamsOnWater` → `teamsInArena`; `onWaterRequired`/`onWaterSeverity`/`onWaterAt` → `inArena*`.

- [ ] **Step 4: Tests anpassen und laufen lassen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test
```

Erwartet: BUILD SUCCESS. Die Testcontainers-Läufe prüfen beide Migrationen gegen eine frische Datenbank.

- [ ] **Step 5: Commit**

```bash
git add backend/src
git commit -m "Call the place where crews race an arena, not the water"
```

---

## Task 7: Frontend-Typen und die geteilte Chip-Ableitung

**Files:**
- Modify (generiert): `frontend/src/api/types.gen.ts`, `frontend/src/api/sdk.gen.ts`
- Modify: `frontend/src/components/event/match/matchStatusChip.ts`
- Modify: `frontend/src/i18n/{de,en,da}/translations.json`
- Test: `frontend/src/components/event/match/matchStatusChip.test.ts`

**Interfaces:**
- Consumes: die Backend-Typen aus Tasks 5 und 6.
- Produces:
  - `matchStatusChip` liefert für `PREPARING` `{labelKey: 'event.match.status.preparing', color: 'info'}`.
  - `arenaChip` (ehemals `waterChip`), gleiche Signatur.
  - `roundCounterChips` mit dem Topf `event.match.status.counter.preparing` **vor** `…counter.running`.
  - `slotMatchStatus` leitet `PREPARING` aus `slot.matchActivatedAt` + `slot.matchStartedAt` ab.

- [ ] **Step 1: Typen neu erzeugen**

```bash
cd frontend && npm install && npm run generate
```

Erwartet: `src/api/types.gen.ts` enthält `'PREPARING'` in `LiveDashboardMatchState` und kein `currentlyRunning` mehr in `LiveDashboardMatchDto`.

```bash
grep -n "PREPARING" frontend/src/api/types.gen.ts
```

- [ ] **Step 2: Die fehlschlagenden Tests schreiben**

In `frontend/src/components/event/match/matchStatusChip.test.ts`:

```ts
it('zeigt einen Lauf am Start als „In Vorbereitung“', () => {
    const chip = matchStatusChip(
        {state: 'PREPARING', startedAt: undefined, teamsTotal: 6, teamsScored: 0},
        '2026-08-14T10:00:00',
        new Date('2026-08-14T10:20:00'),
    )
    expect(chip.labelKey).toBe('event.match.status.preparing')
    expect(chip.color).toBe('info')
})

it('macht aus einem Lauf am Start keinen ueberfaelligen', () => {
    const chip = matchStatusChip(
        {state: 'PREPARING', startedAt: undefined, teamsTotal: 6, teamsScored: 0},
        '2026-08-14T10:00:00',
        new Date('2026-08-14T11:00:00'),
    )
    expect(chip.labelKey).not.toBe('event.match.status.overdue')
})

it('zaehlt vorbereitete Laeufe in einen eigenen Topf', () => {
    const chips = roundCounterChips([
        {state: 'PREPARING', startedAt: undefined, teamsTotal: 6, teamsScored: 0},
        {state: 'RUNNING', startedAt: undefined, teamsTotal: 6, teamsScored: 0},
    ])
    expect(chips.map(c => c.labelKey)).toEqual([
        'event.match.status.counter.preparing',
        'event.match.status.counter.running',
    ])
})

it('leitet PREPARING aus einem aktivierten, nicht gestarteten Slot ab', () => {
    const status = slotMatchStatus({
        ...baseSlot,
        matchId: 'm1',
        matchActivatedAt: '2026-08-14T09:55:00',
        matchStartedAt: null,
    })
    expect(status?.state).toBe('PREPARING')
})
```

`baseSlot` an den bestehenden Fixtures der Datei ausrichten.

- [ ] **Step 3: Tests laufen lassen und Fehlschlag bestätigen**

```bash
cd frontend && npm run test -- matchStatusChip
```

Erwartet: FAIL — `chip.labelKey` ist `event.match.status.upcoming` statt `…preparing`.

- [ ] **Step 4: `matchStatusChip.ts` umbauen**

Vor den `RUNNING`-Zweig (Zeile 71) setzen:

```ts
    // Vor RUNNING: ein Lauf am Start ist noch nicht unterwegs, und die Reihenfolge folgt der von
    // `LiveDashboardLogic.deriveMatchState` im Backend.
    if (status.state === 'PREPARING') {
        return {labelKey: 'event.match.status.preparing', color: 'info'}
    }
```

`waterChip` → `arenaChip` umbenennen; in ihm `status.teamsOnWater` → `status.teamsInArena`, `labelKey` → `'event.match.status.inArena'`, und die Zustandsprüfung erweitern:

```ts
    if (
        status.state !== 'UPCOMING' &&
        status.state !== 'PREPARING' &&
        status.state !== 'RUNNING'
    )
        return null
```

Die KDoc von `arenaChip` entsprechend umschreiben („in der Arena" ist keine Phase des Laufs, sondern eine Eigenschaft des Vorbereitungsstands).

`roundCounterChips`: neuen Topf **an den Anfang** des `buckets`-Arrays:

```ts
        {
            n: count(s => s.state === 'PREPARING'),
            labelKey: 'event.match.status.counter.preparing',
            color: 'info',
        },
```

`slotMatchStatus`: die nachgebildete Kette erweitern —

```ts
    const state = slot.matchActivatedAt
        ? slot.matchStartedAt
            ? 'RUNNING'
            : 'PREPARING'
        : slot.matchFinishedAt
          ? 'FINISHED'
          : slot.state === 'SKIPPED'
            ? 'SKIPPED'
            : slot.matchTeamsTotal > 0 && slot.matchTeamsScored >= slot.matchTeamsTotal
              ? 'AWAITING_FINISH'
              : 'UPCOMING'
```

- [ ] **Step 5: Übersetzungen ergänzen**

In `frontend/src/i18n/de/translations.json` unter `event.match.status`:

```json
"preparing": "In Vorbereitung",
"inArena": "Arena {{onWater}}/{{total}}",
```

Den Platzhalternamen dabei ebenfalls umbenennen: `arenaChip` übergibt `{inArena, total}`, der Text lautet `"Arena {{inArena}}/{{total}}"`. Den alten Schlüssel `water` entfernen. Unter `event.match.status.counter` ergänzen: `"preparing": "{{n}} in Vorbereitung"`.

Englisch: `"preparing": "Preparing"`, `"inArena": "Arena {{inArena}}/{{total}}"`, `"counter.preparing": "{{n}} preparing"`.
Dänisch: `"preparing": "Forbereder"`, `"inArena": "Arena {{inArena}}/{{total}}"`, `"counter.preparing": "{{n}} forbereder"`.

Alle bestehenden Texte, die „Wasser" enthalten, in allen drei Dateien mitziehen.

- [ ] **Step 6: Tests laufen lassen**

```bash
cd frontend && npm run test
```

Erwartet: alle Suiten grün.

- [ ] **Step 7: Commit**

```bash
git add frontend/src
git commit -m "Give every board the same word for a crew at the start"
```

---

## Task 8: Die Oberflächen

**Files:**
- Modify: `frontend/src/components/event/liveDashboard/common.ts:17-21, 160-172`
- Modify: `frontend/src/components/event/liveDashboard/LiveDashboardMatchCard.tsx:61-67, 210-225, 439-470`
- Modify: `frontend/src/pages/event/LiveDashboardPage.tsx`
- Modify: `frontend/src/components/event/schedule/timelineIndicator.ts:49-95`
- Modify: `frontend/src/components/event/schedule/ScheduleTimelineIndicator.tsx:95-99`
- Modify: `frontend/src/components/event/info/athleteBoard/AthleteBoardMatchCard.tsx:47-62`
- Modify: `frontend/src/components/event/competition/excecution/{CompetitionExecution,CompetitionExecutionRound}.tsx`
- Modify: `frontend/src/components/event/match/{DragDropMatchLists,ManageRunningMatchesDialog}.tsx`
- Test: `frontend/src/components/event/liveDashboard/common.test.ts`, `frontend/src/components/event/schedule/timelineIndicator.test.ts`

**Interfaces:**
- Consumes: alles aus Task 7.
- Produces: `matchControls(match, mayFinish, mayControl): {showFinish: boolean; showActivationToggle: boolean; showMarkStarted: boolean}` — `showRunToggle` wird umbenannt, `showMarkStarted` kommt dazu.

- [ ] **Step 1: Die fehlschlagenden Tests schreiben**

In `frontend/src/components/event/liveDashboard/common.test.ts`:

```ts
it('zeigt einen Lauf in Vorbereitung im Live-Ausschnitt', () => {
    expect(isLiveMatch({...baseMatch, state: 'PREPARING'})).toBe(true)
})

it('bietet bei einem Lauf in Vorbereitung den Laeuft-Knopf an', () => {
    const controls = matchControls({...baseMatch, state: 'PREPARING'}, true, true)
    expect(controls.showMarkStarted).toBe(true)
    expect(controls.showActivationToggle).toBe(true)
})

it('bietet den Laeuft-Knopf nicht mehr an, sobald der Lauf unterwegs ist', () => {
    const controls = matchControls({...baseMatch, state: 'RUNNING'}, true, true)
    expect(controls.showMarkStarted).toBe(false)
})
```

- [ ] **Step 2: Tests laufen lassen und Fehlschlag bestätigen**

```bash
cd frontend && npm run test -- liveDashboard/common
```

Erwartet: FAIL — `showMarkStarted` ist `undefined`.

- [ ] **Step 3: `common.ts` umbauen**

```ts
export const isLiveMatch = (match: LiveDashboardMatchDto): boolean =>
    match.state === 'PREPARING' ||
    match.state === 'RUNNING' ||
    match.state === 'AWAITING_FINISH'
```

```ts
export const matchControls = (
    match: LiveDashboardMatchDto,
    mayFinish: boolean,
    mayControl: boolean,
): {showFinish: boolean; showActivationToggle: boolean; showMarkStarted: boolean} => {
    if (match.state === 'SKIPPED') {
        return {showFinish: false, showActivationToggle: false, showMarkStarted: false}
    }
    return {
        showFinish: mayFinish && isLiveMatch(match),
        showActivationToggle: mayControl && match.state !== 'AWAITING_FINISH',
        // Nur solange der Lauf am Start steht: "Laeuft" stellt fest, dass das Rennen unterwegs ist.
        // Bei einem bereits gestarteten Lauf gaebe es nichts mehr festzustellen.
        showMarkStarted: mayControl && match.state === 'PREPARING',
    }
}
```

Die KDoc darüber um den neuen Knopf ergänzen.

- [ ] **Step 4: Die Dashboard-Karte umbauen**

`LiveDashboardMatchCard.tsx`:
- `const running = match.state === 'RUNNING'` bleibt; ergänzen: `const preparing = match.state === 'PREPARING'`.
- Der Zustandstext der Karte kommt aus `matchStatusChip(...)` statt aus eigener Logik — dieselbe Aussage wie Durchführung und Zeitplan.
- Der Knopf `showRunToggle` heißt jetzt `showActivationToggle` und ruft weiterhin `onSetRunning` (umbenennen zu `onSetActivated`, Endpunkt `.../activation`).
- Neuer Knopf bei `showMarkStarted`: Beschriftung `t('event.liveDashboard.control.markStarted')` = **„Läuft"**, ruft `startLiveDashboardMatch({path: {eventId, matchId}})`. Steht `event.raceclockerAutoPull`, darunter als `caption` der Hinweis `t('event.liveDashboard.control.markStartedAutoHint')` = „RaceClocker meldet den Start automatisch, sobald er ihn sieht."
- Beim Pausen-Hinweis (Zeile ~220) einen Knopf `t('event.competition.execution.results.raceclocker.poll.resume')` ergänzen, der `resumeRaceClockerAutoPull({path: {eventId, competitionId, competitionMatchId: match.matchId}})` aufruft — bisher gibt es diesen Weg nur im Durchführungs-Tab, und der Schiedsrichter deaktiviert im Dashboard.

Neue Übersetzungsschlüssel in allen drei Sprachen ergänzen.

- [ ] **Step 5: Zeitstrahl und Athleten-Anzeige**

`timelineIndicator.ts`: `TimelineEntryState` um `'preparing'` erweitern. In `dashboardMatchState` einen `case 'PREPARING': return 'preparing'` ergänzen; in `scheduleSlotState` vor dem `matchStartedAt`-Zweig:

```ts
    if (slot.matchActivatedAt && !slot.matchStartedAt) {
        return 'preparing'
    }
```

`ScheduleTimelineIndicator.tsx`: `stateColor` um `preparing` erweitern (MUI `info.main`), und der Puls-Effekt (`animation`, Zeile 99) bleibt `running` vorbehalten — ein Lauf am Start pulsiert nicht.

`AthleteBoardMatchCard.tsx:47-62`: `renderRunningStart` liest jetzt `match.state`:

```tsx
    const renderRunningStart = () =>
        match.name ? null : match.state === 'PREPARING' ? (
            <Typography sx={{fontSize: 'clamp(0.75rem, 1.3vw, 1rem)'}} color="text.secondary">
                {t('event.info.athleteBoard.preparing')}
            </Typography>
        ) : match.actualStartTime ? (
            <Typography sx={{fontSize: 'clamp(0.75rem, 1.3vw, 1rem)'}} color="text.secondary">
                {t('event.info.athleteBoard.startedAt', {
                    time: formatClockTime(match.actualStartTime),
                })}
            </Typography>
        ) : null
```

Der Kommentar darüber wird angepasst: Die Unterscheidung kommt jetzt aus dem gemeinsamen Zustand, nicht aus einer zweiten Ableitung. Die **Auswahl** des Blocks „Aktueller Lauf" bleibt unverändert — PREPARING und RUNNING stehen beide dort.

- [ ] **Step 6: Die übrigen Aufrufer**

`CompetitionExecution.tsx`, `CompetitionExecutionRound.tsx`, `DragDropMatchLists.tsx`, `ManageRunningMatchesDialog.tsx`, `LiveDashboardPage.tsx`: alle Verwendungen von `currentlyRunning` auf `activatedAt`/`state` umstellen, `updateMatchRunningState` auf `updateMatchActivation`. Der Schalter in `CompetitionExecutionRound.tsx:398` beschriftet sich neu: nicht mehr „Aktiv", sondern `t('event.competition.execution.match.activated')` = „Am Start".

```bash
cd frontend && grep -rn "currentlyRunning\|waterChip\|teamsOnWater\|showRunToggle" src --include="*.ts" --include="*.tsx" | grep -v "gen.ts"
```

Erwartet nach dem Umbau: keine Treffer.

- [ ] **Step 7: Tests und Build**

```bash
cd frontend && npm run test && npm run build
```

Erwartet: alle Suiten grün, `tsc -b && vite build` ohne Fehler.

- [ ] **Step 8: Commit**

```bash
git add frontend/src
git commit -m "Let the referee say a race is under way"
```

---

## Task 9: Gesamtlauf und Handtest

**Files:** keine Änderungen; falls Fehler auftauchen, in der jeweils zuständigen Datei beheben.

- [ ] **Step 1: Backend vollständig**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test
```

Erwartet: BUILD SUCCESS, 0 Failures, 0 Errors. Die genaue Testzahl im Abschlussbericht nennen.

- [ ] **Step 2: Frontend vollständig**

```bash
cd frontend && npm run test && npm run build
```

Erwartet: alle Suiten grün, Build ohne TypeScript-Fehler.

- [ ] **Step 3: Restbestände suchen**

```bash
grep -rn "currently_running\|currentlyRunning\|CURRENTLY_RUNNING\|NOT_ON_WATER\|onWater\|OnWater" backend/src frontend/src --include="*.kt" --include="*.ts" --include="*.tsx" --include="*.sql" --include="*.yaml" --include="*.json" | grep -v "gen.ts" | grep -v "db/migration/V202608091400" | grep -v "db/migration/V202608091410" | grep -v "db/migration/V202608071200"
```

Erwartet: keine Treffer. Die drei ausgenommenen Migrationen dürfen die alten Namen enthalten — sie beschreiben den Stand vor bzw. den Übergang selbst.

- [ ] **Step 4: Handtest gegen den Förde-Seed**

Die Anwendung starten (Dev-DB + Backend + Frontend, `.claude/launch.json`) und mit `seed-foerde.sql` durchspielen:

1. Lauf im Schiedsrichter-Dashboard aktivieren → Chip zeigt **„In Vorbereitung"**, Athleten-Anzeige zeigt den Lauf unter „Aktueller Lauf" mit dem Untertitel „in Vorbereitung".
2. Zeitplan-Tab und Durchführungs-Tab zeigen **denselben** Chip.
3. Knopf „Läuft" drücken → alle drei Ansichten wechseln auf „Läuft" mit Laufzeit.
4. Lauf beenden → die Kette aktiviert die nächste Startgruppe, und die steht auf „In Vorbereitung", nicht auf „Läuft".
5. Einen aktivierten Lauf deaktivieren → er fällt auf „Anstehend" zurück, der Hinweis „Automatischer Abruf pausiert" erscheint, und der Knopf daneben gibt ihn wieder frei.

> Fehlt `backend/.env`/`frontend/.env` in diesem Worktree, ist dieser Schritt hier nicht ausführbar. Dann Schritte 1–3 abschließen und den Handtest als **offen** an Thomas melden, statt ihn zu überspringen und Vollständigkeit zu behaupten.

- [ ] **Step 5: Abschlussbericht**

Im Bericht nennen: Testzahlen beider Seiten, ob der Handtest lief oder offen ist, und jede Abweichung vom Plan mit Begründung.

---

## Selbstprüfung des Plans

**Spec-Abdeckung:** §1.1 → Task 1. §1.2 → Task 2. §1.3 → Tasks 3+5. §2 Tabelle → Task 3 (Aktivieren, Läuft, Deaktivieren, Beenden, Kette) und Task 4 (beide Poll-Zweige). §2.1 → Task 8 Step 4. §2.2 → Task 4. §2.3 → Task 3 Step 4 + Task 8 Step 4. §3 Tabelle → Tasks 7+8, jede Zeile einem Schritt zugeordnet. §3.1 → Task 3. §4.1 → Task 6 Step 1. §4.2 → Tasks 6+7. §5 → die Testschritte in Tasks 2, 3, 4, 7, 8 und der Gesamtlauf in Task 9. §6 Reihenfolge → Tasknummerierung. §7 Risiko → Task 9 Step 4.

**Typkonsistenz:** `activatedAt` (nicht `activated`) als DTO-/Spaltenname durchgehend; `activated: Boolean` nur als Request-/Query-Parameter. `matchActivatedAt`/`matchStartedAt` durchgehend in `ChainSlot` und `EventScheduleSlotDto`. `showActivationToggle`/`showMarkStarted` in Task 8 identisch zu Task 8 Step 3. `teamsInArena` durchgehend (Backend Task 6, Frontend Task 7). `arenaChip` in Task 7 definiert, in Task 8 Step 6 als Restbestandsprüfung referenziert.
