# Zeitnahme-Tab pro Wettkampf — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Zeitnahme-Konfiguration eines Wettkampfs (Zeitnahme-System, RaceClocker-Ergebnis-URLs, Startlisten-Presets, Ergebnis-Import-Preset) wandert aus dem Durchführungs-Tab in einen eigenen Tab „Zeitnahme" und wird serverseitig aufgelöst, statt bei jedem Download von Hand gewählt zu werden.

**Architecture:** Vier neue Spalten auf `competition` tragen die Konfiguration. Ein neues Backend-Modul `app/timingConfig/` liefert GET/PUT `/timing-config` und ersetzt den alten Endpunkt `/competitionExecution/raceclocker-config`. Die Preset-Auswahl wird zu einer reinen, unit-testbaren Regel (`StartListConfigTarget.configId`) im Backend; die `config`-Parameter von Startlisten-Download und Ergebnis-Import fallen weg. Im Frontend entsteht ein neuer Tab, `StartListConfigPicker.tsx` wird gelöscht, `MatchResultUploadDialog.tsx` verliert sein Preset-Feld.

**Tech Stack:** Kotlin/Ktor, jOOQ (Codegen aus der DB), Flyway, KIO (`de.lambda9.tailwind`), Postgres 17 · React 18, TypeScript, MUI, react-hook-form-mui, TanStack Router, vitest · OpenAPI (handgepflegt) → `@hey-api/openapi-ts`

**Spec:** `docs/superpowers/specs/2026-08-05-zeitnahme-tab-design.md`

## Global Constraints

- **Branch:** Alles auf `feature/crf-2026`. **Niemals nach `main` mergen oder auf `main` committen.**
- **Keine AI-Attribution** in Commit-Messages (kein `Co-Authored-By`, keine Claude-Erwähnung).
- **Java:** Vor jedem Maven-Aufruf `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
- **Datenbank:** `cd backend && docker compose up -d` startet `db` (Port 7653, Dev) **und** `build-db` (Port 7652). Der jOOQ-Codegen braucht **7652**; ohne laufende `build-db` schlägt jeder `./mvnw`-Aufruf in `generate-sources` fehl.
- **jOOQ-Codegen läuft automatisch** in der `generate-sources`-Phase (Flyway `migrate` → jOOQ `generate`). Nach einer neuen Migration genügt `./mvnw test`; generierte Quellen landen in `backend/target/generated-sources/jooq` und sind **nicht** eingecheckt.
- **Migrationen:** Dateiname `V<yyyyMMddHHmm>__<snake_case>.sql`, exakt 12 Ziffern. Erste Zeile immer `set search_path to ready2race, pg_catalog, public;`. Jüngste vorhandene Version ist `V202608051000__chain_progression_mode.sql` — neue Migration muss später datieren.
- **OpenAPI ist handgepflegt:** `backend/src/main/resources/openapi/documentation.yaml` wird von Hand geändert, danach `cd frontend && npm run generate`. Es gibt **kein** Codegen aus Kotlin. `frontend/src/api/{sdk.gen.ts,types.gen.ts,index.ts}` sind generiert und eingecheckt — nie von Hand editieren.
- **i18n:** Jeder neue Schlüssel muss in **alle drei** Dateien: `frontend/src/i18n/de/translations.json`, `en/translations.json`, `da/translations.json`. Alle drei haben identische Schlüsselstruktur; Dänisch ist echt übersetzt, kein Fallback. Deutsche Texte mit echten Umlauten (ä, ö, ü, ß), niemals ae/oe/ue/ss.
- **Enums als Text:** Es gibt keinen jOOQ-Converter. Text-Spalten werden von Hand mit `.name` geschrieben und mit `valueOf(...)` gelesen (Vorbild: `ChainProgressionMode`, `EventRepo.getChainProgressionMode`).
- **Tests:** Backend `cd backend && ./mvnw test` (kotlin.test, camelCase-Methodennamen). Frontend `cd frontend && npm run test` (vitest, deutsche `it(...)`-Beschreibungen) und `npm run build`. Nach Branchwechsel ggf. `npm install`.
- **Zwischenzustand:** Tasks 4–5 entfernen die `config`-Parameter im Backend, Task 6 zieht das Frontend nach. Zwischen Task 4 und Task 6 ist der CSV-Download in der laufenden App defekt. Das ist beabsichtigt und wird in Task 6 geschlossen — kein Grund, die Reihenfolge zu ändern.

---

## File Structure

**Backend — neu:**

| Datei | Verantwortung |
|---|---|
| `backend/src/main/resources/db/migration/V202608051500__competition_timing_config.sql` | Vier Spalten auf `competition` |
| `.../app/timingConfig/entity/TimingSystem.kt` | Enum `RACECLOCKER`/`WEBSCORER` |
| `.../app/timingConfig/entity/TimingConfigDto.kt` | Antwort des GET |
| `.../app/timingConfig/entity/TimingConfigRequest.kt` | Body des PUT inkl. URL-Validierung |
| `.../app/timingConfig/boundary/TimingConfigService.kt` | Lesen/Schreiben der sechs Felder |
| `.../app/timingConfig/boundary/timingConfig.kt` | Route GET/PUT `/timing-config` |
| `.../app/competitionExecution/entity/StartListConfigTarget.kt` | **Die Auflösungsregel** als reine Property |
| `backend/src/test/kotlin/.../competitionExecution/StartListConfigTargetTest.kt` | Unit-Tests der Regel |

**Backend — geändert:** `competitionExecution/entity/StartListFileType.kt` (sealed interface → enum), `competitionExecution/boundary/competitionExecution.kt` (Route `/raceclocker-config` weg, `config`-Param weg, `request`-Part weg), `competitionExecution/boundary/CompetitionExecutionService.kt` (RaceClocker-Config-Funktionen weg, Auflösung in `getStartList` und `updateMatchResultByFile`), `competitionExecution/control/CompetitionMatchRepo.kt` (neue Query), `competition/boundary/competition.kt` (`timingConfig()` mounten), `startListConfig/entity/StartListConfigError.kt` + `matchResultImportConfig/entity/MatchResultImportConfigError.kt` (`NotConfigured`), `calls/responses/ErrorCode.kt`, `openapi/documentation.yaml`.

**Backend — gelöscht:** `raceclocker/entity/RaceClockerConfigDto.kt`, `raceclocker/entity/RaceClockerConfigRequest.kt`, `competitionExecution/entity/UploadMatchResultRequest.kt`.

**Frontend — neu:** `components/event/competition/timing/CompetitionTimingConfig.tsx` (Tab-Inhalt), `components/event/competition/timing/timingConfigForm.ts` (Form-Typ + Mapping + Warnlogik, rein, testbar), `components/event/competition/timing/timingConfigForm.test.ts`.

**Frontend — geändert:** `pages/event/CompetitionPage.tsx`, `components/event/competition/excecution/CompetitionExecution.tsx`, `.../CompetitionExecutionRound.tsx`, `.../MatchResultUploadDialog.tsx`, die drei `translations.json`.

**Frontend — gelöscht:** `components/event/competition/excecution/RaceClockerConfigDialog.tsx`, `components/event/competition/excecution/StartListConfigPicker.tsx`.

---

## Task 1: Migration und jOOQ-Codegen

**Files:**
- Create: `backend/src/main/resources/db/migration/V202608051500__competition_timing_config.sql`

**Interfaces:**
- Consumes: nichts.
- Produces: die jOOQ-Felder `COMPETITION.TIMING_SYSTEM` (`String?`), `COMPETITION.STARTLIST_CONFIG_QUALIFICATION` (`UUID?`), `COMPETITION.STARTLIST_CONFIG_ROUNDS` (`UUID?`), `COMPETITION.RESULT_IMPORT_CONFIG` (`UUID?`) sowie die gleichnamigen Properties auf `CompetitionRecord`.

- [ ] **Step 1: Datenbank starten**

```bash
cd backend && docker compose up -d
```

Erwartung: zwei Container laufen, u. a. `build-db` auf Port 7652. Prüfen mit `docker compose ps`.

- [ ] **Step 2: Migration schreiben**

Create `backend/src/main/resources/db/migration/V202608051500__competition_timing_config.sql`:

```sql
set search_path to ready2race, pg_catalog, public;

-- Zeitnahme-Konfiguration pro Wettkampf (Design 2026-08-05).
--
-- Die beiden RaceClocker-Ergebnis-URLs liegen bereits auf dieser Tabelle (V202607211200). Hier kommt
-- dazu, mit welchem Fremdsystem der Wettkampf ueberhaupt arbeitet und welche Spalten-Presets dafuer
-- gelten -- bisher wurde das Preset bei jedem CSV-Download von Hand aus der globalen Liste gewaehlt.
--
-- Bewusst auf `competition` und nicht auf `competition_properties`: letztere haengt laut
-- Check-Constraint entweder an einem Wettkampf ODER an einer Wettkampf-Vorlage. Eine Vorlage kann
-- diese Werte nicht tragen, denn sie zeigen auf konkrete Rennen in einem fremden System, angelegt
-- fuer eine konkrete Regatta.
alter table competition
    add column timing_system                  text,
    -- RaceClocker braucht pro Wettkampf zwei Rennen und damit zwei Presets: das Zeitfahren-Rennen
    -- darf die Lauf-Spalte nicht enthalten (sonst kippt RaceClocker es in den Wave-Modus und der
    -- Countdown fehlt), das Laeufe-Rennen muss sie enthalten. Welches gilt, entscheidet dieselbe
    -- competition_setup_round.is_qualification, die auch die URL-Auswahl steuert.
    --
    -- Ist der Qualifikations-Slot leer, greift der Runden-Slot. Genau deshalb braucht Webscorer nur
    -- ein Preset: dort gibt es diese Zweiteilung nicht.
    add column startlist_config_qualification uuid references startlist_export_config on delete set null,
    add column startlist_config_rounds        uuid references startlist_export_config on delete set null,
    -- Ergebnis-Import per xlsx: bei Webscorer der Hauptweg, bei RaceClocker der Notausgang, wenn der
    -- Ergebnis-Feed am Renntag klemmt.
    add column result_import_config           uuid references match_result_import_config on delete set null;

-- Alle Spalten nullable ohne Default: bestehende Wettkaempfe bleiben unkonfiguriert, es gibt keinen
-- Datenmigrations-Schritt. `on delete set null` laesst das Loeschen eines Presets in der
-- Konfigurationsverwaltung zu -- der Wettkampf verliert dann nur seine Vorbelegung.
```

- [ ] **Step 3: Migration und Codegen laufen lassen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && cd backend && ./mvnw -q generate-sources
```

Erwartung: kein Fehler. Bei `Connection refused` läuft `build-db` nicht → Step 1 wiederholen.

- [ ] **Step 4: Prüfen, dass die Spalten im generierten Record stehen**

```bash
grep -n "timingSystem\|startlistConfigQualification\|startlistConfigRounds\|resultImportConfig" backend/target/generated-sources/jooq/de/lambda9/ready2race/backend/database/generated/tables/records/CompetitionRecord.kt
```

Erwartung: vier Treffer. Findet `grep` nichts, hat der Codegen die Migration nicht gesehen — Dateiname und Versionsnummer prüfen.

