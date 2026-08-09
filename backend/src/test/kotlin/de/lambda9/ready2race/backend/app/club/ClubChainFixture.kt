package de.lambda9.ready2race.backend.app.club

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.club.boundary.ClubComposition
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
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
import de.lambda9.ready2race.backend.database.generated.tables.references.NAMED_PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import java.time.LocalDateTime
import java.util.UUID

/*
 * Die eine gemeldete Mannschaft, an der sich die Vereinskette entscheidet - für alle Anzeigen, die
 * sie zeigen: Athleten-Anzeige und Urkunde in ClubChainInDisplaysTest, das Schiedsrichter-Board in
 * LiveDashboardClubChainTest.
 *
 * Sie steht hier gemeinsam, weil dieselben Meldedaten in allen drei Anzeigen dieselbe Kette ergeben
 * müssen - zwei Vorrichtungen nebeneinander würden genau das nicht mehr belegen, sobald eine von
 * beiden gepflegt wird und die andere nicht.
 *
 * Der Aufbau ist bewusst der schlimmste Fall aus den echten Meldedaten der CRF 2026: sieben
 * Personen aus fünf Vereinen, ein Verein doppelt, ein "N.N."-Platzhalter, zwei Gastruderer über
 * `participant.external` - und ein meldender Verein, dem KEINE der Personen angehört. Genau
 * deshalb darf REGISTERING_CLUB in keiner Kette auftauchen.
 */

val CHAIN_SEED_TIME: LocalDateTime = LocalDateTime.of(2026, 8, 14, 10, 0)

const val REGISTERING_CLUB = "Erster Kieler Ruder-Club von 1862 e.V."
const val MAINZ = "Mainzer Ruder-Verein 1878 e.V."
const val FLENSBURG = "Ruderklub Flensburg e.V."
const val MARBURG = "Marburger Ruderverein von 1911 e.V."
const val NUERTINGEN = "Ruderclub Nürtingen"
const val ROSTOCK = "Rostocker Ruder-Club von 1885 e.V."

/**
 * Fünf Vereine in Crew-Reihenfolge - der vierte Ruderer fährt für Mainz wie der erste (ein Glied,
 * nicht zwei), und der Steuermann steht mit dem Platzhalter "N.N." statt eines Vereins in den Daten
 * (fällt still raus).
 */
val EXPECTED_CLUBS = listOf(MAINZ, MARBURG, FLENSBURG, NUERTINGEN, ROSTOCK)
val EXPECTED_FULL = EXPECTED_CLUBS.joinToString(ClubComposition.SEPARATOR)

/** Eine Person der Crew, so wie sie gemeldet wurde - mit allem, was eine Ummeldung braucht. */
data class SeededCrewMember(
    val participantId: UUID,
    val namedParticipantId: UUID,
    val role: String,
    val lastName: String,
)

data class SeededClubChain(
    val eventId: UUID,
    val competitionId: UUID,
    val registrationId: UUID,
    val roundId: UUID,
    val matchId: UUID,
    val registeringClubId: UUID,
    val crew: List<SeededCrewMember>,
) {
    fun member(lastName: String): SeededCrewMember = crew.single { it.lastName == lastName }
}

/**
 * Eine Veranstaltung mit einem Wettkampf, einer Runde ("Finale"), einem Lauf und genau einer
 * gemeldeten Mannschaft aus sieben Personen aus fünf Vereinen. Der Lauf läuft und ist gewertet -
 * so erscheint dieselbe Mannschaft in der Athleten-Anzeige, im Schiedsrichter-Board und in der
 * Platzierungsberechnung der Urkunde.
 */
