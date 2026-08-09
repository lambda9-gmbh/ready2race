# „Mein Event" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein dritter Reiter „Mein Event" auf der öffentlichen Ergebnisseite, der nach dem Scan des
QR-Codes am Teilnehmerband die eigenen Läufe, Ergebnisse und freigegebenen Bedingungen zeigt.

**Architecture:** Ein neuer öffentlicher Endpunkt `GET /event/{eventId}/info/my-event/{qrCode}`
neben der Athleten-Anzeige, der den Code über `QrCodeRepo.findByCode` zu einem Teilnehmer auflöst
und dessen Läufe liefert. Sichtbarkeitsregeln, Zustandsableitung und Zwischenspeicherung werden aus
`AthleteBoardLogic`/`EventInfoService` wiederverwendet, nicht nachgebaut. Im Frontend hält eine
Liste im `localStorage` die gescannten Codes; die URL trägt den Code nach dem Einstieg nicht mehr.

**Tech Stack:** Kotlin/Ktor + jOOQ + Flyway (Backend), React 18 + MUI 6 + TanStack Router (Frontend),
`@hey-api/openapi-ts` für den generierten Client, kotlin.test + Testcontainers (Backend-Tests),
vitest (Frontend-Tests).

**Spec:** `docs/superpowers/specs/2026-08-09-mein-event-design.md`

## Global Constraints

- **JAVA_HOME muss gesetzt werden**, die Shell hat es nicht: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21`
- **Datenbank muss laufen**, bevor Maven gebaut wird: `cd backend && docker compose up -d`. Flyway und
  die jOOQ-Codegenerierung hängen in `generate-sources` und brauchen eine erreichbare Datenbank.
- **Generierte Dateien werden neu erzeugt, nie von Hand bearbeitet:**
  `backend/src/main/kotlin/de/lambda9/ready2race/backend/database/generated/**` (Maven) und
  `frontend/src/api/**` (`npm run generate`).
- **Quelle der API-Wahrheit** ist `backend/src/main/resources/openapi/documentation.yaml`. Die Dateien
  unter `api/src/*.tsp` sind Stümpfe und werden **nicht** gepflegt.
- **Migrationsnummer:** `V202608101000`. Sie liegt bewusst nach `V202608091500` aus der
  Auto-Abgleich-Arbeit auf einem Parallelzweig.
- **Drei Sprachen:** jeder neue i18n-Schlüssel muss in `frontend/src/i18n/de/translations.json`,
  `frontend/src/i18n/en/translations.json` **und** `frontend/src/i18n/da/translations.json` stehen.
- **Deutsche Texte mit echten Umlauten** (ä, ö, ü, ß), niemals ae/oe/ue/ss.
- **Kommentare auf Deutsch**, im Stil des umgebenden Codes: sie erklären das *Warum*, nicht das *Was*.
- **Sprachlich generisch bleiben:** Lauf, Startposition, Mannschaft, Wettkampf. Keine sportartspezifischen
  Begriffe wie Boot, Bahn im Sinne von Ruderbahn, Steuermann.
- **`KIO.fail` ohne `!` ist ein No-Op.** In jeder `KIO.comprehension` muss ein Fehlerabbruch
  `!KIO.fail<Problem>(...)` lauten. Ein `KIO.fail(...)` ohne führendes `!` läuft wirkungslos weiter.
- **Keine Freitext-Notiz nach außen:** `CheckedParticipantRequirement.note` darf in keiner Antwort des
  neuen Endpunkts vorkommen.
- **Commit-Nachrichten ohne Hinweise auf KI-Werkzeuge**, kein `Co-Authored-By`. Deutsch mit
  echten Umlauten — die Umlaut-Regel gilt auch für Commit-Zeilen.

---

## File Structure

**Backend — neu:**

| Datei | Verantwortung |
| --- | --- |
| `backend/src/main/resources/db/migration/V202608101000__participant_requirement_publicly_visible.sql` | Spalte `publicly_visible` |
| `.../app/eventInfo/entity/MyEventDto.kt` | Antwortmodell des neuen Endpunkts |
| `.../app/eventInfo/boundary/MyEventLogic.kt` | reine Aufteilungs- und Sortierlogik, ohne Datenbank |
| `.../app/eventInfo/boundary/MyEventService.kt` | Auflösung des Codes, Zusammenbau, Zwischenspeicher |
| `.../app/eventInfo/control/MyEventRepo.kt` | teilnehmerbezogene Abfragen |

**Backend — geändert:**

| Datei | Änderung |
| --- | --- |
| `.../app/participantRequirement/entity/ParticipantRequirementDto.kt` | Feld `publiclyVisible` |
| `.../app/participantRequirement/entity/ParticipantRequirementForEventDto.kt` | Feld `publiclyVisible` |
| `.../app/participantRequirement/entity/ParticipantRequirementUpsertDto.kt` | Feld `publiclyVisible` |
| `.../app/participantRequirement/control/Conversions.kt` | Feld in fünf Abbildungen |
| `.../app/participantRequirement/boundary/ParticipantRequirementService.kt:266` | Feld beim Aktualisieren |
| `.../app/eventInfo/entity/EventInfoProblem.kt` | Fehlerfall `QrCodeNotFound` |
| `.../app/eventInfo/boundary/eventInfo.kt` | Route im `publicInfo`-Block |
| `backend/src/main/resources/openapi/documentation.yaml` | neues Feld + neuer Pfad + neue Schemata |

**Frontend — neu:**

| Datei | Verantwortung |
| --- | --- |
| `frontend/src/utils/myEventStorage.ts` | Liste gescannter Codes im `localStorage`, rein und testbar |
| `frontend/src/utils/myEventStorage.test.ts` | Tests dazu |
| `frontend/src/components/results/myEvent/usePolledEndpoint.ts` | verallgemeinerter Abruftakt |
| `frontend/src/components/results/myEvent/MyEventPanel.tsx` | Zusammenbau der Blöcke |
| `frontend/src/components/results/myEvent/MyEventPersonSwitcher.tsx` | Umschalter mit Entfernen |
| `frontend/src/components/results/myEvent/MyEventRequirements.tsx` | Bedingungsliste und Warnband |
| `frontend/src/components/results/myEvent/MyEventMatchList.tsx` | Läufe, Ergebnisse, unterminierte Meldungen |
| `frontend/src/components/results/myEvent/myEventOrder.ts` | tageszeitliche Umsortierung, rein |
| `frontend/src/components/results/myEvent/myEventOrder.test.ts` | Tests dazu |

**Frontend — geändert:**

| Datei | Änderung |
| --- | --- |
| `frontend/src/pages/results/ResultsQrCodePage.tsx` | speichert statt zu verwerfen |
| `frontend/src/pages/results/ResultsPage.tsx:22` | dritter Reiter |
| `frontend/src/layouts/ResultsLayout.tsx` | `noindex` |
| `frontend/src/components/event/info/athleteBoard/useAthleteBoardData.ts` | nutzt den verallgemeinerten Takt |
| `frontend/src/components/event/participantRequirement/ParticipantRequirementDialog.tsx` | Checkbox |
| `frontend/src/i18n/{de,en,da}/translations.json` | neue Schlüssel |

---

## Task 1: Bedingungen öffentlich freigebbar machen (Backend)

**Files:**
- Create: `backend/src/main/resources/db/migration/V202608101000__participant_requirement_publicly_visible.sql`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/participantRequirement/entity/ParticipantRequirementDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/participantRequirement/entity/ParticipantRequirementForEventDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/participantRequirement/entity/ParticipantRequirementUpsertDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/participantRequirement/control/Conversions.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/participantRequirement/boundary/ParticipantRequirementService.kt:266`
- Modify: `backend/src/main/resources/openapi/documentation.yaml`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/participantRequirement/ParticipantRequirementConversionsTest.kt` (neu)

**Interfaces:**
- Produces: `ParticipantRequirementForEventDto.publiclyVisible: Boolean`,
  `ParticipantRequirementUpsertDto.publiclyVisible: Boolean?`,
  Spalte `PARTICIPANT_REQUIREMENT.PUBLICLY_VISIBLE` in den generierten jOOQ-Klassen.

- [ ] **Step 1: Migration schreiben**

`backend/src/main/resources/db/migration/V202608101000__participant_requirement_publicly_visible.sql`:

```sql
set search_path to ready2race, pg_catalog, public;

-- Gibt eine Bedingung für das persönliche Dashboard "Mein Event" frei. Der Standard ist
-- bewusst "aus": die Freigabe ist eine Entscheidung des Veranstalters darüber, welche
-- Aussage über eine Person öffentlich sichtbar sein darf, und darf nicht als Nebeneffekt
-- einer Migration entstehen. Die Freitext-Notiz aus checked_participant_requirement wird
-- unabhängig von diesem Schalter niemals öffentlich ausgeliefert.
-- Die View participant_requirement_for_event selektiert pr.* und übernimmt die Spalte
-- daher ohne eigene Anpassung; afterMigrate.sql erzeugt sie im selben Lauf neu.
alter table participant_requirement
    add column publicly_visible boolean not null default false;
```

- [ ] **Step 2: Datenbank starten und Code neu erzeugen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
cd backend && docker compose up -d && ./mvnw -q generate-sources
```

Erwartet: Läuft durch. Danach enthält
`backend/src/main/kotlin/de/lambda9/ready2race/backend/database/generated/tables/ParticipantRequirement.kt`
das Feld `PUBLICLY_VISIBLE`. Prüfen mit:

```bash
grep -c "PUBLICLY_VISIBLE" backend/src/main/kotlin/de/lambda9/ready2race/backend/database/generated/tables/ParticipantRequirement.kt
```

Erwartet: Zahl größer 0.

- [ ] **Step 3: Fehlschlagenden Test schreiben**

`backend/src/test/kotlin/de/lambda9/ready2race/backend/app/participantRequirement/ParticipantRequirementConversionsTest.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.participantRequirement

import de.lambda9.ready2race.backend.app.participantRequirement.control.toDto
import de.lambda9.ready2race.backend.app.participantRequirement.control.toRecord
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementUpsertDto
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRequirementRecord
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrNull
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Die Freigabe für "Mein Event" ist die einzige Stelle, an der eine Aussage über eine Person
 * öffentlich wird. Ein stillschweigend auf `true` gekipptes Standardverhalten wäre ein
 * Datenschutzfehler, deshalb prüfen diese Tests beide Richtungen der Abbildung samt Standard.
 */