- [ ] **Step 5: Volle Testsuite als Regression**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && cd backend && ./mvnw test
```

Erwartung: BUILD SUCCESS, alle bestehenden Tests grün. Ein reines Spalten-Hinzufügen darf nichts brechen — `afterMigrate.sql` braucht **keine** Änderung, weil keine der 14 Views über `competition` eine Ganzzeilen-Referenz (`c.*`, `array_agg(c)`) benutzt.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V202608051500__competition_timing_config.sql
git commit -m "Add per-competition timing configuration columns"
```

---

## Task 2: Die Auflösungsregel (TDD)

> **Korrektur am 05.08.2026 nach dem Abschluss-Review.** Der unten stehende Code trägt den Rückfall
> vom Quali- auf den Runden-Slot bedingungslos. Das ist falsch: bei RaceClocker schiebt der Rückfall
> die Lauf-Spalte in das Zeitfahren-Rennen, kippt es in den Wave-Modus und der Countdown ist am Start
> weg — der Ausfall, gegen den dieser Tab gebaut wird. Gewarnt hätte nichts, weil die Warnlogik das
> Quali-Preset absichtlich nicht anmahnt.
>
> Der ausgelieferte Stand (Commit `40783577`) trägt daher ein zusätzliches Feld `timingSystem` und
> die Regel `!isQualification → roundsConfig` / `RACECLOCKER → qualificationConfig` (kein Rückfall) /
> `sonst → qualificationConfig ?: roundsConfig`. `getStartListConfigTarget` in Task 4 projiziert
> `COMPETITION.TIMING_SYSTEM` mit. Der Test umfasst sieben statt fünf Fälle. Siehe Spec Abschnitt 4.

Die Regel ist eine reine Funktion und wird zuerst als Property auf einem kleinen Datenträger gebaut — genau wie `RaceClockerMatchTarget.resultsUrl` die URL-Regel trägt. Damit ist sie ohne Datenbank testbar.

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/entity/StartListConfigTarget.kt`
- Create: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/StartListConfigTargetTest.kt`

**Interfaces:**
- Consumes: nichts.
- Produces: `data class StartListConfigTarget(val isQualification: Boolean, val qualificationConfig: UUID?, val roundsConfig: UUID?)` mit `val configId: UUID?`.

- [ ] **Step 1: Den fehlschlagenden Test schreiben**

Create `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/StartListConfigTargetTest.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.competitionExecution.entity.StartListConfigTarget
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Die Regel, die entscheidet, mit welchem Spalten-Preset die Startliste eines Laufs exportiert wird.
 * Sie hat dieselbe Form wie die URL-Auswahl in RaceClockerMatchTarget und denselben Grund: RaceClocker
 * braucht pro Wettkampf zwei Rennen, und das Zeitfahren-Preset darf die Lauf-Spalte nicht enthalten.
 */
class StartListConfigTargetTest {

    private val timeTrial = UUID.randomUUID()
    private val heats = UUID.randomUUID()

    @Test
    fun qualificationRoundUsesTheQualificationConfig() {
        val target = StartListConfigTarget(
            isQualification = true,
            qualificationConfig = timeTrial,
            roundsConfig = heats,
        )

        assertEquals(timeTrial, target.configId)
    }

    @Test
    fun otherRoundsUseTheRoundsConfig() {
        val target = StartListConfigTarget(
            isQualification = false,
            qualificationConfig = timeTrial,
            roundsConfig = heats,
        )

        assertEquals(heats, target.configId)
    }

    @Test
    fun qualificationFallsBackToTheRoundsConfig() {
        // Webscorer kennt die Zweiteilung nicht: dort wird nur der Runden-Slot gefuellt, und er muss
        // dann auch fuer die Qualifikation gelten.
        val target = StartListConfigTarget(
            isQualification = true,
            qualificationConfig = null,
            roundsConfig = heats,
        )

        assertEquals(heats, target.configId)
    }

    @Test
    fun otherRoundsDoNotFallBackToTheQualificationConfig() {
        // Kein Rueckfall in diese Richtung: das Zeitfahren-Preset ohne Lauf-Spalte wuerde in einem
        // Laeufe-Rennen die Zuordnung zum Lauf verlieren.
        val target = StartListConfigTarget(
            isQualification = false,
            qualificationConfig = timeTrial,
            roundsConfig = null,
        )

        assertNull(target.configId)
    }

    @Test
    fun nothingConfiguredResolvesToNull() {
        val target = StartListConfigTarget(
            isQualification = true,
            qualificationConfig = null,
            roundsConfig = null,
        )

        assertNull(target.configId)
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && cd backend && ./mvnw test -Dtest=StartListConfigTargetTest
```

Erwartung: Compile-Fehler `Unresolved reference: StartListConfigTarget`.

- [ ] **Step 3: Die minimale Implementierung schreiben**

Create `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/entity/StartListConfigTarget.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.competitionExecution.entity

import java.util.UUID

/**
 * Mit welchem Spalten-Preset die Startliste eines Laufs exportiert wird.
 *
 * Gegenstueck zu [de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget] auf der
 * Export-Seite und aus demselben Grund zweigeteilt: RaceClocker braucht pro Wettkampf zwei Rennen, und
 * das Zeitfahren-Rennen darf keine Lauf-Spalte bekommen, sonst kippt es in den Wave-Modus und verliert
 * den Countdown. [isQualification] waehlt zwischen den beiden Presets.
 *
 * Der Rueckfall auf [roundsConfig] ist kein Komfort, sondern der Grund, warum Webscorer nur ein Preset
 * braucht: dort gibt es die Zweiteilung nicht. Umgekehrt gibt es keinen Rueckfall -- ein Laeufe-Rennen
 * mit dem Zeitfahren-Preset zu bestuecken wuerde die Lauf-Zuordnung verlieren.
 */
data class StartListConfigTarget(
    val isQualification: Boolean,
    val qualificationConfig: UUID?,
    val roundsConfig: UUID?,
) {
    val configId: UUID? get() = if (isQualification) qualificationConfig ?: roundsConfig else roundsConfig
}
```

- [ ] **Step 4: Test laufen lassen und Erfolg bestätigen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && cd backend && ./mvnw test -Dtest=StartListConfigTargetTest
```

Erwartung: 5 Tests, alle PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/entity/StartListConfigTarget.kt backend/src/test/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/StartListConfigTargetTest.kt
git commit -m "Resolve the start list preset from the round instead of a dialog choice"
```

---

## Task 3: Endpunkt `/timing-config`

Ersetzt `/competitionExecution/raceclocker-config` vollständig. Neues Modul `app/timingConfig/`, benannt wie die bestehenden Nachbarn `app/startListConfig/` und `app/matchResultImportConfig/`.

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/timingConfig/entity/TimingSystem.kt`
- Create: `.../app/timingConfig/entity/TimingConfigDto.kt`
- Create: `.../app/timingConfig/entity/TimingConfigRequest.kt`
- Create: `.../app/timingConfig/boundary/TimingConfigService.kt`
- Create: `.../app/timingConfig/boundary/timingConfig.kt`
- Modify: `.../app/competition/boundary/competition.kt:116` (Route mounten)
- Modify: `.../app/competitionExecution/boundary/competitionExecution.kt:59-81` (alte Route löschen)
- Modify: `.../app/competitionExecution/boundary/CompetitionExecutionService.kt:886-925` (alte Funktionen löschen)
- Delete: `.../app/raceclocker/entity/RaceClockerConfigDto.kt`, `.../app/raceclocker/entity/RaceClockerConfigRequest.kt`
- Modify: `backend/src/main/resources/openapi/documentation.yaml`

**Interfaces:**
- Consumes: `CompetitionRepo.getRecordById(id: UUID)`, `CompetitionRepo.update(id: UUID, f: CompetitionRecord.() -> Unit)`, `RaceClockerFeed.normalizeUrl(String)`, `CompetitionError.CompetitionNotFound`.
- Produces: `TimingConfigService.getTimingConfig(competitionId: UUID): App<ServiceError, ApiResponse.Dto<TimingConfigDto>>`, `TimingConfigService.updateTimingConfig(competitionId: UUID, userId: UUID, request: TimingConfigRequest): App<ServiceError, ApiResponse.NoData>`, `enum class TimingSystem { RACECLOCKER, WEBSCORER }`, `fun Route.timingConfig()`. Frontend-Operationen heißen `getTimingConfig` / `updateTimingConfig`.

**Kein neuer Unit-Test in diesem Task, mit Absicht:** der Service ist dünnes Lesen/Schreiben über `CompetitionRepo`, und seine einzige Logik — die URL-Normalisierung — zieht unverändert aus dem alten Endpunkt um und ist über `RaceClockerFeed.normalizeUrl` in `RaceClockerFeedTest` (fünf Fälle) schon abgedeckt. Nachgewiesen wird dieser Task durch `./mvnw test` (Compile + Regression) und den manuellen Round-Trip in Task 9.

- [ ] **Step 1: Das Enum anlegen**

Create `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/timingConfig/entity/TimingSystem.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.timingConfig.entity

/**
 * Mit welchem Fremdsystem die Zeitnahme eines Wettkampfs arbeitet.
 *
 * [RACECLOCKER] holt die Ergebnisse aus dem oeffentlichen Ergebnis-Feed und braucht dafuer die beiden
 * Rennen-URLs. [WEBSCORER] kennt keinen Rueckweg ueber eine URL; dort kommen die Ergebnisse als
 * Tabelle zurueck und hochgeladen. Ist nichts gesetzt (Spalte `null`), ist der Wettkampf an kein
 * Fremdsystem gebunden -- dann fehlt lediglich die Vorbelegung, und der Export verlangt sie.
 *
 * Als Text gespeichert und von Hand konvertiert, wie ChainProgressionMode: es gibt keinen
 * jOOQ-Converter in diesem Projekt.
 */
enum class TimingSystem { RACECLOCKER, WEBSCORER }
```

- [ ] **Step 2: DTO und Request anlegen**

Create `.../app/timingConfig/entity/TimingConfigDto.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.timingConfig.entity

import java.util.UUID

data class TimingConfigDto(
    val timingSystem: TimingSystem?,
    val timeTrialResultsUrl: String?,
    val heatsResultsUrl: String?,
    val startlistConfigQualification: UUID?,
    val startlistConfigRounds: UUID?,
    val resultImportConfig: UUID?,
)
```

Create `.../app/timingConfig/entity/TimingConfigRequest.kt` — die URL-Validierung ist wortgleich aus `RaceClockerConfigRequest` übernommen, inklusive Begründung:

```kotlin
package de.lambda9.ready2race.backend.app.timingConfig.entity

import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerFeed
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrNull
import java.util.UUID

/**
 * Die Zeitnahme-Konfiguration eines Wettkampfs. Jedes Feld ist optional: die RaceClocker-Rennen
 * entstehen dort erst kurz vor der Regatta, die Konfiguration muss also unvollstaendig speicherbar
 * sein. Woran es fehlt, zeigt die Oberflaeche im Zeitnahme- und im Durchfuehrungs-Tab.
 */