fun TestComprehensionScope<JEnv>.seedClubChain(): SeededClubChain {
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
            createdAt = CHAIN_SEED_TIME,
            updatedAt = CHAIN_SEED_TIME,
            // Der Begriff bleibt in der Veranstaltung stehen (Startlisten nutzen ihn weiter) -
            // in den umgestellten Anzeigen darf er nicht mehr auftauchen.
            mixedTeamTerm = "Renngemeinschaft",
        )
    )

    !COMPETITION.insert(
        CompetitionRecord(
            id = competitionId,
            event = eventId,
            createdAt = CHAIN_SEED_TIME,
            updatedAt = CHAIN_SEED_TIME,
        )
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
        CompetitionSetupRecord(
            competitionProperties = propertiesId,
            createdAt = CHAIN_SEED_TIME,
            updatedAt = CHAIN_SEED_TIME,
        )
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
            startTime = CHAIN_SEED_TIME,
            createdAt = CHAIN_SEED_TIME,
            updatedAt = CHAIN_SEED_TIME,
            currentlyRunning = true,
        )
    )

    val registeringClubId = seedClub(REGISTERING_CLUB)
    val mainzId = seedClub(MAINZ)
    val flensburgId = seedClub(FLENSBURG)
    val nuertingenId = seedClub(NUERTINGEN)

    !EVENT_REGISTRATION.insert(
        EventRegistrationRecord(
            id = eventRegistrationId,
            event = eventId,
            club = registeringClubId,
            createdAt = CHAIN_SEED_TIME,
            updatedAt = CHAIN_SEED_TIME,
        )
    )
    !COMPETITION_REGISTRATION.insert(
        CompetitionRegistrationRecord(
            id = registrationId,
            eventRegistration = eventRegistrationId,
            competition = competitionId,
            club = registeringClubId,
            name = "Mix Nord",
            createdAt = CHAIN_SEED_TIME,
            updatedAt = CHAIN_SEED_TIME,
        )
    )

    // Die Rolle bestimmt die Reihenfolge in der Kette (siehe CompetitionMatchTeamRepo).
    val crew = listOf(
        seedCrewMember(registrationId, "1. Ruderer", "Albers", clubId = mainzId),
        seedCrewMember(registrationId, "2. Ruderer", "Bruns", clubId = registeringClubId, externalClubName = MARBURG),
        seedCrewMember(registrationId, "3. Ruderer", "Cordes", clubId = flensburgId),
        seedCrewMember(registrationId, "4. Ruderer", "Dohm", clubId = mainzId),
        seedCrewMember(registrationId, "5. Steuermann", "Evers", clubId = registeringClubId, externalClubName = "N.N."),
        seedCrewMember(registrationId, "6. Ruderer", "Fischer", clubId = nuertingenId),
        seedCrewMember(registrationId, "7. Ruderer", "Groth", clubId = registeringClubId, externalClubName = ROSTOCK),
    )

    !COMPETITION_MATCH_TEAM.insert(
        CompetitionMatchTeamRecord(
            id = UUID.randomUUID(),
            competitionMatch = matchId,
            competitionRegistration = registrationId,
            startNumber = 1,
            place = 1,
            placesCalculated = true,
            createdAt = CHAIN_SEED_TIME,
            updatedAt = CHAIN_SEED_TIME,
        )
    )

    return SeededClubChain(
        eventId = eventId,
        competitionId = competitionId,
        registrationId = registrationId,
        roundId = roundId,
        matchId = matchId,
        registeringClubId = registeringClubId,
        crew = crew,
    )
}

fun TestComprehensionScope<JEnv>.seedClub(name: String): UUID {
    val id = UUID.randomUUID()
    !CLUB.insert(ClubRecord(id = id, name = name, createdAt = CHAIN_SEED_TIME, updatedAt = CHAIN_SEED_TIME))
    return id
}

/**
 * Eine Person mit ihrer Rolle im Boot. [externalClubName] gesetzt heißt Gastruderer: dann zählt
 * dieser Freitext, nicht [clubId] - der ist bei Gastruderern der meldende Verein, weil eine Person
 * ohne eigenen Vereins-Datensatz gar nicht in der Datenbank stehen kann. Genau daran hängt der
 * Fall: keine Anzeige darf hier auf [clubId] zurückfallen.
 *
 * [role] wird als eigener `named_participant` angelegt; ohne [registrationId] bleibt die Person
 * ungemeldet - so sieht ein Vereinsmitglied aus, das erst über eine Ummeldung ins Boot kommt.
 */
fun TestComprehensionScope<JEnv>.seedCrewMember(
    registrationId: UUID?,
    role: String,
    lastName: String,
    clubId: UUID,
    externalClubName: String? = null,
): SeededCrewMember {
    val participantId = UUID.randomUUID()
    val namedParticipantId = UUID.randomUUID()

    !NAMED_PARTICIPANT.insert(
        NamedParticipantRecord(
            id = namedParticipantId,
            name = role,
            createdAt = CHAIN_SEED_TIME,
            updatedAt = CHAIN_SEED_TIME,
        )
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
            createdAt = CHAIN_SEED_TIME,
            updatedAt = CHAIN_SEED_TIME,
        )
    )
    if (registrationId != null) {
        !COMPETITION_REGISTRATION_NAMED_PARTICIPANT.insert(
            CompetitionRegistrationNamedParticipantRecord(
                competitionRegistration = registrationId,
                namedParticipant = namedParticipantId,
                participant = participantId,
            )
        )
    }

    return SeededCrewMember(
        participantId = participantId,
        namedParticipantId = namedParticipantId,
        role = role,
        lastName = lastName,
    )
}