class ParticipantRequirementConversionsTest {

    private fun upsert(publiclyVisible: Boolean?) = ParticipantRequirementUpsertDto(
        name = "Aktivenpass",
        description = null,
        optional = false,
        checkInApp = false,
        publiclyVisible = publiclyVisible,
        checkEarliestMinutesBefore = null,
        checkLatestMinutesBefore = null,
    )

    @Test
    fun upsertCarriesPubliclyVisibleIntoRecord() {
        val record = upsert(true).toRecord(UUID.randomUUID()).unsafeRunSync(Unit).getOrNull()
        assertNotNull(record)
        assertEquals(true, record.publiclyVisible)
    }

    @Test
    fun missingPubliclyVisibleDefaultsToFalse() {
        val record = upsert(null).toRecord(UUID.randomUUID()).unsafeRunSync(Unit).getOrNull()
        assertNotNull(record)
        assertEquals(false, record.publiclyVisible)
    }

    @Test
    fun recordCarriesPubliclyVisibleIntoDto() {
        val now = LocalDateTime.of(2026, 8, 9, 12, 0)
        val record = ParticipantRequirementRecord(
            id = UUID.randomUUID(),
            name = "Aktivenpass",
            description = null,
            optional = false,
            checkInApp = false,
            publiclyVisible = true,
            checkEarliestMinutesBefore = null,
            checkLatestMinutesBefore = null,
            createdAt = now,
            createdBy = null,
            updatedAt = now,
            updatedBy = null,
        )
        val dto = record.toDto().unsafeRunSync(Unit).getOrNull()
        assertNotNull(dto)
        assertEquals(true, dto.publiclyVisible)

        record.publiclyVisible = false
        val dtoOff = record.toDto().unsafeRunSync(Unit).getOrNull()
        assertNotNull(dtoOff)
        assertFalse(dtoOff.publiclyVisible)
    }
}
```

- [ ] **Step 4: Test laufen lassen und Fehlschlag bestätigen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q -Dtest=ParticipantRequirementConversionsTest test
```

Erwartet: Übersetzungsfehler, weil `ParticipantRequirementUpsertDto` kein `publiclyVisible` kennt.

- [ ] **Step 5: Feld in die drei DTOs aufnehmen**

`ParticipantRequirementDto.kt` und `ParticipantRequirementForEventDto.kt` bekommen jeweils
`val publiclyVisible: Boolean,` direkt hinter `checkInApp`.

In `ParticipantRequirementUpsertDto.kt` hinter `checkInApp`:

```kotlin
    val publiclyVisible: Boolean?,
```

und im `example`-Block hinter `checkInApp = false,`:

```kotlin
                publiclyVisible = false,
```

- [ ] **Step 6: Abbildungen in `Conversions.kt` ergänzen**

Fünf Stellen, jeweils direkt hinter der `checkInApp`-Zeile:

- in `ParticipantRequirementUpsertDto.toRecord`: `publiclyVisible = publiclyVisible ?: false,`
- in `ParticipantRequirementRecord.toDto`: `publiclyVisible = publiclyVisible,`
- in `ParticipantRequirementForEventRecord.toDto`: `publiclyVisible = publiclyVisible ?: false,`
- in `ParticipantRequirementForEventRecord.toRequirementDto`: `publiclyVisible = publiclyVisible ?: false,`
- in `ParticipantRequirementForEventRecord.toNamedParticipantRequirementDto` **nicht** ergänzen —
  `CompetitionRegistrationNamedParticipantRequirementDto` beschreibt die Meldemaske und hat mit der
  öffentlichen Sichtbarkeit nichts zu tun.

In `ParticipantRequirementService.kt` hinter Zeile 266 (`checkInApp = request.checkInApp ?: false`):

```kotlin
            publiclyVisible = request.publiclyVisible ?: false
```

- [ ] **Step 7: Test laufen lassen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q -Dtest=ParticipantRequirementConversionsTest test
```

Erwartet: BUILD SUCCESS, drei Tests grün.

- [ ] **Step 8: OpenAPI ergänzen**

In `backend/src/main/resources/openapi/documentation.yaml` bei den Schemata
`ParticipantRequirementDto`, `ParticipantRequirementForEventDto` und
`ParticipantRequirementUpsertDto` jeweils in `properties` ergänzen:

```yaml
        publiclyVisible:
          type: boolean
          description: "Im öffentlichen Dashboard \"Mein Event\" sichtbar"
```

Bei `ParticipantRequirementDto` und `ParticipantRequirementForEventDto` zusätzlich
`- publiclyVisible` in die `required`-Liste aufnehmen; bei `ParticipantRequirementUpsertDto`
**nicht** (das Feld ist beim Anlegen freiwillig).

- [ ] **Step 9: Gesamten Testlauf**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test
```

Erwartet: BUILD SUCCESS. Schlägt ein bestehender Test fehl, weil ihm der neue Konstruktorparameter
fehlt, dort `publiclyVisible = false` ergänzen.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/resources/db/migration/V202608101000__participant_requirement_publicly_visible.sql backend/src/main/kotlin backend/src/test backend/src/main/resources/openapi/documentation.yaml
git commit -m "Bedingungen koennen fuer das persoenliche Dashboard freigegeben werden"
```

---

## Task 2: Checkbox im Bedingungs-Editor (Frontend)

**Files:**
- Modify: `frontend/src/components/event/participantRequirement/ParticipantRequirementDialog.tsx`
- Modify: `frontend/src/i18n/de/translations.json`, `frontend/src/i18n/en/translations.json`, `frontend/src/i18n/da/translations.json`
- Regenerate: `frontend/src/api/**`

**Interfaces:**
- Consumes: `publiclyVisible` aus Task 1 (OpenAPI → generierter Client)

- [ ] **Step 1: Client neu erzeugen**

```bash
cd frontend && npm run generate
```

Prüfen:

```bash
grep -n "publiclyVisible" frontend/src/api/types.gen.ts | head
```

Erwartet: Treffer in `ParticipantRequirementDto`, `ParticipantRequirementForEventDto`,
`ParticipantRequirementUpsertDto`.

- [ ] **Step 2: i18n-Schlüssel ergänzen**

In allen drei Dateien im Block `participantRequirement` (der auf oberster Ebene, bei
`"checkInApp"` in `frontend/src/i18n/de/translations.json:1717`) direkt hinter `checkInApp`:

- `de`: `"publiclyVisible": "Für Teilnehmende sichtbar (Mein Event)",`
- `en`: `"publiclyVisible": "Visible to participants (My Event)",`
- `da`: `"publiclyVisible": "Synlig for deltagere (Min begivenhed)",`

Denselben Schlüssel auch im zweiten `participantRequirement`-Block ergänzen
(`frontend/src/i18n/de/translations.json:1189`), falls dieser ebenfalls `checkInApp` führt —
mit `grep -n '"checkInApp"' frontend/src/i18n/de/translations.json` beide Stellen bestimmen.

- [ ] **Step 3: Formular erweitern**

In `ParticipantRequirementDialog.tsx` an vier Stellen:

Typ `ParticipantRequirementForm` hinter `checkInApp: boolean`:

```typescript
    publiclyVisible: boolean
```

`defaultValues` hinter `checkInApp: false,`:

```typescript
        publiclyVisible: false,
```

Formularfeld hinter der `checkInApp`-Checkbox:

```tsx
                <FormInputCheckbox
                    name="publiclyVisible"
                    label={t('participantRequirement.publiclyVisible')}
                />
```

`mapFormToRequest` hinter `checkInApp: formData.checkInApp,`:

```typescript
        publiclyVisible: formData.publiclyVisible,
```

`mapDtoToForm` hinter `checkInApp: dto.checkInApp,`:

```typescript
        publiclyVisible: dto.publiclyVisible,
```

- [ ] **Step 4: Übersetzung und Bau prüfen**

```bash
cd frontend && npm run build
```

Erwartet: kein TypeScript-Fehler.

- [ ] **Step 5: Commit**

```bash
git add frontend/src
git commit -m "Bedingungs-Editor: Freigabe fuer Mein Event"
```

---

## Task 3: Antwortmodell und reine Logik für „Mein Event"

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/MyEventDto.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/MyEventLogic.kt`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventInfo/MyEventLogicTest.kt`

**Interfaces:**
- Consumes: `AthleteBoardLogic.isPublicResult`, `AthleteBoardLogic.startState`,
  `AthleteBoardLogic.MIN_REFRESH_INTERVAL_SECONDS`, `AthleteBoardStartState`,
  `PublicResultsVisibility`
- Produces:
  - `MyEventDto(displayName, clubName, eventName, serverTime, refreshIntervalSeconds, running, upcoming, results, unscheduled, requirements)`
  - `MyEventMatchDto`, `MyEventTeamMemberDto`, `MyEventResultDto`, `MyEventRegistrationDto`, `MyEventRequirementDto`
  - `MyEventLogic.PARTICIPANT_CACHE_TTL_SECONDS: Int`
  - `MyEventLogic.split(entries: List<MyEventLogic.RawMatch>, now: LocalDateTime, visibility: PublicResultsVisibility, showCountdown: Boolean): MyEventLogic.Split`

- [ ] **Step 1: Antwortmodell anlegen**

`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/MyEventDto.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.time.LocalDateTime
import java.util.UUID

/**
 * Das persönliche Dashboard eines Teilnehmenden, erreichbar über den QR-Code am Band.
 *
 * Bewusst nicht enthalten: E-Mail-Adresse, Geschlecht, Jahrgang und die Freitext-Notiz zu
 * einer Bedingung. Die ersten drei braucht die Ansicht nicht, die Notiz ist für interne
 * Augen geschrieben. Siehe docs/superpowers/specs/2026-08-09-mein-event-design.md.
 */
