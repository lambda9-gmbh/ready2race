package de.lambda9.ready2race.backend.app.club

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.certificate.boundary.AwardCertificateService
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateMode
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateOptions
import de.lambda9.ready2race.backend.app.club.boundary.ClubNameKey
import de.lambda9.ready2race.backend.app.club.control.ClubShortNameRepo
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import de.lambda9.ready2race.backend.app.documentTemplate.boundary.GapDocumentTemplateService
import de.lambda9.ready2race.backend.app.documentTemplate.entity.AssignGapDocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderType
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import de.lambda9.ready2race.backend.app.eventInfo.boundary.EventInfoService
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubShortNameRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationNamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.NamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION_NAMED_PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.GAP_DOCUMENT_TEMPLATE
import de.lambda9.ready2race.backend.database.generated.tables.references.NAMED_PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.text.TextAlign
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.tailwind.jooq.Jooq
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Die Vereinskette am echten Postgres - Athleten-Anzeige und Urkunde.
 *
 * Die Ableitung selbst ist in [ClubCompositionTest] ohne Datenbank festgeschrieben. Was sich dort
 * nicht prüfen lässt und genau hier schiefgeht, sind die Abfragen: der Verein einer Person hängt
 * an einem *zweiten*, aliasierten CLUB-Join, während der bisherige CLUB-Join weiterhin den
 * meldenden Verein liefert. Verwechselt man die beiden, sieht der Code richtig aus und die Anzeige
 * zeigt trotzdem für jedes Boot den Verein, der es angemeldet hat.
 *
 * Die gemeldete Mannschaft ist deshalb absichtlich so gebaut, dass der meldende Verein in KEINER
 * der beiden Ketten vorkommen darf.
 */
class ClubChainInDisplaysTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 14, 10, 0)

    private val registeringClub = "Erster Kieler Ruder-Club von 1862 e.V."
    private val mainz = "Mainzer Ruder-Verein 1878 e.V."
    private val flensburg = "Ruderklub Flensburg e.V."
    private val marburg = "Marburger Ruderverein von 1911 e.V."

    /**
     * Die Kette, wie sie beide Anzeigen erwarten: drei Vereine in Crew-Reihenfolge. Der vierte
     * Ruderer fährt für Mainz wie der erste (ein Glied, nicht zwei), und der Steuermann steht in
     * den Meldedaten mit dem Platzhalter "N.N." statt eines Vereins (fällt still raus).
     */
    private val expectedFull = "$mainz / $marburg / $flensburg"

    private data class Seeded(val eventId: UUID, val competitionId: UUID, val registrationId: UUID)

    /**
     * Eine Veranstaltung mit einem Wettkampf, einer Runde ("Finale"), einem Lauf und genau einer
     * gemeldeten Mannschaft aus fünf Personen. Der Lauf läuft und ist gewertet - so erscheint
     * dieselbe Mannschaft in der Athleten-Anzeige und in der Platzierungsberechnung der Urkunde.
     */
    private fun TestComprehensionScope<JEnv>.seed(): Seeded {
        val eventId = UUID.randomUUID()
        val competitionId = UUID.randomUUID()
        val propertiesId = UUID.randomUUID()
        val roundId = UUID.randomUUID()
        val matchId = UUID.randomUUID()
        val eventRegistrationId = UUID.randomUUID()
        val registrationId = UUID.randomUUID()

        !EVENT.insert(
            EventRecord(
                id = eventId,
                name = "Testregatta",
                createdAt = now,
                updatedAt = now,
                // Der Begriff bleibt in der Veranstaltung stehen (Startlisten nutzen ihn weiter) -
                // in den beiden umgestellten Anzeigen darf er nicht mehr auftauchen.
                mixedTeamTerm = "Renngemeinschaft",
            )
        )

        !COMPETITION.insert(
            CompetitionRecord(id = competitionId, event = eventId, createdAt = now, updatedAt = now)
        )
        !COMPETITION_PROPERTIES.insert(
            CompetitionPropertiesRecord(
                id = propertiesId,
                competition = competitionId,
                identifier = "1",
                name = "Coastal Quad",
            )
        )
        !COMPETITION_SETUP.insert(
            CompetitionSetupRecord(competitionProperties = propertiesId, createdAt = now, updatedAt = now)
        )
        !COMPETITION_SETUP_ROUND.insert(
            CompetitionSetupRoundRecord(
                id = roundId,
                competitionSetup = propertiesId,
                name = "Finale",
                required = true,
                useDefaultSeeding = true,
                // EQUAL: die letzte Runde vergibt Platz 1 - mehr braucht die Urkunde nicht.
                placesOption = CompetitionSetupPlacesOption.EQUAL.name,
            )
        )
        !COMPETITION_SETUP_MATCH.insert(
            CompetitionSetupMatchRecord(
                id = matchId,
                competitionSetupRound = roundId,
                weighting = 1,
                name = "Lauf 1",
                executionOrder = 1,
                teams = 1,
            )
        )
        !COMPETITION_MATCH.insert(
            CompetitionMatchRecord(
                competitionSetupMatch = matchId,
                startTime = now,
                createdAt = now,
                updatedAt = now,
                currentlyRunning = true,
            )
        )

        val registeringClubId = club(registeringClub)
        val mainzId = club(mainz)
        val flensburgId = club(flensburg)

        !EVENT_REGISTRATION.insert(
            EventRegistrationRecord(
                id = eventRegistrationId,
                event = eventId,
                club = registeringClubId,
                createdAt = now,
                updatedAt = now,
            )
        )
        !COMPETITION_REGISTRATION.insert(
            CompetitionRegistrationRecord(
                id = registrationId,
                eventRegistration = eventRegistrationId,
                competition = competitionId,
                club = registeringClubId,
                name = "Mix Nord",
                createdAt = now,
                updatedAt = now,
            )
        )

        // Die Rolle bestimmt die Reihenfolge in der Kette (siehe CompetitionMatchTeamRepo).
        crewMember(registrationId, "1. Ruderer", "Albers", clubId = mainzId)
        crewMember(registrationId, "2. Ruderer", "Bruns", clubId = registeringClubId, externalClubName = marburg)
        crewMember(registrationId, "3. Ruderer", "Cordes", clubId = flensburgId)
        crewMember(registrationId, "4. Ruderer", "Dohm", clubId = mainzId)
        crewMember(registrationId, "5. Steuermann", "Evers", clubId = registeringClubId, externalClubName = "N.N.")

        !COMPETITION_MATCH_TEAM.insert(
            CompetitionMatchTeamRecord(
                id = UUID.randomUUID(),
                competitionMatch = matchId,
                competitionRegistration = registrationId,
                startNumber = 1,
                place = 1,
                placesCalculated = true,
                createdAt = now,
                updatedAt = now,
            )
        )

        return Seeded(eventId, competitionId, registrationId)
    }

    private fun TestComprehensionScope<JEnv>.club(name: String): UUID {
        val id = UUID.randomUUID()
        !CLUB.insert(ClubRecord(id = id, name = name, createdAt = now, updatedAt = now))
        return id
    }

    /**
     * Eine Person mit ihrer Rolle im Boot. [externalClubName] gesetzt heißt Gastruderer: dann zählt
     * dieser Freitext, nicht [clubId] - der ist bei Gastruderern der meldende Verein, weil eine
     * Person ohne eigenen Vereins-Datensatz gar nicht in der Datenbank stehen kann. Genau daran
     * hängt der Fall: die Anzeige darf hier nicht auf [clubId] zurückfallen.
     */
    private fun TestComprehensionScope<JEnv>.crewMember(
        registrationId: UUID,
        role: String,
        lastName: String,
        clubId: UUID,
        externalClubName: String? = null,
    ) {
        val participantId = UUID.randomUUID()
        val namedParticipantId = UUID.randomUUID()

        !NAMED_PARTICIPANT.insert(
            NamedParticipantRecord(id = namedParticipantId, name = role, createdAt = now, updatedAt = now)
        )
        !PARTICIPANT.insert(
            ParticipantRecord(
                id = participantId,
                club = clubId,
                firstname = "Test",
                lastname = lastName,
                year = 1990,
                gender = Gender.M,
                external = externalClubName != null,
                externalClubName = externalClubName,
                createdAt = now,
                updatedAt = now,
            )
        )
        !COMPETITION_REGISTRATION_NAMED_PARTICIPANT.insert(
            CompetitionRegistrationNamedParticipantRecord(
                competitionRegistration = registrationId,
                namedParticipant = namedParticipantId,
                participant = participantId,
            )
        )
    }

    @Test
    fun theAthleteBoardShowsTheClubsTheAthletesWearInsteadOfOneMixedTeamTerm() = testComprehension {
        val seeded = seed()

        // Eine gepflegte Kurzform, die die Heuristik nicht erraten könnte ("Mainzer RV") - so ist
        // belegt, dass die Anzeige club_short_name wirklich heranzieht.
        !ClubShortNameRepo.upsert(
            ClubShortNameRecord(
                nameKey = ClubNameKey.of(mainz),
                sampleName = mainz,
                shortName = "Mainz",
                createdAt = now,
                updatedAt = now,
            )
        )

        val board = (!EventInfoService.getAthleteBoard(seeded.eventId)).dto
        val team = board.running.single().teams.single()

        assertEquals(expectedFull, team.clubsFull)
        assertEquals("Mainz / Marburger RV / RK Flensburg", team.clubsShort)

        // Der Kern des Ganzen: der meldende Verein steht nirgends, und "Renngemeinschaft" ist weg.
        assertFalse(team.clubsFull!!.contains("Kieler"), "meldender Verein in der Kette: ${team.clubsFull}")
        assertFalse(team.clubsShort!!.contains("Renngemeinschaft"))
    }

    @Test
    fun theAwardCertificateCarriesTheWholeChainWithoutAnyShortening() = testComprehension {
        val seeded = seed()

        // Auch eine gepflegte Kurzform darf die Urkunde nicht erreichen - sie zeigt ausdrücklich
        // immer die vollen Vereinsnamen.
        !ClubShortNameRepo.upsert(
            ClubShortNameRecord(
                nameKey = ClubNameKey.of(mainz),
                sampleName = mainz,
                shortName = "Mainz",
                createdAt = now,
                updatedAt = now,
            )
        )
        assignAwardCertificateTemplate()

        val file = !AwardCertificateService.downloadForCompetition(
            eventId = seeded.eventId,
            competitionId = seeded.competitionId,
            options = AwardCertificateOptions(
                maxPlace = 3,
                mode = AwardCertificateMode.PER_TEAM,
                withBackground = false,
            ),
            format = AwardCertificateService.Format.PDF,
        )

        val raw = pdfText(file.bytes)
        val text = raw.replace(Regex("""\s+"""), " ")

        // Die Kette passt nicht in eine Zeile und wird umgebrochen (siehe GapTextWrap) - deshalb
        // steht sie hier nicht am Stück. Was zählt: jeder Verein steht vollständig da, keiner ist
        // am Umbruch zerrissen, und die Reihenfolge im Boot bleibt.
        listOf(mainz, marburg, flensburg).forEach {
            assertTrue(text.contains(it), "Verein fehlt oder ist am Umbruch zerrissen: '$it' in: $text")
        }
        assertTrue(text.indexOf(mainz) < text.indexOf(marburg), text)
        assertTrue(text.indexOf(marburg) < text.indexOf(flensburg), text)

        assertTrue(
            raw.trim().lines().size > 1,
            "Die Kette hätte umgebrochen werden müssen, steht aber auf einer Zeile: $raw",
        )

        assertFalse(text.contains("Renngemeinschaft"), text)
        assertFalse(text.contains("Mainzer RV"), "gekürzt statt voll ausgeschrieben: $text")
        assertFalse(text.contains("Kieler"), "meldender Verein auf der Urkunde: $text")
    }

    /**
     * Eine A4-Vorlage mit genau einem Platzhalter - dem Vereinsnamen -, über die volle Breite und
     * so groß, wie eine echte Urkunde ihn setzt.
     */
    private fun TestComprehensionScope<JEnv>.assignAwardCertificateTemplate() {
        !GapDocumentTemplateService.addTemplate(
            File("urkunde.pdf", emptyA4Pdf()),
            GapDocumentTemplateRequest(
                type = GapDocumentType.AWARD_CERTIFICATE,
                placeholders = listOf(
                    GapDocumentPlaceholderRequest(
                        name = null,
                        type = GapDocumentPlaceholderType.CLUB_NAME,
                        page = 1,
                        relLeft = 0.0,
                        relTop = 0.5,
                        relWidth = 1.0,
                        relHeight = 0.04,
                        textAlign = TextAlign.CENTER,
                        fontSize = 18,
                        bold = false,
                        italic = false,
                        staticText = null,
                    )
                ),
                fontName = null,
            ),
            null,
        )

        val templateId = !Jooq.query { selectFrom(GAP_DOCUMENT_TEMPLATE).fetchSingle().id!! }
        !GapDocumentTemplateService.assignTemplate(
            GapDocumentType.AWARD_CERTIFICATE,
            AssignGapDocumentTemplateRequest(templateId),
        )
    }

    private fun emptyA4Pdf(): ByteArray {
        val doc = PDDocument()
        doc.addPage(PDPage(PDRectangle.A4))
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    private fun pdfText(bytes: ByteArray): String {
        val doc = Loader.loadPDF(bytes)
        val text = PDFTextStripper().getText(doc)
        doc.close()
        return text
    }
}
