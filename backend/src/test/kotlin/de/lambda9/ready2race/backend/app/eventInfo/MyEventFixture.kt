package de.lambda9.ready2race.backend.app.eventInfo

import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionDeregistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationNamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventHasParticipantRequirementRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.NamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantHasRequirementForEventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRequirementRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.QrCodesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_DEREGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION_NAMED_PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_HAS_PARTICIPANT_REQUIREMENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.NAMED_PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_REQUIREMENT
import de.lambda9.ready2race.backend.database.generated.tables.references.QR_CODES
import de.lambda9.ready2race.backend.database.generated.tables.references.SUBSTITUTION
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime
import java.util.UUID

/**
 * Baut eine kleine, aber vollständige Veranstaltung auf: eine Person, die in einem Wettkampf
 * einem Lauf zugeordnet ist, in einem zweiten Wettkampf gemeldet ist ohne gesetzten Lauf, und
 * daneben ein fremder Lauf sowie ein Helfer-QR-Code, an die sie nicht herankommen darf.
 *
 * Alles läuft in einem einzigen [Jooq.query]-Block: `testComprehension` dreht die Transaktion
 * nach jedem Test zurück, ein Aufbau in mehreren Schritten brächte nichts als Reihenfolge-
 * Fallen. Namen und Kennungen tragen einen Zufallsanteil, weil ein Test (fremde Veranstaltung)
 * zwei Aufbauten nebeneinander braucht und Vereinsnamen veranstaltungsübergreifend eindeutig
 * sein müssen.
 */
object MyEventFixture {

    data class Fixture(
        val eventId: UUID,
        val participantId: UUID,
        val participantName: String,
        val participantQrCode: String,
        val appUserQrCode: String,
        val ownMatchId: UUID,
        val foreignMatchId: UUID,
        val unscheduledCompetitionId: UUID,
        val publicRequirementName: String,
        val deregisteredTeamName: String,
        val coxQrCode: String,
        val coxRequirementName: String,
        val internalNote: String,
        // Ab hier alles, was für Ersatzleute gebraucht wird: eine Auswechslung hängt an
        // (Meldung, Runde, Rolle), und geprüft wird sie aus der Sicht mehrerer Personen.
        val ownRegistrationId: UUID,
        val foreignRegistrationId: UUID,
        val racedRoundId: UUID,
        /** Die Runde des zweiten Wettkampfs - nur da, um "rundenscharf" prüfen zu können. */
        val unscheduledRoundId: UUID,
        val rowerRoleId: UUID,
        /** Die zweite Rolle im selben Boot - an ihr hängt `coxRequirementName`. */
        val coxRoleId: UUID,
        val coxId: UUID,
        val teamMateQrCode: String,
        /** Vereinsmitglied ohne eigene Meldung - nur über eine Einwechslung kommt sie in ein Boot. */
        val reserveId: UUID,
        val reserveName: String,
        val reserveQrCode: String,
        val strangerId: UUID,
        val strangerName: String,
        val strangerQrCode: String,
    )