data class MyEventDto(
    val displayName: String,
    val clubName: String?,
    val eventName: String,
    val serverTime: LocalDateTime,
    val refreshIntervalSeconds: Int,
    val running: List<MyEventMatchDto>,
    val upcoming: List<MyEventMatchDto>,
    val results: List<MyEventResultDto>,
    val unscheduled: List<MyEventRegistrationDto>,
    val requirements: List<MyEventRequirementDto>,
)

data class MyEventMatchDto(
    val matchId: UUID,
    val competitionName: String,
    val categoryName: String?,
    val roundName: String?,
    val matchName: String?,
    val startTime: LocalDateTime?,
    val actualStartTime: LocalDateTime?,
    val startState: AthleteBoardStartState,
    val lane: Int?,
    val teamName: String?,
    val clubName: String?,
    val teamMembers: List<MyEventTeamMemberDto>,
)

data class MyEventTeamMemberDto(
    val name: String,
    val role: String?,
    /** true für die Person, der dieser QR-Code gehört — die Anzeige hebt sie hervor. */
    val self: Boolean,
)

data class MyEventResultDto(
    val matchId: UUID,
    val competitionName: String,
    val categoryName: String?,
    val roundName: String?,
    val matchName: String?,
    val startTime: LocalDateTime?,
    val actualStartTime: LocalDateTime?,
    val place: Int?,
    val timeString: String?,
    val penaltySeconds: Int?,
    val penaltyNote: String?,
    val failed: Boolean,
    val failedReason: String?,
    val deregistered: Boolean,
    val deregisteredReason: String?,
)

data class MyEventRegistrationDto(
    val competitionId: UUID,
    val competitionIdentifier: String,
    val competitionName: String,
    val categoryName: String?,
    val teamName: String?,
    val role: String?,
)

data class MyEventRequirementDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val optional: Boolean,
    val fulfilled: Boolean,
)
```

- [ ] **Step 2: Fehlschlagenden Test schreiben**

`backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventInfo/MyEventLogicTest.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.eventInfo

import de.lambda9.ready2race.backend.app.event.entity.PublicResultsVisibility
import de.lambda9.ready2race.backend.app.eventInfo.boundary.MyEventLogic
import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardStartState
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "Mein Event" darf ein Ergebnis nicht früher zeigen als die Athleten-Anzeige. Diese Tests
 * halten fest, dass die Aufteilung dieselben Regeln benutzt wie AthleteBoardLogic — driften
 * die beiden auseinander, steht dasselbe Rennen auf zwei Bildschirmen unterschiedlich da.
 */
class MyEventLogicTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 14, 10, 0)

    private fun raw(
        startTime: LocalDateTime? = null,
        actualStartTime: LocalDateTime? = null,
        finishedAt: LocalDateTime? = null,
        allTeamsScored: Boolean = false,
        currentlyRunning: Boolean = false,
    ) = MyEventLogic.RawMatch(
        matchId = UUID.randomUUID(),
        competitionName = "Wettkampf",
        categoryName = null,
        roundName = null,
        matchName = null,
        startTime = startTime,
        actualStartTime = actualStartTime,
        finishedAt = finishedAt,
        allTeamsScored = allTeamsScored,
        currentlyRunning = currentlyRunning,
        lane = 1,
        teamName = null,
        clubName = "Verein",
        teamMembers = emptyList(),
        place = null,
        timeString = null,
        penaltySeconds = null,
        penaltyNote = null,
        failed = false,
        failedReason = null,
        deregistered = false,
        deregisteredReason = null,
    )

    @Test
    fun finishedMatchBecomesResult() {
        val split = MyEventLogic.split(
            entries = listOf(raw(startTime = now.minusHours(1), finishedAt = now.minusMinutes(30))),
            now = now,
            visibility = PublicResultsVisibility.FINISHED_ONLY,
            showCountdown = true,
        )
        assertEquals(1, split.results.size)
        assertTrue(split.running.isEmpty())
        assertTrue(split.upcoming.isEmpty())
    }

    @Test
    fun scoredButUnfinishedMatchStaysHiddenUnderFinishedOnly() {
        val split = MyEventLogic.split(
            entries = listOf(raw(startTime = now.minusHours(1), allTeamsScored = true, currentlyRunning = true)),
            now = now,
            visibility = PublicResultsVisibility.FINISHED_ONLY,
            showCountdown = true,
        )
        assertTrue(split.results.isEmpty())
        assertEquals(1, split.running.size)
    }

    @Test
    fun scoredButUnfinishedMatchAppearsUnderResultsComplete() {
        val split = MyEventLogic.split(
            entries = listOf(raw(startTime = now.minusHours(1), allTeamsScored = true, currentlyRunning = true)),
            now = now,
            visibility = PublicResultsVisibility.RESULTS_COMPLETE,
            showCountdown = true,
        )
        assertEquals(1, split.results.size)
        assertTrue(split.running.isEmpty())
    }

    @Test
    fun upcomingMatchesAreSortedByStartTime() {
        val late = raw(startTime = now.plusHours(2))
        val early = raw(startTime = now.plusMinutes(30))
        val split = MyEventLogic.split(
            entries = listOf(late, early),
            now = now,
            visibility = PublicResultsVisibility.FINISHED_ONLY,
            showCountdown = true,
        )
        assertEquals(listOf(early.matchId, late.matchId), split.upcoming.map { it.matchId })
    }

    @Test
    fun passedStartTimeYieldsOverdueInsteadOfNegativeCountdown() {
        val split = MyEventLogic.split(
            entries = listOf(raw(startTime = now.minusMinutes(5))),
            now = now,
            visibility = PublicResultsVisibility.FINISHED_ONLY,
            showCountdown = true,
        )
        assertEquals(AthleteBoardStartState.OVERDUE, split.upcoming.single().startState)
    }

    @Test
    fun resultsAreSortedNewestFirst() {
        val older = raw(startTime = now.minusHours(3), finishedAt = now.minusHours(3))
        val newer = raw(startTime = now.minusHours(1), finishedAt = now.minusHours(1))
        val split = MyEventLogic.split(
            entries = listOf(older, newer),
            now = now,
            visibility = PublicResultsVisibility.FINISHED_ONLY,
            showCountdown = true,
        )
        assertEquals(listOf(newer.matchId, older.matchId), split.results.map { it.matchId })
    }

    @Test
    fun cacheTtlMatchesAthleteBoard() {
        // Beide Ansichten zeigen dieselben Läufe. Eine kürzere TTL hier würde "Mein Event"
        // vor der Anzeige aktualisieren und damit widersprüchliche Stände nebeneinander erzeugen.
        assertEquals(5, MyEventLogic.PARTICIPANT_CACHE_TTL_SECONDS)
    }
}
```

- [ ] **Step 3: Test laufen lassen und Fehlschlag bestätigen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q -Dtest=MyEventLogicTest test
```

Erwartet: Übersetzungsfehler, `MyEventLogic` existiert nicht.

