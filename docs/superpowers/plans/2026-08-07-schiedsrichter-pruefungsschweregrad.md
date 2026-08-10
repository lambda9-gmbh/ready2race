# Schweregrad je Prüfung im Schiedsrichter-Dashboard — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Jede Prüfung im Schiedsrichter-Dashboard bekommt einen pro Wettkampf einstellbaren Schweregrad (OK / Warning / Critical), und „auf dem Wasser" gilt nur noch für Wettkämpfe, die eine An-/Abmeldung verlangen.

**Architecture:** Eine dünn besetzte Tabelle `competition_check_severity` hält nur Abweichungen vom eingebauten Standard; der Standard ist exakt das heutige Verhalten. Das Backend löst Konfiguration + Zustand zu einem fertigen Schweregrad auf und liefert ihn im Dashboard-DTO aus — das Frontend malt nur noch. Ein neuer Verwaltungsdialog auf der Event-Seite schreibt die Abweichungen; ein Flag auf `competition_properties` steuert, ob „auf dem Wasser" für einen Wettkampf überhaupt gilt.

**Tech Stack:** Kotlin + Ktor + jOOQ + Flyway (Backend), React + TypeScript + MUI + react-hook-form-mui (Frontend), Vitest, kotlin.test. Frontend-Typen werden aus `documentation.yaml` generiert, jOOQ-Klassen aus der Build-Datenbank.

**Entwurf:** `docs/superpowers/specs/2026-08-07-schiedsrichter-pruefungsschweregrad-design.md`

## Global Constraints