    fun create(): JIO<Fixture> = Jooq.query {
        val now = LocalDateTime.now()
        val tag = UUID.randomUUID().toString().take(8)

        val clubId = UUID.randomUUID()
        insertInto(CLUB).set(ClubRecord(id = clubId, name = "Ruderclub $tag", createdAt = now, updatedAt = now))
            .execute()

        val eventId = UUID.randomUUID()
        insertInto(EVENT).set(EventRecord(id = eventId, name = "Regatta $tag", createdAt = now, updatedAt = now))
            .execute()

        // Wettkampf 1: hier ist die Testperson einem Lauf zugeordnet.
        val racedCompetitionId = insertCompetition(eventId, identifier = "1-$tag", name = "Vierer $tag", now = now)
        // Wettkampf 2: gemeldet, aber kein Lauf gesetzt.
        val unscheduledCompetitionId =
            insertCompetition(eventId, identifier = "2-$tag", name = "Einer $tag", now = now)

        val namedParticipantId = UUID.randomUUID()
        insertInto(NAMED_PARTICIPANT).set(
            NamedParticipantRecord(
                id = namedParticipantId,
                name = "Ruderin",
                createdAt = now,
                updatedAt = now,
            )
        ).execute()

        // Zweite Rolle im selben Boot: nur so lässt sich prüfen, dass eine an sie gebundene
        // Bedingung nicht bei allen anderen auch auftaucht.
        val coxRoleId = UUID.randomUUID()
        insertInto(NAMED_PARTICIPANT).set(
            NamedParticipantRecord(
                id = coxRoleId,
                name = "Steuerfrau $tag",
                createdAt = now,
                updatedAt = now,
            )
        ).execute()

        val participantName = "Mia Musterfrau $tag"
        val participantId = insertParticipant(clubId, "Mia", "Musterfrau $tag", now)
        val teamMateId = insertParticipant(clubId, "Lea", "Mitfahrerin $tag", now)
        val coxId = insertParticipant(clubId, "Nele", "Steuerfrau $tag", now)
        val strangerName = "Tom Fremd $tag"
        val strangerId = insertParticipant(clubId, "Tom", "Fremd $tag", now)
        // Nirgends gemeldet: sie steht nur dann in einem Boot, wenn sie eingewechselt wird.
        val reserveName = "Ida Ersatz $tag"
        val reserveId = insertParticipant(clubId, "Ida", "Ersatz $tag", now)

        val eventRegistrationId = UUID.randomUUID()
        insertInto(EVENT_REGISTRATION).set(
            EventRegistrationRecord(
                id = eventRegistrationId,
                event = eventId,
                club = clubId,
                createdAt = now,
                updatedAt = now,
            )
        ).execute()

        val ownRegistrationId =
            insertRegistration(eventRegistrationId, racedCompetitionId, clubId, "Boot A $tag", now)
        val foreignRegistrationId =
            insertRegistration(eventRegistrationId, racedCompetitionId, clubId, "Boot B $tag", now)
        val unscheduledRegistrationId =
            insertRegistration(eventRegistrationId, unscheduledCompetitionId, clubId, "Boot C $tag", now)
        // Vor der Auslosung zurückgezogen: kein Lauf, aber auch kein Warten mehr.
        val deregisteredTeamName = "Boot D $tag"
        val deregisteredRegistrationId =
            insertRegistration(eventRegistrationId, unscheduledCompetitionId, clubId, deregisteredTeamName, now)
        insertInto(COMPETITION_DEREGISTRATION).set(
            CompetitionDeregistrationRecord(
                competitionRegistration = deregisteredRegistrationId,
                reason = "Krankheit",
                createdAt = now,
                updatedAt = now,
            )
        ).execute()

        insertCrew(ownRegistrationId, namedParticipantId, participantId)
        insertCrew(ownRegistrationId, namedParticipantId, teamMateId)
        insertCrew(ownRegistrationId, coxRoleId, coxId)
        insertCrew(foreignRegistrationId, namedParticipantId, strangerId)
        insertCrew(unscheduledRegistrationId, namedParticipantId, participantId)
        insertCrew(deregisteredRegistrationId, namedParticipantId, participantId)

        val participantQrCode = insertParticipantQrCode(eventId, participantId, "teilnehmer-$tag", now)
        val coxQrCode = insertParticipantQrCode(eventId, coxId, "steuerfrau-$tag", now)
        val teamMateQrCode = insertParticipantQrCode(eventId, teamMateId, "mitfahrerin-$tag", now)
        val strangerQrCode = insertParticipantQrCode(eventId, strangerId, "fremd-$tag", now)
        val reserveQrCode = insertParticipantQrCode(eventId, reserveId, "ersatz-$tag", now)

        // Helfer-Code: derselbe Aufbau, nur zeigt er auf einen app_user. Er muss genauso ins
        // Leere laufen wie ein erfundener Code.
        val appUserId = UUID.randomUUID()
        insertInto(APP_USER).set(
            AppUserRecord(
                id = appUserId,
                email = "helfer-$tag@example.org",
                password = "x",
                firstname = "Hilfs",
                lastname = "Kraft",
                language = "de",
                createdAt = now,
                updatedAt = now,
            )
        ).execute()

        val appUserQrCode = "helfer-$tag"
        insertInto(QR_CODES).set(
            QrCodesRecord(
                id = UUID.randomUUID(),
                qrCodeId = appUserQrCode,
                appUser = appUserId,
                event = eventId,
                createdAt = now,
            )
        ).execute()

        val ownMatchId = insertMatch(racedCompetitionId, "Lauf 1", executionOrder = 1, now = now)
        insertMatchTeam(ownMatchId, ownRegistrationId, now)

        val foreignMatchId = insertMatch(racedCompetitionId, "Lauf 2", executionOrder = 2, now = now)
        insertMatchTeam(foreignMatchId, foreignRegistrationId, now)

        val publicRequirementName = "Startberechtigung $tag"
        val publicRequirementId =
            insertRequirement(eventId, publicRequirementName, publiclyVisible = true, now = now)
        insertRequirement(eventId, "Interne Prüfung $tag", publiclyVisible = false, now = now)

        // Freigegeben, aber nur für die Steuerfrau: darf bei der Ruderin nicht auftauchen.
        val coxRequirementName = "Steuerprüfung $tag"
        insertRequirement(
            eventId,
            coxRequirementName,
            publiclyVisible = true,
            now = now,
            namedParticipantId = coxRoleId,
        )

        val internalNote = "interne-notiz-$tag"
        insertInto(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT).set(
            ParticipantHasRequirementForEventRecord(
                participant = participantId,
                event = eventId,
                participantRequirement = publicRequirementId,
                createdAt = now,
                note = internalNote,
            )
        ).execute()

        Fixture(
            eventId = eventId,
            participantId = participantId,
            participantName = participantName,
            participantQrCode = participantQrCode,
            appUserQrCode = appUserQrCode,
            ownMatchId = ownMatchId,
            foreignMatchId = foreignMatchId,
            unscheduledCompetitionId = unscheduledCompetitionId,
            publicRequirementName = publicRequirementName,
            deregisteredTeamName = deregisteredTeamName,
            coxQrCode = coxQrCode,
            coxRequirementName = coxRequirementName,
            internalNote = internalNote,
            ownRegistrationId = ownRegistrationId,
            foreignRegistrationId = foreignRegistrationId,
            racedRoundId = findRoundId(racedCompetitionId),
            unscheduledRoundId = findRoundId(unscheduledCompetitionId),
            rowerRoleId = namedParticipantId,
            coxRoleId = coxRoleId,
            coxId = coxId,
            teamMateQrCode = teamMateQrCode,
            reserveId = reserveId,
            reserveName = reserveName,
            reserveQrCode = reserveQrCode,
            strangerId = strangerId,
            strangerName = strangerName,
            strangerQrCode = strangerQrCode,
        )
    }