- [ ] **Step 4: Logik schreiben**

`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/MyEventLogic.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.eventInfo.boundary

import de.lambda9.ready2race.backend.app.event.entity.PublicResultsVisibility
import de.lambda9.ready2race.backend.app.eventInfo.entity.MyEventMatchDto
import de.lambda9.ready2race.backend.app.eventInfo.entity.MyEventResultDto
import de.lambda9.ready2race.backend.app.eventInfo.entity.MyEventTeamMemberDto
import java.time.LocalDateTime
import java.util.UUID

/**
 * Reine Aufteilungs- und Sortierlogik des persönlichen Dashboards: aus einer flachen Liste
 * der eigenen Läufe werden "läuft gerade", "kommt noch" und "Ergebnis".
 *
 * Die Sichtbarkeitsregel für Ergebnisse und die Ableitung des Startzustands stammen
 * unverändert aus [AthleteBoardLogic]. Das ist Absicht und keine Bequemlichkeit: erschiene
 * ein Ergebnis hier früher als auf der Athleten-Anzeige, stünde dasselbe Rennen auf zwei
 * Bildschirmen unterschiedlich da — und der gezeigte Wert kann sich durch eine später
 * eintreffende Zeitstrafe noch ändern.
 */
object MyEventLogic {

    /** Gleiche Frist wie [AthleteBoardLogic.CACHE_TTL_SECONDS], siehe Klassenkommentar. */
    const val PARTICIPANT_CACHE_TTL_SECONDS = AthleteBoardLogic.CACHE_TTL_SECONDS

    /**
     * Ein Lauf der Person, wie ihn die Datenbank liefert — vor der Einordnung in
     * laufend/kommend/Ergebnis.
     */
    data class RawMatch(
        val matchId: UUID,
        val competitionName: String,
        val categoryName: String?,
        val roundName: String?,
        val matchName: String?,
        val startTime: LocalDateTime?,
        val actualStartTime: LocalDateTime?,
        val finishedAt: LocalDateTime?,
        val allTeamsScored: Boolean,
        val currentlyRunning: Boolean,
        val lane: Int?,
        val teamName: String?,
        val clubName: String?,
        val teamMembers: List<MyEventTeamMemberDto>,
        val place: Int?,
        val timeString: String?,
        val penaltySeconds: Int?,
        val penaltyNote: String?,
        val failed: Boolean,
        val failedReason: String?,
        val deregistered: Boolean,
        val deregisteredReason: String?,
    )

    data class Split(
        val running: List<MyEventMatchDto>,
        val upcoming: List<MyEventMatchDto>,
        val results: List<MyEventResultDto>,
    )

    fun split(
        entries: List<RawMatch>,
        now: LocalDateTime,
        visibility: PublicResultsVisibility,
        showCountdown: Boolean,
    ): Split {
        val (finished, open) = entries.partition {
            AthleteBoardLogic.isPublicResult(it.finishedAt, it.allTeamsScored, visibility)
        }
        val (running, upcoming) = open.partition { it.currentlyRunning }

        return Split(
            running = running
                .sortedWith(compareBy(nullsLast()) { it.startTime })
                .map { it.toMatchDto(now, showCountdown) },
            upcoming = upcoming
                .sortedWith(compareBy(nullsLast()) { it.startTime })
                .map { it.toMatchDto(now, showCountdown) },
            // Neuestes zuerst: nach dem Rennen interessiert das eigene letzte Ergebnis,
            // nicht das vom Vormittag.
            results = finished
                .sortedWith(compareByDescending(nullsLast()) { it.actualStartTime ?: it.startTime })
                .map { it.toResultDto() },
        )
    }

    private fun RawMatch.toMatchDto(now: LocalDateTime, showCountdown: Boolean) = MyEventMatchDto(
        matchId = matchId,
        competitionName = competitionName,
        categoryName = categoryName,
        roundName = roundName,
        matchName = matchName,
        startTime = startTime,
        actualStartTime = actualStartTime,
        startState = AthleteBoardLogic.startState(startTime, now, showCountdown),
        lane = lane,
        teamName = teamName,
        clubName = clubName,
        teamMembers = teamMembers,
    )

    private fun RawMatch.toResultDto() = MyEventResultDto(
        matchId = matchId,
        competitionName = competitionName,
        categoryName = categoryName,
        roundName = roundName,
        matchName = matchName,
        startTime = startTime,
        actualStartTime = actualStartTime,
        place = place,
        timeString = timeString,
        penaltySeconds = penaltySeconds,
        penaltyNote = penaltyNote,
        failed = failed,
        failedReason = failedReason,
        deregistered = deregistered,
        deregisteredReason = deregisteredReason,
    )
}
```

- [ ] **Step 5: Test laufen lassen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q -Dtest=MyEventLogicTest test
```

Erwartet: BUILD SUCCESS, sieben Tests grün. Schlägt `cacheTtlMatchesAthleteBoard` fehl, ist
`AthleteBoardLogic.CACHE_TTL_SECONDS` nicht mehr 5 — dann den erwarteten Wert im Test
angleichen, **nicht** die Konstante entkoppeln.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventInfo
git commit -m "Mein Event: Antwortmodell und Aufteilungslogik"
```

---

## Task 4: Abfragen, Dienst und Endpunkt

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/control/MyEventRepo.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/MyEventService.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/entity/EventInfoProblem.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/eventInfo.kt`
- Modify: `backend/src/main/resources/openapi/documentation.yaml`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventInfo/MyEventServiceIT.kt`

**Interfaces:**
- Consumes: `MyEventLogic.split`, `MyEventLogic.RawMatch`, `MyEventDto` (Task 3),
  `QrCodeRepo.findByCode(qrCodeId: String): JIO<QrCodesRecord?>`,
  `EventRepo.exists`, `EventRepo.getName`, `EventRepo.getPublicResultsVisibility`,
  `ParticipantRequirementForEventRepo.get(eventId, onlyActive, onlyForApp)`
- Produces: `MyEventService.getMyEvent(eventId: UUID, qrCode: String): App<EventInfoProblem, ApiResponse.Dto<MyEventDto>>`,
  Route `GET /event/{eventId}/info/my-event/{qrCode}`

- [ ] **Step 1: Fehlerfall ergänzen**

In `EventInfoProblem.kt` in das `sealed interface`:

```kotlin
    data class QrCodeNotFound(val qrCode: String) : EventInfoProblem
```

und in `respond()`:

```kotlin
        is QrCodeNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            // Bewusst dieselbe Antwort für "gibt es nicht", "gehört zu einer anderen
            // Veranstaltung" und "gehört zu einer Helferrolle": eine unterscheidbare
            // Meldung würde verraten, welche Codes existieren.
            message = "No participant found for this code"
        )
```

- [ ] **Step 2: Fehlschlagenden Integrationstest schreiben**

`backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventInfo/MyEventServiceIT.kt`.
Der Test baut seine Daten selbst auf. Die generierten `...Record`-Klassen sind die
verbindliche Auskunft darüber, welche Spalten Pflicht sind: fehlt ein Pflichtfeld, meldet es
der Kotlin-Übersetzer. Aufbau in dieser Reihenfolge, jeweils per `Jooq.query { ... }` und
`insertInto`:

`club` → `event` → `competition` + `competition_properties` → `named_participant` →
`participant` → `event_registration` → `competition_registration` →
`competition_registration_named_participant` → `qr_codes` → `competition_match` →
`competition_match_team`.