data class TimingConfigRequest(
    val timingSystem: TimingSystem?,
    val timeTrialResultsUrl: String?,
    val heatsResultsUrl: String?,
    val startlistConfigQualification: UUID?,
    val startlistConfigRounds: UUID?,
    val resultImportConfig: UUID?,
) : Validatable {

    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            validateUrl(timeTrialResultsUrl, "timeTrialResultsUrl"),
            validateUrl(heatsResultsUrl, "heatsResultsUrl"),
        )

    companion object {

        /**
         * Hier abgelehnt statt erst beim Abholen, damit ein Tippfehler beim Bearbeiten auffaellt und
         * nicht mitten in der Regatta. Nutzt dieselbe Normalisierung wie der Pull: der Host ist auf
         * RaceClocker festgenagelt, ein fehlendes Schema wird ergaenzt -- so sieht eine URL aus, die
         * aus der Adresszeile kopiert wurde.
         */
        private fun validateUrl(value: String?, field: String): ValidationResult {
            if (value.isNullOrBlank()) return ValidationResult.Valid

            return if (RaceClockerFeed.normalizeUrl(value).unsafeRunSync().getOrNull() == null) {
                ValidationResult.Invalid.Message { "$field must be a URL on raceclocker.com" }
            } else {
                ValidationResult.Valid
            }
        }

        val example
            get() = TimingConfigRequest(
                timingSystem = TimingSystem.RACECLOCKER,
                timeTrialResultsUrl = "https://www.raceclocker.com/7ffb822a",
                heatsResultsUrl = "https://www.raceclocker.com/7c854955",
                startlistConfigQualification = UUID.randomUUID(),
                startlistConfigRounds = UUID.randomUUID(),
                resultImportConfig = UUID.randomUUID(),
            )
    }
}
```

- [ ] **Step 3: Den Service anlegen**

Create `.../app/timingConfig/boundary/TimingConfigService.kt`. Die URL-Normalisierung ist unverändert aus `CompetitionExecutionService.updateRaceClockerConfig` übernommen:

```kotlin
package de.lambda9.ready2race.backend.app.timingConfig.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competition.entity.CompetitionError
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerFeed
import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingConfigDto
import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingConfigRequest
import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingSystem
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.UUID

object TimingConfigService {

    fun getTimingConfig(
        competitionId: UUID,
    ): App<ServiceError, ApiResponse.Dto<TimingConfigDto>> = KIO.comprehension {

        val competition = !CompetitionRepo.getRecordById(competitionId).orDie()
            .onNullFail { CompetitionError.CompetitionNotFound }

        KIO.ok(
            ApiResponse.Dto(
                TimingConfigDto(
                    timingSystem = competition.timingSystem?.let { TimingSystem.valueOf(it) },
                    timeTrialResultsUrl = competition.raceclockerTtResultsUrl,
                    heatsResultsUrl = competition.raceclockerHeatsResultsUrl,
                    startlistConfigQualification = competition.startlistConfigQualification,
                    startlistConfigRounds = competition.startlistConfigRounds,
                    resultImportConfig = competition.resultImportConfig,
                )
            )
        )
    }

    fun updateTimingConfig(
        competitionId: UUID,
        userId: UUID,
        request: TimingConfigRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        // Normalisiert gespeichert (Schema ergaenzt, http auf https gehoben), damit der Tab hinterher
        // zeigt, was das Abholen tatsaechlich anfragt. Leer heisst "nicht konfiguriert" -- Leerstrings
        // wuerden das Abholen spaeter mit einem unbrauchbaren URL-Fehler scheitern lassen statt mit dem
        // klaren "keine URL hinterlegt".
        val timeTrialUrl = request.timeTrialResultsUrl?.trim()?.takeIf { it.isNotBlank() }
            ?.let { (!RaceClockerFeed.normalizeUrl(it)).toString() }
        val heatsUrl = request.heatsResultsUrl?.trim()?.takeIf { it.isNotBlank() }
            ?.let { (!RaceClockerFeed.normalizeUrl(it)).toString() }

        !CompetitionRepo.update(competitionId) {
            timingSystem = request.timingSystem?.name
            raceclockerTtResultsUrl = timeTrialUrl
            raceclockerHeatsResultsUrl = heatsUrl
            startlistConfigQualification = request.startlistConfigQualification
            startlistConfigRounds = request.startlistConfigRounds
            resultImportConfig = request.resultImportConfig
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie().onNullFail { CompetitionError.CompetitionNotFound }

        noData
    }
}
```

Falls `noData` und `App` nicht auflösen: die Imports aus dem Kopf von `CompetitionExecutionService.kt` übernehmen (dort werden dieselben Helfer benutzt) — `grep -n "^import" backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/boundary/CompetitionExecutionService.kt` zeigt die exakten Pfade.

- [ ] **Step 4: Die Route anlegen**

Create `.../app/timingConfig/boundary/timingConfig.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.timingConfig.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingConfigRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.timingConfig() {
    route("/timing-config") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val competitionId = !pathParam("competitionId", uuid)

                TimingConfigService.getTimingConfig(competitionId)
            }
        }
        put {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val competitionId = !pathParam("competitionId", uuid)

                val body = !receiveKIO(TimingConfigRequest.example)
                TimingConfigService.updateTimingConfig(
                    competitionId = competitionId,
                    userId = user.id!!,
                    request = body,
                )
            }
        }
    }
}
```

- [ ] **Step 5: Route mounten**

Modify `.../app/competition/boundary/competition.kt` — bei den Geschwister-Routen (Zeile 114-116) `timingConfig()` ergänzen und den Import oben hinzufügen:

```kotlin
            competitionRegistration()
            competitionSetup("competitionId")
            competitionExecution()
            timingConfig()
```

Import: `import de.lambda9.ready2race.backend.app.timingConfig.boundary.timingConfig`

- [ ] **Step 6: Alte Route und alte Service-Funktionen löschen**

In `.../app/competitionExecution/boundary/competitionExecution.kt` den kompletten Block `route("/raceclocker-config") { ... }` (Zeilen 59-81) löschen, samt dem Import `de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerConfigRequest` (Zeile 7).

In `.../app/competitionExecution/boundary/CompetitionExecutionService.kt` die Funktionen `getRaceClockerConfig` (ab Zeile 886) und `updateRaceClockerConfig` (ab Zeile 903) löschen, samt der beiden Imports `RaceClockerConfigDto` und `RaceClockerConfigRequest` (Zeilen 32-33).

```bash
git rm backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/entity/RaceClockerConfigDto.kt backend/src/main/kotlin/de/lambda9/ready2race/backend/app/raceclocker/entity/RaceClockerConfigRequest.kt
```

- [ ] **Step 7: OpenAPI umstellen**

In `backend/src/main/resources/openapi/documentation.yaml`:

1. Den kompletten Pfad `/event/{eventId}/competition/{competitionId}/competitionExecution/raceclocker-config` (Zeilen 1588-1633) durch diesen ersetzen — Einrückung exakt beibehalten (zwei Leerzeichen für den Pfad):

```yaml
  /event/{eventId}/competition/{competitionId}/timing-config:
    parameters:
      - $ref: '#/components/parameters/eventId'
      - $ref: '#/components/parameters/competitionId'
    get:
      operationId: getTimingConfig
      description: The timing configuration of this competition - timing system, RaceClocker results URLs and the column presets used for export and import.
      responses:
        200:
          description: Timing configuration successfully retrieved
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/TimingConfigDto'
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
      operationId: updateTimingConfig
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/TimingConfigRequest'
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

2. Die Schemata `RaceClockerConfigDto` und `RaceClockerConfigRequest` durch `TimingConfigDto`, `TimingSystem` und `TimingConfigRequest` ersetzen. Sechs Felder, alle optional:

```yaml
    TimingConfigDto:
      type: object
      properties:
        timingSystem:
          $ref: '#/components/schemas/TimingSystem'
        timeTrialResultsUrl:
          type: string
          description: Public results URL of the individual-start race used for the qualification round.
        heatsResultsUrl:
          type: string
          description: Public results URL of the wave-start race used for all other rounds.
        startlistConfigQualification:
          type: string
          format: uuid
        startlistConfigRounds:
          type: string
          format: uuid
        resultImportConfig:
          type: string
          format: uuid

    TimingSystem:
      type: string
      enum:
        - RACECLOCKER
        - WEBSCORER

    TimingConfigRequest:
      type: object
      description: >
        Every field is optional - the RaceClocker races only exist shortly before the regatta, so an
        incomplete configuration must be storable. The URLs must be https URLs on raceclocker.com; the
        host is pinned so the backend cannot be pointed at other services.
      properties:
        timingSystem:
          $ref: '#/components/schemas/TimingSystem'
          nullable: true
        timeTrialResultsUrl:
          type: string
          nullable: true
        heatsResultsUrl:
          type: string
          nullable: true
        startlistConfigQualification:
          type: string
          format: uuid
          nullable: true
        startlistConfigRounds:
          type: string
          format: uuid
          nullable: true
        resultImportConfig:
          type: string
          format: uuid
          nullable: true
```

Nach dem Bearbeiten die YAML-Syntax prüfen:

```bash
python3 -c "import yaml,sys; yaml.safe_load(open('backend/src/main/resources/openapi/documentation.yaml')); print('yaml ok')"
```

Erwartung: `yaml ok`. Außerdem sicherstellen, dass `RaceClockerConfig` nirgends mehr vorkommt:

```bash
grep -n "RaceClockerConfig" backend/src/main/resources/openapi/documentation.yaml
```

Erwartung: keine Ausgabe.

- [ ] **Step 8: Backend baut und Tests laufen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && cd backend && ./mvnw test
```

Erwartung: BUILD SUCCESS. Bleibt ein `Unresolved reference: RaceClockerConfig...`, wurde eine Verwendung übersehen — `grep -rn "RaceClockerConfig" backend/src/main`.

- [ ] **Step 9: Commit**

```bash
git add -A backend/src/main/kotlin backend/src/main/resources/openapi/documentation.yaml
git commit -m "Serve the competition timing configuration from its own endpoint"
```

---

## Task 4: Startlisten-Preset serverseitig auflösen

**Files:**
- Modify: `.../app/competitionExecution/entity/StartListFileType.kt` (sealed interface → enum)
- Modify: `.../app/competitionExecution/control/CompetitionMatchRepo.kt` (neue Query nach `getForRaceClockerPull`, ca. Zeile 72)
- Modify: `.../app/competitionExecution/boundary/competitionExecution.kt:214-235`
- Modify: `.../app/competitionExecution/boundary/CompetitionExecutionService.kt:1412-1436`
- Modify: `.../app/startListConfig/entity/StartListConfigError.kt`
- Modify: `.../calls/responses/ErrorCode.kt`
- Modify: `backend/src/main/resources/openapi/documentation.yaml:1671-1708`

**Interfaces:**
- Consumes: `StartListConfigTarget` (Task 2), `StartListConfigRepo.get(id: UUID)`.
- Produces: `CompetitionMatchRepo.getStartListConfigTarget(id: UUID): JIO<StartListConfigTarget?>`, `StartListConfigError.NotConfigured`, `ErrorCode.STARTLIST_CONFIG_NOT_CONFIGURED`. `StartListFileType` ist danach `enum class StartListFileType { PDF, CSV }`.

- [ ] **Step 1: `StartListFileType` zum Enum machen**

Replace den kompletten Inhalt von `.../app/competitionExecution/entity/StartListFileType.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.competitionExecution.entity

