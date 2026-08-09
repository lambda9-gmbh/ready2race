# Siegerehrungsbogen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein druckbares PDF für die Siegerehrung — eine A4-Seite je Wertungskategorie, mit den Rängen 1–3, Namen, Vereinen, Zeiten und Lauf, mit vorheriger Auswahl der Ehrungen.

**Architecture:** Neues Backend-Modul `app/awardCeremony/` mit strikter Dreiteilung: `AwardCeremonyLogic` (rein, ohne DB — Gruppierung, Ranking, Vereinsverdichtung, Formatierung), `AwardCeremonyPdf` (nur Layout im vorhandenen `document { page { … } }`-DSL), `AwardCeremonyService` (Datenbeschaffung, Auswahl, Fehler). Frontend ein Dialog nach dem Muster von `AwardCertificateDialog.tsx`, eingehängt im Wettkämpfe-Tab und auf der Platzierungsseite.

**Tech Stack:** Kotlin, Ktor, KIO/tailwind, JOOQ, PDFBox (über das hauseigene `pdf`-DSL), kotlin.test, Testcontainers; React + TypeScript + MUI, `react-hook-form-mui`, i18next, generierter API-Client aus `documentation.yaml`.

**Spec:** `docs/superpowers/specs/2026-08-09-siegerehrungsbogen-design.md` — bei jeder Unklarheit dort nachsehen, nicht raten.

## Global Constraints