    /**
     * Eine Auswechslung in der Runde [roundId]: [participantOut] verlässt die Meldung
     * [registrationId], [participantIn] rückt in derselben Rolle nach.
     *
     * Ein Tausch zwischen zwei Booten sind zwei Aufrufe mit aufeinanderfolgendem
     * [orderForRound] - genau so legt [de.lambda9.ready2race.backend.app.substitution.boundary.SubstitutionService.addSubstitution]
     * ihn ab, und nur dann erkennt ihn die Auflösung wieder als Tausch. Die Reihenfolge ist je
     * Runde eindeutig, nicht je Meldung.
     */
    fun substitute(
        registrationId: UUID,
        roundId: UUID,
        namedParticipantId: UUID,
        participantOut: UUID,
        participantIn: UUID,
        orderForRound: Long,
    ): JIO<Unit> = Jooq.query {
        val now = LocalDateTime.now()
        insertInto(SUBSTITUTION).set(
            SubstitutionRecord(
                id = UUID.randomUUID(),
                competitionRegistration = registrationId,
                competitionSetupRound = roundId,
                participantOut = participantOut,
                participantIn = participantIn,
                namedParticipant = namedParticipantId,
                orderForRound = orderForRound,
                createdAt = now,
                updatedAt = now,
            )
        ).execute()
    }

    private fun org.jooq.DSLContext.insertParticipantQrCode(
        eventId: UUID,
        participantId: UUID,
        code: String,
        now: LocalDateTime,
    ): String {
        insertInto(QR_CODES).set(
            QrCodesRecord(
                id = UUID.randomUUID(),
                qrCodeId = code,
                participant = participantId,
                event = eventId,
                createdAt = now,
            )
        ).execute()
        return code
    }

    /** Die einzige Runde eines Wettkampfs - der Aufbau legt je Wettkampf genau eine an. */
    private fun org.jooq.DSLContext.findRoundId(competitionId: UUID): UUID =
        select(COMPETITION_SETUP_ROUND.ID)
            .from(COMPETITION_SETUP_ROUND)
            .join(COMPETITION_PROPERTIES).on(COMPETITION_PROPERTIES.ID.eq(COMPETITION_SETUP_ROUND.COMPETITION_SETUP))
            .where(COMPETITION_PROPERTIES.COMPETITION.eq(competitionId))
            .fetchOne(COMPETITION_SETUP_ROUND.ID)!!