/**
 * In welchem Format eine Startliste ausgegeben wird. Das CSV-Spalten-Preset steckt nicht mehr hier: es
 * wird aus der Zeitnahme-Konfiguration des Wettkampfs und der Runde aufgeloest
 * (siehe [StartListConfigTarget]).
 */
enum class StartListFileType { PDF, CSV }
```

Das private `StartListFileTypeParam` in `competitionExecution.kt:23-26` wird damit überflüssig — es war nur der Doppelgänger des sealed interface. Löschen.

- [ ] **Step 2: Fehlerfall ergänzen**

Modify `.../app/startListConfig/entity/StartListConfigError.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.startListConfig.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.HttpStatusCode

enum class StartListConfigError : ServiceError {
    NotFound,

    /**
     * Der Wettkampf hat kein Startlisten-Preset hinterlegt, also gibt es keine Spaltenbelegung fuer den
     * CSV-Export. Vor dem Zeitnahme-Tab wurde das Preset bei jedem Download einzeln gewaehlt; jetzt
     * gehoert es zum Wettkampf, und die Oberflaeche verweist bei diesem Code dorthin.
     */
    NotConfigured;

    override fun respond(): ApiError = when(this) {
        NotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Start list config not found"
        )

        NotConfigured -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No start list export preset configured for this competition",
            errorCode = ErrorCode.STARTLIST_CONFIG_NOT_CONFIGURED,
        )
    }
}
```

Modify `.../calls/responses/ErrorCode.kt` — am Ende des RaceClocker-Blocks (nach `RACECLOCKER_MATCH_IS_BYE,`) ergänzen:

```kotlin
    STARTLIST_CONFIG_NOT_CONFIGURED,
    RESULT_IMPORT_CONFIG_NOT_CONFIGURED,
```

(`RESULT_IMPORT_CONFIG_NOT_CONFIGURED` wird in Task 5 benutzt; beide Codes hier gemeinsam anzulegen hält den Enum-Block an einer Stelle.)

- [ ] **Step 3: Die Query ergänzen**

Modify `.../app/competitionExecution/control/CompetitionMatchRepo.kt` — direkt nach `getForRaceClockerPull` einfügen. Die Join-Kette ist wortgleich von dort übernommen.

**Entscheidung (Thomas, 05.08.2026): die Doppelung bleibt stehen — kein gemeinsamer Join-Helfer.** Der Alternativweg müsste `getForRaceClockerPull` mit anfassen, den Ergebnis-Abholweg, der neun Tage vor der Regatta läuft und nur manuell erprobt ist. Acht Zeilen jOOQ sind der günstigere Preis. Nicht als Befund melden.

```kotlin
    /**
     * Welches Spalten-Preset die Startliste dieses Laufs bekommt. Dieselbe Join-Kette wie
     * [getForRaceClockerPull] und aus demselben Grund dieselbe Weiche: die Runde entscheidet, weil
     * RaceClocker pro Wettkampf zwei Rennen mit unterschiedlichen Spalten braucht.
     */
    fun getStartListConfigTarget(id: UUID) = Jooq.query {
        select(
            COMPETITION_SETUP_ROUND.IS_QUALIFICATION,
            COMPETITION.STARTLIST_CONFIG_QUALIFICATION,
            COMPETITION.STARTLIST_CONFIG_ROUNDS,
        )
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .where(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(id))
            .fetchOne {
                StartListConfigTarget(
                    // Im Schema not null; die Projektion verliert nur die Garantie.
                    isQualification = it[COMPETITION_SETUP_ROUND.IS_QUALIFICATION] == true,
                    qualificationConfig = it[COMPETITION.STARTLIST_CONFIG_QUALIFICATION],
                    roundsConfig = it[COMPETITION.STARTLIST_CONFIG_ROUNDS],
                )
            }
    }
```

Import ergänzen: `import de.lambda9.ready2race.backend.app.competitionExecution.entity.StartListConfigTarget`

- [ ] **Step 4: Auflösung in `getStartList` einbauen**

Modify `.../app/competitionExecution/boundary/CompetitionExecutionService.kt` — der `when`-Block in `getStartList` (Zeilen 1424-1436). Der CSV-Zweig lädt das Preset jetzt über die Runde:

```kotlin
        val (bytes, extension) = when (startListType) {
            StartListFileType.PDF -> {
                val pdfTemplate = !DocumentTemplateRepo.getAssigned(DocumentType.START_LIST, match.event!!).orDie()
                    .andThenNotNull { it.toPdfTemplate() }
                buildPdf(data, pdfTemplate) to "pdf"
            }

            StartListFileType.CSV -> {
                val target = !CompetitionMatchRepo.getStartListConfigTarget(matchId).orDie()
                    .onNullFail { CompetitionExecutionError.MatchNotFound }
                val configId = !KIO.failOnNull(target.configId) { StartListConfigError.NotConfigured }
                val config = !StartListConfigRepo.get(configId).orDie()
                    .onNullFail { StartListConfigError.NotFound }
                buildCsv(data, config) to "csv"
            }
        }