- **Sprache im Code:** Kommentare und Doc-Kommentare auf Deutsch mit echten Umlauten (ä, ö, ü, ß), nie `ae`/`oe`/`ue`/`ss`. Bezeichner auf Englisch, wie im übrigen Backend.
- **Kommentare erklären das Warum**, nicht das Was — vergleiche die Kommentardichte in `AwardCertificateService.kt`. Keine Kommentare, die den Code nacherzählen.
- **Keine Datenbankmigration.** Dieses Feature legt keine Tabelle und keine Spalte an.
- **KIO:** `KIO.failOn(cond) { Error }` und `onTrueFail` geben nur mit vorangestelltem `!` innerhalb einer `KIO.comprehension` einen Fehler zurück. Ein `!` zu vergessen ist in diesem Projekt eine bekannte Fehlerklasse — jede `failOn`-Zeile im Review prüfen.
- **Nie ein leerer Platzhalter im PDF.** Fehlt ein optionaler Wert, entfällt die Zeile bzw. der Bestandteil komplett — kein „—", kein Doppelpunkt ins Leere. Das ist eine ausdrückliche Anforderung.
- **Privileg:** beide Endpoints `Privilege.ReadEventGlobal`.
- **Commits:** deutsch, im Ton der bestehenden Historie („Gather club short names …" ist die Ausnahme; die letzten Commits sind deutsche Aussagesätze). Nie Claude, KI oder Co-Authored-By erwähnen.
- **Backend-Tests:** Der Maven-Wrapper liegt in `backend/`, nicht im Repo-Wurzelverzeichnis, und `JAVA_HOME` ist in dieser Shell nicht gesetzt. Jeder Testlauf beginnt deshalb mit:
  ```bash
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test -Dtest=AwardCeremonyLogicTest
  ```
  Die Service-Tests brauchen einen laufenden Docker (Testcontainers).

---

## File Structure

**Backend, neu:**

| Datei | Verantwortung |
|---|---|
| `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/awardCeremony/entity/AwardCeremony.kt` | alle Datentypen des Moduls (Eingang, Ausgang, DTOs, Request) |
| `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/awardCeremony/entity/AwardCeremonyError.kt` | die vier Fehler mit ErrorCodes |
| `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/awardCeremony/boundary/AwardCeremonyLogic.kt` | reine Fachlogik: Gruppierung, Ranking, Verdichtung, Formatierung, Dichte |
| `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/awardCeremony/boundary/AwardCeremonyPdf.kt` | nur das Seitenlayout |
| `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/awardCeremony/boundary/AwardCeremonyService.kt` | Datenbeschaffung, Auswahl, Fehlerfälle, `ApiResponse.File` |
| `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/awardCeremony/boundary/awardCeremony.kt` | Ktor-Routen |

**Backend, geändert:**

| Datei | Änderung |
|---|---|
| `backend/src/main/kotlin/de/lambda9/ready2race/backend/calls/responses/ErrorCode.kt` | vier neue Codes |
| `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/event/boundary/event.kt` | `awardCeremony()` neben `awardCertificate()` einhängen |
| `backend/src/main/resources/openapi/documentation.yaml` | beide Endpoints + Schemas |

**Backend, Tests neu:** `AwardCeremonyLogicTest.kt`, `AwardCeremonyPdfTest.kt`, `AwardCeremonyServiceTest.kt` unter `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/awardCeremony/`.

**Frontend, neu:** `frontend/src/components/awardCeremony/AwardCeremonyDialog.tsx`, `frontend/src/components/awardCeremony/awardCeremonyError.ts`.

**Frontend, geändert:** `CompetitionsAndEventDays.tsx`, `CompetitionPlaces.tsx`, `i18n/{de,en,da}/translations.json`.

---

## Task 1: Die reine Fachlogik

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/awardCeremony/entity/AwardCeremony.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/awardCeremony/boundary/AwardCeremonyLogic.kt`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/awardCeremony/AwardCeremonyLogicTest.kt`

**Interfaces:**
- Consumes: `de.lambda9.ready2race.backend.app.club.boundary.ClubComposition.of(clubNames: List<String?>, settings: ClubShortNameSettings): ClubComposition` und `ClubComposition.clubWorn(external: Boolean?, externalClubName: String?, ownClubName: String?): String?`; `ClubShortNameSettings.none`; `de.lambda9.ready2race.backend.validation.Validatable` / `ValidationResult`
- Produces: sämtliche Typen aus `AwardCeremony.kt` (unten vollständig) und
  `AwardCeremonyLogic.groupByRatingCategory(List<AwardCeremonyCandidate>): List<Pair<String?, List<AwardCeremonyCandidate>>>`,
  `.rank(List<AwardCeremonyCandidate>): List<AwardCeremonyRank>`,
  `.team(AwardCeremonyCandidate): AwardCeremonyTeam` (internal),
  `.sheet(eventName, eventDate, eventLocation, competitionIdentifier, competitionShortName, competitionName, ratingCategoryName, candidates): AwardCeremonySheet`,
  `.densityFor(personRows: Int): AwardCeremonyDensity`,
  `.formatBoatLine(teamName: String?, startNumber: Int): String`,
  `.formatPenalty(seconds: Int?, note: String?): String?`,
  `.formatRaceLine(roundName: String?, matchName: String?, at: LocalDateTime?): String?`

**Reihenfolge:** erst die Datentypen, dann *alle* Tests, dann die vollständige Logik. Ein
Zwischenstand, in dem `team()` schon existiert, aber Verein, Strafe und Lauf noch nicht füllt,
ist ausdrücklich nicht gewollt — er wäre nicht abnehmbar.


- [ ] **Step 1: Datentypen anlegen**

Datei `entity/AwardCeremony.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.awardCeremony.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.time.LocalDateTime
import java.util.UUID

/**
 * Ein Boot, wie es aus der Platzberechnung in die Fachlogik geht. Bewusst frei von JOOQ-Records
 * und von KIO: alles, was an der Siegerehrung fachlich schiefgehen kann, soll sich ohne Datenbank
 * testen lassen.
 */
data class AwardCeremonyCandidate(
    /** Der in [CompetitionExecutionService.computeCompetitionPlaces] berechnete Platz im Gesamtfeld. */
    val competitionPlace: Int,
    val startNumber: Int,
    val ratingCategoryName: String?,
    /** Der meldende Verein der Mannschaft - reine Verwaltung, nicht der Verein der Personen. */
    val registeringClubName: String,
    val teamName: String?,
    val time: String?,
    val penaltySeconds: Int?,
    val penaltyNote: String?,
    val roundName: String?,
    val matchName: String?,
    val matchTime: LocalDateTime?,
    val participants: List<AwardCeremonyCandidateParticipant>,
)

data class AwardCeremonyCandidateParticipant(
    val firstName: String,
    val lastName: String,
    val role: String,
    val external: Boolean?,
    val externalClubName: String?,
    /** Der eigene Verein der Person; bei Gastruderern leer, dort trägt [externalClubName]. */
    val ownClubName: String?,
)

/** Die Einheit einer Seite. `null` als Kategorie ist ein gültiger Wert, kein „unbekannt". */
data class AwardCeremonyKey(
    val competitionId: UUID,
    val ratingCategoryName: String?,
)

data class AwardCeremonyChoiceDto(
    val competitionId: UUID,
    val competitionIdentifier: String,
    val competitionShortName: String?,
    val competitionName: String,
    val ratingCategoryName: String?,
    /** Die Zahl der tatsächlich geehrten Boote, also der Ränge bis drei samt Gleichständen. */
    val awardedTeams: Int,
)

data class AwardCeremonyKeyRequest(
    val competitionId: UUID,
    val ratingCategoryName: String?,
)

/**
 * Leere oder fehlende Auswahl bedeutet „alle Ehrungen". Deshalb gibt es hier nichts zu
 * validieren - eine leere Liste ist kein Fehler, sondern der Normalfall „alles drucken".
 */
data class AwardCeremonySelectionRequest(
    val selection: List<AwardCeremonyKeyRequest>?,
) : Validatable {

    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example
            get() = AwardCeremonySelectionRequest(
                selection = listOf(
                    AwardCeremonyKeyRequest(
                        competitionId = UUID.randomUUID(),
                        ratingCategoryName = "Masters A",
                    )
                )
            )
    }
}

/**
 * Schriftgrößenstufe einer Seite. Drei Achter mit Steuermann ergeben 27 Personenzeilen und
 * sprengen A4; ab [AwardCeremonyLogic.COMPACT_THRESHOLD] Zeilen rückt die Seite eine Stufe
 * zusammen, statt unkontrolliert umzubrechen.
 */
enum class AwardCeremonyDensity {
    NORMAL,
    COMPACT,
}

/** Genau eine A4-Seite. */
data class AwardCeremonySheet(
    val eventName: String,
    val eventDate: String,
    val eventLocation: String?,
    val competitionIdentifier: String,
    val competitionShortName: String?,
    val competitionName: String,
    val ratingCategoryName: String?,
    /**
     * Geplanter Zeitpunkt der Ehrung. Bleibt vorerst immer `null`: der Zeitplan kennt nur freie
     * Slots mit Freitext-Namen, ohne Bezug zu einem Wettkampf. Ist der Wert `null`, entfällt die
     * Zeile ersatzlos - ein leerer Platzhalter wäre auf dem Pult schlimmer als gar nichts.
     */
    val ceremonyTime: LocalDateTime?,
    val ranks: List<AwardCeremonyRank>,
    val density: AwardCeremonyDensity,
)

data class AwardCeremonyRank(
    val rank: Int,
    /** Mehrere Boote teilen sich diesen Rang. */
    val shared: Boolean,
    /** Das erste Boot des Rangs - nur dort wird die Rangzahl gedruckt. */
    val first: Boolean,
    val team: AwardCeremonyTeam,
)

data class AwardCeremonyTeam(
    /** Titelzeile: der Verein, oder bei gemischter Crew die Vereinskette in Bootsreihenfolge. */
    val clubLine: String,
    /** Der meldende Verein - nur gesetzt, wenn er von [clubLine] abweicht. */
    val registeringClub: String?,
    /** „Boot „RCN I" · Startnummer 3" - reduziert auf die vorhandenen Teile. */
    val boatLine: String,
    val time: String?,
    /** „Zeitstrafe +10 s (Frühstart)" - null ohne Strafe. */
    val penalty: String?,
    /** „Finale A · Sa 15.08., 14:35" - null, wenn nichts davon vorliegt. */
    val raceLine: String?,
    val athletes: List<AwardCeremonyAthlete>,
)

data class AwardCeremonyAthlete(
    val name: String,
    val role: String,
    /** Heimatverein - nur gesetzt, wenn er von der Titelzeile des Bootes abweicht. */
    val club: String?,
)
```

- [ ] **Step 2: Die Tests für Gruppierung und Ranking schreiben**

Datei `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/awardCeremony/AwardCeremonyLogicTest.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.awardCeremony

import de.lambda9.ready2race.backend.app.awardCeremony.boundary.AwardCeremonyLogic
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyCandidate
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyCandidateParticipant
import kotlin.test.Test
import kotlin.test.assertEquals

class AwardCeremonyLogicTest {

    private fun rower(
        firstName: String = "Anna",
        lastName: String = "Meier",
        role: String = "Ruderin",
        external: Boolean? = false,
        externalClubName: String? = null,
        ownClubName: String? = "Ruderclub Nürtingen",
    ) = AwardCeremonyCandidateParticipant(
        firstName = firstName,
        lastName = lastName,
        role = role,
        external = external,
        externalClubName = externalClubName,
        ownClubName = ownClubName,
    )

    private fun candidate(
        place: Int,
        startNumber: Int = place,
        ratingCategoryName: String? = null,
        registeringClubName: String = "Ruderclub Nürtingen",
        teamName: String? = "RCN I",
        time: String? = "4:12,7",
        penaltySeconds: Int? = null,
        penaltyNote: String? = null,
        roundName: String? = "Finale",
        matchName: String? = "Finale A",
        participants: List<AwardCeremonyCandidateParticipant> = listOf(rower()),
    ) = AwardCeremonyCandidate(
        competitionPlace = place,
        startNumber = startNumber,
        ratingCategoryName = ratingCategoryName,
        registeringClubName = registeringClubName,
        teamName = teamName,
        time = time,
        penaltySeconds = penaltySeconds,
        penaltyNote = penaltyNote,
        roundName = roundName,
        matchName = matchName,
        matchTime = null,
        participants = participants,
    )

    @Test
    fun categoriesBecomeSeparateGroupsInAlphabeticalOrder() {
        val groups = AwardCeremonyLogic.groupByRatingCategory(
            listOf(
                candidate(1, ratingCategoryName = "Masters B"),
                candidate(2, ratingCategoryName = "Masters A"),
                candidate(3, ratingCategoryName = "Masters B"),
            )
        )

        assertEquals(listOf("Masters A", "Masters B"), groups.map { it.first })
        assertEquals(listOf(1, 2), groups.map { it.second.size })
    }

    @Test
    fun competitionWithoutCategoriesBecomesOneGroupWithNullKey() {
        val groups = AwardCeremonyLogic.groupByRatingCategory(listOf(candidate(1), candidate(2)))

        assertEquals(listOf(null), groups.map { it.first })
        assertEquals(2, groups.single().second.size)
    }

    @Test
    fun theGroupWithoutCategoryComesLast() {
        val groups = AwardCeremonyLogic.groupByRatingCategory(
            listOf(
                candidate(1, ratingCategoryName = null),
                candidate(2, ratingCategoryName = "Masters A"),
            )
        )

        assertEquals(listOf("Masters A", null), groups.map { it.first })
    }

    @Test
    fun ranksRestartAtOneWithinTheCategory() {
        val ranks = AwardCeremonyLogic.rank(listOf(candidate(2), candidate(5), candidate(7), candidate(9)))

        assertEquals(listOf(1, 2, 3), ranks.map { it.rank })
        assertEquals(listOf(false, false, false), ranks.map { it.shared })
    }

    @Test
    fun aTieOnSecondLeavesNoThirdRank() {
        val ranks = AwardCeremonyLogic.rank(
            listOf(candidate(1), candidate(2, startNumber = 4), candidate(2, startNumber = 9), candidate(5))
        )

        assertEquals(listOf(1, 2, 2), ranks.map { it.rank })
        assertEquals(listOf(false, true, true), ranks.map { it.shared })
        assertEquals(listOf(true, true, false), ranks.map { it.first })
    }

    @Test
    fun aTieOnFirstKeepsTheThirdRank() {
        val ranks = AwardCeremonyLogic.rank(
            listOf(candidate(1, startNumber = 2), candidate(1, startNumber = 6), candidate(3))
        )

        assertEquals(listOf(1, 1, 3), ranks.map { it.rank })
        assertEquals(listOf(true, true, false), ranks.map { it.shared })
    }

    @Test
    fun aTieAtTheCutoffPrintsEveryEntitledBoat() {
        val ranks = AwardCeremonyLogic.rank(
            listOf(candidate(1), candidate(2), candidate(4, startNumber = 3), candidate(4, startNumber = 8))
        )

        assertEquals(listOf(1, 2, 3, 3), ranks.map { it.rank })
    }

    @Test
    fun tiedBoatsAreOrderedByStartNumber() {
        val ranks = AwardCeremonyLogic.rank(
            listOf(candidate(1, startNumber = 9, teamName = "spät"), candidate(1, startNumber = 2, teamName = "früh"))
        )

        assertEquals(listOf("früh", "spät"), ranks.map { it.team.boatLine.substringAfter("Boot „").substringBefore("\"") })
    }

    @Test
    fun onlyThreeRanksReachThePage() {
        val ranks = AwardCeremonyLogic.rank((1..8).map { candidate(it) })

        assertEquals(listOf(1, 2, 3), ranks.map { it.rank })
    }
}
```

- [ ] **Step 3: Die Tests für Verdichtung, Strafe, Lauf und Dichte ergänzen**

An `AwardCeremonyLogicTest` anhängen (die Hilfsfunktionen `rower`/`candidate` aus Step 2 bleiben):

```kotlin
    @Test
    fun oneClubForEveryoneCollapsesToASingleLine() {
        val team = AwardCeremonyLogic.team(
            candidate(
                1,
                registeringClubName = "Ruderclub Nürtingen",
                participants = listOf(
                    rower(firstName = "Anna", ownClubName = "Ruderclub Nürtingen"),
                    rower(firstName = "Bernd", ownClubName = "Ruderclub Nürtingen"),
                ),
            )
        )

        assertEquals("Ruderclub Nürtingen", team.clubLine)
        assertNull(team.registeringClub)
        assertEquals(listOf(null, null), team.athletes.map { it.club })
    }

    @Test
    fun aMixedCrewShowsTheClubChainAndTheRegisteringClub() {
        val team = AwardCeremonyLogic.team(
            candidate(
                1,
                registeringClubName = "Ruderclub Nürtingen",
                participants = listOf(
                    rower(firstName = "Anna", ownClubName = "Ruderclub Nürtingen"),
                    rower(firstName = "Bernd", ownClubName = "RG Hansa Kiel"),
                ),
            )
        )

        assertEquals("Ruderclub Nürtingen / RG Hansa Kiel", team.clubLine)
        assertEquals("Ruderclub Nürtingen", team.registeringClub)
        assertEquals(listOf("Ruderclub Nürtingen", "RG Hansa Kiel"), team.athletes.map { it.club })
    }

    @Test
    fun aGuestRowerCarriesTheirExternalClub() {
        val team = AwardCeremonyLogic.team(
            candidate(
                1,
                registeringClubName = "Ruderclub Nürtingen",
                participants = listOf(
                    rower(firstName = "Anna", ownClubName = "Ruderclub Nürtingen"),
                    rower(
                        firstName = "Sven",
                        external = true,
                        externalClubName = "Roskilde Roklub",
                        ownClubName = null,
                    ),
                ),
            )
        )

        assertEquals("Ruderclub Nürtingen / Roskilde Roklub", team.clubLine)
        assertEquals("Roskilde Roklub", team.athletes[1].club)
    }

    @Test
    fun aCrewRowingForAnotherClubNamesTheRegisteringClubSeparately() {
        val team = AwardCeremonyLogic.team(
            candidate(
                1,
                registeringClubName = "Ruderverein Meldestelle",
                participants = listOf(rower(ownClubName = "Ruderclub Nürtingen")),
            )
        )

        assertEquals("Ruderclub Nürtingen", team.clubLine)
        assertEquals("Ruderverein Meldestelle", team.registeringClub)
        assertNull(team.athletes.single().club)
    }

    @Test
    fun aTeamWithoutUsableClubDataFallsBackToTheRegisteringClub() {
        val team = AwardCeremonyLogic.team(
            candidate(
                1,
                registeringClubName = "Ruderclub Nürtingen",
                participants = listOf(rower(ownClubName = null, external = false)),
            )
        )

        assertEquals("Ruderclub Nürtingen", team.clubLine)
        assertNull(team.registeringClub)
    }

    @Test
    fun everyAthleteKeepsNameAndRole() {
        val team = AwardCeremonyLogic.team(
            candidate(
                1,
                participants = listOf(
                    rower(firstName = "Anna", lastName = "Meier", role = "Schlagfrau"),
                    rower(firstName = "Bernd", lastName = "Groß", role = "Steuermann"),
                ),
            )
        )

        assertEquals(listOf("Anna Meier", "Bernd Groß"), team.athletes.map { it.name })
        assertEquals(listOf("Schlagfrau", "Steuermann"), team.athletes.map { it.role })
    }

    @Test
    fun aBoatWithoutNameShowsOnlyTheStartNumber() {
        assertEquals("Startnummer 3", AwardCeremonyLogic.formatBoatLine(null, 3))
        assertEquals("Startnummer 3", AwardCeremonyLogic.formatBoatLine("  ", 3))
        assertEquals("Boot „RCN I\" · Startnummer 3", AwardCeremonyLogic.formatBoatLine("RCN I", 3))
    }

    @Test
    fun aPenaltyIsOnlyPrintedWhenThereIsOne() {
        assertNull(AwardCeremonyLogic.formatPenalty(null, null))
        assertNull(AwardCeremonyLogic.formatPenalty(null, "Frühstart"))
        assertEquals("Zeitstrafe +10 s", AwardCeremonyLogic.formatPenalty(10, null))
        assertEquals("Zeitstrafe +10 s (Frühstart)", AwardCeremonyLogic.formatPenalty(10, "Frühstart"))
    }

    @Test
    fun theRaceLineShrinksToWhatIsKnown() {
        val at = LocalDateTime.of(2026, 8, 15, 14, 35)

        assertEquals("Finale A · 15.08., 14:35", AwardCeremonyLogic.formatRaceLine("Finale", "Finale A", at))
        assertEquals("Finale · 15.08., 14:35", AwardCeremonyLogic.formatRaceLine("Finale", null, at))
        assertEquals("Finale A", AwardCeremonyLogic.formatRaceLine("Finale", "Finale A", null))
        assertNull(AwardCeremonyLogic.formatRaceLine(null, null, null))
    }

    @Test
    fun aCrowdedPageMovesDownOneStep() {
        assertEquals(AwardCeremonyDensity.NORMAL, AwardCeremonyLogic.densityFor(18))
        assertEquals(AwardCeremonyDensity.COMPACT, AwardCeremonyLogic.densityFor(19))
    }

    @Test
    fun theSheetCarriesHeadingsRanksAndDensity() {
        val sheet = AwardCeremonyLogic.sheet(
            eventName = "Küstenregatta Kiel",
            eventDate = "15.–16. August 2026",
            eventLocation = "Kiel",
            competitionIdentifier = "17-NC",
            competitionShortName = "CM 4x+",
            competitionName = "Mixed-Coastal-Vierer mit Steuermann",
            ratingCategoryName = "Masters A",
            candidates = listOf(candidate(1), candidate(2)),
        )

        assertEquals("Masters A", sheet.ratingCategoryName)
        assertEquals(listOf(1, 2), sheet.ranks.map { it.rank })
        assertEquals(AwardCeremonyDensity.NORMAL, sheet.density)
        assertNull(sheet.ceremonyTime)
    }
```

Die Imports der Testdatei um `java.time.LocalDateTime`, `kotlin.test.assertNull` und `de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyDensity` ergänzen.

- [ ] **Step 4: Tests laufen lassen und Fehlschlag prüfen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test -Dtest=AwardCeremonyLogicTest
```

Erwartet: Kompilierfehler „Unresolved reference: AwardCeremonyLogic".

- [ ] **Step 5: `AwardCeremonyLogic` schreiben**

Datei `boundary/AwardCeremonyLogic.kt`. Zuerst das Grundgerüst mit Gruppierung und Ranking:

Grundgerüst mit `groupByRatingCategory`, `rank` und `formatBoatLine`. Das `team()` darin ist nur der Aufhänger und wird gleich darunter vollständig ersetzt — es darf nicht so stehen bleiben:

```kotlin
package de.lambda9.ready2race.backend.app.awardCeremony.boundary

import de.lambda9.ready2race.backend.app.awardCeremony.entity.*

object AwardCeremonyLogic {

    /** Geehrt wird bis Rang drei - die Zahl der Medaillensätze, nicht die Zahl der Boote. */
    const val MAX_RANK = 3

    fun groupByRatingCategory(
        candidates: List<AwardCeremonyCandidate>,
    ): List<Pair<String?, List<AwardCeremonyCandidate>>> = candidates
        .groupBy { it.ratingCategoryName }
        .toList()
        // Kategorien alphabetisch, die Gruppe ohne Kategorie zuletzt: sie ist bei gemischten
        // Wettkämpfen der Rest, nicht der Anfang.
        .sortedWith(compareBy(nullsLast<String>()) { it.first })

    /**
     * Die Ränge einer Wertungskategorie, neu ab 1 gezählt.
     *
     * Standard-Wettkampfranking: gleicher Platz im Gesamtfeld ⇒ gleicher Rang in der Kategorie,
     * der nächste Rang überspringt entsprechend viele Stellen (1, 2, 2, 4). Bei einem Gleichstand
     * auf zwei gibt es damit keine Bronze - das ist fachlich richtig und kein Fehler in der
     * Ausgabe.
     *
     * Beginnt eine Gruppe von Gleichplatzierten noch innerhalb der ersten drei Ränge, kommen
     * *alle* ihre Boote auf das Blatt, auch wenn dadurch mehr als drei Blöcke entstehen: geehrt
     * wird, wer den Rang hat.
     */
    fun rank(candidates: List<AwardCeremonyCandidate>): List<AwardCeremonyRank> {
        val sorted = candidates.sortedWith(compareBy({ it.competitionPlace }, { it.startNumber }))
        val ranks = mutableListOf<AwardCeremonyRank>()

        var index = 0
        while (index < sorted.size) {
            val rank = index + 1
            if (rank > MAX_RANK) break

            val place = sorted[index].competitionPlace
            val tied = sorted.drop(index).takeWhile { it.competitionPlace == place }

            tied.forEachIndexed { position, candidate ->
                ranks.add(
                    AwardCeremonyRank(
                        rank = rank,
                        shared = tied.size > 1,
                        first = position == 0,
                        team = team(candidate),
                    )
                )
            }

            index += tied.size
        }

        return ranks
    }

    /** Gerüst: die Vereinsverdichtung ersetzt diese Fassung im selben Task. */
    internal fun team(candidate: AwardCeremonyCandidate): AwardCeremonyTeam = AwardCeremonyTeam(
        clubLine = candidate.registeringClubName,
        registeringClub = null,
        boatLine = formatBoatLine(candidate.teamName, candidate.startNumber),
        time = candidate.time,
        penalty = null,
        raceLine = null,
        athletes = candidate.participants.map {
            AwardCeremonyAthlete(name = "${it.firstName} ${it.lastName}", role = it.role, club = null)
        },
    )

    fun formatBoatLine(teamName: String?, startNumber: Int): String = listOfNotNull(
        teamName?.takeIf { it.isNotBlank() }?.let { "Boot „$it\"" },
        "Startnummer $startNumber",
    ).joinToString(" · ")
}
```

Und im selben Zug die Verdichtung, die Formatierer und die Dichte — `team()` ersetzt dabei die
vorläufige Fassung aus dem Gerüst vollständig:

`team` ersetzen und die neuen Funktionen ergänzen:

```kotlin
    /**
     * Ab wie vielen Personenzeilen die Seite eine Schriftstufe zurückgeht. Drei Vierer mit
     * Steuermann sind 15 Zeilen und passen bequem; drei Achter mit Steuermann sind 27 und
     * passen nicht.
     */
    const val COMPACT_THRESHOLD = 18

    // Ohne Wochentag: dessen Abkürzung hängt an der CLDR-Fassung des JDK ("Sa" vs. "Sa.") und
    // machte den Test von der Java-Version abhängig. Der Tag steht ohnehin im Datum.
    private val raceTimeFormat = DateTimeFormatter.ofPattern("dd.MM., HH:mm", Locale.GERMANY)

    fun densityFor(personRows: Int): AwardCeremonyDensity =
        if (personRows > COMPACT_THRESHOLD) AwardCeremonyDensity.COMPACT else AwardCeremonyDensity.NORMAL

    /**
     * Die Strafe hängt an den Sekunden, nicht an der Notiz: eine Notiz ohne Sekunden ist keine
     * Zeitstrafe und darf auf dem Blatt nicht wie eine aussehen.
     */
    fun formatPenalty(seconds: Int?, note: String?): String? = seconds?.let {
        val text = "Zeitstrafe +$it s"
        val reason = note?.takeIf { n -> n.isNotBlank() }
        if (reason == null) text else "$text ($reason)"
    }

    /**
     * Der Laufname ist die genauere Angabe („Finale A") und verdrängt deshalb den Rundennamen
     * („Finale"); fehlt er, tritt die Runde an seine Stelle. Fehlt beides und die Uhrzeit, gibt
     * es keine Zeile - eine leere Klammer wäre schlechter als gar nichts.
     */
    fun formatRaceLine(roundName: String?, matchName: String?, at: LocalDateTime?): String? =
        listOfNotNull(
            (matchName ?: roundName)?.takeIf { it.isNotBlank() },
            at?.format(raceTimeFormat),
        ).takeIf { it.isNotEmpty() }?.joinToString(" · ")

    internal fun team(candidate: AwardCeremonyCandidate): AwardCeremonyTeam {
        // Der Verein, den eine Person trägt - dieselbe Regel wie im Schiedsrichter-Board. Der
        // meldende Verein steht bewusst NICHT in dieser Kette: wer gemeldet hat, ist Verwaltung.
        val worn = candidate.participants.map {
            ClubComposition.clubWorn(it.external, it.externalClubName, it.ownClubName)
        }

        // Volle Namen, keine Kurzformen: das Blatt wird vorgelesen, und "RC Nürtingen" spricht
        // sich schlechter als der ausgeschriebene Name.
        val chain = ClubComposition.of(worn, ClubShortNameSettings.none).full
        val clubLine = chain.ifEmpty { candidate.registeringClubName }

        return AwardCeremonyTeam(
            clubLine = clubLine,
            // Sagt die Titelzeile schon alles, wäre "Meldender Verein: dasselbe" reine
            // Wiederholung - dann entfällt die Zeile.
            registeringClub = candidate.registeringClubName.takeIf { it != clubLine },
            boatLine = formatBoatLine(candidate.teamName, candidate.startNumber),
            time = candidate.time,
            penalty = formatPenalty(candidate.penaltySeconds, candidate.penaltyNote),
            raceLine = formatRaceLine(candidate.roundName, candidate.matchName, candidate.matchTime),
            athletes = candidate.participants.map { participant ->
                val club = ClubComposition.clubWorn(
                    participant.external,
                    participant.externalClubName,
                    participant.ownClubName,
                )
                AwardCeremonyAthlete(
                    name = "${participant.firstName} ${participant.lastName}",
                    role = participant.role,
                    club = club?.takeIf { it.isNotBlank() && it != clubLine },
                )
            },
        )
    }

    fun sheet(
        eventName: String,
        eventDate: String,
        eventLocation: String?,
        competitionIdentifier: String,
        competitionShortName: String?,
        competitionName: String,
        ratingCategoryName: String?,
        candidates: List<AwardCeremonyCandidate>,
    ): AwardCeremonySheet {
        val ranks = rank(candidates)

        return AwardCeremonySheet(
            eventName = eventName,
            eventDate = eventDate,
            eventLocation = eventLocation,
            competitionIdentifier = competitionIdentifier,
            competitionShortName = competitionShortName,
            competitionName = competitionName,
            ratingCategoryName = ratingCategoryName,
            // Siehe AwardCeremonySheet.ceremonyTime: der Zeitplan gibt den Termin (noch) nicht her.
            ceremonyTime = null,
            ranks = ranks,
            density = densityFor(ranks.sumOf { it.team.athletes.size }),
        )
    }
```

Imports ergänzen: `de.lambda9.ready2race.backend.app.club.boundary.ClubComposition`, `de.lambda9.ready2race.backend.app.club.boundary.ClubShortNameSettings`, `java.time.LocalDateTime`, `java.time.format.DateTimeFormatter`, `java.util.Locale`.

- [ ] **Step 6: Tests laufen lassen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test -Dtest=AwardCeremonyLogicTest
```

Erwartet: alle Tests grün.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/awardCeremony backend/src/test/kotlin/de/lambda9/ready2race/backend/app/awardCeremony
git commit -m "Ränge, Vereine und Zeiten einer Wertungskategorie aufbereiten"
```

---

## Task 2: Das Seitenlayout

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/awardCeremony/boundary/AwardCeremonyPdf.kt`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/awardCeremony/AwardCeremonyPdfTest.kt`

**Interfaces:**
- Consumes: `AwardCeremonySheet` und die darin hängenden Typen aus Task 1; das PDF-DSL `de.lambda9.ready2race.backend.pdf.document(format, pagePadding) { page { block { text { } table { column(); row { cell { } } } } } }`, `FontStyle`, `Padding`
- Produces: `AwardCeremonyPdf.render(sheets: List<AwardCeremonySheet>): ByteArray`

**Hinweis zum DSL:** `text(...)` kennt nur `centered`, keine Rechtsbündigkeit. Die Zeit rechts neben der Rangzahl entsteht deshalb über eine zweispaltige `table`. `block(keepTogether = true)` hält einen Rangblock zusammen. Ein `page { }` ist die Seitengrenze — genau eine pro Bogen, nie mehr.

- [ ] **Step 1: Den Test schreiben**

Datei `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/awardCeremony/AwardCeremonyPdfTest.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.awardCeremony

import de.lambda9.ready2race.backend.app.awardCeremony.boundary.AwardCeremonyLogic
import de.lambda9.ready2race.backend.app.awardCeremony.boundary.AwardCeremonyPdf
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyCandidate
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyCandidateParticipant
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonySheet
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AwardCeremonyPdfTest {

    private fun rower(firstName: String, lastName: String, ownClubName: String? = "Ruderclub Nürtingen") =
        AwardCeremonyCandidateParticipant(
            firstName = firstName,
            lastName = lastName,
            role = "Ruderin",
            external = false,
            externalClubName = null,
            ownClubName = ownClubName,
        )

    private fun candidate(
        place: Int,
        participants: List<AwardCeremonyCandidateParticipant> = listOf(rower("Anna", "Meier")),
        time: String? = "4:12,7",
        teamName: String? = "RCN I",
    ) = AwardCeremonyCandidate(
        competitionPlace = place,
        startNumber = place,
        ratingCategoryName = null,
        registeringClubName = "Ruderclub Nürtingen",
        teamName = teamName,
        time = time,
        penaltySeconds = null,
        penaltyNote = null,
        roundName = "Finale",
        matchName = "Finale A",
        matchTime = null,
        participants = participants,
    )

    private fun sheet(
        identifier: String,
        ratingCategoryName: String?,
        candidates: List<AwardCeremonyCandidate> = listOf(candidate(1), candidate(2), candidate(3)),
    ): AwardCeremonySheet = AwardCeremonyLogic.sheet(
        eventName = "Küstenregatta Kiel",
        eventDate = "15.–16. August 2026",
        eventLocation = "Kiel",
        competitionIdentifier = identifier,
        competitionShortName = "CM 4x+",
        competitionName = "Mixed-Coastal-Vierer mit Steuermann",
        ratingCategoryName = ratingCategoryName,
        candidates = candidates,
    )

    private fun textOfPage(bytes: ByteArray, page: Int): String =
        Loader.loadPDF(bytes).use { doc ->
            PDFTextStripper().apply {
                startPage = page
                endPage = page
            }.getText(doc)
        }

    private fun pageCount(bytes: ByteArray): Int = Loader.loadPDF(bytes).use { it.numberOfPages }

    @Test
    fun everyCeremonyGetsExactlyOnePage() {
        val bytes = AwardCeremonyPdf.render(
            listOf(
                sheet("17-NC", "Masters A"),
                sheet("17-NC", "Masters B"),
                sheet("18-NC", null),
            )
        )

        assertEquals(3, pageCount(bytes))
    }

    @Test
    fun eachPageCarriesItsOwnHeadings() {
        val bytes = AwardCeremonyPdf.render(listOf(sheet("17-NC", "Masters A"), sheet("18-NC", "Masters B")))

        val first = textOfPage(bytes, 1)
        assertContains(first, "SIEGEREHRUNG")
        assertContains(first, "Küstenregatta Kiel")
        assertContains(first, "17-NC")
        assertContains(first, "Masters A")
        assertFalse(first.contains("Masters B"), "Kategorien dürfen sich nicht über Seiten mischen")

        val second = textOfPage(bytes, 2)
        assertContains(second, "18-NC")
        assertContains(second, "Masters B")
    }

    @Test
    fun theSheetWithoutCategoryHasNoRatingLine() {
        val bytes = AwardCeremonyPdf.render(listOf(sheet("18-NC", null)))

        assertFalse(textOfPage(bytes, 1).contains("Wertung"), "Ohne Kategorie darf keine leere Wertungszeile stehen")
    }

    @Test
    fun namesClubAndTimeReachThePage() {
        val bytes = AwardCeremonyPdf.render(
            listOf(
                sheet(
                    "17-NC",
                    "Masters A",
                    listOf(candidate(1, listOf(rower("Anna", "Meier"), rower("Bernd", "Groß", "RG Hansa Kiel")))),
                )
            )
        )

        val text = textOfPage(bytes, 1)
        assertContains(text, "Anna Meier")
        assertContains(text, "Bernd Groß")
        assertContains(text, "RG Hansa Kiel")
        assertContains(text, "4:12,7")
        assertContains(text, "Startnummer 1")
    }

    @Test
    fun aFullFieldOfEightsStillFitsOnOnePage() {
        val eight = (1..9).map { rower("Ruderin$it", "Nachname$it") }
        val bytes = AwardCeremonyPdf.render(
            listOf(sheet("17-NC", null, listOf(candidate(1, eight), candidate(2, eight), candidate(3, eight))))
        )

        assertEquals(1, pageCount(bytes))
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag prüfen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test -Dtest=AwardCeremonyPdfTest
```

Erwartet: Kompilierfehler „Unresolved reference: AwardCeremonyPdf".

- [ ] **Step 3: Das Layout schreiben**

Datei `boundary/AwardCeremonyPdf.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.awardCeremony.boundary

import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyDensity
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyRank
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonySheet
import de.lambda9.ready2race.backend.pdf.BlockBuilder
import de.lambda9.ready2race.backend.pdf.FontStyle
import de.lambda9.ready2race.backend.pdf.Padding
import de.lambda9.ready2race.backend.pdf.document
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Der Siegerehrungsbogen: je Wertungskategorie genau eine A4-Seite, zum Vorlesen gesetzt.
 *
 * Die Seitengrenze entsteht strukturell über `page { }` und nicht über eine Höhenrechnung -
 * damit kann eine Kategorie gar nicht auf zwei Blätter rutschen. Der Preis dafür ist die
 * Dichtestufe aus [AwardCeremonyLogic.densityFor]: ein sehr großes Feld wird enger gesetzt,
 * statt umzubrechen.
 */
object AwardCeremonyPdf {

    private val ceremonyTimeFormat = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy, HH:mm", Locale.GERMANY)

    fun render(sheets: List<AwardCeremonySheet>): ByteArray {
        val doc = document(format = PDRectangle.A4) {
            sheets.forEach { sheet ->
                page {
                    header(sheet)
                    sheet.ranks.forEach { rankBlock(it, sheet.density) }
                }
            }
        }

        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    private fun BlockBuilder.header(sheet: AwardCeremonySheet) {
        block(padding = Padding(bottom = 18f)) {
            text(fontStyle = FontStyle.BOLD, fontSize = 18f, centered = true) { "SIEGEREHRUNG" }
            text(fontSize = 11f, centered = true) {
                listOfNotNull(sheet.eventName, sheet.eventDate.takeIf { it.isNotBlank() }, sheet.eventLocation)
                    .joinToString(" · ")
            }
        }

        block(padding = Padding(bottom = 10f)) {
            text(fontStyle = FontStyle.BOLD, fontSize = 14f) {
                listOfNotNull(sheet.competitionIdentifier, sheet.competitionShortName).joinToString(" · ")
            }
            text(fontSize = 12f) { sheet.competitionName }

            // Zweispaltig, weil das DSL nur zentriert oder linksbündig kann: links die Wertung,
            // rechts der Lauf. Beide Zellen dürfen leer bleiben.
            table(padding = Padding(top = 6f)) {
                column(0.5f)
                column(0.5f)
                row {
                    cell {
                        sheet.ratingCategoryName?.let {
                            text(fontStyle = FontStyle.BOLD, fontSize = 12f) { "Wertung: $it" }
                        }
                    }
                    cell {
                        // Die Lauf-Angabe der Ehrung ist die des ersten Rangs; alle Ränge einer
                        // Kategorie stammen in aller Regel aus demselben Lauf.
                        sheet.ranks.firstOrNull()?.team?.raceLine?.let {
                            text(fontSize = 11f, centered = true) { it }
                        }
                        sheet.ceremonyTime?.let {
                            text(fontSize = 11f, centered = true) { "Ehrung: ${it.format(ceremonyTimeFormat)}" }
                        }
                    }
                }
            }
        }
    }

    private fun BlockBuilder.rankBlock(entry: AwardCeremonyRank, density: AwardCeremonyDensity) {
        val nameSize = if (density == AwardCeremonyDensity.COMPACT) 10f else 12f
        val metaSize = if (density == AwardCeremonyDensity.COMPACT) 9f else 10f
        val gap = if (density == AwardCeremonyDensity.COMPACT) 8f else 14f

        block(keepTogether = true, padding = Padding(bottom = gap)) {
            table {
                column(0.12f)
                column(0.63f)
                column(0.25f)

                row {
                    cell {
                        // Bei geteiltem Rang trägt nur das erste Boot die Zahl - zweimal
                        // dieselbe Zahl untereinander liest sich wie ein Fehler.
                        if (entry.first) {
                            text(fontStyle = FontStyle.BOLD, fontSize = 20f) { "${entry.rank}." }
                        }
                    }
                    cell {
                        text(fontStyle = FontStyle.BOLD, fontSize = 14f) { entry.team.clubLine }
                        if (entry.shared) {
                            text(fontSize = metaSize) { "geteilter ${entry.rank}. Platz" }
                        }
                    }
                    cell {
                        entry.team.time?.let {
                            text(fontStyle = FontStyle.BOLD, fontSize = 14f, centered = true) { it }
                        }
                        entry.team.penalty?.let {
                            text(fontSize = metaSize, centered = true) { it }
                        }
                    }
                }
            }

            block(padding = Padding(left = 24f, top = 2f)) {
                text(fontSize = metaSize) { entry.team.boatLine }
                entry.team.registeringClub?.let {
                    text(fontSize = metaSize) { "Meldender Verein: $it" }
                }

                block(padding = Padding(top = 4f)) {
                    entry.team.athletes.forEach { athlete ->
                        text(fontSize = nameSize) {
                            val name = "${athlete.name} (${athlete.role})"
                            athlete.club?.let { "$name — $it" } ?: name
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Tests laufen lassen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test -Dtest=AwardCeremonyPdfTest
```

Erwartet: alle fünf Tests grün. Schlägt `aFullFieldOfEightsStillFitsOnOnePage` fehl, sind die Abstände zu großzügig — `gap`, das `Padding` des Headers und `COMPACT_THRESHOLD` sind die Stellschrauben; die Größe der Rangzahl bleibt unverändert.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/awardCeremony backend/src/test/kotlin/de/lambda9/ready2race/backend/app/awardCeremony
git commit -m "Siegerehrungsbogen auf A4 setzen"
```

---

## Task 3: Service — Daten, Auswahl, Fehler

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/awardCeremony/entity/AwardCeremonyError.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/awardCeremony/boundary/AwardCeremonyService.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/calls/responses/ErrorCode.kt`
- Test: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/awardCeremony/AwardCeremonyServiceTest.kt`

**Interfaces:**
- Consumes: `AwardCeremonyLogic.groupByRatingCategory`, `AwardCeremonyLogic.sheet`, `AwardCeremonyPdf.render` (Task 2); `CompetitionExecutionService.computeCompetitionPlaces(competitionId: UUID): App<ServiceError, List<Pair<CompetitionMatchTeamWithRegistration, Int>>>`; `CompetitionExecutionService.sortRounds`; `CompetitionSetupService.getSetupRoundsWithMatches(key: UUID): App<CompetitionSetupError, List<CompetitionSetupRoundWithMatches>>`; `CompetitionRepo.getByEvent(eventId)`; `EventRepo.get(eventId)`; `EventDayRepo.getByEvent(eventId)`; `EventService.checkIsChallengeEvent(eventId)`; `AwardCertificateLogic.formatEventDate(days: List<LocalDate>): String`; `lexiNumberComp`
- Produces: `AwardCeremonyService.listCeremonies(eventId: UUID): App<ServiceError, ApiResponse.ListDto<AwardCeremonyChoiceDto>>` und `AwardCeremonyService.download(eventId: UUID, request: AwardCeremonySelectionRequest): App<ServiceError, ApiResponse.File>`

- [ ] **Step 1: Fehler und ErrorCodes anlegen**

In `ErrorCode.kt` neben den `AWARD_CERTIFICATE_*`-Einträgen ergänzen:

```kotlin
    AWARD_CEREMONY_NO_RESULTS,
    AWARD_CEREMONY_COMPETITION_NOT_IN_EVENT,
    AWARD_CEREMONY_UNKNOWN_RATING_CATEGORY,
    AWARD_CEREMONY_IS_CHALLENGE_EVENT,
```

Datei `entity/AwardCeremonyError.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.awardCeremony.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.HttpStatusCode

enum class AwardCeremonyError : ServiceError {
    NoResults,
    CompetitionNotInEvent,
    UnknownRatingCategory,

    /**
     * Ein Challenge-Event hat weder Läufe noch Platzierungen; eine Siegerehrung gibt es dort
     * grundsätzlich nicht. Eigener Fehler statt NoResults, damit die Antwort nicht nach "noch
     * nicht fertig" klingt und das Büro auf Ergebnisse wartet, die nie kommen.
     */
    IsChallengeEvent;

    override fun respond(): ApiError = when (this) {
        NoResults -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No placed teams for these ceremonies",
            errorCode = ErrorCode.AWARD_CEREMONY_NO_RESULTS,
        )

        CompetitionNotInEvent -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Competition does not belong to this event",
            errorCode = ErrorCode.AWARD_CEREMONY_COMPETITION_NOT_IN_EVENT,
        )

        UnknownRatingCategory -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "There is no such rating category with placed teams in this competition",
            errorCode = ErrorCode.AWARD_CEREMONY_UNKNOWN_RATING_CATEGORY,
        )

        IsChallengeEvent -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Award ceremony sheets are not available for a challenge event",
            errorCode = ErrorCode.AWARD_CEREMONY_IS_CHALLENGE_EVENT,
        )
    }
}
```

- [ ] **Step 2: Den Test schreiben**

`AwardCeremonyServiceTest.kt` nach dem Muster von `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/GapDocumentTemplateServiceTest.kt` — **diese Datei zuerst lesen**, sie zeigt, wie ein Event samt Wettkampf, Setup, Meldungen und Läufen im Test aufgebaut wird. Der Test deckt ab:

```kotlin
    @Test
    fun ceremoniesAreListedPerCompetitionAndCategory() { /* zwei Kategorien in einem Wettkampf ⇒ zwei Einträge, sortiert nach Rennnummer, awardedTeams stimmt */ }

    @Test
    fun aCompetitionWithoutPlacedTeamsIsNotOffered() { /* Wettkampf ohne gesetzte Plätze taucht in listCeremonies nicht auf */ }

    @Test
    fun anEmptySelectionRendersEveryCeremony() { /* download mit selection = null ⇒ Seitenzahl == listCeremonies().size */ }

    @Test
    fun theSelectionLimitsThePdfToTheChosenCeremonies() { /* eine von zwei Kategorien gewählt ⇒ eine Seite */ }

    @Test
    fun aCompetitionOfAnotherEventIsRejected() { /* CompetitionNotInEvent */ }

    @Test
    fun anUnknownRatingCategoryIsRejected() { /* UnknownRatingCategory */ }

    @Test
    fun aChallengeEventIsRejectedBeforeAnythingElse() { /* IsChallengeEvent */ }
```

Seitenzahl im Test über `Loader.loadPDF(file.bytes).use { it.numberOfPages }`.

- [ ] **Step 3: Tests laufen lassen und Fehlschlag prüfen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test -Dtest=AwardCeremonyServiceTest
```

Erwartet: Kompilierfehler „Unresolved reference: AwardCeremonyService".

- [ ] **Step 4: Den Service schreiben**

Datei `boundary/AwardCeremonyService.kt`. Aufbau:

```kotlin
object AwardCeremonyService {

    fun listCeremonies(eventId: UUID): App<ServiceError, ApiResponse.ListDto<AwardCeremonyChoiceDto>> =
        KIO.comprehension {
            !EventService.checkIsChallengeEvent(eventId).onTrueFail { AwardCeremonyError.IsChallengeEvent }
            val ceremonies = !collect(eventId, competitionIds = null)
            KIO.ok(ApiResponse.ListDto(ceremonies.map { it.choice }))
        }

    fun download(
        eventId: UUID,
        request: AwardCeremonySelectionRequest,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        // Steht bewusst vor allem anderen: siehe AwardCeremonyError.IsChallengeEvent.
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { AwardCeremonyError.IsChallengeEvent }

        val event = !EventRepo.get(eventId).orDie().onNullFail { EventError.NotFound }
        val eventDays = !EventDayRepo.getByEvent(eventId).orDie()
        val eventDate = AwardCertificateLogic.formatEventDate(eventDays.map { it.date })

        val selection = request.selection?.takeIf { it.isNotEmpty() }
        val all = !collect(eventId, competitionIds = selection?.map { it.competitionId }?.distinct())

        val chosen = if (selection == null) all else {
            // Jede angeforderte Ehrung muss es geben - eine still verschluckte Auswahl wäre auf
            // dem Pult ein fehlendes Blatt, das niemand bemerkt, bis die Sprecherin danach greift.
            !selection.traverse { key ->
                all.firstOrNull {
                    it.choice.competitionId == key.competitionId &&
                        it.choice.ratingCategoryName == key.ratingCategoryName
                }?.let { KIO.ok(it) } ?: KIO.fail(AwardCeremonyError.UnknownRatingCategory)
            }
            // Reihenfolge kommt aus `all`, nicht aus der Auswahl.
            all.filter { candidateMatchesSelection(it, selection) }
        }

        !KIO.failOn(chosen.isEmpty()) { AwardCeremonyError.NoResults }

        val sheets = chosen.map { ceremony ->
            AwardCeremonyLogic.sheet(
                eventName = event.name,
                eventDate = eventDate,
                eventLocation = event.location,
                competitionIdentifier = ceremony.choice.competitionIdentifier,
                competitionShortName = ceremony.choice.competitionShortName,
                competitionName = ceremony.choice.competitionName,
                ratingCategoryName = ceremony.choice.ratingCategoryName,
                candidates = ceremony.candidates,
            )
        }

        KIO.ok(
            ApiResponse.File(
                name = "siegerehrung_${event.name}.pdf",
                bytes = AwardCeremonyPdf.render(sheets),
            )
        )
    }
}
```

`collect(eventId, competitionIds)` ist der private Kern:

1. `CompetitionRepo.getByEvent(eventId).orDie()`, nach `lexiNumberComp { it.identifier }` sortieren.
2. Ist `competitionIds != null`: `!KIO.failOn(competitionIds.any { id -> competitions.none { it.id == id } }) { AwardCeremonyError.CompetitionNotInEvent }`, danach auf diese IDs filtern.
3. Je Wettkampf `CompetitionExecutionService.computeCompetitionPlaces(competition.id!!)` und `CompetitionSetupService.getSetupRoundsWithMatches(competition.id!!)`; letztere mit `CompetitionExecutionService.sortRounds(...)` ordnen.
4. Ausgeschlossene Boote (`deregistered || out || failed`) entfernen — dieselbe Regel wie im Urkundengenerator.
5. Je Boot den Lauf suchen, in dem sein Platz entstand:

```kotlin
    private fun raceOf(
        rounds: List<CompetitionSetupRoundWithMatches>,
        registrationId: UUID,
    ): Triple<String?, String?, LocalDateTime?> {
        // Rückwärts, weil der Platz in der letzten Runde entsteht, in der die Meldung vorkommt.
        val found = rounds.asReversed().firstNotNullOfOrNull { round ->
            round.matches
                .firstOrNull { match -> match.teams.any { it.competitionRegistration == registrationId } }
                ?.let { round to it }
        } ?: return Triple(null, null, null)

        val (round, match) = found
        val name = round.setupMatches.firstOrNull { it.id == match.competitionSetupMatch }?.name
        return Triple(round.setupRoundName, name, match.startedAt ?: match.startTime)
    }
```

6. Je Boot einen `AwardCeremonyCandidate` bauen (`registeringClubName = team.clubName`, `teamName = team.registrationName`, `time = team.timeString`, Beteiligte aus `team.participants` mit `external`, `externalClubName`, `clubName` → `ownClubName`).
7. `AwardCeremonyLogic.groupByRatingCategory(...)` und je Gruppe ein internes `Ceremony(choice = AwardCeremonyChoiceDto(...), candidates = …)` mit `awardedTeams = AwardCeremonyLogic.rank(candidates).size`.
8. Gruppen ohne Kandidaten entfallen; sie können nach Schritt 4 leer sein.

`candidateMatchesSelection` ist ein einfacher Vergleich auf `(competitionId, ratingCategoryName)`.

- [ ] **Step 5: Tests laufen lassen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test -Dtest=AwardCeremonyServiceTest
```

Erwartet: alle Tests grün. Jede `KIO.failOn`-Zeile im Code noch einmal ansehen — ohne führendes `!` ist sie ein No-Op und der Test würde still danebengreifen.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend backend/src/test/kotlin/de/lambda9/ready2race/backend/app/awardCeremony
git commit -m "Ehrungen einer Veranstaltung sammeln und als PDF ausliefern"
```

---

## Task 4: Routen und OpenAPI

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/awardCeremony/boundary/awardCeremony.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/event/boundary/event.kt` (bei `awardCertificate()`, Zeile 98)
- Modify: `backend/src/main/resources/openapi/documentation.yaml`

**Interfaces:**
- Consumes: `AwardCeremonyService.listCeremonies`, `AwardCeremonyService.download`, `AwardCeremonySelectionRequest.example`
- Produces: `fun Route.awardCeremony()`; HTTP `GET /api/event/{eventId}/awardCeremony` und `POST /api/event/{eventId}/awardCeremony/pdf`

- [ ] **Step 1: Die Routen schreiben**

```kotlin
package de.lambda9.ready2race.backend.app.awardCeremony.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonySelectionRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.awardCeremony() {

    route("/awardCeremony") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)

                AwardCeremonyService.listCeremonies(eventId)
            }
        }

        // POST, obwohl es ein Download ist: die Auswahl umfasst bei einer Regatta mit 40 Rennen
        // leicht hundert Schlüssel und passt nicht mehr sinnvoll in einen Query-String.
        post("/pdf") {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val body = !receiveKIO(AwardCeremonySelectionRequest.example)

                AwardCeremonyService.download(eventId, body)
            }
        }
    }
}
```

- [ ] **Step 2: Route einhängen**

In `event.kt` direkt unter `awardCertificate()` (Zeile 98) `awardCeremony()` ergänzen, samt Import `de.lambda9.ready2race.backend.app.awardCeremony.boundary.awardCeremony`.

- [ ] **Step 3: OpenAPI ergänzen**

In `documentation.yaml` beide Pfade eintragen. Vorlage sind die vorhandenen `/event/{eventId}/awardCertificates`-Einträge (Suchbegriff `awardCertificates`). Nötig:

- `/event/{eventId}/awardCeremony` `get`, `operationId: getAwardCeremonies`, Antwort `AwardCeremonyChoiceDtoList` (nach dem Muster der übrigen `…ListDto`-Antworten in der Datei), Fehler 400/401/403/404.
- `/event/{eventId}/awardCeremony/pdf` `post`, `operationId: downloadAwardCeremonySheets`, `requestBody` `AwardCeremonySelectionRequest`, Antwort `200` mit `application/pdf`, `schema: {type: string, format: binary}` — genau wie beim Urkunden-Download —, Fehler 400/401/403/404.
- Schemas `AwardCeremonyChoiceDto`, `AwardCeremonySelectionRequest`, `AwardCeremonyKeyRequest`. `ratingCategoryName` ist überall `nullable: true`, `selection` ebenso.

- [ ] **Step 4: Backend bauen und alle Modul-Tests laufen lassen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test -Dtest='AwardCeremony*'
```

Erwartet: alle drei Testklassen grün, keine Kompilierfehler.

- [ ] **Step 5: Client generieren und prüfen**

```bash
cd frontend && npm run generate
```

Erwartet: `src/api/sdk.gen.ts` enthält `getAwardCeremonies` und `downloadAwardCeremonySheets`.

```bash
cd frontend && npx tsc --noEmit -p tsconfig.app.json
```

Erwartet: keine Fehler.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app frontend/src/api backend/src/main/resources/openapi/documentation.yaml
git commit -m "Endpunkte für den Siegerehrungsbogen bereitstellen"
```

---

## Task 5: Auswahloberfläche

**Files:**
- Create: `frontend/src/components/awardCeremony/AwardCeremonyDialog.tsx`
- Create: `frontend/src/components/awardCeremony/awardCeremonyError.ts`
- Modify: `frontend/src/components/event/CompetitionsAndEventDays.tsx`
- Modify: `frontend/src/components/event/competition/excecution/CompetitionPlaces.tsx`
- Modify: `frontend/src/i18n/de/translations.json`, `frontend/src/i18n/en/translations.json`, `frontend/src/i18n/da/translations.json`

**Interfaces:**
- Consumes: `getAwardCeremonies`, `downloadAwardCeremonySheets` aus `@api/sdk.gen.ts` (Task 4); `AwardCeremonyChoiceDto` aus `@api/types.gen.ts`; `useFetch`, `useFeedback` aus `@utils/hooks.ts`; `getFilename` aus `@utils/helpers.ts`; `BaseDialog`, `SubmitButton`, `Throbber`
- Produces: `<AwardCeremonyDialog open onClose eventId competitionId? />`

**Vorbild:** `frontend/src/components/awardCertificate/AwardCertificateDialog.tsx` und `frontend/src/components/certificate/certificateError.ts` — **beide zuerst lesen** und Aufbau, Download-Mechanik und Fehleranzeige davon übernehmen.

- [ ] **Step 1: Fehlerzuordnung anlegen**

`awardCeremonyError.ts` nach dem Vorbild von `certificateError.ts`: aus dem `errorCode` der Antwort einen i18n-Schlüssel machen, mit Rückfall auf `awardCeremony.download.error.unexpected`. Abzudeckende Codes: `AWARD_CEREMONY_NO_RESULTS`, `AWARD_CEREMONY_COMPETITION_NOT_IN_EVENT`, `AWARD_CEREMONY_UNKNOWN_RATING_CATEGORY`, `AWARD_CEREMONY_IS_CHALLENGE_EVENT`.

- [ ] **Step 2: Den Dialog schreiben**

Verhalten:
- Beim Öffnen `getAwardCeremonies({path: {eventId}})` laden, währenddessen `<Throbber />`.
- Liste nach Wettkampf gruppiert: je Wettkampf eine Kopfzeile „`identifier` · `shortName` — `name`", darunter je Kategorie eine `Checkbox` mit Label `ratingCategoryName ?? t('awardCeremony.download.withoutCategory')` und dem Zusatz „(n Boote)". Hat ein Wettkampf nur eine Ehrung ohne Kategorie, steht die Checkbox direkt in der Kopfzeile.
- Schalter „alle auswählen / alle abwählen" über der Liste.
- Ist `competitionId` gesetzt: nur dessen Ehrungen anzeigen und alle vorauswählen. Sonst alles vorauswählen.
- `Herunterladen` ruft `downloadAwardCeremonySheets({path: {eventId}, body: {selection}})`, `selection` ist die Liste der angehakten `{competitionId, ratingCategoryName}`. Download über das versteckte `<a>` wie im Urkundendialog, Fallback-Dateiname `award_ceremony.pdf`.
- Der Button ist deaktiviert, solange nichts ausgewählt ist.
- Fehler als `<Alert severity="warning">` über der Liste, Text aus `awardCeremonyError.ts`.

- [ ] **Step 3: Einstiegspunkte einhängen**

In `CompetitionsAndEventDays.tsx` innerhalb des vorhandenen `!props.isChallengeEvent && user.checkPrivilege(readEventGlobal)`-Zweigs einen Button `t('awardCeremony.download.button')` mit `<EmojiEvents />` als `startIcon` ergänzen, der den Dialog ohne `competitionId` öffnet.

In `CompetitionPlaces.tsx` in der bestehenden Button-Zeile (Zeile 96–115) neben dem Urkunden-Button einen zweiten Button mit demselben Privileg-Vorbehalt ergänzen, der den Dialog mit `competitionId` öffnet.

- [ ] **Step 4: Texte ergänzen**

In allen drei `translations.json` einen Block `awardCeremony` neben `awardCertificate` anlegen. Deutsch:

```json
"awardCeremony": {
  "download": {
    "button": "Siegerehrungsbogen",
    "title": "Siegerehrungsbogen herunterladen",
    "action": "Herunterladen",
    "hint": "Eine Seite je Wertungskategorie, mit den Plätzen 1 bis 3.",
    "selectAll": "Alle auswählen",
    "deselectAll": "Alle abwählen",
    "withoutCategory": "Ohne Wertungskategorie",
    "boats_one": "{{count}} Boot",
    "boats_other": "{{count}} Boote",
    "empty": "Es gibt noch keine Ehrungen — dafür fehlen die Platzierungen.",
    "error": {
      "noResults": "Für die gewählten Ehrungen gibt es keine Platzierungen.",
      "competitionNotInEvent": "Dieser Wettkampf gehört nicht zu dieser Veranstaltung. Bitte die Seite neu laden.",
      "unknownRatingCategory": "Eine gewählte Wertungskategorie gibt es nicht mehr. Bitte die Seite neu laden.",
      "isChallengeEvent": "Für eine Challenge-Veranstaltung gibt es keine Siegerehrungsbögen — dort werden keine Läufe gefahren und keine Plätze vergeben.",
      "unexpected": "Der Siegerehrungsbogen konnte nicht erzeugt werden. Bitte die Seite neu laden und es erneut versuchen."
    }
  }
}
```

Englisch und Dänisch sinngemäß übersetzen, mit denselben Schlüsseln. Im Zweifel den Stil der benachbarten `awardCertificate`-Einträge derselben Sprachdatei übernehmen.

- [ ] **Step 5: Prüfen**

```bash
cd frontend && npx tsc --noEmit -p tsconfig.app.json && npm run lint
```

Erwartet: beides ohne Fehler.

- [ ] **Step 6: Commit**

```bash
git add frontend/src
git commit -m "Ehrungen für den Siegerehrungsbogen auswählen lassen"
```

---

## Abschluss

- [ ] **Vollständiger Backend-Testlauf**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test
```

Erwartet: keine neuen Fehlschläge gegenüber dem Stand vor Task 1. Der Vergleichslauf gehört an den Anfang von Task 1, falls auf `main` bereits etwas rot ist.

- [ ] **Handtest in der laufenden App** (siehe `docs/superpowers/specs/2026-08-05-testkatalog-crf-2026.md` für den Aufbau eines lokalen Events): Bogen einer Veranstaltung mit zwei Kategorien in einem Wettkampf ziehen, Seitenzahl und Umbruch am Ausdruck prüfen.

---

## Self-Review

**Spec-Abdeckung:**

| Spec-Abschnitt | Task |
|---|---|
| 4 Datenquellen, Zuordnung Team → Lauf | Task 3, Step 4 (`raceOf`) |
| 4 Kein Ehrungstermin, kein Platzhalter | Task 1 (`ceremonyTime`, `sheet`), Task 2 (Header) |
| 5 Bildung und Reihenfolge der Ehrungen | Task 1 (`groupByRatingCategory`), Task 3 (`collect`) |
| 5 API `GET` / `POST` | Task 4 |
| 5 Fehlerfälle | Task 3, Step 1 + Tests in Step 2 |
| 6 Ranking und Gleichstände | Task 1, Steps 2 und 5 |
| 7 Layout, Verdichtung, Umbruchschutz | Task 1 (Verdichtung, Dichte), Task 2 (Layout) |
| 8 Modulaufbau | File Structure, Tasks 1–4 |
| 9 Frontend | Task 5 |
| 10 Tests 1–10 | Task 1 (alle zehn), Task 2 (Pdf), Task 3 (Service) |
| 11 Nicht enthalten | nirgends implementiert — bewusst |

**Namenskonsistenz geprüft:** `AwardCeremonyDensity` (nicht `Density`) in Entity, Logic, Pdf; `registeringClubName` am Kandidaten gegenüber `registeringClub` am aufbereiteten Team — die Unterscheidung ist gewollt (Rohwert vs. „nur wenn abweichend") und in beiden Doc-Kommentaren benannt. `boatLine` ist nicht-nullable (leer kann sie nicht werden, die Startnummer gibt es immer), `raceLine` und `penalty` sind nullable.