- Branch: `feature/crf-2026` (dieser Worktree). Nichts nach `main`.
- Java: `JAVA_HOME=/opt/homebrew/opt/openjdk@21`, Maven über `backend/mvnw`. Ohne gesetztes `JAVA_HOME` schlägt jeder Maven-Aufruf fehl.
- jOOQ-Codegen läuft gegen die Build-Datenbank auf `localhost:7652` (Container `backend-build-db-1`, DB `ready2race-build`, User `developer`, Passwort `sql`). Der Container läuft bereits; falls nicht, muss er vor Task 1 gestartet werden.
- Generierte Dateien werden **nicht** von Hand bearbeitet: `backend/target/generated-sources/jooq/**` entsteht aus der Migration, `frontend/src/api/sdk.gen.ts` und `frontend/src/api/types.gen.ts` aus `backend/src/main/resources/openapi/documentation.yaml` per `npm run generate` in `frontend/`.
- Kommentare und Bezeichner im Code auf Deutsch oder Englisch wie in der jeweiligen Datei üblich; Umlaute in deutschen Texten immer ausgeschrieben (ä, ö, ü, ß), niemals ae/oe/ue/ss.
- Übersetzungen immer in allen drei Sprachdateien: `frontend/src/i18n/de/translations.json`, `.../en/translations.json`, `.../da/translations.json`.
- Keine Erwähnung von Claude oder KI in Commit-Nachrichten.
- Commit-Nachrichten in englischer Sprache im Imperativ, wie im Repo üblich (z.B. „Show on-water time per boat on the referee dashboard").

## Namen, die über Task-Grenzen hinweg gelten

Diese Bezeichner werden in Task 2 definiert und in fast jedem folgenden Task benutzt. Wer eine spätere Task umsetzt, ohne die frühere gelesen zu haben, findet sie hier:

**Kotlin** (`de.lambda9.ready2race.backend.app.liveDashboard.entity`):
- `enum class CheckType { INVOICE_OPEN, NOT_ON_WATER, REQUIREMENT, REQUIREMENT_TIME_WINDOW }`
- `enum class CheckSeverity { OK, WARNING, CRITICAL }` — was konfiguriert werden kann
- `enum class EffectiveSeverity { NEUTRAL, OK, WARNING, CRITICAL }` — was ausgeliefert wird; die Reihenfolge der Konstanten ist die Rangfolge
- `data class CheckSeverityKey(val competitionId: UUID, val checkType: CheckType, val requirementId: UUID?)`
- `class CheckSeverityConfig(val overrides: Map<CheckSeverityKey, CheckSeverity>)`

**Funktionen in `LiveDashboardLogic`:**
- `defaultSeverity(checkType: CheckType, optional: Boolean): CheckSeverity`
- `effectiveSeverity(fulfilled: Boolean, configured: CheckSeverity): EffectiveSeverity`
- `worstSeverity(severities: List<EffectiveSeverity>): EffectiveSeverity`
- `requirementSeverity(checked: Boolean, timeCheckStatus: TimeCheckStatus?, missingSeverity: CheckSeverity, timeWindowSeverity: CheckSeverity): EffectiveSeverity`
- `invoiceSeverity(state: LiveDashboardInvoiceState, configured: CheckSeverity): EffectiveSeverity`
- `onWaterSeverity(evaluated: Boolean, onWater: Boolean, configured: CheckSeverity): EffectiveSeverity`
- `teamSeverity(requirementSeverities: List<EffectiveSeverity>, invoice: EffectiveSeverity, onWater: EffectiveSeverity): EffectiveSeverity`

**TypeScript:** Die generierten Typen heißen wie die OpenAPI-Schemas: `CheckType`, `CheckSeverity`, `EffectiveSeverity`, `CheckSeverityConfigDto`, `CheckSeverityEntryDto`, `UpdateCheckSeverityRequest`.

---

### Task 1: Migration und jOOQ-Klassen

Legt Tabelle und Flag an und erzeugt die jOOQ-Klassen, auf die alle folgenden Backend-Tasks zugreifen. Ohne diese Task compiliert nichts, was `COMPETITION_CHECK_SEVERITY` nennt.

**Files:**
- Create: `backend/src/main/resources/db/migration/V202608071200__referee_check_severity.sql`

**Interfaces:**
- Consumes: nichts
- Produces: jOOQ-Referenzen `COMPETITION_CHECK_SEVERITY` (Spalten `COMPETITION`, `CHECK_TYPE`, `PARTICIPANT_REQUIREMENT`, `SEVERITY`, `CREATED_AT`, `CREATED_BY`, `UPDATED_AT`, `UPDATED_BY`), Record-Klasse `CompetitionCheckSeverityRecord`, sowie die neue Spalte `COMPETITION_PROPERTIES.CHECK_IN_OUT_REQUIRED`

- [ ] **Step 1: Prüfen, dass die Build-Datenbank läuft**

```bash
docker ps --format '{{.Names}} {{.Ports}}' | grep 7652
```

Erwartet: eine Zeile mit `backend-build-db-1` und `0.0.0.0:7652->5432/tcp`. Fehlt sie, den Container starten, bevor es weitergeht.

- [ ] **Step 2: Migration schreiben**

Datei `backend/src/main/resources/db/migration/V202608071200__referee_check_severity.sql`:

```sql
set search_path to ready2race, pg_catalog, public;

-- Schweregrade der Schiedsrichter-Prüfungen (Entwurf 2026-08-07).
--
-- Zwei getrennte Fragen, zwei getrennte Orte: OB eine Prüfung für einen Wettkampf gilt, ist eine
-- Eigenschaft des Rennformats und steht am Wettkampf. WIE hart sie geahndet wird, ist eine
-- Entscheidung des Renntages und steht in competition_check_severity.

-- Der Beachsprint braucht keine An-/Abmeldung aufs Wasser, die Langstrecke schon. Das Flag liegt
-- auf competition_properties und nicht auf competition, weil diese Tabelle wahlweise an einem
-- Wettkampf ODER an einer Wettkampf-Vorlage hängt: so wird es einmal in der Vorlage gesetzt statt
-- bei jeder Regatta neu. Default true erhält das bisherige Verhalten aller bestehenden Wettkämpfe.
alter table competition_properties
    add column check_in_out_required boolean not null default true;

-- Bewusst dünn besetzt: nur Abweichungen vom eingebauten Standard stehen hier. Fehlt eine Zeile,
-- gilt LiveDashboardLogic.defaultSeverity -- und der ist exakt das Verhalten vor diesem Entwurf.
-- Deshalb gibt es keinen Datenmigrations-Schritt: bestehende Regatten verhalten sich unverändert.
create table competition_check_severity
(
    competition             uuid      not null references competition on delete cascade,
    -- INVOICE_OPEN | NOT_ON_WATER | REQUIREMENT | REQUIREMENT_TIME_WINDOW
    check_type              text      not null,
    -- nur bei den beiden REQUIREMENT-Typen gesetzt
    participant_requirement uuid references participant_requirement on delete cascade,
    -- OK | WARNING | CRITICAL
    severity                text      not null,
    created_at              timestamp not null,
    created_by              uuid references app_user on delete set null,
    updated_at              timestamp not null,
    updated_by              uuid references app_user on delete set null
);

-- Zwei partielle Indizes statt eines zusammengesetzten: Postgres behandelt NULLs in einem
-- Unique-Key als verschieden, ein einzelner Index ließe für INVOICE_OPEN und NOT_ON_WATER
-- beliebig viele Duplikate zu.
create unique index uq_ccs_competition_check
    on competition_check_severity (competition, check_type)
    where participant_requirement is null;

create unique index uq_ccs_competition_check_requirement
    on competition_check_severity (competition, check_type, participant_requirement)
    where participant_requirement is not null;

create index idx_ccs_competition on competition_check_severity (competition);
```

- [ ] **Step 3: Migration anwenden und jOOQ-Klassen erzeugen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -q generate-sources
```

Erwartet: Build endet ohne Fehler. Flyway meldet die neue Migration `202608071200`.

- [ ] **Step 4: Prüfen, dass die Klassen entstanden sind**

```bash
ls backend/target/generated-sources/jooq/de/lambda9/ready2race/backend/database/generated/tables/CompetitionCheckSeverity.kt
grep -c "CHECK_IN_OUT_REQUIRED" backend/target/generated-sources/jooq/de/lambda9/ready2race/backend/database/generated/tables/CompetitionProperties.kt
```

Erwartet: Die Datei existiert, der Zähler ist größer als 0. Ist er 0, wurde die Migration nicht angewandt — Schritt 3 wiederholen und die Ausgabe lesen.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V202608071200__referee_check_severity.sql
git commit -m "Add tables for configurable referee check severities"
```

---

### Task 2: Bewertungslogik (rein, ohne Datenbank)

Die eigentliche Regel des Entwurfs, als reine Funktionen mit vollständiger Testabdeckung. Alles Weitere ruft nur noch auf, was hier entsteht.

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/entity/CheckSeverity.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/LiveDashboardLogic.kt`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/LiveDashboardLogicTest.kt`

**Interfaces:**
- Consumes: `LiveDashboardInvoiceState`, `TimeCheckStatus` aus `LiveDashboardDto.kt`
- Produces: alle unter „Namen, die über Task-Grenzen hinweg gelten" aufgeführten Enums, `CheckSeverityConfig` und die Logik-Funktionen

- [ ] **Step 1: Die Enums und den Konfigurations-Träger anlegen**

Datei `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/entity/CheckSeverity.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.liveDashboard.entity

import java.util.UUID

/**
 * Die Prüfungen, die das Schiedsrichter-Dashboard bewertet.
 *
 * [REQUIREMENT] und [REQUIREMENT_TIME_WINDOW] beziehen sich auf dieselbe Teilnahmebedingung und
 * sind trotzdem getrennt einstellbar: "gar nicht abgehakt" und "abgehakt, aber zur falschen Zeit"
 * sind am Zelt zwei verschiedene Vorgänge.
 */
enum class CheckType { INVOICE_OPEN, NOT_ON_WATER, REQUIREMENT, REQUIREMENT_TIME_WINDOW }

/** Was pro Wettkampf eingestellt werden kann. */
enum class CheckSeverity { OK, WARNING, CRITICAL }

/**
 * Was ausgeliefert wird. Die Reihenfolge der Konstanten IST die Rangfolge: [worstSeverity]
 * verlässt sich auf die natürliche Ordnung der Aufzählung.
 *
 * [NEUTRAL] heißt "hierzu gibt es nichts zu sagen" - entweder gilt die Prüfung nicht, oder sie ist
 * nicht erfüllt und ausdrücklich als [CheckSeverity.OK] eingestuft. Genau das ist der graue Kreis:
 * "unbezahlt, wird heute nicht geahndet" darf nicht aussehen wie "bezahlt".
 */
enum class EffectiveSeverity { NEUTRAL, OK, WARNING, CRITICAL }

data class CheckSeverityKey(
    val competitionId: UUID,
    val checkType: CheckType,
    /** Nur bei [CheckType.REQUIREMENT] und [CheckType.REQUIREMENT_TIME_WINDOW] gesetzt. */
    val requirementId: UUID? = null,
)

/**
 * Die abweichend eingestellten Schweregrade einer Veranstaltung. Bewusst nur die Abweichungen:
 * fehlt ein Eintrag, gilt [LiveDashboardLogic.defaultSeverity], und der entspricht dem Verhalten
 * vor dieser Einstellmöglichkeit. Ein neuer Wettkampf und eine neue Teilnahmebedingung sind damit
 * ohne einen einzigen Pflegeschritt richtig eingestellt.
 */
data class CheckSeverityConfig(val overrides: Map<CheckSeverityKey, CheckSeverity>) {

    companion object {
        val empty = CheckSeverityConfig(emptyMap())
    }
}
```

- [ ] **Step 2: Die Tests schreiben**

An `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/LiveDashboardLogicTest.kt` anhängen (innerhalb der bestehenden Klasse `LiveDashboardLogicTest`), und die neuen Importe oben ergänzen:

```kotlin
import de.lambda9.ready2race.backend.app.liveDashboard.entity.CheckSeverity
import de.lambda9.ready2race.backend.app.liveDashboard.entity.CheckSeverityConfig
import de.lambda9.ready2race.backend.app.liveDashboard.entity.CheckSeverityKey
import de.lambda9.ready2race.backend.app.liveDashboard.entity.CheckType
import de.lambda9.ready2race.backend.app.liveDashboard.entity.EffectiveSeverity
```

```kotlin
    // --- Schweregrade ---

    private val competitionA: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val competitionB: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000b1")
    private val requirementA: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000c1")

    @Test
    fun defaultsReproduceTodaysBehaviour() {
        assertEquals(CheckSeverity.CRITICAL, LiveDashboardLogic.defaultSeverity(CheckType.INVOICE_OPEN, false))
        assertEquals(CheckSeverity.CRITICAL, LiveDashboardLogic.defaultSeverity(CheckType.NOT_ON_WATER, false))
        // Pflichtbedingung rot, optionale Bedingung ohne Wirkung - wie vor der Einstellmöglichkeit
        assertEquals(CheckSeverity.CRITICAL, LiveDashboardLogic.defaultSeverity(CheckType.REQUIREMENT, false))
        assertEquals(CheckSeverity.OK, LiveDashboardLogic.defaultSeverity(CheckType.REQUIREMENT, true))
        assertEquals(
            CheckSeverity.WARNING,
            LiveDashboardLogic.defaultSeverity(CheckType.REQUIREMENT_TIME_WINDOW, false)
        )
    }

    @Test
    fun fulfilledCheckIsAlwaysOk() {
        CheckSeverity.entries.forEach { configured ->
            assertEquals(EffectiveSeverity.OK, LiveDashboardLogic.effectiveSeverity(true, configured))
        }
    }

    @Test
    fun unfulfilledCheckFollowsConfiguration() {
        // Stufe OK heißt "zählt nicht", nicht "ist in Ordnung" - deshalb NEUTRAL, nicht OK.
        assertEquals(EffectiveSeverity.NEUTRAL, LiveDashboardLogic.effectiveSeverity(false, CheckSeverity.OK))
        assertEquals(EffectiveSeverity.WARNING, LiveDashboardLogic.effectiveSeverity(false, CheckSeverity.WARNING))
        assertEquals(EffectiveSeverity.CRITICAL, LiveDashboardLogic.effectiveSeverity(false, CheckSeverity.CRITICAL))
    }

    @Test
    fun worstSeverityTakesTheHighestRankAndNeutralWhenEmpty() {
        assertEquals(EffectiveSeverity.NEUTRAL, LiveDashboardLogic.worstSeverity(emptyList()))
        assertEquals(
            EffectiveSeverity.CRITICAL,
            LiveDashboardLogic.worstSeverity(
                listOf(EffectiveSeverity.OK, EffectiveSeverity.CRITICAL, EffectiveSeverity.WARNING)
            )
        )
        assertEquals(
            EffectiveSeverity.OK,
            LiveDashboardLogic.worstSeverity(listOf(EffectiveSeverity.NEUTRAL, EffectiveSeverity.OK))
        )
    }

    @Test
    fun requirementSeverityCombinesMissingAndTimeWindow() {
        // abgehakt, im Fenster
        assertEquals(
            EffectiveSeverity.OK,
            LiveDashboardLogic.requirementSeverity(
                true, TimeCheckStatus.OK, CheckSeverity.CRITICAL, CheckSeverity.WARNING
            )
        )
        // abgehakt, zu spät -> das Zeitfenster entscheidet
        assertEquals(
            EffectiveSeverity.WARNING,
            LiveDashboardLogic.requirementSeverity(
                true, TimeCheckStatus.LATE, CheckSeverity.CRITICAL, CheckSeverity.WARNING
            )
        )
        // nicht abgehakt -> das Zeitfenster ist bedeutungslos
        assertEquals(
            EffectiveSeverity.CRITICAL,
            LiveDashboardLogic.requirementSeverity(
                false, TimeCheckStatus.NOT_CHECKED, CheckSeverity.CRITICAL, CheckSeverity.WARNING
            )
        )
        // kein Zeitfenster konfiguriert
        assertEquals(
            EffectiveSeverity.NEUTRAL,
            LiveDashboardLogic.requirementSeverity(false, null, CheckSeverity.OK, CheckSeverity.WARNING)
        )
    }

    @Test
    fun invoiceSeverityDistinguishesNoInvoiceFromPaid() {
        // Ohne Rechnung gibt es nichts zu bewerten
        assertEquals(
            EffectiveSeverity.NEUTRAL,
            LiveDashboardLogic.invoiceSeverity(LiveDashboardInvoiceState.NONE, CheckSeverity.CRITICAL)
        )
        assertEquals(
            EffectiveSeverity.OK,
            LiveDashboardLogic.invoiceSeverity(LiveDashboardInvoiceState.PAID, CheckSeverity.CRITICAL)
        )
        assertEquals(
            EffectiveSeverity.CRITICAL,
            LiveDashboardLogic.invoiceSeverity(LiveDashboardInvoiceState.OPEN, CheckSeverity.CRITICAL)
        )
        // Der Gnaden-Fall: offene Rechnung wird heute nicht geahndet
        assertEquals(
            EffectiveSeverity.NEUTRAL,
            LiveDashboardLogic.invoiceSeverity(LiveDashboardInvoiceState.OPEN, CheckSeverity.OK)
        )
    }

    @Test
    fun onWaterIsOnlyJudgedWhenItApplies() {
        // Wettkampf ohne An-/Abmeldung oder Lauf nicht aktiv: keine Aussage
        assertEquals(
            EffectiveSeverity.NEUTRAL,
            LiveDashboardLogic.onWaterSeverity(evaluated = false, onWater = false, configured = CheckSeverity.CRITICAL)
        )
        assertEquals(
            EffectiveSeverity.CRITICAL,
            LiveDashboardLogic.onWaterSeverity(evaluated = true, onWater = false, configured = CheckSeverity.CRITICAL)
        )
        assertEquals(
            EffectiveSeverity.OK,
            LiveDashboardLogic.onWaterSeverity(evaluated = true, onWater = true, configured = CheckSeverity.CRITICAL)
        )
    }

    @Test
    fun teamSeverityIsTheWorstOfItsChecks() {
        assertEquals(
            EffectiveSeverity.CRITICAL,
            LiveDashboardLogic.teamSeverity(
                requirementSeverities = listOf(EffectiveSeverity.OK),
                invoice = EffectiveSeverity.CRITICAL,
                onWater = EffectiveSeverity.NEUTRAL,
            )
        )
        // Mannschaft ohne jede Prüfung bleibt grau
        assertEquals(
            EffectiveSeverity.NEUTRAL,
            LiveDashboardLogic.teamSeverity(emptyList(), EffectiveSeverity.NEUTRAL, EffectiveSeverity.NEUTRAL)
        )
    }

    @Test
    fun configuredValueBeatsDefaultAndStaysWithinItsCompetition() {
        val config = CheckSeverityConfig(
            mapOf(CheckSeverityKey(competitionA, CheckType.INVOICE_OPEN) to CheckSeverity.WARNING)
        )

        assertEquals(
            CheckSeverity.WARNING,
            config.severityFor(competitionA, CheckType.INVOICE_OPEN, optional = false)
        )
        // Ein anderer Wettkampf bleibt beim Standard
        assertEquals(
            CheckSeverity.CRITICAL,
            config.severityFor(competitionB, CheckType.INVOICE_OPEN, optional = false)
        )
        // Fehlender Eintrag -> Standard
        assertEquals(
            CheckSeverity.CRITICAL,
            config.severityFor(competitionA, CheckType.REQUIREMENT, requirementA, optional = false)
        )
    }
```

- [ ] **Step 3: Tests laufen lassen und scheitern sehen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -q test -Dtest=LiveDashboardLogicTest
```

Erwartet: Compile-Fehler „unresolved reference: defaultSeverity" (und die übrigen neuen Funktionen). Das ist das gewünschte Scheitern.

- [ ] **Step 4: Die Logik implementieren**

In `LiveDashboardLogic.kt` die Importe um die neuen Entitäten ergänzen und diese Funktionen hinzufügen:

```kotlin
    /**
     * Der eingebaute Standard, wenn für einen Wettkampf nichts eingestellt ist. Er ist mit Absicht
     * genau das Verhalten vor dieser Einstellmöglichkeit: dadurch braucht die Migration keinen
     * Datenschritt, und ein neu angelegter Wettkampf ist ohne Pflege richtig eingestellt.
     */
    fun defaultSeverity(checkType: CheckType, optional: Boolean): CheckSeverity = when (checkType) {
        CheckType.INVOICE_OPEN -> CheckSeverity.CRITICAL
        CheckType.NOT_ON_WATER -> CheckSeverity.CRITICAL
        CheckType.REQUIREMENT -> if (optional) CheckSeverity.OK else CheckSeverity.CRITICAL
        CheckType.REQUIREMENT_TIME_WINDOW -> CheckSeverity.WARNING
    }

    /**
     * Eine erfüllte Prüfung ist immer [EffectiveSeverity.OK] - der Schweregrad beschreibt nur, was
     * ihr Fehlen bedeutet. Die Stufe [CheckSeverity.OK] wird dabei zu [EffectiveSeverity.NEUTRAL]
     * und nicht zu OK: sonst sähe "offen, wird heute nicht geahndet" aus wie "bezahlt".
     */
    fun effectiveSeverity(fulfilled: Boolean, configured: CheckSeverity): EffectiveSeverity =
        if (fulfilled) {
            EffectiveSeverity.OK
        } else when (configured) {
            CheckSeverity.OK -> EffectiveSeverity.NEUTRAL
            CheckSeverity.WARNING -> EffectiveSeverity.WARNING
            CheckSeverity.CRITICAL -> EffectiveSeverity.CRITICAL
        }

    /** Nutzt die natürliche Ordnung von [EffectiveSeverity]; leer heißt "nichts zu sagen". */
    fun worstSeverity(severities: List<EffectiveSeverity>): EffectiveSeverity =
        severities.maxOrNull() ?: EffectiveSeverity.NEUTRAL

    /**
     * Eine Teilnahmebedingung trägt zwei Prüfungen: ob sie abgehakt ist und ob das rechtzeitig
     * geschah. Die Anzeige hat aber nur ein Symbol je Bedingung - also gilt die schlechtere.
     * Ist sie nicht abgehakt, sagt das Zeitfenster ohnehin nichts.
     */
    fun requirementSeverity(
        checked: Boolean,
        timeCheckStatus: TimeCheckStatus?,
        missingSeverity: CheckSeverity,
        timeWindowSeverity: CheckSeverity,
    ): EffectiveSeverity {
        val missing = effectiveSeverity(checked, missingSeverity)
        val window = if (
            timeCheckStatus == TimeCheckStatus.LATE || timeCheckStatus == TimeCheckStatus.TOO_EARLY
        ) {
            effectiveSeverity(false, timeWindowSeverity)
        } else {
            EffectiveSeverity.NEUTRAL
        }
        return worstSeverity(listOf(missing, window))
    }

    /**
     * [LiveDashboardInvoiceState.NONE] heißt "es gibt keine Rechnung" und ist deshalb keine
     * erfüllte Prüfung, sondern gar keine - sonst würde ein Boot ohne Rechnung grün leuchten.
     */
    fun invoiceSeverity(state: LiveDashboardInvoiceState, configured: CheckSeverity): EffectiveSeverity =
        when (state) {
            LiveDashboardInvoiceState.NONE -> EffectiveSeverity.NEUTRAL
            LiveDashboardInvoiceState.PAID -> EffectiveSeverity.OK
            LiveDashboardInvoiceState.OPEN -> effectiveSeverity(false, configured)
        }

    /**
     * [evaluated] fasst zusammen, wann "auf dem Wasser" überhaupt eine Aussage ist: der Lauf ist
     * aktiv, der Wettkampf verlangt eine An-/Abmeldung und die Mannschaft ist nicht abgemeldet.
     * Beim Beachsprint ist das nie der Fall - dort gibt es kein Auschecken am Steg.
     */
    fun onWaterSeverity(evaluated: Boolean, onWater: Boolean, configured: CheckSeverity): EffectiveSeverity =
        if (!evaluated) EffectiveSeverity.NEUTRAL else effectiveSeverity(onWater, configured)

    fun teamSeverity(
        requirementSeverities: List<EffectiveSeverity>,
        invoice: EffectiveSeverity,
        onWater: EffectiveSeverity,
    ): EffectiveSeverity = worstSeverity(requirementSeverities + invoice + onWater)
```

Und in `CheckSeverity.kt` die Auflösung an `CheckSeverityConfig` ergänzen:

```kotlin
    /**
     * Der eingestellte Schweregrad, sonst der Standard. [optional] wirkt nur auf
     * [CheckType.REQUIREMENT] und stammt aus `participant_requirement.optional`.
     */
    fun severityFor(
        competitionId: UUID,
        checkType: CheckType,
        requirementId: UUID? = null,
        optional: Boolean = false,
    ): CheckSeverity =
        overrides[CheckSeverityKey(competitionId, checkType, requirementId)]
            ?: LiveDashboardLogic.defaultSeverity(checkType, optional)
```

(Import `de.lambda9.ready2race.backend.app.liveDashboard.boundary.LiveDashboardLogic` in `CheckSeverity.kt` ergänzen.)

- [ ] **Step 5: Tests laufen lassen und bestehen sehen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -q test -Dtest=LiveDashboardLogicTest
```

Erwartet: alle Tests grün, keine Fehler.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/entity/CheckSeverity.kt \
        backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/LiveDashboardLogic.kt \
        backend/src/test/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/LiveDashboardLogicTest.kt
git commit -m "Add severity resolution for referee dashboard checks"
```

---

### Task 3: Wettkampf-Flag „An-/Abmeldung erforderlich"

Das Flag durchreichen: Backend-DTO und -Request, OpenAPI, Wettkampf-Formular. Danach ist es im Formular setzbar, wirkt aber noch nirgends — das kommt in Task 4 und 8.

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionProperties/entity/CompetitionPropertiesDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionProperties/entity/CompetitionPropertiesRequest.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionProperties/entity/CompetitionPropertiesContainingReference.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionProperties/control/Conversions.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionProperties/control/CompetitionPropertiesRepo.kt`
- Modify: `backend/src/main/resources/openapi/documentation.yaml` (Schema `CompetitionPropertiesDto` ab Zeile 9112 und das zugehörige Request-Schema)
- Modify: `frontend/src/components/event/competition/common.ts`
- Modify: `frontend/src/components/event/competition/CompetitionPropertiesFormInputs.tsx`
- Modify: `frontend/src/i18n/{de,en,da}/translations.json`

**Interfaces:**
- Consumes: Spalte `COMPETITION_PROPERTIES.CHECK_IN_OUT_REQUIRED` aus Task 1
- Produces: Feld `checkInOutRequired: Boolean` in `CompetitionPropertiesDto` und im Request; Formularfeld `checkInOutRequired: boolean` in `CompetitionForm`

- [ ] **Step 1: Die betroffenen Stellen auflisten**

```bash
grep -rn "shortName" backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionProperties/
```

`shortName` ist ein Feld derselben Art und markiert in jeder betroffenen Datei genau die Zeile, hinter der `checkInOutRequired` einzufügen ist. Erwartet: Treffer in `CompetitionPropertiesDto.kt`, `CompetitionPropertiesRequest.kt`, `CompetitionPropertiesContainingReference.kt`, `Conversions.kt`, `CompetitionPropertiesRepo.kt`.

- [ ] **Step 2: Feld im Backend ergänzen**

In jeder aus Schritt 1 gefundenen Datei neben `shortName` ergänzen:

```kotlin
    val checkInOutRequired: Boolean,
```

In den Conversions (Record → Dto und Request → Record) entsprechend:

```kotlin
                checkInOutRequired = checkInOutRequired,
```

Im Request-Dto mit Vorbelegung, damit bestehende Aufrufer nicht brechen:

```kotlin
    /** Ob dieser Wettkampf eine An-/Abmeldung aufs Wasser verlangt (Beachsprint: nein). */
    val checkInOutRequired: Boolean = true,
```

- [ ] **Step 3: Backend compilieren**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -q compile
```

Erwartet: kein Fehler. Meldet der Compiler eine fehlende Zuweisung in einer weiteren Conversion-Datei, dort dasselbe Feld ergänzen und erneut übersetzen.

- [ ] **Step 4: OpenAPI ergänzen**

In `backend/src/main/resources/openapi/documentation.yaml` im Schema `CompetitionPropertiesDto` (ab Zeile 9112) und im zugehörigen Request-Schema jeweils unter `properties` einfügen und in `required` aufnehmen:

```yaml
        checkInOutRequired:
          type: boolean
          description: "Whether boats of this competition check in and out at the pontoon. Off for formats without it (e.g. beach sprint) - the referee dashboard then does not judge 'on the water' at all."
```

- [ ] **Step 5: Frontend-Typen erzeugen**

```bash
cd frontend && npm run generate
grep -c "checkInOutRequired" src/api/types.gen.ts
```

Erwartet: Zähler größer als 0.

- [ ] **Step 6: Formularfeld ergänzen**

In `frontend/src/components/event/competition/common.ts`:
- in `CompetitionForm` (ab Zeile 9) neben `shortName`: `checkInOutRequired: boolean`
- in `competitionFormDefaultValues` (ab Zeile 36): `checkInOutRequired: true,`
- in `mapCompetitionFormToCompetitionPropertiesRequest` (ab Zeile 52): `checkInOutRequired: formData.checkInOutRequired,`
- in `mapCompetitionPropertiesToCompetitionForm` (ab Zeile 88): `checkInOutRequired: dto.checkInOutRequired,`

In `frontend/src/components/event/competition/CompetitionPropertiesFormInputs.tsx` nach dem Kurznamen-Feld:

```tsx
            <FormInputSwitch
                name={'checkInOutRequired'}
                label={t('event.competition.checkInOutRequired')}
            />
```

`FormInputSwitch` ist dort bereits importiert.

- [ ] **Step 7: Übersetzungen ergänzen**

Unter `event.competition` in allen drei Dateien:

```
de: "checkInOutRequired": "An-/Abmeldung aufs Wasser erforderlich"
en: "checkInOutRequired": "Requires check-in/out on the water"
da: "checkInOutRequired": "Kræver ind-/udtjekning på vandet"
```

- [ ] **Step 8: Frontend übersetzen und prüfen**

```bash
cd frontend && npx tsc -b && npm run lint
```

Erwartet: beide ohne Fehler.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/competitionProperties backend/src/main/resources/openapi/documentation.yaml frontend/src
git commit -m "Add per-competition flag for required check-in/out"
```

---

### Task 4: Schweregrade im Dashboard-Backend

Konfiguration lesen, auflösen, ausliefern. Danach enthält die Poll-Antwort fertige Schweregrade.

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/control/CheckSeverityRepo.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/entity/LiveDashboardDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/control/LiveDashboardRepo.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/LiveDashboardService.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/LiveDashboardLogic.kt` (`summarizeRequirements` entfernen)
- Modify: `backend/src/main/resources/openapi/documentation.yaml`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/LiveDashboardLogicTest.kt` (Tests zu `summarizeRequirements` entfernen)

**Interfaces:**
- Consumes: alles aus Task 2, `COMPETITION_CHECK_SEVERITY` aus Task 1, `CHECK_IN_OUT_REQUIRED` aus Task 3
- Produces: `LiveDashboardRequirementStatusDto.severity: EffectiveSeverity`, `LiveDashboardTeamDto.severity: EffectiveSeverity`, `LiveDashboardTeamDto.onWaterRequired: Boolean`; `LiveDashboardRequirementSummaryDto` existiert nicht mehr

- [ ] **Step 1: Repo für die Konfiguration**

Datei `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/control/CheckSeverityRepo.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.liveDashboard.control

import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_CHECK_SEVERITY
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object CheckSeverityRepo {

    /** Alle abweichenden Schweregrade der Wettkämpfe einer Veranstaltung. */
    fun getByEvent(eventId: UUID) = Jooq.query {
        select(
            COMPETITION_CHECK_SEVERITY.COMPETITION,
            COMPETITION_CHECK_SEVERITY.CHECK_TYPE,
            COMPETITION_CHECK_SEVERITY.PARTICIPANT_REQUIREMENT,
            COMPETITION_CHECK_SEVERITY.SEVERITY,
        )
            .from(COMPETITION_CHECK_SEVERITY)
            .join(COMPETITION).on(COMPETITION_CHECK_SEVERITY.COMPETITION.eq(COMPETITION.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .fetch()
    }
}
```

- [ ] **Step 2: Aus den Zeilen die Konfiguration bauen — Test zuerst**

An `LiveDashboardLogicTest` anhängen:

```kotlin
    @Test
    fun unknownCheckTypesAreIgnoredInsteadOfCrashing() {
        // Eine Zeile aus einer neueren Version darf die Anzeige nicht lahmlegen.
        val config = LiveDashboardLogic.buildCheckSeverityConfig(
            listOf(
                Triple(competitionA, "INVOICE_OPEN" to null, "WARNING"),
                Triple(competitionA, "SOMETHING_NEW" to null, "CRITICAL"),
                Triple(competitionA, "REQUIREMENT" to requirementA, "NOT_A_SEVERITY"),
            )
        )

        assertEquals(1, config.overrides.size)
        assertEquals(CheckSeverity.WARNING, config.severityFor(competitionA, CheckType.INVOICE_OPEN))
    }
```

- [ ] **Step 3: Test laufen lassen und scheitern sehen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -q test -Dtest=LiveDashboardLogicTest
```

Erwartet: „unresolved reference: buildCheckSeverityConfig".

- [ ] **Step 4: Die Umwandlung implementieren**

In `LiveDashboardLogic`:

```kotlin
    /**
     * Baut die Konfiguration aus den Datenbankzeilen. Unbekannte Werte werden übergangen statt zu
     * scheitern: die Anzeige am Steg darf nicht ausfallen, weil eine Zeile aus einer neueren
     * Version in der Tabelle steht. Ohne Eintrag greift ohnehin der Standard.
     *
     * [rows] je Zeile: Wettkampf, (Prüfungsart, Bedingung), Schweregrad - alles als Rohwerte.
     */
    fun buildCheckSeverityConfig(
        rows: List<Triple<UUID, Pair<String, UUID?>, String>>,
    ): CheckSeverityConfig = CheckSeverityConfig(
        rows.mapNotNull { (competitionId, check, severity) ->
            val (typeName, requirementId) = check
            val type = CheckType.entries.firstOrNull { it.name == typeName } ?: return@mapNotNull null
            val value = CheckSeverity.entries.firstOrNull { it.name == severity } ?: return@mapNotNull null
            CheckSeverityKey(competitionId, type, requirementId) to value
        }.toMap()
    )
```

- [ ] **Step 5: Test laufen lassen und bestehen sehen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -q test -Dtest=LiveDashboardLogicTest
```

Erwartet: grün.

- [ ] **Step 6: DTOs anpassen**

In `LiveDashboardDto.kt`:
- `LiveDashboardRequirementStatusDto` um `val severity: EffectiveSeverity,` erweitern
- `LiveDashboardTeamDto`: `requirements: LiveDashboardRequirementSummaryDto` ersetzen durch

```kotlin
    /** Fertige Ampel der Zeile - die Bewertungsregeln liegen im Backend, siehe [LiveDashboardLogic]. */
    val severity: EffectiveSeverity,
    /**
     * Die Rechnung getrennt bewertet: der Detail-Dialog färbt seinen Rechnungs-Chip danach ein.
     * Aus [severity] ließe sich das nicht zurückrechnen - dort ist sie mit allem anderen verrechnet.
     */
    val invoiceSeverity: EffectiveSeverity,
    /** Ob dieser Wettkampf überhaupt eine An-/Abmeldung verlangt; steuert die Anzeige von [onWaterAt]. */
    val onWaterRequired: Boolean,
```

- `LiveDashboardRequirementSummaryDto` löschen, ebenso `LiveDashboardLogic.summarizeRequirements` samt dessen Tests in `LiveDashboardLogicTest`.

- [ ] **Step 7: Repo um Wettkampf und Flag erweitern**

In `LiveDashboardRepo.getTeams` der `select(...)`-Liste hinzufügen (der Join auf `COMPETITION` und `COMPETITION_PROPERTIES` besteht bereits):

```kotlin
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_PROPERTIES.CHECK_IN_OUT_REQUIRED,
```

- [ ] **Step 8: Service verdrahten**

In `LiveDashboardService.getLiveDashboard` nach den übrigen Reads:

```kotlin
            val severityConfig = LiveDashboardLogic.buildCheckSeverityConfig(
                !CheckSeverityRepo.getByEvent(eventId).orDie().map { rows ->
                    rows.map {
                        Triple(
                            it[COMPETITION_CHECK_SEVERITY.COMPETITION]!!,
                            it[COMPETITION_CHECK_SEVERITY.CHECK_TYPE]!! to
                                it[COMPETITION_CHECK_SEVERITY.PARTICIPANT_REQUIREMENT],
                            it[COMPETITION_CHECK_SEVERITY.SEVERITY]!!,
                        )
                    }
                }
            )
```

`ParticipantContext` um `severityConfig: CheckSeverityConfig` und `competitionId: UUID` erweitern — Letzteres kommt je Mannschaft aus `first.get("competition_id", UUID::class.java)!!`, also als Parameter von `buildParticipants` statt als Feld des Kontexts.

In `buildParticipants` beim Bau von `LiveDashboardRequirementStatusDto`:

```kotlin
                            severity = LiveDashboardLogic.requirementSeverity(
                                checked = check != null,
                                timeCheckStatus = timeCheck?.status,
                                missingSeverity = context.severityConfig.severityFor(
                                    competitionId, CheckType.REQUIREMENT, requirementId, optional
                                ),
                                timeWindowSeverity = context.severityConfig.severityFor(
                                    competitionId, CheckType.REQUIREMENT_TIME_WINDOW, requirementId
                                ),
                            ),
```

(`timeCheck` und `optional` vorher in lokale Werte ziehen, damit sie zweimal nutzbar sind.)

In `buildTeamDto` statt `requirements = ...summarizeRequirements(...)`:

```kotlin
                        onWaterRequired = checkInOutRequired,
                        invoiceSeverity = invoiceSeverity,
                        severity = LiveDashboardLogic.teamSeverity(
                            requirementSeverities = participants.flatMap { it.requirements }.map { it.severity },
                            invoice = invoiceSeverity,
                            onWater = LiveDashboardLogic.onWaterSeverity(
                                // Nur bei aktivem Lauf eine Aussage: vorher gehört das Boot noch an den Steg.
                                evaluated = matchRunning && checkInOutRequired && !deregistered,
                                onWater = onWaterAt != null,
                                configured = severityConfig.severityFor(competitionId, CheckType.NOT_ON_WATER),
                            ),
                        ),
```

mit vorher, im selben Rumpf:

```kotlin
                val invoiceSeverity = LiveDashboardLogic.invoiceSeverity(
                    invoiceState,
                    severityConfig.severityFor(competitionId, CheckType.INVOICE_OPEN),
                )
```

`checkInOutRequired` kommt aus `first[COMPETITION_PROPERTIES.CHECK_IN_OUT_REQUIRED] == true`, `matchRunning` wird von `buildMatchDto` als zusätzlicher Parameter an `buildTeamDto` durchgereicht (dort liegt `running` bereits vor). `invoiceState`, `deregistered` und `onWaterAt` vorher in lokale Werte ziehen.

In `getTeamDetail` dieselbe Konfiguration laden und an `ParticipantContext` übergeben; die Wettkampf-ID stammt dort aus der ersten Zeile von `teamRecords`.

- [ ] **Step 9: Backend übersetzen und alle Tests laufen lassen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -q test
```

Erwartet: alle Tests grün. Fehler wegen `summarizeRequirements` in weiteren Tests: diese Tests entfernen, die Regel gibt es nicht mehr.

- [ ] **Step 10: OpenAPI nachziehen**

In `documentation.yaml`:
- Neue Schemas `CheckSeverity` (`enum: [OK, WARNING, CRITICAL]`) und `EffectiveSeverity` (`enum: [NEUTRAL, OK, WARNING, CRITICAL]`) mit Beschreibung.
- `LiveDashboardRequirementStatusDto` (ab Zeile 12752): `severity` als `$ref` auf `EffectiveSeverity`, in `required`.
- `LiveDashboardTeamDto` (ab Zeile 12823): `requirements` entfernen (auch aus `required`), `severity` und `invoiceSeverity` (beide `$ref` `EffectiveSeverity`, required) und `onWaterRequired` (`type: boolean`, required) ergänzen.
- `LiveDashboardRequirementSummaryDto` (ab Zeile 12882) löschen.

```bash
cd frontend && npm run generate
grep -c "LiveDashboardRequirementSummaryDto" src/api/types.gen.ts
```

Erwartet: Zähler ist 0.

- [ ] **Step 11: Commit**

```bash
git add backend/src frontend/src/api
git commit -m "Resolve referee check severities in the live dashboard backend"
```

---

### Task 5: Schweregrade im Dashboard-Frontend

Das Frontend hört auf, selbst zu bewerten, und zeigt zusätzlich „auf dem Wasser" als eigene Prüfung im Detail-Dialog.

**Files:**
- Modify: `frontend/src/components/event/liveDashboard/common.ts`
- Modify: `frontend/src/components/event/liveDashboard/LiveDashboardMatchCard.tsx`
- Modify: `frontend/src/components/event/liveDashboard/LiveDashboardTeamDialog.tsx`
- Modify: `frontend/src/i18n/{de,en,da}/translations.json`
- Test: `frontend/src/components/event/liveDashboard/common.test.ts`

**Interfaces:**
- Consumes: `EffectiveSeverity`, `team.severity`, `team.onWaterRequired`, `requirement.severity` aus Task 4
- Produces: `severityIconFor(severity: EffectiveSeverity)` als gemeinsame Icon-Zuordnung; `requirementSeverity`, `teamSeverity`, `participantSeverity` und `worstSeverity` gibt es im Frontend nicht mehr

- [ ] **Step 1: Die Tests der entfallenden Funktionen entfernen**

In `frontend/src/components/event/liveDashboard/common.test.ts` alle Testfälle zu `requirementSeverity`, `participantSeverity`, `teamSeverity` und `worstSeverity` löschen. Die Regeln liegen jetzt in `LiveDashboardLogicTest` — sie an zwei Stellen zu prüfen hieße, sie an zwei Stellen zu pflegen.

- [ ] **Step 2: Test laufen lassen**

```bash
cd frontend && npx vitest run src/components/event/liveDashboard/common.test.ts
```

Erwartet: die verbleibenden Tests laufen grün (Import-Fehler auf gelöschte Funktionen zeigen, dass noch Testfälle übersehen wurden).

- [ ] **Step 3: `common.ts` aufräumen**

`Severity`, `rank`, `worstSeverity`, `requirementSeverity`, `participantSeverity` und `teamSeverity` entfernen. `severityChipColor` auf den generierten Typ umstellen:

```ts
import {EffectiveSeverity} from '@api/types.gen.ts'

export const severityChipColor: Record<
    EffectiveSeverity,
    'success' | 'warning' | 'error' | 'default'
> = {
    OK: 'success',
    WARNING: 'warning',
    CRITICAL: 'error',
    NEUTRAL: 'default',
}
```

- [ ] **Step 4: Icon-Zuordnung an einer Stelle bündeln**

Bisher steht dieselbe `switch`-Anweisung in `LiveDashboardMatchCard.tsx` und `LiveDashboardTeamDialog.tsx` — jetzt, wo beide denselben Typ verwenden, in eine neue Datei `frontend/src/components/event/liveDashboard/SeverityIcon.tsx`:

```tsx
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import CancelIcon from '@mui/icons-material/Cancel'
import WarningAmberIcon from '@mui/icons-material/WarningAmber'
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked'
import {EffectiveSeverity} from '@api/types.gen.ts'

/**
 * Draußen zählt Kontrast: die dunklen Palette-Varianten bleiben auch bei Sonne lesbar, während die
 * konfigurierten main-Töne verblassen.
 */
const SeverityIcon = ({severity, size = 28}: {severity: EffectiveSeverity; size?: number}) => {
    const sx = {fontSize: size, display: 'block'}
    switch (severity) {
        case 'OK':
            return <CheckCircleIcon sx={{...sx, color: 'success.dark'}} />
        case 'WARNING':
            return <WarningAmberIcon sx={{...sx, color: 'warning.dark'}} />
        case 'CRITICAL':
            return <CancelIcon sx={{...sx, color: 'error.dark'}} />
        case 'NEUTRAL':
            return <RadioButtonUncheckedIcon sx={{...sx, color: 'text.disabled'}} />
    }
}

export default SeverityIcon
```

- [ ] **Step 5: Karte umstellen**

In `LiveDashboardMatchCard.tsx` die lokale `severityIcon`-Funktion löschen, `SeverityIcon` importieren und die Zeile 331 ersetzen:

```tsx
                            <SeverityIcon severity={team.severity} />
```

Der Kommentar darüber („Auf dem Wasser zählt nur bei aktivem Lauf in die Ampel") wandert mit der Regel ins Backend und entfällt hier. `teamSeverity` aus dem Import in Zeile 11-19 entfernen.

Die Anzeige der Ablegezeit (Zeile 257) nur noch zeigen, wenn sie für den Wettkampf überhaupt vorgesehen ist:

```tsx
                                {team.onWaterRequired && team.onWaterAt && (
```

- [ ] **Step 6: Dialog umstellen**

In `LiveDashboardTeamDialog.tsx`:
- lokale `severityIcon`-Funktion löschen, `SeverityIcon` importieren, in der Bedingungsliste `<SeverityIcon severity={r.severity} size={24} />` verwenden
- `requirementSeverity` aus dem Import in Zeile 28 entfernen, `severityChipColor` behalten
- den Rechnungs-Chip nach Schweregrad einfärben statt fest (das feste rot/grün stammt aus der Zeit, als „offen" immer ein Fehler war):

```tsx
                        <Chip
                            size="small"
                            label={t(`event.liveDashboard.invoice.${team.invoiceState}`)}
                            color={severityChipColor[team.invoiceSeverity]}
                        />
```

- „auf dem Wasser" als eigenen Chip neben dem Rechnungs-Chip, nur wenn der Wettkampf ihn verlangt:

```tsx
                        {team.onWaterRequired && (
                            <Chip
                                size="small"
                                color={team.onWaterAt ? 'success' : 'default'}
                                label={
                                    team.onWaterAt
                                        ? t('event.liveDashboard.team.onWaterAt', {
                                              time: format(new Date(team.onWaterAt), t('format.time')),
                                          })
                                        : t('event.liveDashboard.team.notOnWater')
                                }
                            />
                        )}
```

- [ ] **Step 7: Übersetzung ergänzen**

Unter `event.liveDashboard.team` in allen drei Dateien:

```
de: "notOnWater": "Nicht auf dem Wasser"
en: "notOnWater": "Not on the water"
da: "notOnWater": "Ikke på vandet"
```

- [ ] **Step 8: Übersetzen, prüfen, testen**

```bash
cd frontend && npx tsc -b && npm run lint && npm test
```

Erwartet: alle drei ohne Fehler.

- [ ] **Step 9: Commit**

```bash
git add frontend/src backend/src/main/resources/openapi/documentation.yaml backend/src/main/kotlin
git commit -m "Render referee check severities from the backend"
```

---

### Task 6: Verwaltungs-Schnittstelle

Lesen und Schreiben der Konfiguration. Danach lässt sich alles per HTTP einstellen, nur noch ohne Oberfläche.

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/entity/CheckSeverityConfigDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/control/CheckSeverityRepo.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/LiveDashboardService.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/liveDashboard/boundary/liveDashboard.kt`
- Modify: `backend/src/main/resources/openapi/documentation.yaml`

**Interfaces:**
- Consumes: `CheckSeverityRepo.getByEvent`, die Enums aus Task 2
- Produces: `GET`/`PUT /event/{eventId}/checkSeverity`; generierte Frontend-Funktionen `getCheckSeverityConfig` und `updateCheckSeverityConfig`

- [ ] **Step 1: DTOs anlegen**

Datei `CheckSeverityConfigDto.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.liveDashboard.entity

import java.util.UUID

/** Ein Wettkampf, wie ihn die Verwaltung braucht - Kennung, Name und ob er eine An-/Abmeldung verlangt. */
data class CheckSeverityCompetitionDto(
    val competitionId: UUID,
    val identifier: String,
    val name: String,
    val checkInOutRequired: Boolean,
)

/**
 * Eine Zeile der Verwaltungs-Matrix. [requirementId] ist nur bei den Bedingungs-Prüfungen gesetzt,
 * [name] trägt bei ihnen den Namen der Bedingung - die beiden festen Prüfungen benennt die
 * Oberfläche selbst.
 */
data class CheckSeverityRowDto(
    val checkType: CheckType,
    val requirementId: UUID?,
    val name: String?,
)

data class CheckSeverityEntryDto(
    val competitionId: UUID,
    val checkType: CheckType,
    val requirementId: UUID?,
    val severity: CheckSeverity,
)

/**
 * [entries] enthält NUR Abweichungen vom Standard. Die Oberfläche zeigt für jede Kombination aus
 * [competitions] und [rows] den passenden Eintrag oder den Standard aus [defaults].
 */
data class CheckSeverityConfigDto(
    val competitions: List<CheckSeverityCompetitionDto>,
    val rows: List<CheckSeverityRowDto>,
    val defaults: List<CheckSeverityRowDefaultDto>,
    val entries: List<CheckSeverityEntryDto>,
)

data class CheckSeverityRowDefaultDto(
    val checkType: CheckType,
    val requirementId: UUID?,
    val severity: CheckSeverity,
)

data class UpdateCheckSeverityRequest(
    val entries: List<CheckSeverityEntryDto>,
)
```

- [ ] **Step 2: Repo um Schreiben und Lesen der Stammdaten erweitern**

In `CheckSeverityRepo` ergänzen:

```kotlin
    /** Wettkämpfe der Veranstaltung samt An-/Abmelde-Flag, sortiert wie in der Wettkampfliste. */
    fun getCompetitions(eventId: UUID) = Jooq.query {
        select(
            COMPETITION.ID,
            COMPETITION_PROPERTIES.IDENTIFIER,
            COMPETITION_PROPERTIES.NAME,
            COMPETITION_PROPERTIES.CHECK_IN_OUT_REQUIRED,
        )
            .from(COMPETITION)
            .join(COMPETITION_PROPERTIES).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .orderBy(COMPETITION_PROPERTIES.IDENTIFIER.asc())
            .fetch()
    }

    /**
     * Ersetzt die Abweichungen aller Wettkämpfe einer Veranstaltung in einem Zug. Standardwerte
     * kommen als Löschung an, nicht als Zeile - so bleibt die Tabelle dünn und ein später
     * geänderter Standard wirkt auch auf Bestandsdaten.
     */
    fun replaceForEvent(
        eventId: UUID,
        records: List<CompetitionCheckSeverityRecord>,
    ) = Jooq.query {
        deleteFrom(COMPETITION_CHECK_SEVERITY)
            .where(
                COMPETITION_CHECK_SEVERITY.COMPETITION.`in`(
                    select(COMPETITION.ID).from(COMPETITION).where(COMPETITION.EVENT.eq(eventId))
                )
            )
            .execute()
        batchInsert(records).execute()
    }
```

- [ ] **Step 3: Service-Funktionen**

In `LiveDashboardService` ergänzen:

```kotlin
    /**
     * Was die Verwaltung braucht: die Wettkämpfe, die einstellbaren Prüfungen samt ihren
     * Standardwerten und die bisherigen Abweichungen. Die Zeitfenster-Prüfung erscheint nur für
     * Bedingungen, für die überhaupt ein Fenster konfiguriert ist - sonst gäbe es nichts zu
     * bewerten.
     */
    fun getCheckSeverityConfig(
        eventId: UUID,
    ): App<LiveDashboardError, ApiResponse.Dto<CheckSeverityConfigDto>> = KIO.comprehension {
        val exists = !EventRepo.exists(eventId).orDie()
        if (!exists) {
            return@comprehension KIO.fail(LiveDashboardError.EventNotFound(eventId))
        }

        val competitionRecords = !CheckSeverityRepo.getCompetitions(eventId).orDie()
        val requirementRecords = !LiveDashboardRepo.getEventRequirements(eventId).orDie()
        val entryRecords = !CheckSeverityRepo.getByEvent(eventId).orDie()

        // Dieselbe Bedingung kann mehreren Rollen zugeordnet sein und taucht dann mehrfach auf -
        // eingestellt wird sie trotzdem nur einmal.
        val requirements = requirementRecords.distinctBy { it[PARTICIPANT_REQUIREMENT.ID] }

        val rows = buildList {
            add(CheckSeverityRowDto(CheckType.INVOICE_OPEN, null, null))
            add(CheckSeverityRowDto(CheckType.NOT_ON_WATER, null, null))
            requirements.forEach { req ->
                val id = req[PARTICIPANT_REQUIREMENT.ID]!!
                add(CheckSeverityRowDto(CheckType.REQUIREMENT, id, req[PARTICIPANT_REQUIREMENT.NAME]))
                val hasWindow = req[PARTICIPANT_REQUIREMENT.CHECK_EARLIEST_MINUTES_BEFORE] != null ||
                    req[PARTICIPANT_REQUIREMENT.CHECK_LATEST_MINUTES_BEFORE] != null
                if (hasWindow) {
                    add(
                        CheckSeverityRowDto(
                            CheckType.REQUIREMENT_TIME_WINDOW,
                            id,
                            req[PARTICIPANT_REQUIREMENT.NAME],
                        )
                    )
                }
            }
        }

        val optionalById = requirements.associate {
            it[PARTICIPANT_REQUIREMENT.ID]!! to (it[PARTICIPANT_REQUIREMENT.OPTIONAL] == true)
        }

        KIO.ok(
            ApiResponse.Dto(
                CheckSeverityConfigDto(
                    competitions = competitionRecords.map {
                        CheckSeverityCompetitionDto(
                            competitionId = it[COMPETITION.ID]!!,
                            identifier = it[COMPETITION_PROPERTIES.IDENTIFIER]!!,
                            name = it[COMPETITION_PROPERTIES.NAME]!!,
                            checkInOutRequired = it[COMPETITION_PROPERTIES.CHECK_IN_OUT_REQUIRED] == true,
                        )
                    },
                    rows = rows,
                    defaults = rows.map { row ->
                        CheckSeverityRowDefaultDto(
                            checkType = row.checkType,
                            requirementId = row.requirementId,
                            severity = LiveDashboardLogic.defaultSeverity(
                                row.checkType,
                                row.requirementId?.let { optionalById[it] } == true,
                            ),
                        )
                    },
                    entries = entryRecords.mapNotNull { r ->
                        val type = CheckType.entries
                            .firstOrNull { it.name == r[COMPETITION_CHECK_SEVERITY.CHECK_TYPE] }
                            ?: return@mapNotNull null
                        val severity = CheckSeverity.entries
                            .firstOrNull { it.name == r[COMPETITION_CHECK_SEVERITY.SEVERITY] }
                            ?: return@mapNotNull null
                        CheckSeverityEntryDto(
                            competitionId = r[COMPETITION_CHECK_SEVERITY.COMPETITION]!!,
                            checkType = type,
                            requirementId = r[COMPETITION_CHECK_SEVERITY.PARTICIPANT_REQUIREMENT],
                            severity = severity,
                        )
                    },
                )
            )
        )
    }

    /**
     * Ersetzt die Abweichungen der Veranstaltung. Einträge, die dem Standard entsprechen, werden
     * verworfen statt gespeichert: die Tabelle bleibt dünn, und ein später geänderter Standard
     * wirkt auch auf Bestandsdaten.
     */
    fun updateCheckSeverityConfig(
        eventId: UUID,
        request: UpdateCheckSeverityRequest,
        userId: UUID,
    ): App<LiveDashboardError, ApiResponse.NoData> = KIO.comprehension {
        val exists = !EventRepo.exists(eventId).orDie()
        if (!exists) {
            return@comprehension KIO.fail(LiveDashboardError.EventNotFound(eventId))
        }

        val optionalById = !LiveDashboardRepo.getEventRequirements(eventId).orDie().map { rows ->
            rows.associate {
                it[PARTICIPANT_REQUIREMENT.ID]!! to (it[PARTICIPANT_REQUIREMENT.OPTIONAL] == true)
            }
        }
        val competitionIds = !CheckSeverityRepo.getCompetitions(eventId).orDie()
            .map { rows -> rows.mapNotNull { it[COMPETITION.ID] }.toSet() }

        val now = LocalDateTime.now()
        val records = request.entries
            // Einträge fremder Veranstaltungen werden stillschweigend übergangen: der Dialog
            // schickt immer nur die eigenen, alles andere ist ein Fehler des Aufrufers.
            .filter { it.competitionId in competitionIds }
            .filter {
                it.severity != LiveDashboardLogic.defaultSeverity(
                    it.checkType,
                    it.requirementId?.let { id -> optionalById[id] } == true,
                )
            }
            .map {
                CompetitionCheckSeverityRecord(
                    competition = it.competitionId,
                    checkType = it.checkType.name,
                    participantRequirement = it.requirementId,
                    severity = it.severity.name,
                    createdAt = now,
                    createdBy = userId,
                    updatedAt = now,
                    updatedBy = userId,
                )
            }

        !CheckSeverityRepo.replaceForEvent(eventId, records).orDie()

        noData
    }
```

- [ ] **Step 4: Routen**

In `liveDashboard.kt` innerhalb von `route("/event/{eventId}/liveDashboard")` **nicht** ergänzen — die Verwaltung hängt nicht am Dashboard-Poll. Stattdessen im selben `fun Route.liveDashboard()` ein zweiter Block:

```kotlin
    route("/event/{eventId}/checkSeverity") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)

                LiveDashboardService.getCheckSeverityConfig(eventId)
            }
        }

        put {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val body = !receiveKIO(UpdateCheckSeverityRequest.example)

                LiveDashboardService.updateCheckSeverityConfig(eventId, body, user.id!!)
            }
        }
    }
```

Wie `receiveKIO` und das `example`-Muster im Repo aussehen, zeigt eine bestehende `put`-Route mit Rumpf:

```bash
grep -rn "receiveKIO" backend/src/main/kotlin/de/lambda9/ready2race/backend/app/event/boundary/event.kt | head -3
```

Dem dortigen Muster folgen (Validierung über `Validatable`, `example` als Companion-Wert).

- [ ] **Step 5: Backend übersetzen und starten**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -q test
```

Erwartet: grün.

- [ ] **Step 6: OpenAPI ergänzen und Frontend-Typen erzeugen**

Schemas `CheckType`, `CheckSeverityCompetitionDto`, `CheckSeverityRowDto`, `CheckSeverityRowDefaultDto`, `CheckSeverityEntryDto`, `CheckSeverityConfigDto`, `UpdateCheckSeverityRequest` sowie die beiden Pfade unter `/event/{eventId}/checkSeverity` mit `operationId: getCheckSeverityConfig` bzw. `updateCheckSeverityConfig`.

```bash
cd frontend && npm run generate
grep -c "getCheckSeverityConfig" src/api/sdk.gen.ts
```

Erwartet: Zähler größer als 0.

- [ ] **Step 7: Commit**

```bash
git add backend/src frontend/src/api
git commit -m "Add API for managing referee check severities"
```

---

### Task 7: Verwaltungsdialog

**Files:**
- Create: `frontend/src/components/event/liveDashboard/CheckSeverityDialog.tsx`
- Create: `frontend/src/components/event/liveDashboard/checkSeverity.ts`
- Create: `frontend/src/components/event/liveDashboard/checkSeverity.test.ts`
- Modify: `frontend/src/pages/event/EventPage.tsx`
- Modify: `frontend/src/i18n/{de,en,da}/translations.json`

**Interfaces:**
- Consumes: `getCheckSeverityConfig`, `updateCheckSeverityConfig`, `CheckSeverityConfigDto`, `CheckSeverityEntryDto` aus Task 6
- Produces: nichts für spätere Tasks

- [ ] **Step 1: Die reine Hilfslogik testen**

Datei `frontend/src/components/event/liveDashboard/checkSeverity.test.ts`:

```ts
import {describe, expect, test} from 'vitest'
import {rowSummary} from './checkSeverity.ts'

describe('rowSummary', () => {
    test('nennt den Wert, wenn alle Wettkämpfe ihn teilen', () => {
        expect(rowSummary(['CRITICAL', 'CRITICAL'])).toEqual({kind: 'uniform', severity: 'CRITICAL'})
    })

    test('meldet gemischt, sobald ein Wettkampf abweicht', () => {
        expect(rowSummary(['CRITICAL', 'WARNING'])).toEqual({kind: 'mixed'})
    })

    test('ohne Wettkämpfe gibt es nichts zu verdichten', () => {
        expect(rowSummary([])).toEqual({kind: 'mixed'})
    })
})
```

- [ ] **Step 2: Test laufen lassen und scheitern sehen**

```bash
cd frontend && npx vitest run src/components/event/liveDashboard/checkSeverity.test.ts
```

Erwartet: Fehler „Failed to resolve import ./checkSeverity.ts".

- [ ] **Step 3: Hilfslogik implementieren**

Datei `frontend/src/components/event/liveDashboard/checkSeverity.ts`:

```ts
import {CheckSeverity, CheckSeverityConfigDto, CheckSeverityEntryDto} from '@api/types.gen.ts'

export type RowSummary = {kind: 'uniform'; severity: CheckSeverity} | {kind: 'mixed'}

/**
 * Der verdichtete Zustand einer Zeile. Er steht eingeklappt neben dem Namen der Prüfung und
 * beantwortet die einzige Frage, die man ohne Aufklappen hat: Ist hier vom Standard abgewichen
 * worden, und wenn ja, überall gleich?
 */
export const rowSummary = (severities: CheckSeverity[]): RowSummary =>
    severities.length > 0 && severities.every(s => s === severities[0])
        ? {kind: 'uniform', severity: severities[0]}
        : {kind: 'mixed'}

/** Der eingestellte Wert eines Feldes der Matrix, sonst der vom Server gelieferte Standard. */
export const severityAt = (
    config: CheckSeverityConfigDto,
    entries: CheckSeverityEntryDto[],
    competitionId: string,
    checkType: CheckSeverityEntryDto['checkType'],
    requirementId: string | null,
): CheckSeverity =>
    entries.find(
        e =>
            e.competitionId === competitionId &&
            e.checkType === checkType &&
            (e.requirementId ?? null) === requirementId,
    )?.severity ??
    config.defaults.find(
        d => d.checkType === checkType && (d.requirementId ?? null) === requirementId,
    )?.severity ??
    'CRITICAL'
```

- [ ] **Step 4: Test laufen lassen und bestehen sehen**

```bash
cd frontend && npx vitest run src/components/event/liveDashboard/checkSeverity.test.ts
```

Erwartet: drei Tests grün.

- [ ] **Step 5: Dialog bauen**

Datei `frontend/src/components/event/liveDashboard/CheckSeverityDialog.tsx`:

```tsx
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    IconButton,
    MenuItem,
    Select,
    Stack,
    Typography,
} from '@mui/material'
import {Close} from '@mui/icons-material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import {useEffect, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getCheckSeverityConfig, updateCheckSeverityConfig} from '@api/sdk.gen.ts'
import {CheckSeverity, CheckSeverityEntryDto, CheckSeverityRowDto} from '@api/types.gen.ts'
import Throbber from '@components/Throbber.tsx'
import LoadingButton from '@components/form/LoadingButton.tsx'
import {rowSummary, severityAt} from './checkSeverity.ts'

const SEVERITIES: CheckSeverity[] = ['OK', 'WARNING', 'CRITICAL']

type Props = {
    open: boolean
    onClose: () => void
    eventId: string
}

/**
 * Gegliedert nach Prüfung, nicht nach Wettkampf: der Schiedsrichter-Obmann entscheidet über
 * "offene Rechnungen", nicht über "Wettkampf 7". Gespeichert wird trotzdem je Wettkampf - die
 * Sammelaktion je Zeile ist der Regattatag-Fall in einem Klick.
 */
const CheckSeverityDialog = ({open, onClose, eventId}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const [entries, setEntries] = useState<CheckSeverityEntryDto[]>([])
    const [saving, setSaving] = useState(false)

    const {data: config, pending} = useFetch(
        signal => getCheckSeverityConfig({signal, path: {eventId}}),
        {deps: [eventId, open]},
    )

    // Die Matrix wird beim Öffnen vollständig aufgefüllt - auch mit den Standardwerten. Damit ist
    // jedes Feld ein bearbeitbarer Wert; welche davon gespeichert werden, entscheidet der Server.
    useEffect(() => {
        if (!config) return
        setEntries(
            config.competitions.flatMap(competition =>
                config.rows.map(row => ({
                    competitionId: competition.competitionId,
                    checkType: row.checkType,
                    requirementId: row.requirementId,
                    severity: severityAt(
                        config,
                        config.entries,
                        competition.competitionId,
                        row.checkType,
                        row.requirementId ?? null,
                    ),
                })),
            ),
        )
    }, [config])

    const rowKey = (row: CheckSeverityRowDto) => `${row.checkType}:${row.requirementId ?? ''}`

    const matches = (entry: CheckSeverityEntryDto, row: CheckSeverityRowDto) =>
        entry.checkType === row.checkType &&
        (entry.requirementId ?? null) === (row.requirementId ?? null)

    const setSeverity = (row: CheckSeverityRowDto, competitionId: string | null, value: CheckSeverity) =>
        setEntries(current =>
            current.map(entry =>
                matches(entry, row) && (competitionId === null || entry.competitionId === competitionId)
                    ? {...entry, severity: value}
                    : entry,
            ),
        )

    const rowLabel = (row: CheckSeverityRowDto) =>
        row.checkType === 'REQUIREMENT'
            ? (row.name ?? '')
            : row.checkType === 'REQUIREMENT_TIME_WINDOW'
              ? t('event.liveDashboard.checkSeverity.check.REQUIREMENT_TIME_WINDOW')
              : t(`event.liveDashboard.checkSeverity.check.${row.checkType}`)

    const summaryLabel = (row: CheckSeverityRowDto) => {
        const summary = rowSummary(entries.filter(e => matches(e, row)).map(e => e.severity))
        return summary.kind === 'uniform'
            ? t('event.liveDashboard.checkSeverity.uniform', {
                  severity: t(`event.liveDashboard.checkSeverity.severity.${summary.severity}`),
              })
            : t('event.liveDashboard.checkSeverity.mixed')
    }

    const handleSave = async () => {
        setSaving(true)
        const {error} = await updateCheckSeverityConfig({path: {eventId}, body: {entries}})
        setSaving(false)
        if (error) {
            feedback.error(t('event.liveDashboard.checkSeverity.saveError'))
        } else {
            feedback.success(t('event.liveDashboard.checkSeverity.saved'))
            onClose()
        }
    }

    return (
        <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
            <DialogTitle sx={{pr: 6}}>
                {t('event.liveDashboard.checkSeverity.title')}
                <IconButton onClick={onClose} sx={{position: 'absolute', right: 8, top: 8}}>
                    <Close />
                </IconButton>
            </DialogTitle>
            <DialogContent>
                <Typography variant="body2" color="text.secondary" sx={{mb: 2}}>
                    {t('event.liveDashboard.checkSeverity.description')}
                </Typography>
                {pending && !config ? (
                    <Throbber />
                ) : (
                    config?.rows.map(row => (
                        <Accordion
                            key={rowKey(row)}
                            // Zeitfenster gehört sichtbar unter seine Bedingung
                            sx={{ml: row.checkType === 'REQUIREMENT_TIME_WINDOW' ? 3 : 0}}>
                            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                                <Box
                                    sx={{
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        width: 1,
                                        pr: 1,
                                    }}>
                                    <Typography>{rowLabel(row)}</Typography>
                                    <Typography variant="body2" color="text.secondary">
                                        {summaryLabel(row)}
                                    </Typography>
                                </Box>
                            </AccordionSummary>
                            <AccordionDetails>
                                <Stack
                                    direction="row"
                                    spacing={1}
                                    alignItems="center"
                                    flexWrap="wrap"
                                    sx={{mb: 1}}>
                                    <Typography variant="body2">
                                        {t('event.liveDashboard.checkSeverity.setAll')}
                                    </Typography>
                                    {SEVERITIES.map(severity => (
                                        <Button
                                            key={severity}
                                            size="small"
                                            onClick={() => setSeverity(row, null, severity)}>
                                            {t(
                                                `event.liveDashboard.checkSeverity.severity.${severity}`,
                                            )}
                                        </Button>
                                    ))}
                                </Stack>
                                {config?.competitions.map(competition => {
                                    // Ohne An-/Abmeldung gibt es beim Beachsprint nichts zu bewerten
                                    const notApplicable =
                                        row.checkType === 'NOT_ON_WATER' &&
                                        !competition.checkInOutRequired
                                    const entry = entries.find(
                                        e =>
                                            matches(e, row) &&
                                            e.competitionId === competition.competitionId,
                                    )
                                    return (
                                        <Stack
                                            key={competition.competitionId}
                                            direction="row"
                                            spacing={1}
                                            alignItems="center"
                                            sx={{py: 0.5, opacity: notApplicable ? 0.5 : 1}}>
                                            <Typography sx={{flex: 1}}>
                                                {competition.identifier} | {competition.name}
                                            </Typography>
                                            {notApplicable ? (
                                                <Typography variant="body2" color="text.secondary">
                                                    {t(
                                                        'event.liveDashboard.checkSeverity.noCheckInOut',
                                                    )}
                                                </Typography>
                                            ) : (
                                                <Select
                                                    size="small"
                                                    value={entry?.severity ?? 'CRITICAL'}
                                                    onChange={event =>
                                                        setSeverity(
                                                            row,
                                                            competition.competitionId,
                                                            event.target.value as CheckSeverity,
                                                        )
                                                    }>
                                                    {SEVERITIES.map(severity => (
                                                        <MenuItem key={severity} value={severity}>
                                                            {t(
                                                                `event.liveDashboard.checkSeverity.severity.${severity}`,
                                                            )}
                                                        </MenuItem>
                                                    ))}
                                                </Select>
                                            )}
                                        </Stack>
                                    )
                                })}
                            </AccordionDetails>
                        </Accordion>
                    ))
                )}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>{t('common.cancel')}</Button>
                <LoadingButton pending={saving} variant="contained" onClick={handleSave}>
                    {t('common.save')}
                </LoadingButton>
            </DialogActions>
        </Dialog>
    )
}

export default CheckSeverityDialog
```

Die beiden Schlüssel `common.cancel` und `common.save` gibt es bereits; mit

```bash
grep -n '"cancel"\|"save"' frontend/src/i18n/de/translations.json | head
```

den tatsächlichen Pfad bestätigen und gegebenenfalls anpassen.

- [ ] **Step 6: Knopf auf der Event-Seite**

In `frontend/src/pages/event/EventPage.tsx`:
- Import ergänzen und `const [checkSeverityOpen, setCheckSeverityOpen] = useState(false)` neben Zeile 113
- in der Karte ab Zeile 428 unter dem bestehenden Link:

```tsx
                                        {user.checkPrivilege(updateEventGlobal) && (
                                            <Button
                                                startIcon={<TuneOutlined />}
                                                variant="outlined"
                                                fullWidth
                                                sx={{mt: 1}}
                                                onClick={() => setCheckSeverityOpen(true)}>
                                                {t('event.liveDashboard.checkSeverity.manage')}
                                            </Button>
                                        )}
```

- neben dem bestehenden `ManageRunningMatchesDialog` (Zeile 537) den neuen Dialog rendern
- `TuneOutlined` aus `@mui/icons-material` importieren

- [ ] **Step 7: Übersetzungen**

Unter `event.liveDashboard` in allen drei Dateien, Block `checkSeverity`:

| Schlüssel | de | en | da |
|---|---|---|---|
| `manage` | Prüfungen verwalten | Manage checks | Administrer kontroller |
| `title` | Schweregrad der Prüfungen | Severity of checks | Kontrollernes alvorsgrad |
| `description` | Legt je Wettkampf fest, wie schwer eine nicht erfüllte Prüfung wiegt. | Sets per competition how much an unmet check weighs. | Angiver pr. konkurrence, hvor tungt en ikke-opfyldt kontrol vejer. |
| `setAll` | alle setzen | set all | sæt alle |
| `uniform` | alle: {{severity}} | all: {{severity}} | alle: {{severity}} |
| `mixed` | gemischt | mixed | blandet |
| `noCheckInOut` | keine An-/Abmeldung erforderlich | no check-in/out required | ingen ind-/udtjekning påkrævet |
| `check.INVOICE_OPEN` | Rechnung offen | Invoice open | Faktura åben |
| `check.NOT_ON_WATER` | Nicht auf dem Wasser | Not on the water | Ikke på vandet |
| `check.REQUIREMENT_TIME_WINDOW` | Zeitfenster verletzt | Time window violated | Tidsvindue overskredet |
| `severity.OK` | OK (ohne Wirkung) | OK (no effect) | OK (uden effekt) |
| `severity.WARNING` | Warnung | Warning | Advarsel |
| `severity.CRITICAL` | Kritisch | Critical | Kritisk |
| `saved` | Einstellungen gespeichert | Settings saved | Indstillinger gemt |
| `saveError` | Einstellungen konnten nicht gespeichert werden. | Settings could not be saved. | Indstillingerne kunne ikke gemmes. |

- [ ] **Step 8: Übersetzen, prüfen, testen**

```bash
cd frontend && npx tsc -b && npm run lint && npm test
```

Erwartet: alle drei ohne Fehler.

- [ ] **Step 9: Commit**

```bash
git add frontend/src
git commit -m "Add dialog for managing referee check severities"
```

---

### Task 8: QR-App und Scan-Übersicht

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/participantTracking/entity/TeamForScanOverviewDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/participantTracking/control/ParticipantTrackingRepo.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/participantTracking/control/Conversions.kt`
- Modify: `backend/src/main/resources/openapi/documentation.yaml` (`TeamForScanOverviewDto` ab Zeile 13734)
- Modify: `frontend/src/components/qrApp/TeamCheckInOut.tsx`
- Modify: `frontend/src/i18n/{de,en,da}/translations.json`

**Interfaces:**
- Consumes: `COMPETITION_PROPERTIES.CHECK_IN_OUT_REQUIRED` aus Task 1
- Produces: `TeamForScanOverviewDto.checkInOutRequired: Boolean`

- [ ] **Step 1: Feld im DTO ergänzen**

In `TeamForScanOverviewDto.kt` nach `competitionName`:

```kotlin
    /** Ob dieser Wettkampf eine An-/Abmeldung verlangt - beim Beachsprint zum Beispiel nicht. */
    val checkInOutRequired: Boolean,
```

- [ ] **Step 2: Abfrage und Umwandlung nachziehen**

In der Abfrage, die `TeamForScanOverviewDto` speist, `COMPETITION_PROPERTIES.CHECK_IN_OUT_REQUIRED` mitselektieren (der Join auf `COMPETITION_PROPERTIES` besteht dort bereits; falls nicht, analog zu `LiveDashboardRepo.getTeams` ergänzen) und in der Conversion durchreichen.

```bash
grep -rn "TeamForScanOverviewDto(" backend/src/main/kotlin
```

zeigt die Stelle, an der das Objekt gebaut wird.

- [ ] **Step 3: Backend übersetzen**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -q compile
```

Erwartet: kein Fehler.

- [ ] **Step 4: OpenAPI und Frontend-Typen**

Im Schema `TeamForScanOverviewDto` (ab Zeile 13734) ergänzen und in `required` aufnehmen:

```yaml
        checkInOutRequired:
          type: boolean
          description: "Whether this competition uses check-in/out at all. Scans are recorded per participant and event, so a participant racing in one competition that requires it still checks out - the flag only drives what the app shows."
```

```bash
cd frontend && npm run generate
```

- [ ] **Step 5: QR-App anpassen**

In `frontend/src/components/qrApp/TeamCheckInOut.tsx`:

- im Kartenkopf, direkt unter dem Wettkampfnamen:

```tsx
                                                    {!team.checkInOutRequired && (
                                                        <Chip
                                                            size="small"
                                                            label={t(
                                                                'club.participant.tracking.noCheckInOutNeeded',
                                                            )}
                                                        />
                                                    )}
```

- den Knopf am unteren Rand nur zeigen, wenn wenigstens ein Team eine An-/Abmeldung verlangt:

```tsx
    const anyTeamRequiresCheckInOut = teamsData?.some(team => team.checkInOutRequired) ?? false
```

```tsx
                        {anyTeamRequiresCheckInOut ? (
                            <LoadingButton …>…</LoadingButton>
                        ) : (
                            <Typography variant="body2" color="text.secondary">
                                {t('club.participant.tracking.noCheckInOutForAnyTeam')}
                            </Typography>
                        )}
```

`Chip` ist in der Datei bereits importiert.

- [ ] **Step 6: Übersetzungen**

Unter `club.participant.tracking` in allen drei Dateien:

| Schlüssel | de | en | da |
|---|---|---|---|
| `noCheckInOutNeeded` | keine An-/Abmeldung nötig | no check-in/out needed | ingen ind-/udtjekning nødvendig |
| `noCheckInOutForAnyTeam` | Für diese Meldungen ist keine An-/Abmeldung vorgesehen. | None of these entries use check-in/out. | Ingen af disse tilmeldinger bruger ind-/udtjekning. |

- [ ] **Step 7: Übersetzen, prüfen, testen**

```bash
cd frontend && npx tsc -b && npm run lint && npm test
```

Erwartet: alle drei ohne Fehler.

- [ ] **Step 8: Commit**

```bash
git add backend/src frontend/src
git commit -m "Hide check-in/out for competitions that do not use it"
```

---

## Abschluss

Nach Task 8 einmal vollständig prüfen:

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw test
```

```bash
cd frontend && npx tsc -b && npm run lint && npm test
```

Beides muss ohne Fehler durchlaufen, bevor die Arbeit als fertig gilt.