```kotlin
package de.lambda9.ready2race.backend.app.eventInfo

import de.lambda9.ready2race.backend.app.eventInfo.boundary.MyEventService
import de.lambda9.ready2race.backend.app.eventInfo.entity.EventInfoProblem
import com.fasterxml.jackson.databind.ObjectMapper
import de.lambda9.ready2race.testing.testComprehension
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Prüft die Zugriffsgrenzen des öffentlichen Endpunkts an einer echten Datenbank. Genau hier
 * entscheidet sich, ob ein fremder oder ein Helfer-Code an persönliche Daten kommt — das
 * lässt sich nicht sinnvoll mit Attrappen prüfen.
 */
class MyEventServiceIT {

    @Test
    fun unknownCodeIsNotFound() = testComprehension {
        val fixture = !MyEventFixture.create()
        assertKIOFails(EventInfoProblem.QrCodeNotFound("gibt-es-nicht")) {
            MyEventService.getMyEvent(fixture.eventId, "gibt-es-nicht")
        }
    }

    @Test
    fun codeOfAnotherEventIsNotFound() = testComprehension {
        val fixture = !MyEventFixture.create()
        val other = !MyEventFixture.create()
        assertKIOFails(EventInfoProblem.QrCodeNotFound(fixture.participantQrCode)) {
            MyEventService.getMyEvent(other.eventId, fixture.participantQrCode)
        }
    }

    @Test
    fun appUserCodeIsNotFound() = testComprehension {
        val fixture = !MyEventFixture.create()
        assertKIOFails(EventInfoProblem.QrCodeNotFound(fixture.appUserQrCode)) {
            MyEventService.getMyEvent(fixture.eventId, fixture.appUserQrCode)
        }
    }

    @Test
    fun ownMatchesAreReturnedAndForeignAreNot() = testComprehension {
        val fixture = !MyEventFixture.create()
        val response = !MyEventService.getMyEvent(fixture.eventId, fixture.participantQrCode)
        val dto = response.dto
        val allMatchIds = (dto.running + dto.upcoming).map { it.matchId } + dto.results.map { it.matchId }
        assertTrue(allMatchIds.contains(fixture.ownMatchId))
        assertFalse(allMatchIds.contains(fixture.foreignMatchId))
    }

    @Test
    fun onlyPubliclyVisibleRequirementsAreReturned() = testComprehension {
        val fixture = !MyEventFixture.create()
        val response = !MyEventService.getMyEvent(fixture.eventId, fixture.participantQrCode)
        val names = response.dto.requirements.map { it.name }
        assertEquals(listOf(fixture.publicRequirementName), names)
    }

    @Test
    fun internalNoteNeverLeavesTheServer() = testComprehension {
        // Gegen die ausgelieferte JSON-Darstellung geprüft, nicht gegen die Datenklasse:
        // ein später ergänztes Feld oder eine eingebettete Struktur würde die Notiz sonst
        // unbemerkt nach außen tragen. Der Test muss scheitern, sobald sie irgendwo auftaucht.
        val fixture = !MyEventFixture.create()
        val response = !MyEventService.getMyEvent(fixture.eventId, fixture.participantQrCode)
        val json = ObjectMapper().findAndRegisterModules().writeValueAsString(response.dto)
        assertFalse(json.contains(fixture.internalNote))
    }

    @Test
    fun registrationWithoutMatchAppearsAsUnscheduled() = testComprehension {
        val fixture = !MyEventFixture.create()
        val response = !MyEventService.getMyEvent(fixture.eventId, fixture.participantQrCode)
        assertTrue(response.dto.unscheduled.any { it.competitionId == fixture.unscheduledCompetitionId })
    }
}
```

Dazu `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/eventInfo/MyEventFixture.kt` mit
einem `object MyEventFixture { fun create(): JIO<Fixture> }`, das die oben genannten Zeilen
anlegt und folgendes zurückgibt:

```kotlin
data class Fixture(
    val eventId: UUID,
    val participantId: UUID,
    val participantQrCode: String,
    val appUserQrCode: String,
    val ownMatchId: UUID,
    val foreignMatchId: UUID,
    val unscheduledCompetitionId: UUID,
    val publicRequirementName: String,
    val internalNote: String,
)
```

Inhalt des Aufbaus: eine Veranstaltung mit zwei Wettkämpfen. Im ersten ist die Testperson
gemeldet und einem Lauf zugeordnet (`ownMatchId`); außerdem existiert ein Lauf einer fremden
Mannschaft (`foreignMatchId`). Im zweiten Wettkampf ist die Testperson gemeldet, aber kein Lauf
gesetzt (`unscheduledCompetitionId`). Zwei Bedingungen sind der Veranstaltung zugeordnet, eine
mit `publiclyVisible = true` und einer Notiz im `checked_participant_requirement`, eine mit
`publiclyVisible = false`. Zusätzlich ein `qr_codes`-Eintrag mit gesetztem `app_user` statt
`participant`.

- [ ] **Step 3: Test laufen lassen und Fehlschlag bestätigen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q -Dtest=MyEventServiceIT test
```

Erwartet: Übersetzungsfehler, `MyEventService` existiert nicht.

- [ ] **Step 4: Abfragen schreiben**

`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/control/MyEventRepo.kt` mit
zwei Funktionen. Der Verbundweg ist derselbe wie in
`CompetitionMatchTeamRepo.getTeamsForUpcomingMatch:118-129`:

```
COMPETITION_MATCH
  ← COMPETITION_MATCH_TEAM
  → COMPETITION_REGISTRATION
  ← COMPETITION_REGISTRATION_NAMED_PARTICIPANT
  → PARTICIPANT
```

```kotlin
fun findMatchesForParticipant(eventId: UUID, participantId: UUID): JIO<List<Record>>
fun findRegistrationsWithoutMatch(eventId: UUID, participantId: UUID): JIO<List<Record>>
```

`findMatchesForParticipant` liefert je Lauf eine Zeile mit allem, was `MyEventLogic.RawMatch`
braucht, plus die Namen und Rollen der Mannschaftsmitglieder derselben
`COMPETITION_REGISTRATION`. Die Einschränkung auf die Veranstaltung erfolgt über
`COMPETITION.EVENT.eq(eventId)`.

`findRegistrationsWithoutMatch` nimmt dieselben `COMPETITION_REGISTRATION`-Zeilen und filtert auf
`notExists` eines `COMPETITION_MATCH_TEAM`-Eintrags.

- [ ] **Step 5: Dienst schreiben**

`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventInfo/boundary/MyEventService.kt`:

```kotlin
fun getMyEvent(eventId: UUID, qrCode: String): App<EventInfoProblem, ApiResponse.Dto<MyEventDto>> =
    KIO.comprehension {
        val exists = !EventRepo.exists(eventId).orDie()
        if (!exists) {
            !KIO.fail<EventInfoProblem>(EventInfoProblem.EventNotFound(eventId))
        }

        val record = !QrCodeRepo.findByCode(qrCode).orDie()
        val participantId = record
            ?.takeIf { it.event == eventId }
            ?.participant
        if (participantId == null) {
            !KIO.fail<EventInfoProblem>(EventInfoProblem.QrCodeNotFound(qrCode))
        }
        // ...
    }
```

Beachte die drei Bedingungen in einer: unbekannter Code, fremde Veranstaltung und Helferrolle
(`participant` ist dann `null`) laufen absichtlich in dieselbe Antwort.

Der Rest baut das DTO zusammen:

1. `EventRepo.getName(eventId)`, `EventRepo.getPublicResultsVisibility(eventId)`
2. `MyEventRepo.findMatchesForParticipant` → `MyEventLogic.RawMatch` → `MyEventLogic.split`
3. `MyEventRepo.findRegistrationsWithoutMatch` → `MyEventRegistrationDto`
4. Bedingungen: `ParticipantRequirementForEventRepo.get(eventId, onlyActive = true)`, gefiltert
   auf `publiclyVisible`, `fulfilled` aus `participant_has_requirement_for_event`
5. `refreshIntervalSeconds`: `AthleteBoardLogic.MIN_REFRESH_INTERVAL_SECONDS.coerceAtLeast(15)`

Der Zwischenspeicher folgt dem Muster aus `EventInfoService.kt:43-46`: eine
`ConcurrentHashMap<Pair<UUID, UUID>, CachedMyEvent>` mit `builtAt` und
`MyEventLogic.PARTICIPANT_CACHE_TTL_SECONDS`. Kommentar dazu, warum es ihn gibt: bei einer
Regatta laden hunderte Telefone im selben Takt.

- [ ] **Step 6: Route eintragen**

In `eventInfo.kt` **innerhalb** des `rateLimit(RateLimitName("publicInfo"))`-Blocks, direkt hinter
`get("/athlete-board")`:

```kotlin
            // Persönliches Dashboard, erreichbar über den QR-Code am Teilnehmerband.
            // Öffentlich wie die Anzeigen darüber; welche Felder ein anonymer Aufruf sieht,
            // entscheidet ausschließlich MyEventService.
            get("/my-event/{qrCode}") {
                call.respondComprehension {
                    val eventId = !pathParam("eventId", uuid)
                    val qrCode = !pathParam("qrCode")

                    MyEventService.getMyEvent(eventId, qrCode)
                }
            }
```

- [ ] **Step 7: Test laufen lassen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q -Dtest=MyEventServiceIT test
```

Erwartet: BUILD SUCCESS, sechs Tests grün.

- [ ] **Step 8: OpenAPI ergänzen**

In `documentation.yaml` hinter dem Pfad `/event/{eventId}/info/athlete-board` den neuen Pfad
mit `operationId: getMyEvent`, den Parametern `eventId` (`$ref` auf `#/components/parameters/eventId`)
und `qrCode` (`in: path`, `type: string`), Antwort `200` mit `$ref: '#/components/schemas/MyEventDto'`
sowie `404` und `500`. Dazu die Schemata `MyEventDto`, `MyEventMatchDto`, `MyEventTeamMemberDto`,
`MyEventResultDto`, `MyEventRegistrationDto` und `MyEventRequirementDto` mit denselben Feldern und
Pflichtangaben wie die Kotlin-Datenklassen aus Task 3. `startState` verweist auf das bestehende
`AthleteBoardStartState`.