    /**
     * Wettkampf samt Eigenschaften und Ablauf. Der Ablauf (`competition_setup` + eine Runde) muss
     * sein, weil die Lauf-Abfrage über genau diese Kette vom Lauf zum Wettkampf zurückfindet.
     */
    private fun org.jooq.DSLContext.insertCompetition(
        eventId: UUID,
        identifier: String,
        name: String,
        now: LocalDateTime,
    ): UUID {
        val competitionId = UUID.randomUUID()
        insertInto(COMPETITION).set(
            CompetitionRecord(id = competitionId, event = eventId, createdAt = now, updatedAt = now)
        ).execute()

        val propertiesId = UUID.randomUUID()
        insertInto(COMPETITION_PROPERTIES).set(
            CompetitionPropertiesRecord(
                id = propertiesId,
                competition = competitionId,
                identifier = identifier,
                name = name,
            )
        ).execute()

        insertInto(COMPETITION_SETUP).set(
            CompetitionSetupRecord(competitionProperties = propertiesId, createdAt = now, updatedAt = now)
        ).execute()

        insertInto(COMPETITION_SETUP_ROUND).set(
            CompetitionSetupRoundRecord(
                id = UUID.randomUUID(),
                competitionSetup = propertiesId,
                name = "Hauptrunde",
                required = true,
                useDefaultSeeding = true,
                placesOption = "ASCENDING",
            )
        ).execute()

        return competitionId
    }

    private fun org.jooq.DSLContext.insertParticipant(
        clubId: UUID,
        firstname: String,
        lastname: String,
        now: LocalDateTime,
    ): UUID {
        val id = UUID.randomUUID()
        insertInto(PARTICIPANT).set(
            ParticipantRecord(
                id = id,
                club = clubId,
                firstname = firstname,
                lastname = lastname,
                year = 1990,
                gender = Gender.F,
                createdAt = now,
                updatedAt = now,
            )
        ).execute()
        return id
    }

    private fun org.jooq.DSLContext.insertRegistration(
        eventRegistrationId: UUID,
        competitionId: UUID,
        clubId: UUID,
        name: String,
        now: LocalDateTime,
    ): UUID {
        val id = UUID.randomUUID()
        insertInto(COMPETITION_REGISTRATION).set(
            CompetitionRegistrationRecord(
                id = id,
                eventRegistration = eventRegistrationId,
                competition = competitionId,
                club = clubId,
                name = name,
                createdAt = now,
                updatedAt = now,
            )
        ).execute()
        return id
    }

    private fun org.jooq.DSLContext.insertCrew(
        registrationId: UUID,
        namedParticipantId: UUID,
        participantId: UUID,
    ) {
        insertInto(COMPETITION_REGISTRATION_NAMED_PARTICIPANT).set(
            CompetitionRegistrationNamedParticipantRecord(
                competitionRegistration = registrationId,
                namedParticipant = namedParticipantId,
                participant = participantId,
            )
        ).execute()
    }

    /** Legt Ablauf-Lauf und Durchführungs-Lauf an; die Kennung beider ist dieselbe UUID. */
    private fun org.jooq.DSLContext.insertMatch(
        competitionId: UUID,
        name: String,
        executionOrder: Int,
        now: LocalDateTime,
    ): UUID {
        val roundId = findRoundId(competitionId)

        val matchId = UUID.randomUUID()
        insertInto(COMPETITION_SETUP_MATCH).set(
            CompetitionSetupMatchRecord(
                id = matchId,
                competitionSetupRound = roundId,
                weighting = executionOrder,
                name = name,
                executionOrder = executionOrder,
            )
        ).execute()

        insertInto(COMPETITION_MATCH).set(
            CompetitionMatchRecord(
                competitionSetupMatch = matchId,
                startTime = now.plusHours(2),
                createdAt = now,
                updatedAt = now,
            )
        ).execute()

        return matchId
    }

    private fun org.jooq.DSLContext.insertMatchTeam(
        matchId: UUID,
        registrationId: UUID,
        now: LocalDateTime,
    ) {
        insertInto(COMPETITION_MATCH_TEAM).set(
            CompetitionMatchTeamRecord(
                id = UUID.randomUUID(),
                competitionMatch = matchId,
                competitionRegistration = registrationId,
                startNumber = 1,
                createdAt = now,
                updatedAt = now,
            )
        ).execute()
    }

    /**
     * Bedingung samt Zuordnung zur Veranstaltung. Ohne [namedParticipantId] gilt sie für alle,
     * mit ihr nur für Personen in dieser Rolle.
     */
    private fun org.jooq.DSLContext.insertRequirement(
        eventId: UUID,
        name: String,
        publiclyVisible: Boolean,
        now: LocalDateTime,
        namedParticipantId: UUID? = null,
    ): UUID {
        val id = UUID.randomUUID()
        insertInto(PARTICIPANT_REQUIREMENT).set(
            ParticipantRequirementRecord(
                id = id,
                name = name,
                optional = false,
                createdAt = now,
                updatedAt = now,
                publiclyVisible = publiclyVisible,
            )
        ).execute()

        insertInto(EVENT_HAS_PARTICIPANT_REQUIREMENT).set(
            EventHasParticipantRequirementRecord(
                event = eventId,
                participantRequirement = id,
                namedParticipant = namedParticipantId,
                createdAt = now,
            )
        ).execute()

        return id
    }
}