```

`KIO.failOnNull` ist in dieser Datei bereits in Gebrauch; falls der Import fehlt, aus `competitionExecution.kt` übernehmen.

- [ ] **Step 5: Route anpassen**

Modify `.../app/competitionExecution/boundary/competitionExecution.kt` — der `/startList`-Block (Zeilen 214-235) wird zu:

```kotlin
            get("/startList") {
                call.respondComprehension {
                    !authenticate(Privilege.ReadEventGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val competitionMatchId = !pathParam("competitionMatchId", uuid)
                    val fileType = !queryParam("fileType", enum<StartListFileType>())

                    CompetitionExecutionService.downloadStartlist(
                        eventId = eventId,
                        matchId = competitionMatchId,
                        type = fileType
                    )
                }
            }
```

- [ ] **Step 6: OpenAPI anpassen**

In `backend/src/main/resources/openapi/documentation.yaml` den `config`-Query-Parameter des `/startList`-Pfads (Zeilen 1688-1694) löschen. Das Schema `StartListFileType` (Zeilen 11638-11642) **bleibt** — `fileType` gibt es weiter. Beim Pfad `400` als möglichen Fehler belassen; er trägt jetzt auch `STARTLIST_CONFIG_NOT_CONFIGURED`.

```bash
grep -n "This parameter is required with fileType" backend/src/main/resources/openapi/documentation.yaml
```

Erwartung: keine Ausgabe.

- [ ] **Step 7: Bauen und testen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && cd backend && ./mvnw test
```

Erwartung: BUILD SUCCESS. `WebDAVExportService.kt:567` benutzt `StartListFileType.PDF` — das kompiliert mit dem Enum unverändert weiter. Falls dort ein Fehler auftaucht, ist der Enum-Umbau unvollständig.

- [ ] **Step 8: Commit**

```bash
git add -A backend/src/main backend/src/main/resources/openapi/documentation.yaml
git commit -m "Pick the start list preset from the competition rather than the request"
```

---

## Task 5: Ergebnis-Import-Preset serverseitig auflösen

**Files:**
- Delete: `.../app/competitionExecution/entity/UploadMatchResultRequest.kt`
- Modify: `.../app/competitionExecution/boundary/competitionExecution.kt:139-196` (Multipart ohne `request`-Part), `:311`, `:404` (falsche Beispiel-Referenz korrigieren)
- Modify: `.../app/competitionExecution/boundary/CompetitionExecutionService.kt:624-640`
- Modify: `.../app/matchResultImportConfig/entity/MatchResultImportConfigError.kt`
- Modify: `backend/src/main/resources/openapi/documentation.yaml:6487-6530`, `:13270-13277`

**Interfaces:**
- Consumes: `CompetitionRepo.getRecordById`, `MatchResultImportConfigRepo.get(id: UUID)`, `ErrorCode.RESULT_IMPORT_CONFIG_NOT_CONFIGURED` (in Task 4 angelegt).
- Produces: `MatchResultImportConfigError.NotConfigured`. `updateMatchResultByFile` verliert den Parameter `request: UploadMatchResultRequest`.

- [ ] **Step 1: Fehlerfall ergänzen**

Modify `.../app/matchResultImportConfig/entity/MatchResultImportConfigError.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.matchResultImportConfig.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.HttpStatusCode

enum class MatchResultImportConfigError : ServiceError {
    NotFound,

    /** Der Wettkampf hat kein Ergebnis-Import-Preset hinterlegt; siehe Zeitnahme-Tab. */
    NotConfigured;

    override fun respond(): ApiError = when(this) {
        NotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "match result import config not found"
        )

        NotConfigured -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No result import preset configured for this competition",
            errorCode = ErrorCode.RESULT_IMPORT_CONFIG_NOT_CONFIGURED,
        )
    }
}
```

- [ ] **Step 2: Service auf die Wettkampf-Spalte umstellen**

Modify `.../app/competitionExecution/boundary/CompetitionExecutionService.kt` — Kopf von `updateMatchResultByFile` (Zeilen 624-638). `competitionId` ist bereits Parameter, die Signatur wird also nur kürzer:

```kotlin
    fun updateMatchResultByFile(
        eventId: UUID,
        competitionId: UUID,
        matchId: UUID,
        file: File,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }

        val match = !checkUpdateMatchResult(competitionId, matchId)
        !prepareForNewPlaces(matchId)

        // Das Preset gehoert zum Wettkampf (Zeitnahme-Tab), nicht mehr zur einzelnen Anfrage.
        val competition = !CompetitionRepo.getRecordById(competitionId).orDie()
            .onNullFail { CompetitionError.CompetitionNotFound }
        val configId = !KIO.failOnNull(competition.resultImportConfig) {
            MatchResultImportConfigError.NotConfigured
        }
        val config = !MatchResultImportConfigRepo.get(configId).orDie()
            .onNullFail { MatchResultImportConfigError.NotFound }

        val identifierColumn = config.colTeamRegistrationId
```

Der Rest der Funktion bleibt unverändert. Nötige Imports ergänzen, falls nicht vorhanden: `CompetitionRepo`, `CompetitionError`.

- [ ] **Step 3: Multipart-Route entschlacken**

Modify `.../app/competitionExecution/boundary/competitionExecution.kt` — im `put("/results-file")`-Block: die Variable `var request: UploadMatchResultRequest? = null`, den ganzen `is PartData.FormItem ->`-Zweig, die Zeile `val req = !KIO.failOnNull(request) { RequestError.BodyMissing(UploadMatchResultRequest.example) }` und den Aufrufparameter `request = req` entfernen. Der Aufruf lautet danach:

```kotlin
                    CompetitionExecutionService.updateMatchResultByFile(
                        eventId = eventId,
                        competitionId = competitionId,
                        matchId = competitionMatchId,
                        file = file,
                        userId = user.id!!
                    )
```

- [ ] **Step 4: Die beiden falschen Beispiel-Referenzen korrigieren**

An den Zeilen 311 und 404 steht `RequestError.BodyMissing(UploadMatchResultRequest.example)`, obwohl dort ein `CompetitionChallengeResultRequest` geparst wird — ein Copy-Paste-Fehler, der bisher ein falsches Beispiel in die Fehlerantwort schrieb. Beide ersetzen durch:

```kotlin
                            !KIO.failOnNull(request) { RequestError.BodyMissing(CompetitionChallengeResultRequest.example) }
```

Ohne diese Korrektur lässt sich `UploadMatchResultRequest` nicht löschen.

- [ ] **Step 5: Die Request-Klasse löschen**

```bash
git rm backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionExecution/entity/UploadMatchResultRequest.kt
grep -rn "UploadMatchResultRequest" backend/src/main
```

Erwartung: keine Ausgabe.

- [ ] **Step 6: OpenAPI anpassen**

Im `results-file`-Pfad (Zeilen 6487-6530) die `request`-Property aus dem Multipart-Body und aus dessen `required`-Liste entfernen, sodass nur `files` bleibt. Das Schema `UploadMatchResultRequest` (Zeilen 13270-13277) löschen.

```bash
grep -n "UploadMatchResultRequest" backend/src/main/resources/openapi/documentation.yaml
python3 -c "import yaml; yaml.safe_load(open('backend/src/main/resources/openapi/documentation.yaml')); print('yaml ok')"
```

Erwartung: kein Treffer, dann `yaml ok`.

- [ ] **Step 7: Bauen und testen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && cd backend && ./mvnw test
```

Erwartung: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add -A backend/src backend/src/main/resources/openapi/documentation.yaml
git commit -m "Pick the result import preset from the competition rather than the request"
```

---

## Task 6: SDK neu generieren und das Frontend nachziehen

Nach diesem Task kompiliert das Frontend wieder, und der defekte Zwischenzustand aus Tasks 4-5 ist geschlossen. Der neue Tab kommt erst in Task 7 — hier geht es nur darum, die alten Aufrufwege zu entfernen.

**Files:**
- Modify (generiert): `frontend/src/api/{sdk.gen.ts,types.gen.ts,index.ts}`
- Delete: `frontend/src/components/event/competition/excecution/StartListConfigPicker.tsx`, `.../RaceClockerConfigDialog.tsx`
- Modify: `.../CompetitionExecution.tsx`, `.../CompetitionExecutionRound.tsx`, `.../MatchResultUploadDialog.tsx`

**Interfaces:**
- Consumes: die generierten `getTimingConfig` / `updateTimingConfig` / `TimingConfigDto` / `TimingConfigRequest` / `TimingSystem`.
- Produces: `handleDownloadStartList(competitionMatchId: string, fileType: StartListFileType): Promise<void>` (ohne `config`), `handleUploadMatchResults(competitionMatchId: string, file: File): Promise<void>`, `MatchResultUploadDialog` mit `onSuccess: (file: File) => Promise<void>`.

- [ ] **Step 1: SDK generieren**

```bash
cd frontend && npm install && npm run generate
```

Erwartung: `frontend/src/api/` wird neu geschrieben. Prüfen:

```bash
grep -n "getTimingConfig\|updateTimingConfig" frontend/src/api/sdk.gen.ts | head
grep -n "RaceClockerConfig\|UploadMatchResultRequest" frontend/src/api/types.gen.ts
```

Erwartung: erste Zeile findet beide Operationen, zweite findet nichts.

- [ ] **Step 2: Den Startlisten-Picker löschen**

```bash
git rm frontend/src/components/event/competition/excecution/StartListConfigPicker.tsx frontend/src/components/event/competition/excecution/RaceClockerConfigDialog.tsx
```

- [ ] **Step 3: `CompetitionExecution.tsx` entschlacken**

Zu entfernen:
- Import von `StartListConfigPicker` (Zeile 67) und `RaceClockerConfigDialog` (Zeile 69).
- `startListMatch`-State samt `showStartListConfigDialog` und `closeStartListConfigDialog` (Zeilen 264-266).
- `showRaceClockerConfig`-State (Zeile 268).
- Der Konfigurations-Knopf samt umgebender `<Box sx={{my: 2}}>` (Zeilen 787-794).
- Das `<StartListConfigPicker …>`-Element (Zeilen 1257-1261) und das `<RaceClockerConfigDialog …>`-Element (Zeilen 1262-1267).
- Die Prop `setStartListMatch={setStartListMatch}` an `CompetitionExecutionRound` (Zeile 812).

Zu ändern — `handleDownloadStartList` verliert den `config`-Parameter und behandelt den neuen Fehlercode:

```tsx
    const handleDownloadStartList = async (
        competitionMatchId: string,
        fileType: StartListFileType,
    ) => {
        const {data, error, response} = await downloadStartList({
            path: {
                eventId,
                competitionId,
                competitionMatchId,
            },
            query: {
                fileType,
            },
        })
        const anchor = downloadRef.current

        if (error) {
            if (error.status.value === 409) {
                feedback.error(t('event.competition.execution.startList.error.missingStartTime'))
            } else if (
                error.status.value === 400 &&
                'errorCode' in error &&
                error.errorCode === 'STARTLIST_CONFIG_NOT_CONFIGURED'
            ) {
                feedback.error(t('event.competition.execution.startList.error.notConfigured'))
            } else {
                feedback.error(t('common.error.unexpected'))
            }
        } else if (data !== undefined && anchor) {
```

(Der Rest des `else if`-Zweigs bleibt unverändert. Trifft der `errorCode`-Zugriff auf einen Typfehler, weil der generierte Fehlertyp ihn nicht kennt: dann `(error as {errorCode?: string}).errorCode` verwenden und den Cast mit einem kurzen Kommentar begründen.)

`handleUploadMatchResults` verliert `config` und bekommt dieselbe Fehlerbehandlung mit `RESULT_IMPORT_CONFIG_NOT_CONFIGURED` und dem Schlüssel `event.competition.execution.results.error.notConfigured`:

```tsx
    const handleUploadMatchResults = async (competitionMatchId: string, file: File) => {
        const {error} = await uploadResultFile({
            path: {
                eventId,
                competitionId,
                competitionMatchId,
            },
            body: {
                files: [file],
            },
        })
```

Und das Upload-Dialog-Element:

```tsx
            <MatchResultUploadDialog
                open={showMatchResultImportConfigDialog}
                onClose={closeMatchResultImportConfigDialog}
                onSuccess={async file => handleUploadMatchResults(resultImportMatch!, file)}
            />
```

`resultImportMatch` bleibt — es identifiziert den Lauf für die Dateiauswahl.

- [ ] **Step 4: `CompetitionExecutionRound.tsx` anpassen**

- Prop `setStartListMatch: Dispatch<SetStateAction<string | null>>` (Zeile 52) und ihr Destructuring (Zeile 67) entfernen.
- Neue Prop für den direkten CSV-Download ergänzen, neben `handleDownloadStartListPDF`:

```tsx
    handleDownloadStartListCSV: (competitionMatchId: string) => Promise<void>
```

- Im `onSelectItem` des Startlisten-Menüs (Zeilen 403-413) den CSV-Zweig auf den direkten Download umstellen:

```tsx
                                                case 'CSV':
                                                    await handleDownloadStartListCSV(match.id)
                                                    break
```

In `CompetitionExecution.tsx` die neue Prop übergeben:

```tsx
                        handleDownloadStartListCSV={matchId =>
                            handleDownloadStartList(matchId, 'CSV')
                        }
```

- [ ] **Step 5: `MatchResultUploadDialog.tsx` auf reine Dateiauswahl reduzieren**

Zu entfernen: der Import von `getMatchResultImportConfigs`, `FormInputAutocomplete`, `AutocompleteOption`, `useFetch`, `InlineLink`; das `useFetch`-Block (Zeilen 47-53); das Feld `config` aus `Form` und `defaultValues`; das `<FormInputAutocomplete name={'config'} …>` (Zeilen 109-115); der `<Alert>` mit dem Verweis auf die Preset-Verwaltung (Zeilen 96-107).

`Props` und `onSuccess`:

```tsx
type Props = {
    open: boolean
    onSuccess: (file: File) => Promise<void>
    onClose: () => void
}

type Form = {
    files: {
        file: File
    }[]
}

const defaultValues: Form = {
    files: [],
}
```

und im `FormContainer`:

```tsx
                onSuccess={async (data: Form) => {
                    setSubmitting(true)
                    await onSuccess(data.files[0].file)
                    setSubmitting(false)
                    onClose()
                }}>
```

- [ ] **Step 6: Bauen**

```bash
cd frontend && npm run build
```

Erwartung: erfolgreicher `tsc -b` und Vite-Build, keine Fehler. Jeder verbleibende Fehler nennt eine übersehene Verwendung — abarbeiten, bis der Build durchläuft.

- [ ] **Step 7: Frontend-Tests**

```bash
cd frontend && npm run test
```

Erwartung: alle bestehenden Tests grün (sie berühren diese Pfade nicht).

- [ ] **Step 8: Commit**

```bash
git add -A frontend/src
git commit -m "Drop the preset pickers from the execution tab"
```

---

## Task 7: Der Tab „Zeitnahme"

**Files:**
- Create: `frontend/src/components/event/competition/timing/timingConfigForm.ts`
- Create: `frontend/src/components/event/competition/timing/timingConfigForm.test.ts`
- Create: `frontend/src/components/event/competition/timing/CompetitionTimingConfig.tsx`
- Modify: `frontend/src/pages/event/CompetitionPage.tsx:37-44`, `:186-197`, `:412-416`
- Modify: `frontend/src/i18n/{de,en,da}/translations.json`

**Interfaces:**
- Consumes: `getTimingConfig`, `updateTimingConfig`, `getStartListConfigs`, `getMatchResultImportConfigs`, `TimingConfigDto`, `TimingConfigRequest`.
- Produces: `type TimingForm`, `mapDtoToTimingForm(dto: TimingConfigDto): TimingForm`, `mapTimingFormToRequest(form: TimingForm): TimingConfigRequest`, `timingConfigWarnings(form: TimingForm): TimingWarning[]` mit `type TimingWarning = 'heatsUrl' | 'startlistRounds'`, Default-Export `CompetitionTimingConfig`.

- [ ] **Step 1: Die fehlschlagenden Tests für Mapping und Warnungen schreiben**

Create `frontend/src/components/event/competition/timing/timingConfigForm.test.ts`:

```ts
import {describe, expect, it} from 'vitest'
import {
    emptyTimingForm,
    mapDtoToTimingForm,
    mapTimingFormToRequest,
    timingConfigWarnings,
} from './timingConfigForm.ts'

const qualificationPreset = '11111111-1111-1111-1111-111111111111'
const roundsPreset = '22222222-2222-2222-2222-222222222222'
const importPreset = '33333333-3333-3333-3333-333333333333'

describe('mapDtoToTimingForm', () => {
    it('setzt ein fehlendes Zeitnahme-System auf NONE', () => {
        const form = mapDtoToTimingForm({})

        expect(form.timingSystem).toBe('NONE')
    })

    it('belegt jedes Feld des Formulars, damit reset() keines verwirft', () => {
        const form = mapDtoToTimingForm({timingSystem: 'RACECLOCKER'})

        expect(Object.keys(form).sort()).toEqual(Object.keys(emptyTimingForm).sort())
    })
})

describe('mapTimingFormToRequest', () => {
    it('schickt NONE als null', () => {
        const request = mapTimingFormToRequest({...emptyTimingForm, timingSystem: 'NONE'})

        expect(request.timingSystem).toBeNull()
    })

    it('verwirft die URLs, wenn nicht RaceClocker gewählt ist', () => {
        // Sonst bliebe eine URL stehen, die im Tab gar nicht mehr sichtbar ist.
        const request = mapTimingFormToRequest({
            ...emptyTimingForm,
            timingSystem: 'WEBSCORER',
            heatsResultsUrl: 'https://www.raceclocker.com/7c854955',
        })

        expect(request.heatsResultsUrl).toBeNull()
    })

    it('verwirft das Qualifikations-Preset, wenn nicht RaceClocker gewählt ist', () => {
        // Webscorer kennt die Zweiteilung nicht und zeigt nur ein Preset-Feld.
        const request = mapTimingFormToRequest({
            ...emptyTimingForm,
            timingSystem: 'WEBSCORER',
            startlistConfigQualification: {id: qualificationPreset, label: 'Zeitfahren'},
            startlistConfigRounds: {id: roundsPreset, label: 'Läufe'},
        })

        expect(request.startlistConfigQualification).toBeNull()
        expect(request.startlistConfigRounds).toBe(roundsPreset)
    })

    it('übernimmt bei RaceClocker beide Presets und das Import-Preset', () => {
        const request = mapTimingFormToRequest({
            timingSystem: 'RACECLOCKER',
            timeTrialResultsUrl: 'https://www.raceclocker.com/7ffb822a',
            heatsResultsUrl: 'https://www.raceclocker.com/7c854955',
            startlistConfigQualification: {id: qualificationPreset, label: 'Zeitfahren'},
            startlistConfigRounds: {id: roundsPreset, label: 'Läufe'},
            resultImportConfig: {id: importPreset, label: 'Webscorer xlsx'},
        })

        expect(request.startlistConfigQualification).toBe(qualificationPreset)
        expect(request.startlistConfigRounds).toBe(roundsPreset)
        expect(request.resultImportConfig).toBe(importPreset)
    })

    it('macht aus einer leeren URL null statt eines Leerstrings', () => {
        const request = mapTimingFormToRequest({
            ...emptyTimingForm,
            timingSystem: 'RACECLOCKER',
            timeTrialResultsUrl: '   ',
        })

        expect(request.timeTrialResultsUrl).toBeNull()
    })
})

describe('timingConfigWarnings', () => {
    it('schweigt, solange kein System gewählt ist', () => {
        expect(timingConfigWarnings(emptyTimingForm)).toEqual([])
    })

    it('mahnt bei RaceClocker die Läufe-URL an', () => {
        const warnings = timingConfigWarnings({
            ...emptyTimingForm,
            timingSystem: 'RACECLOCKER',
            startlistConfigRounds: {id: roundsPreset, label: 'Läufe'},
        })

        expect(warnings).toEqual(['heatsUrl'])
    })

    it('mahnt das Startlisten-Preset an, auch bei Webscorer', () => {
        const warnings = timingConfigWarnings({...emptyTimingForm, timingSystem: 'WEBSCORER'})

        expect(warnings).toEqual(['startlistRounds'])
    })

    it('mahnt die Zeitfahren-URL nicht an — nicht jeder Wettkampf hat eine Qualifikation', () => {
        const warnings = timingConfigWarnings({
            ...emptyTimingForm,
            timingSystem: 'RACECLOCKER',
            heatsResultsUrl: 'https://www.raceclocker.com/7c854955',
            startlistConfigRounds: {id: roundsPreset, label: 'Läufe'},
        })

        expect(warnings).toEqual([])
    })
})
```

- [ ] **Step 2: Tests laufen lassen und Fehlschlag bestätigen**

```bash
cd frontend && npm run test -- timingConfigForm
```

Erwartung: FAIL, `Failed to resolve import "./timingConfigForm.ts"`.

- [ ] **Step 3: Die reine Logik schreiben**

Create `frontend/src/components/event/competition/timing/timingConfigForm.ts`:

```ts
import {TimingConfigDto, TimingConfigRequest} from '@api/types.gen.ts'
import {AutocompleteOption} from '@utils/types.ts'

/**
 * 'NONE' ist der Formular-Stellvertreter für „kein Zeitnahme-System gesetzt“ (Spalte null). Ein
 * Radio-Button braucht einen Wert; null lässt sich nicht auswählen.
 */
export type TimingFormSystem = 'NONE' | 'RACECLOCKER' | 'WEBSCORER'

export type TimingForm = {
    timingSystem: TimingFormSystem
    timeTrialResultsUrl: string
    heatsResultsUrl: string
    startlistConfigQualification: AutocompleteOption
    startlistConfigRounds: AutocompleteOption
    resultImportConfig: AutocompleteOption
}

export const emptyTimingForm: TimingForm = {
    timingSystem: 'NONE',
    timeTrialResultsUrl: '',
    heatsResultsUrl: '',
    startlistConfigQualification: null,
    startlistConfigRounds: null,
    resultImportConfig: null,
}

/**
 * Die Preset-Felder kommen als reine UUID aus dem Backend. Das Label füllt die Komponente nach, sobald
 * die Preset-Listen geladen sind — hier steht nur die ID, damit diese Funktion ohne Netz testbar bleibt.
 */
export const mapDtoToTimingForm = (dto: TimingConfigDto): TimingForm => ({
    timingSystem: dto.timingSystem ?? 'NONE',
    timeTrialResultsUrl: dto.timeTrialResultsUrl ?? '',
    heatsResultsUrl: dto.heatsResultsUrl ?? '',
    startlistConfigQualification: dto.startlistConfigQualification
        ? {id: dto.startlistConfigQualification, label: ''}
        : null,
    startlistConfigRounds: dto.startlistConfigRounds
        ? {id: dto.startlistConfigRounds, label: ''}
        : null,
    resultImportConfig: dto.resultImportConfig ? {id: dto.resultImportConfig, label: ''} : null,
})

const trimmedOrNull = (value: string): string | null => value.trim() || null

/**
 * Felder, die für das gewählte System nicht sichtbar sind, werden bewusst geleert statt durchgereicht:
 * eine unsichtbare URL oder ein unsichtbares Preset wäre eine Einstellung, die niemand mehr findet.
 */
export const mapTimingFormToRequest = (form: TimingForm): TimingConfigRequest => {
    const raceClocker = form.timingSystem === 'RACECLOCKER'
    const configured = form.timingSystem !== 'NONE'

    return {
        timingSystem: form.timingSystem === 'NONE' ? null : form.timingSystem,
        timeTrialResultsUrl: raceClocker ? trimmedOrNull(form.timeTrialResultsUrl) : null,
        heatsResultsUrl: raceClocker ? trimmedOrNull(form.heatsResultsUrl) : null,
        startlistConfigQualification: raceClocker
            ? (form.startlistConfigQualification?.id ?? null)
            : null,
        startlistConfigRounds: configured ? (form.startlistConfigRounds?.id ?? null) : null,
        resultImportConfig: configured ? (form.resultImportConfig?.id ?? null) : null,
    }
}
```

**Korrektur am 05.08.2026 (Review Task 7):** die beiden letzten Zeilen waren zunächst ungeschützt und hätten bei `NONE` gespeicherte Presets durchgereicht, obwohl der Tab die Felder dort verbirgt. Besonders heikel, weil die serverseitige Auflösung `timing_system` gar nicht liest — ein unsichtbares Preset hätte also weiter exportiert. Dazu gehört der Testfall:

```ts
    it('verwirft alle Presets, wenn kein System gesetzt ist', () => {
        const request = mapTimingFormToRequest({
            ...emptyTimingForm,
            timingSystem: 'NONE',
            startlistConfigRounds: {id: roundsPreset, label: 'Läufe'},
            resultImportConfig: {id: importPreset, label: 'Webscorer xlsx'},
        })

        expect(request.startlistConfigRounds).toBeNull()
        expect(request.resultImportConfig).toBeNull()
    })

export type TimingWarning = 'heatsUrl' | 'startlistRounds'

/**
 * Was fehlt, um die Zeitnahme benutzen zu können.
 *
 * Bewusst NICHT dabei: die Zeitfahren-URL und das Qualifikations-Preset. Ein Wettkampf ohne
 * Qualifikationsrunde braucht beides nie, und eine Warnung, die dort dauerhaft steht, wird ignoriert.
 */
export const timingConfigWarnings = (form: TimingForm): TimingWarning[] => {
    if (form.timingSystem === 'NONE') return []

    const warnings: TimingWarning[] = []
    if (form.timingSystem === 'RACECLOCKER' && !form.heatsResultsUrl.trim()) {
        warnings.push('heatsUrl')
    }
    if (!form.startlistConfigRounds) {
        warnings.push('startlistRounds')
    }
    return warnings
}
```

- [ ] **Step 4: Tests laufen lassen und Erfolg bestätigen**

```bash
cd frontend && npm run test -- timingConfigForm
```

Erwartung: 11 Tests, alle PASS.

- [ ] **Step 5: Die Tab-Komponente schreiben**

Create `frontend/src/components/event/competition/timing/CompetitionTimingConfig.tsx`. Sie folgt dem Muster aus dem gelöschten `RaceClockerConfigDialog` (Laden per `useFetch`, Speichern per `FormContainer` + `SubmitButton`), aber als Tab-Inhalt in einer `Card` statt als Dialog. Die bedingte Sichtbarkeit folgt `ScheduleSlotDialog.tsx:100` (`useWatch` aus `react-hook-form-mui`):

```tsx
import {Alert, AlertTitle, Box, Card, Stack, Typography} from '@mui/material'
import {FormContainer, useForm, useWatch} from 'react-hook-form-mui'
import {Trans, useTranslation} from 'react-i18next'
import {useState} from 'react'
import {competitionRoute, eventRoute} from '@routes'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {
    getMatchResultImportConfigs,
    getStartListConfigs,
    getTimingConfig,
    updateTimingConfig,
} from '@api/sdk.gen.ts'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {FormInputRadioButtonGroup} from '@components/form/input/FormInputRadioButtonGroup.tsx'
import FormInputAutocomplete from '@components/form/input/FormInputAutocomplete.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import InlineLink from '@components/InlineLink.tsx'
import {
    emptyTimingForm,
    mapDtoToTimingForm,
    mapTimingFormToRequest,
    TimingForm,
    timingConfigWarnings,
} from './timingConfigForm.ts'

/**
 * Die Zeitnahme-Einstellungen eines Wettkampfs: mit welchem Fremdsystem er arbeitet, unter welchen
 * Adressen dessen Ergebnisse liegen und mit welchen Spalten-Presets exportiert und importiert wird.
 *
 * Bewusst hier und nicht in „Wettkampf bearbeiten": jener Dialog pflegt competition_properties, die
 * laut Check-Constraint auch an einer Wettkampf-Vorlage hängen können. Diese Werte zeigen auf konkrete
 * Rennen einer konkreten Regatta und sind deshalb nie vorlagefähig.
 */
const CompetitionTimingConfig = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const [submitting, setSubmitting] = useState(false)

    const formContext = useForm<TimingForm>({defaultValues: emptyTimingForm})

    const {data: startListConfigs, pending: startListConfigsPending} = useFetch(
        signal => getStartListConfigs({signal}),
        {
            mapData: data => data.data.map(dto => ({id: dto.id, label: dto.name})),
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(t('common.error.unexpected'))
                }
            },
        },
    )

    const {data: importConfigs, pending: importConfigsPending} = useFetch(
        signal => getMatchResultImportConfigs({signal}),
        {
            mapData: data => data.data.map(dto => ({id: dto.id, label: dto.name})),
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(t('common.error.unexpected'))
                }
            },
        },
    )

    useFetch(signal => getTimingConfig({signal, path: {eventId, competitionId}}), {
        onResponse: ({data, error}) => {
            if (error) {
                feedback.error(t('common.error.unexpected'))
            } else if (data) {
                formContext.reset(mapDtoToTimingForm(data))
            }
        },
        deps: [eventId, competitionId],
    })

    const timingSystem = useWatch({control: formContext.control, name: 'timingSystem'})
    const warnings = timingConfigWarnings(formContext.watch())

    return (
        <Card sx={{p: 3, maxWidth: 720}}>
            <FormContainer
                formContext={formContext}
                onSuccess={async (data: TimingForm) => {
                    setSubmitting(true)
                    const {error} = await updateTimingConfig({
                        path: {eventId, competitionId},
                        body: mapTimingFormToRequest(data),
                    })
                    setSubmitting(false)

                    if (error) {
                        feedback.error(
                            error.status.value === 422
                                ? t('event.competition.timing.invalid')
                                : t('common.error.unexpected'),
                        )
                    } else {
                        feedback.success(t('event.competition.timing.saved'))
                    }
                }}>
                <Stack spacing={4}>
                    <FormInputRadioButtonGroup
                        name={'timingSystem'}
                        label={t('event.competition.timing.system')}
                        row
                        options={[
                            {id: 'NONE', label: t('event.competition.timing.systems.none')},
                            {
                                id: 'RACECLOCKER',
                                label: t('event.competition.timing.systems.raceclocker'),
                            },
                            {
                                id: 'WEBSCORER',
                                label: t('event.competition.timing.systems.webscorer'),
                            },
                        ]}
                    />

                    {warnings.length > 0 && (
                        <Alert variant={'outlined'} severity={'warning'}>
                            <AlertTitle>
                                <Trans i18nKey={'event.competition.timing.incomplete.title'} />
                            </AlertTitle>
                            {warnings.map(warning => (
                                <Typography key={warning}>
                                    {t(`event.competition.timing.incomplete.${warning}`)}
                                </Typography>
                            ))}
                        </Alert>
                    )}

                    {timingSystem === 'RACECLOCKER' && (
                        <Stack spacing={4}>
                            <Alert variant={'outlined'} severity={'info'}>
                                <Trans i18nKey={'event.competition.timing.raceclockerHint'} />
                            </Alert>
                            <FormInputText
                                name={'timeTrialResultsUrl'}
                                label={t('event.competition.timing.timeTrialUrl')}
                            />
                            <FormInputText
                                name={'heatsResultsUrl'}
                                label={t('event.competition.timing.heatsUrl')}
                            />
                        </Stack>
                    )}

                    {timingSystem !== 'NONE' && (
                        <Stack spacing={4}>
                            {timingSystem === 'RACECLOCKER' && (
                                <FormInputAutocomplete
                                    name={'startlistConfigQualification'}
                                    options={startListConfigs ?? []}
                                    loading={startListConfigsPending}
                                    label={t('event.competition.timing.startlistQualification')}
                                />
                            )}
                            <FormInputAutocomplete
                                name={'startlistConfigRounds'}
                                options={startListConfigs ?? []}
                                loading={startListConfigsPending}
                                label={t(
                                    timingSystem === 'RACECLOCKER'
                                        ? 'event.competition.timing.startlistRounds'
                                        : 'event.competition.timing.startlist',
                                )}
                            />
                            {/* Die RaceClocker-Presets exportieren ohne Kopfzeile, weil RaceClocker
                                eine solche Zeile als Teilnehmer importiert. Der Spaltenmapper zeigt
                                dann Positionen statt Namen, was leicht unbemerkt schiefgeht. */}
                            <Alert variant={'outlined'} severity={'info'}>
                                <Trans i18nKey={'event.competition.timing.importHint'} />
                            </Alert>
                            <FormInputAutocomplete
                                name={'resultImportConfig'}
                                options={importConfigs ?? []}
                                loading={importConfigsPending}
                                label={t('event.competition.timing.resultImport')}
                            />
                            <Typography variant={'body2'} color={'text.secondary'}>
                                <Trans i18nKey={'event.competition.timing.presetsHint.1'} />
                                <InlineLink
                                    to={'/config'}
                                    search={{tab: 'competition-elements'}}
                                    hash={'startlists'}>
                                    <Trans i18nKey={'event.competition.timing.presetsHint.2'} />
                                </InlineLink>
                                <Trans i18nKey={'event.competition.timing.presetsHint.3'} />
                            </Typography>
                        </Stack>
                    )}

                    <Box>
                        <SubmitButton submitting={submitting}>
                            <Trans i18nKey={'common.save'} />
                        </SubmitButton>
                    </Box>
                </Stack>
            </FormContainer>
        </Card>
    )
}

export default CompetitionTimingConfig
```

Hinweis für den Umsetzenden: `mapDtoToTimingForm` liefert Preset-Optionen mit leerem `label`. Sobald die Preset-Listen geladen sind, zeigt MUI für eine Option, deren `id` in `options` vorkommt, das Label aus `options` — falls die Autocomplete stattdessen leer bleibt, in der `onResponse` von `getTimingConfig` die Labels aus `startListConfigs` nachschlagen und mit `formContext.setValue` setzen. Diesen Fall im Browser prüfen (Step 8).

- [ ] **Step 6: Den Tab in die Seite einhängen**

Modify `frontend/src/pages/event/CompetitionPage.tsx`:

1. `COMPETITION_TABS` (Zeilen 37-44) um `'timing'` ergänzen — hinter `'execution'`, damit die Reihenfolge dem Arbeitsablauf folgt:

```tsx
const COMPETITION_TABS = [
    'general',
    'registrations',
    'teams',
    'setup',
    'execution',
    'timing',
    'places',
] as const
```

2. Import ergänzen:

```tsx
import CompetitionTimingConfig from '@components/event/competition/timing/CompetitionTimingConfig.tsx'
```

3. Nach dem Durchführungs-`Tab` (Zeilen 186-191) den neuen Tab mit denselben Bedingungen:

```tsx
                            {user.checkPrivilege(updateEventGlobal) && !eventData.challengeEvent && (
                                <Tab
                                    label={t('event.competition.timing.tabTitle')}
                                    {...tabProps('timing')}
                                />
                            )}
```

4. Nach dem Durchführungs-`TabPanel` (Zeilen 412-416) das neue Panel:

```tsx
                        {user.checkPrivilege(updateEventGlobal) && !eventData.challengeEvent && (
                            <TabPanel index={'timing'} activeTab={activeTab}>
                                <CompetitionTimingConfig />
                            </TabPanel>
                        )}
```

`validateTabSearch` in `routes.tsx` ist generisch und braucht **keine** Änderung.

- [ ] **Step 7: i18n-Schlüssel in allen drei Sprachen**

Der alte Block `event.competition.execution.raceclocker.config` (de: Zeilen 1039-1050) wird gelöscht — der Knopf und der Dialog existieren nicht mehr. Der Block `event.competition.execution.results.raceclocker` (Zeilen 960-971) **bleibt**: das sind die Fehlermeldungen des Abhol-Vorgangs.

Neu unter `event.competition.timing` (deutsch; Texte für `en` und `da` sinngemäß übersetzen, Struktur identisch halten):

```json
        "timing": {
          "tabTitle": "Zeitnahme",
          "system": "Zeitnahme-System",
          "systems": {
            "none": "nicht gesetzt",
            "raceclocker": "RaceClocker",
            "webscorer": "Webscorer"
          },
          "raceclockerHint": "RaceClocker benötigt pro Wettkampf zwei Rennen: eines vom Typ \"Einzelstarts (Zeitfahren)\" für die Qualifikationsrunde und eines vom Typ \"Start in mehreren Läufen\" für alle übrigen Runden. Tragen Sie hier die öffentlichen Ergebnis-Adressen der beiden Rennen ein. Welche davon für einen Lauf gilt, ermittelt ready2race selbst.",
          "timeTrialUrl": "Ergebnis-Adresse Zeitfahren",
          "heatsUrl": "Ergebnis-Adresse Läufe",
          "startlistQualification": "Startlisten-Preset Qualifikation",
          "startlistRounds": "Startlisten-Preset übrige Runden",
          "startlist": "Startlisten-Preset",
          "resultImport": "Ergebnis-Import-Preset (xlsx)",
          "importHint": "Die RaceClocker-Vorlagen exportieren ohne Kopfzeile, weil RaceClocker eine solche Zeile sonst als Teilnehmer importiert. Ordnen Sie die Spalten beim Import in RaceClocker deshalb nach ihrer Reihenfolge zu – insbesondere die interne Kennung auf \"Extra info\".",
          "presetsHint": {
            "1": "Presets werden ",
            "2": "in der Konfiguration",
            "3": " angelegt und gelten für alle Veranstaltungen."
          },
          "incomplete": {
            "title": "Diese Zeitnahme ist noch nicht einsatzbereit",
            "heatsUrl": "Es fehlt die Ergebnis-Adresse des Läufe-Rennens – ohne sie lassen sich keine Ergebnisse aus RaceClocker holen.",
            "startlistRounds": "Es fehlt das Startlisten-Preset – ohne es lässt sich keine Startliste als CSV exportieren."
          },
          "saved": "Zeitnahme-Einstellungen gespeichert",
          "invalid": "Bitte geben Sie Adressen auf raceclocker.com an."
        }
```

**Bereits vorhanden, nicht erneut anlegen:** `event.competition.execution.startList.error.notConfigured` und `event.competition.execution.results.error.notConfigured` wurden im Aufräum-Commit nach Task 6 (`5498fa5f`) in allen drei Sprachen ergänzt. Ebenso ist `results.dialog.title` dort schon auf „Ergebnisdatei hochladen" umformuliert. Finger weg von beidem, sonst entstehen doppelte JSON-Schlüssel.

Zu löschen sind die verwaisten Dialog-Schlüssel `event.competition.execution.startList.dialog.*` und `event.competition.execution.results.dialog.{config,alert.1,alert.2,alert.3}` — **`results.dialog.file.*` und `results.dialog.title` bleiben**, denn der Upload-Dialog existiert weiter. `startList.error.missingStartTime` bleibt ebenfalls; sie wird auch von `CompetitionPlaces.tsx:61` benutzt. Nach dem Aufräumen prüfen:

```bash
cd frontend && for k in startList.dialog.config raceclocker.config.open; do grep -c "$k" src/i18n/de/translations.json; done
```

Erwartung: `0` für beide. Und dass alle drei Dateien gleich viele Schlüssel haben:

```bash
cd frontend && for l in de en da; do python3 -c "
import json
def leaves(o):
    return sum(leaves(v) for v in o.values()) if isinstance(o, dict) else 1
print('$l', leaves(json.load(open('src/i18n/$l/translations.json'))))
"; done
```

Erwartung: drei identische Zahlen.

- [ ] **Step 8: Bauen, testen, im Browser ansehen**

```bash
cd frontend && npm run test && npm run build
```

Erwartung: alle Tests grün, Build erfolgreich.

Dann Backend und Frontend starten und den Tab öffnen: System umschalten (der URL-Block erscheint und verschwindet), ein Preset wählen, speichern, Seite neu laden — die gespeicherten Werte müssen mit ihren Namen in den Autocompletes stehen. Fehlt ein Label, den Hinweis aus Step 5 umsetzen.

- [ ] **Step 9: Commit**

```bash
git add -A frontend/src
git commit -m "Give each competition a timing settings tab"
```

---

## Task 8: Hinweis in der Durchführung und systemabhängiges Ergebnis-Menü

**Files:**
- Modify: `frontend/src/components/event/competition/excecution/CompetitionExecution.tsx`
- Modify: `frontend/src/components/event/competition/excecution/CompetitionExecutionRound.tsx:52-58`, `:336-377`
- Modify: `frontend/src/i18n/{de,en,da}/translations.json`

**Interfaces:**
- Consumes: `getTimingConfig`, `TimingConfigDto`, `timingConfigWarnings`, `mapDtoToTimingForm` (Task 7).
- Produces: Prop `timingSystem: TimingConfigDto['timingSystem']` an `CompetitionExecutionRound`; `matchResultOptions(timingSystem)` als reine Funktion in `CompetitionExecutionRound.tsx`.

- [ ] **Step 1: Den Test für die Menü-Ableitung schreiben**

Die Ableitung gehört zu `CompetitionExecutionRound`, ist aber eine reine Funktion und wird exportiert, um sie ohne Rendering zu testen. Create `frontend/src/components/event/competition/excecution/matchResultOptions.test.ts`:

```ts
import {describe, expect, it} from 'vitest'
import {matchResultOptions} from './matchResultOptions.ts'

describe('matchResultOptions', () => {
    it('zeigt bei RaceClocker alle Wege — der xlsx-Upload bleibt der Notausgang', () => {
        expect(matchResultOptions('RACECLOCKER')).toEqual(['form', 'XLS', 'RACECLOCKER'])
    })

    it('verbirgt bei Webscorer das Abholen aus RaceClocker', () => {
        expect(matchResultOptions('WEBSCORER')).toEqual(['form', 'XLS'])
    })

    it('lässt ohne gesetztes System alles stehen wie bisher', () => {
        // Bestehende Wettkämpfe haben kein System; ihnen darf nichts wegfallen.
        expect(matchResultOptions(undefined)).toEqual(['form', 'XLS', 'RACECLOCKER'])
    })
})
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

```bash
cd frontend && npm run test -- matchResultOptions
```

Erwartung: FAIL, `Failed to resolve import "./matchResultOptions.ts"`.

- [ ] **Step 3: Die Ableitung schreiben**

Create `frontend/src/components/event/competition/excecution/matchResultOptions.ts`:

```ts
import {TimingConfigDto} from '@api/types.gen.ts'

export const MATCH_RESULT_OPTIONS = ['form', 'XLS', 'RACECLOCKER'] as const
export type MatchResultOption = (typeof MATCH_RESULT_OPTIONS)[number]

/**
 * Welche Wege der Ergebniseingabe ein Lauf anbietet.
 *
 * Bisher waren alle drei fest verdrahtet, auch bei Wettkämpfen, die nie einen RaceClocker-Feed haben.
 * Ist Webscorer gewählt, fällt das Abholen weg. Ohne gesetztes System bleibt alles stehen — sonst
 * verlöre ein bestehender Wettkampf ohne Zutun eine Funktion.
 */
export const matchResultOptions = (
    timingSystem: TimingConfigDto['timingSystem'],
): MatchResultOption[] =>
    timingSystem === 'WEBSCORER'
        ? MATCH_RESULT_OPTIONS.filter(o => o !== 'RACECLOCKER')
        : [...MATCH_RESULT_OPTIONS]
```

- [ ] **Step 4: Test laufen lassen und Erfolg bestätigen**

```bash
cd frontend && npm run test -- matchResultOptions
```

Erwartung: 3 Tests PASS.

- [ ] **Step 5: `CompetitionExecutionRound.tsx` umstellen**

Die lokalen Zeilen 57-58 (`const MATCH_RESULT_OPTIONS = [...]` und `type MatchResultOption = ...`) löschen und stattdessen importieren:

```tsx
import {MatchResultOption, matchResultOptions} from './matchResultOptions.ts'
```

Neue Prop im `Props`-Typ ergänzen:

```tsx
    timingSystem: TimingConfigDto['timingSystem']
```

(Import: `import {TimingConfigDto} from '@api/types.gen.ts'` — die Datei importiert bereits Typen von dort.)

Im Destructuring `timingSystem` aufnehmen und das `items`-Array des Ergebnis-Menüs (Zeile 328-339 im alten Stand, `MATCH_RESULT_OPTIONS.map(...)`) auf die Ableitung umstellen:

```tsx
                                            items={matchResultOptions(timingSystem).map(
```

- [ ] **Step 6: `CompetitionExecution.tsx`: Konfiguration laden, Hinweis anzeigen, Prop durchgeben**

Imports ergänzen:

```tsx
import {getTimingConfig} from '@api/sdk.gen.ts'
import {
    mapDtoToTimingForm,
    timingConfigWarnings,
} from '@components/event/competition/timing/timingConfigForm.ts'
import InlineLink from '@components/InlineLink.tsx'
import {AlertTitle} from '@mui/material'
```

Konfiguration laden (neben den bestehenden `useFetch`-Aufrufen):

```tsx
    const {data: timingConfig} = useFetch(
        signal => getTimingConfig({signal, path: {eventId, competitionId}}),
        {deps: [eventId, competitionId]},
    )

    // Dieselbe Prüfung wie im Zeitnahme-Tab, damit beide Stellen nicht auseinanderlaufen.
    const timingWarnings = timingConfig ? timingConfigWarnings(mapDtoToTimingForm(timingConfig)) : []
```

Den Hinweis dort einsetzen, wo vorher der Konfigurations-Knopf stand (die in Task 6 entfernte `<Box sx={{my: 2}}>`):

```tsx
            {timingWarnings.length > 0 && (
                <Alert variant={'outlined'} severity={'warning'} sx={{my: 2}}>
                    <AlertTitle>
                        <Trans i18nKey={'event.competition.timing.incomplete.title'} />
                    </AlertTitle>
                    {timingWarnings.map(warning => (
                        <Typography key={warning}>
                            {t(`event.competition.timing.incomplete.${warning}`)}
                        </Typography>
                    ))}
                    <InlineLink from={competitionIndexRoute.fullPath} search={{tab: 'timing'}}>
                        <Trans i18nKey={'event.competition.timing.incomplete.link'} />
                    </InlineLink>
                </Alert>
            )}
```

Falls `Alert`, `Trans`, `Typography` oder `competitionIndexRoute` noch nicht importiert sind, ergänzen. Lässt TanStack Router `from` mit `search` an dieser Stelle nicht typprüfen, stattdessen `to={competitionIndexRoute.to}` mit `params={{eventId, competitionId}}` verwenden — der Link muss auf dieselbe Seite mit `?tab=timing` zeigen.

Die neue Prop an `CompetitionExecutionRound` übergeben:

```tsx
                        timingSystem={timingConfig?.timingSystem}
```

- [ ] **Step 7: i18n-Schlüssel ergänzen (alle drei Sprachen)**

Unter `event.competition.timing.incomplete` einen Link-Text hinzufügen:

```json
            "link": "Zu den Zeitnahme-Einstellungen"
```

- [ ] **Step 8: Bauen und testen**

```bash
cd frontend && npm run test && npm run build
```

Erwartung: alle Tests grün, Build erfolgreich.

- [ ] **Step 9: Commit**

```bash
git add -A frontend/src
git commit -m "Warn in the execution tab when the timing setup is incomplete"
```

---

## Task 9: Abschluss-Verifikation

**Files:** keine Änderungen; nur Nachweise.

- [ ] **Step 1: Volle Backend-Suite**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && cd backend && ./mvnw test
```

Erwartung: BUILD SUCCESS, inklusive der 5 neuen `StartListConfigTargetTest`-Fälle.

- [ ] **Step 2: Volle Frontend-Suite und Build**

```bash
cd frontend && npm run test && npm run build
```

Erwartung: alle Tests grün (inkl. 11 `timingConfigForm` und 3 `matchResultOptions`), Build erfolgreich.

- [ ] **Step 3: Nachweisen, dass nichts Totes zurückblieb**

```bash
grep -rn "RaceClockerConfig\|StartListConfigPicker\|UploadMatchResultRequest\|StartListFileTypeParam" backend/src frontend/src backend/src/main/resources/openapi/documentation.yaml
```

Erwartung: **keine Ausgabe.** Jeder Treffer ist ein übersehener Rest.

- [ ] **Step 4: Manueller Round-Trip RaceClocker**

Backend und Frontend starten, ein Wettkampf mit Qualifikationsrunde und mindestens einer weiteren Runde:

1. Tab „Zeitnahme": System RaceClocker, beide URLs, Preset „RaceClocker Zeitfahren" für die Qualifikation, „RaceClocker Läufe" für die übrigen Runden, ein Import-Preset. Speichern.
2. Der Warnhinweis im Durchführungs-Tab ist verschwunden.
3. Durchführung: „Startliste herunterladen → CSV" bei einem Quali-Lauf lädt **ohne Dialog**. Die Datei hat **keine** Lauf-Spalte.
4. Dasselbe bei einem Lauf einer späteren Runde: die Datei **hat** eine Lauf-Spalte.
5. Ergebnis-Menü zeigt alle drei Einträge; „aus RaceClocker holen" funktioniert wie bisher.

- [ ] **Step 5: Manueller Round-Trip Webscorer**

1. Tab „Zeitnahme" eines anderen Wettkampfs: System Webscorer. Der URL-Block ist nicht sichtbar, es gibt genau **ein** Startlisten-Preset-Feld. Preset und Import-Preset setzen, speichern.
2. CSV-Download lädt ohne Dialog mit diesem Preset — auch bei einer Qualifikationsrunde (Rückfall auf den Runden-Slot).
3. Ergebnis-Menü zeigt **kein** „aus RaceClocker holen".
4. xlsx-Upload: der Dialog fragt nur noch nach der Datei, kein Preset-Feld.

- [ ] **Step 6: Manueller Test des unkonfigurierten Falls**

Ein Wettkampf ohne Zeitnahme-Einstellungen (System „nicht gesetzt"):

1. Durchführung zeigt **keinen** Warnhinweis (nichts gewählt = nichts angemahnt).
2. CSV-Download schlägt mit der Meldung „kein Startlisten-Preset hinterlegt … Tab Zeitnahme" fehl.
3. PDF-Download funktioniert unverändert.
4. Ergebnis-Menü zeigt alle drei Einträge.

- [ ] **Step 7: Spec-Status nachziehen und committen**

In `docs/superpowers/specs/2026-08-05-zeitnahme-tab-design.md` die Kopfzeile auf `**Status:** Implementiert` ändern.

```bash
git add docs/superpowers/specs/2026-08-05-zeitnahme-tab-design.md
git commit -m "Mark the timing settings design as implemented"
```