- [ ] **Step 9: Gesamten Testlauf**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test
```

Erwartet: BUILD SUCCESS.

- [ ] **Step 10: Commit**

```bash
git add backend/src
git commit -m "Mein Event: öffentlicher Endpunkt für das persönliche Dashboard"
```

---

## Task 5: Gescannte Codes auf dem Gerät merken

**Files:**
- Create: `frontend/src/utils/myEventStorage.ts`
- Test: `frontend/src/utils/myEventStorage.test.ts`

**Interfaces:**
- Produces:
  - `type MyEventCode = {qrCode: string; eventId: string; displayName?: string}`
  - `readMyEventCodes(): MyEventCode[]`
  - `codesForEvent(eventId: string): MyEventCode[]`
  - `rememberMyEventCode(code: MyEventCode): void`
  - `forgetMyEventCode(qrCode: string): void`
  - `MY_EVENT_STORAGE_KEY: string`

- [ ] **Step 1: Fehlschlagenden Test schreiben**

`frontend/src/utils/myEventStorage.test.ts`:

```typescript
import {beforeEach, describe, expect, it} from 'vitest'
import {
    codesForEvent,
    forgetMyEventCode,
    MY_EVENT_STORAGE_KEY,
    readMyEventCodes,
    rememberMyEventCode,
} from './myEventStorage.ts'

const eventA = '11111111-1111-1111-1111-111111111111'
const eventB = '22222222-2222-2222-2222-222222222222'

describe('myEventStorage', () => {
    beforeEach(() => {
        localStorage.clear()
    })

    it('merkt einen Code und liest ihn zurueck', () => {
        rememberMyEventCode({qrCode: 'abc', eventId: eventA})
        expect(readMyEventCodes()).toEqual([{qrCode: 'abc', eventId: eventA}])
    })

    it('haelt mehrere Codes nebeneinander', () => {
        rememberMyEventCode({qrCode: 'abc', eventId: eventA})
        rememberMyEventCode({qrCode: 'def', eventId: eventA})
        expect(readMyEventCodes()).toHaveLength(2)
    })

    it('ueberschreibt denselben Code statt ihn zu doppeln', () => {
        rememberMyEventCode({qrCode: 'abc', eventId: eventA})
        rememberMyEventCode({qrCode: 'abc', eventId: eventA, displayName: 'Ilka Heller'})
        expect(readMyEventCodes()).toEqual([
            {qrCode: 'abc', eventId: eventA, displayName: 'Ilka Heller'},
        ])
    })

    it('filtert nach Veranstaltung', () => {
        rememberMyEventCode({qrCode: 'abc', eventId: eventA})
        rememberMyEventCode({qrCode: 'def', eventId: eventB})
        expect(codesForEvent(eventA).map(c => c.qrCode)).toEqual(['abc'])
    })

    it('entfernt einen einzelnen Code', () => {
        rememberMyEventCode({qrCode: 'abc', eventId: eventA})
        rememberMyEventCode({qrCode: 'def', eventId: eventA})
        forgetMyEventCode('abc')
        expect(readMyEventCodes().map(c => c.qrCode)).toEqual(['def'])
    })

    it('liefert eine leere Liste bei kaputtem Speicherinhalt', () => {
        // Ein von Hand verbogener oder von einer aelteren Version geschriebener Eintrag
        // darf die Ergebnisseite nicht zerlegen.
        localStorage.setItem(MY_EVENT_STORAGE_KEY, '{kein json')
        expect(readMyEventCodes()).toEqual([])
    })

    it('verwirft Eintraege ohne Pflichtfelder', () => {
        localStorage.setItem(MY_EVENT_STORAGE_KEY, JSON.stringify([{qrCode: 'abc'}, 42]))
        expect(readMyEventCodes()).toEqual([])
    })
})
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

```bash
cd frontend && npx vitest run src/utils/myEventStorage.test.ts
```

Erwartet: FAIL, Modul nicht gefunden.

- [ ] **Step 3: Speicher schreiben**

`frontend/src/utils/myEventStorage.ts`:

```typescript
/**
 * Die über den QR-Code am Band eingestiegenen Personen, gemerkt auf diesem Gerät.
 *
 * Bewusst eine Liste und kein Einzelwert: Eltern mit mehreren Kindern und Betreuende scannen
 * mehrere Bänder, und ein Einzelwert würde sich gegenseitig überschreiben. Der Code steht
 * absichtlich nur hier und nicht in der URL — so wandert kein personenbezogener Link durch
 * Chatgruppen.
 */
export type MyEventCode = {
    qrCode: string
    eventId: string
    displayName?: string
}

export const MY_EVENT_STORAGE_KEY = 'my_event_codes'

const isCode = (value: unknown): value is MyEventCode =>
    typeof value === 'object' &&
    value !== null &&
    typeof (value as MyEventCode).qrCode === 'string' &&
    typeof (value as MyEventCode).eventId === 'string'

export const readMyEventCodes = (): MyEventCode[] => {
    const raw = localStorage.getItem(MY_EVENT_STORAGE_KEY)
    if (!raw) return []
    try {
        const parsed: unknown = JSON.parse(raw)
        if (!Array.isArray(parsed)) return []
        // Ein einzelner kaputter Eintrag verwirft die ganze Liste: der Speicher ist Beiwerk,
        // ein neuer Scan stellt ihn in Sekunden wieder her, und halb gelesene Zustaende
        // waeren schwerer zu verstehen als ein leerer.
        return parsed.every(isCode) ? parsed : []
    } catch {
        return []
    }
}

const write = (codes: MyEventCode[]) =>
    localStorage.setItem(MY_EVENT_STORAGE_KEY, JSON.stringify(codes))

export const codesForEvent = (eventId: string): MyEventCode[] =>
    readMyEventCodes().filter(c => c.eventId === eventId)

export const rememberMyEventCode = (code: MyEventCode) => {
    const others = readMyEventCodes().filter(c => c.qrCode !== code.qrCode)
    write([...others, code])
}

export const forgetMyEventCode = (qrCode: string) => {
    write(readMyEventCodes().filter(c => c.qrCode !== qrCode))
}
```

- [ ] **Step 4: Test laufen lassen**

```bash
cd frontend && npx vitest run src/utils/myEventStorage.test.ts
```

Erwartet: 7 Tests grün.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/utils/myEventStorage.ts frontend/src/utils/myEventStorage.test.ts
git commit -m "Mein Event: gescannte Codes auf dem Gerät merken"
```

---

## Task 6: Einstieg über den QR-Code

**Files:**
- Modify: `frontend/src/pages/results/ResultsQrCodePage.tsx`
- Modify: `frontend/src/routes.tsx` (Suchparameter der Ergebnisroute)

**Interfaces:**
- Consumes: `rememberMyEventCode` (Task 5)
- Produces: Suchparameter `?tab=my-event` an `resultsEventRoute`

- [ ] **Step 1: Suchparameter an der Ergebnisroute erlauben**

In `frontend/src/routes.tsx` bei `resultsEventRoute` ergänzen:

```typescript
export const resultsEventRoute = createRoute({
    getParentRoute: () => resultsRoute,
    path: '/event/$eventId',
    component: () => <ResultsPage />,
    validateSearch: (search: {tab?: string} & SearchSchemaInput) => ({
        tab: search.tab === 'my-event' ? ('my-event' as const) : undefined,
    }),
})
```

- [ ] **Step 2: Einstiegsseite umbauen**

`frontend/src/pages/results/ResultsQrCodePage.tsx` — der `onResponse`-Zweig merkt sich den Code,
bevor er weiterleitet:

```tsx
            } else {
                // Der Code wandert in den Geraetespeicher und nicht in die Zieladresse:
                // ein weitergereichter Link soll niemanden in ein fremdes Dashboard lassen.
                rememberMyEventCode({qrCode: qrCode, eventId: response.data.eventId})
                navigate({
                    to: '/results/event/$eventId',
                    params: {eventId: response.data.eventId},
                    search: {tab: 'my-event'},
                })
            }
```

Import ergänzen:

```typescript
import {rememberMyEventCode} from '@utils/myEventStorage.ts'
```

- [ ] **Step 3: Bau prüfen**

```bash
cd frontend && npm run build
```

Erwartet: kein TypeScript-Fehler.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/results/ResultsQrCodePage.tsx frontend/src/routes.tsx
git commit -m "Mein Event: Einstieg über den QR-Code merkt sich den Code"
```

---

## Task 7: Abruftakt verallgemeinern

**Files:**
- Create: `frontend/src/components/results/myEvent/usePolledEndpoint.ts`
- Modify: `frontend/src/components/event/info/athleteBoard/useAthleteBoardData.ts`

**Interfaces:**
- Produces:
  ```typescript
  export interface PolledState<T> {
      data: T | null
      lastUpdated: Date | null
      notFound: boolean
      initialLoad: boolean
      loadFailed: boolean
  }
  export const usePolledEndpoint = <T>(
      load: (signal: AbortSignal) => Promise<{data?: T; response: Response}>,
      intervalOf: (data: T) => number,
      deps: unknown[],
  ): PolledState<T>
  ```

Begründung: `useAthleteBoardData` enthält 130 Zeilen sorgfältig austarierter Logik (letzter guter
Stand bleibt bei Netzabbruch stehen, Anhalten nach 404, Pause im Hintergrund). Diese Logik ein
zweites Mal zu schreiben würde sie ein zweites Mal falsch machen.

- [ ] **Step 1: Bestehende Datei kopieren und verallgemeinern**

`usePolledEndpoint.ts` enthält den Rumpf von `useAthleteBoardData.ts` unverändert, mit drei
Ersetzungen: der feste `getAthleteBoard`-Aufruf wird zum Parameter `load`, die Ableitung
`result.data.refreshIntervalSeconds` zum Parameter `intervalOf`, und `[eventId]` in den
Abhängigkeitslisten zu `deps`. Alle bestehenden Kommentare bleiben erhalten — sie erklären genau
die Fälle, die hier nicht verloren gehen dürfen.

- [ ] **Step 2: `useAthleteBoardData` auf den neuen Haken umstellen**

```typescript
import {getAthleteBoard} from '@api/sdk.gen'
import {AthleteBoardDto} from '@api/types.gen'
import {usePolledEndpoint, PolledState} from '@components/results/myEvent/usePolledEndpoint.ts'

const FALLBACK_INTERVAL_SECONDS = 15

export type AthleteBoardState = PolledState<AthleteBoardDto>

export const useAthleteBoardData = (eventId: string): AthleteBoardState =>
    usePolledEndpoint<AthleteBoardDto>(
        signal => getAthleteBoard({signal, path: {eventId}}),
        data =>
            data.refreshIntervalSeconds > 0
                ? data.refreshIntervalSeconds
                : FALLBACK_INTERVAL_SECONDS,
        [eventId],
    )
```

- [ ] **Step 3: Bau und Tests prüfen**

```bash
cd frontend && npm run build && npm test
```

Erwartet: kein TypeScript-Fehler, alle bestehenden Tests grün.

- [ ] **Step 4: Athleten-Anzeige von Hand gegenprüfen**

Anwendung starten und `/board/{eventId}` einer Veranstaltung mit Läufen öffnen. Erwartet: die
Anzeige lädt und aktualisiert sich wie zuvor. Diese Umstellung berührt eine Ansicht, die am
Veranstaltungstag auf fest montierten Bildschirmen läuft — ein reiner Übersetzungserfolg genügt
hier nicht.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components
git commit -m "Abruftakt der Athleten-Anzeige für weitere Ansichten nutzbar machen"
```

---

## Task 8: Blöcke des persönlichen Dashboards

**Files:**
- Create: `frontend/src/components/results/myEvent/myEventOrder.ts`
- Test: `frontend/src/components/results/myEvent/myEventOrder.test.ts`
- Create: `frontend/src/components/results/myEvent/MyEventRequirements.tsx`
- Create: `frontend/src/components/results/myEvent/MyEventMatchList.tsx`
- Create: `frontend/src/components/results/myEvent/MyEventPersonSwitcher.tsx`
- Create: `frontend/src/components/results/myEvent/MyEventPanel.tsx`
- Modify: `frontend/src/i18n/{de,en,da}/translations.json`
- Regenerate: `frontend/src/api/**`

**Interfaces:**
- Consumes: `MyEventDto` aus dem generierten Client, `usePolledEndpoint` (Task 7),
  `codesForEvent`, `forgetMyEventCode`, `rememberMyEventCode` (Task 5)
- Produces:
  - `openRequirements(requirements: MyEventRequirementDto[]): MyEventRequirementDto[]`
  - `blockOrder(data: MyEventDto): Array<'requirementBanner' | 'next' | 'matches' | 'results' | 'unscheduled' | 'requirements'>`
  - `MyEventPanel({eventId}: {eventId: string})`

- [ ] **Step 1: Client neu erzeugen**

```bash
cd frontend && npm run generate && grep -n "MyEventDto" src/api/types.gen.ts | head -3
```

Erwartet: Treffer.

- [ ] **Step 2: Fehlschlagenden Test für die Reihenfolge schreiben**

`frontend/src/components/results/myEvent/myEventOrder.test.ts`:

```typescript
import {describe, expect, it} from 'vitest'
import {MyEventDto, MyEventRequirementDto} from '@api/types.gen.ts'
import {blockOrder, openRequirements} from './myEventOrder.ts'

const requirement = (o: Partial<MyEventRequirementDto>): MyEventRequirementDto => ({
    id: crypto.randomUUID(),
    name: 'Bedingung',
    optional: false,
    fulfilled: false,
    ...o,
})

const dto = (o: Partial<MyEventDto>): MyEventDto => ({
    displayName: 'Ilka Heller',
    eventName: 'Veranstaltung',
    serverTime: '2026-08-14T10:00:00',
    refreshIntervalSeconds: 15,
    running: [],
    upcoming: [],
    results: [],
    unscheduled: [],
    requirements: [],
    ...o,
})

describe('openRequirements', () => {
    it('meldet nur nicht erfuellte Pflichtbedingungen', () => {
        const open = requirement({fulfilled: false, optional: false})
        const result = openRequirements([
            open,
            requirement({fulfilled: true, optional: false}),
            requirement({fulfilled: false, optional: true}),
        ])
        expect(result).toEqual([open])
    })
})

describe('blockOrder', () => {
    it('zeigt kein Band, wenn alles erledigt ist', () => {
        const order = blockOrder(dto({requirements: [requirement({fulfilled: true})]}))
        expect(order).not.toContain('requirementBanner')
        // Die Liste bleibt als ruhige Bestaetigung stehen.
        expect(order).toContain('requirements')
    })

    it('zieht ein offenes Band ganz nach oben', () => {
        const order = blockOrder(dto({requirements: [requirement({fulfilled: false})]}))
        expect(order[0]).toBe('requirementBanner')
    })

    it('stellt kommende Laeufe vor die Ergebnisse', () => {
        const order = blockOrder(
            dto({upcoming: [{matchId: 'm1'} as never], results: [{matchId: 'm0'} as never]}),
        )
        expect(order.indexOf('next')).toBeLessThan(order.indexOf('results'))
    })

    it('stellt die Ergebnisse nach vorn, wenn nichts mehr ansteht', () => {
        const order = blockOrder(dto({results: [{matchId: 'm0'} as never]}))
        expect(order.indexOf('results')).toBeLessThan(order.indexOf('matches'))
        expect(order).not.toContain('next')
    })
})
```

- [ ] **Step 3: Test laufen lassen und Fehlschlag bestätigen**

```bash
cd frontend && npx vitest run src/components/results/myEvent/myEventOrder.test.ts
```

Erwartet: FAIL, Modul nicht gefunden.

- [ ] **Step 4: Reihenfolgelogik schreiben**

`frontend/src/components/results/myEvent/myEventOrder.ts`:

```typescript
import {MyEventDto, MyEventRequirementDto} from '@api/types.gen.ts'

export type MyEventBlock =
    | 'requirementBanner'
    | 'next'
    | 'matches'
    | 'results'
    | 'unscheduled'
    | 'requirements'

/**
 * Offen sind nur nicht erfuellte Pflichtbedingungen. Eine offene freiwillige Bedingung soll
 * niemanden am Renntag beunruhigen — sie steht in der Liste weiter unten.
 */
export const openRequirements = (
    requirements: MyEventRequirementDto[],
): MyEventRequirementDto[] => requirements.filter(r => !r.fulfilled && !r.optional)

/**
 * Die Seite sortiert sich nach der Tageszeit um: solange ein eigener Lauf aussteht, steht er
 * oben; ist alles gelaufen, rueckt das Ergebnis nach vorn. Wer zwischen zwei Laeufen aufs
 * Telefon schaut, will "wann muss ich wo sein" sehen, danach "wie lief es".
 */
export const blockOrder = (data: MyEventDto): MyEventBlock[] => {
    const blocks: MyEventBlock[] = []

    if (openRequirements(data.requirements).length > 0) {
        blocks.push('requirementBanner')
    }

    const somethingAhead = data.running.length > 0 || data.upcoming.length > 0
    if (somethingAhead) {
        blocks.push('next', 'matches', 'results')
    } else {
        blocks.push('results', 'matches')
    }

    blocks.push('unscheduled', 'requirements')
    return blocks
}
```

- [ ] **Step 5: Test laufen lassen**

```bash
cd frontend && npx vitest run src/components/results/myEvent/myEventOrder.test.ts
```

Erwartet: 5 Tests grün.

- [ ] **Step 6: i18n-Schlüssel ergänzen**

Neuer Block `myEvent` auf oberster Ebene in allen drei Sprachdateien:

```json
  "myEvent": {
    "tab": "Mein Event",
    "hint": "Scanne den QR-Code auf deinem Band, um deine Läufe und Ergebnisse zu sehen.",
    "notFound": "Zu diesem Code gibt es keine Teilnahme bei dieser Veranstaltung.",
    "next": "Dein nächster Lauf",
    "running": "Läuft gerade",
    "matches": "Meine Läufe",
    "results": "Meine Ergebnisse",
    "unscheduled": "Gemeldet, noch nicht terminiert",
    "requirements": "Meine Bedingungen",
    "requirementsOpen": "Es fehlt noch etwas",
    "requirementsAllDone": "Alles erledigt",
    "requirementFulfilled": "erledigt",
    "requirementOpen": "offen",
    "requirementOptional": "freiwillig",
    "remove": "Eintrag entfernen",
    "lane": "Startposition",
    "team": "Mannschaft",
    "noMatches": "Für dich ist noch kein Lauf eingetragen."
  }
```

Englische und dänische Entsprechungen sinngemäß. In `en` etwa `"tab": "My Event"`,
`"next": "Your next race"`; in `da` `"tab": "Min begivenhed"`, `"next": "Dit næste løb"`.

- [ ] **Step 7: Darstellende Komponenten schreiben**

Die Schnittstellen sind bindend — die Komponenten entstehen unabhängig voneinander und müssen
zusammenpassen:

```typescript
// MyEventRequirements.tsx — zwei Ausgaben aus einer Datei
type MyEventRequirementsProps = {
    requirements: MyEventRequirementDto[]
    variant: 'banner' | 'list'
}
export const MyEventRequirements = (props: MyEventRequirementsProps) => ...

// MyEventMatchList.tsx
type MyEventMatchListProps = {
    matches: MyEventMatchDto[]
    serverTime: string
    variant: 'next' | 'list'
}
export const MyEventMatchList = (props: MyEventMatchListProps) => ...

type MyEventResultListProps = {results: MyEventResultDto[]}
export const MyEventResultList = (props: MyEventResultListProps) => ...

type MyEventUnscheduledListProps = {registrations: MyEventRegistrationDto[]}
export const MyEventUnscheduledList = (props: MyEventUnscheduledListProps) => ...

// MyEventPersonSwitcher.tsx
type MyEventPersonSwitcherProps = {
    codes: MyEventCode[]
    activeQrCode: string
    onSelect: (qrCode: string) => void
    onRemove: (qrCode: string) => void
}
export const MyEventPersonSwitcher = (props: MyEventPersonSwitcherProps) => ...

// MyEventPanel.tsx
export const MyEventPanel = ({eventId}: {eventId: string}) => ...
```

Inhaltliche Vorgaben:

- **`MyEventRequirements`** — `variant="banner"`: `Alert severity="warning"` mit den Namen der
  offenen Pflichtbedingungen aus `openRequirements` und dem Verweis auf die Meldestelle.
  `variant="list"`: `List` über **alle** übergebenen Bedingungen, erledigte eingeschlossen, mit
  `CheckCircleOutlineIcon` bzw. `RadioButtonUncheckedIcon` und dem Zusatz `myEvent.requirementOptional`
  bei freiwilligen.
- **`MyEventMatchList`** — `variant="next"` rendert nur den ersten Eintrag als große Karte mit
  Countdown gegen `serverTime`, `variant="list"` alle als kompakte Zeilen. Aufbau und
  Zeitformatierung wie in `AthleteBoardMatchCard.tsx`. Die eigene Person wird über
  `teamMembers[].self` fett hervorgehoben.
- **`MyEventResultList`** — Platz, Zeit, Strafsekunden mit Grund, DNS/DNF/DSQ, Abmeldung mit Grund.
  Für Strafhinweise `AthleteBoardPenaltyNote.tsx` wiederverwenden.
- **`MyEventPersonSwitcher`** — `ToggleButtonGroup` mit `displayName` (Ersatz: die ersten acht
  Zeichen des Codes, falls der Name noch nicht bekannt ist), je Eintrag ein `IconButton` mit
  `CloseIcon`. Gibt bei nur einem Eintrag `null` zurück.
- **`MyEventPanel`** — hält den aktiven Code in `useState`, ruft `usePolledEndpoint` mit
  `getMyEvent({signal, path: {eventId, qrCode}})`, schreibt den `displayName` aus der Antwort per
  `rememberMyEventCode` zurück und rendert die Blöcke in der Reihenfolge aus `blockOrder`. Ohne
  hinterlegten Code für diese Veranstaltung nur `myEvent.hint`; bei `notFound` die Meldung
  `myEvent.notFound` mit einem Knopf, der `forgetMyEventCode` aufruft.

- [ ] **Step 8: Bau prüfen**

```bash
cd frontend && npm run build && npm test
```

Erwartet: kein TypeScript-Fehler, alle Tests grün.

- [ ] **Step 9: Commit**

```bash
git add frontend/src
git commit -m "Mein Event: Blöcke des persönlichen Dashboards"
```

---

## Task 9: Reiter einhängen und Seite vor Suchmaschinen schützen

**Files:**
- Modify: `frontend/src/pages/results/ResultsPage.tsx`
- Modify: `frontend/src/layouts/ResultsLayout.tsx`

**Interfaces:**
- Consumes: `MyEventPanel` (Task 8), Suchparameter `tab` (Task 6), `codesForEvent` (Task 5)

- [ ] **Step 1: `noindex` in das Ergebnis-Layout**

In `ResultsLayout.tsx` vor dem `return`:

```tsx
    // Die Ergebnisseite traegt Namen von Teilnehmenden und ueber "Mein Event" den Zustand
    // persoenlicher Bedingungen. Ein Suchmaschinentreffer wuerde aus "wer den Link hat"
    // ein "wer den Namen sucht" machen. Der Schutz haengt bewusst am Layout und nicht an
    // der Reiter-Logik, damit er beim Umbau der Reiter nicht verloren geht.
    useEffect(() => {
        const meta = document.createElement('meta')
        meta.name = 'robots'
        meta.content = 'noindex, nofollow'
        document.head.appendChild(meta)
        return () => {
            document.head.removeChild(meta)
        }
    }, [])
```

- [ ] **Step 2: Dritten Reiter ergänzen**

In `ResultsPage.tsx` die Reiterliste erweitern:

```typescript
const RESULTS_TABS = ['latest-results', 'live', 'my-event'] as const
```

(`'upcoming'` ist bereits heute unbenutzt und bleibt es.)

Anfangsreiter aus dem Suchparameter ableiten:

```typescript
    const {tab: tabFromSearch} = resultsEventRoute.useSearch()
    const [activeResultsTab, setActiveResultsTab] = useState<ResultsTab>(
        tabFromSearch === 'my-event' ? 'my-event' : 'latest-results',
    )
```

Im nicht-`challengeEvent`-Zweig hinter dem „Live"-Reiter:

```tsx
                        <Tab
                            label={t('myEvent.tab')}
                            icon={<PersonPinIcon />}
                            iconPosition={smallScreenLayout ? 'top' : 'start'}
                            sx={{flex: 1, maxWidth: 'unset'}}
                            {...resultsTabProps('my-event')}
                        />
```

mit `import PersonPinIcon from '@mui/icons-material/PersonPin'`, und die zugehörige Tafel:

```tsx
                    <TabPanel index={'my-event'} activeTab={activeResultsTab}>
                        <MyEventPanel eventId={eventId} />
                    </TabPanel>
```

Der Reiter erscheint **immer** (außer bei `challengeEvent`), auch ohne hinterlegten Code — sonst
erfährt niemand von der Funktion, und die Bänder erklären sich nicht von selbst.

- [ ] **Step 3: Bau und Tests**

```bash
cd frontend && npm run build && npm test
```

Erwartet: kein TypeScript-Fehler, alle Tests grün.

- [ ] **Step 4: Handtests in der laufenden App**

Anwendung starten (Backend mit laufender Datenbank, `cd frontend && npm run dev`) und der Reihe
nach prüfen. Eine Veranstaltung mit Läufen, mindestens einem zugewiesenen Teilnehmer-QR-Code und
mindestens einer freigegebenen Bedingung wird gebraucht.

1. `/results/{qrCode}` aufrufen → Reiter „Mein Event" ist offen, die Adresszeile zeigt
   `/results/event/{eventId}?tab=my-event` **ohne** den Code
2. Browser schließen, `/results/event/{eventId}` direkt aufrufen → Reiter zeigt weiterhin Daten
3. Zweiten Code scannen → Umschalter erscheint, beide Personen abrufbar
4. Eintrag entfernen → Umschalter verschwindet, sobald nur noch einer übrig ist
5. In einem privaten Fenster `/results/event/{eventId}` öffnen → Reiter mit Hinweistext
6. Bedingung im Editor freigeben → erscheint im Dashboard; Häkchen entfernen → verschwindet
7. Offene Pflichtbedingung → Band oben; abhaken → Band weg, Liste bleibt stehen
8. Lauf beenden → Ergebnis erscheint im Dashboard und auf `/board/{eventId}` gleichzeitig
9. Seitenquelltext prüfen → `<meta name="robots" content="noindex, nofollow">` ist vorhanden

- [ ] **Step 5: Commit**

```bash
git add frontend/src
git commit -m "Mein Event: Reiter auf der Ergebnisseite"
```

---

## Abschluss

- [ ] Übergabetext schreiben, der ausdrücklich festhält: **`publicly_visible` steht standardmäßig
  auf „aus"**, der Bedingungsblock ist also bis zum ersten Häkchen im Editor leer. Ohne diesen
  Hinweis wird der leere Block als Fehler gemeldet.
- [ ] Migrationsnummer `V202608101000` an parallele Sitzungen melden.
