package de.lambda9.ready2race.backend.app.participant

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.participant.boundary.ParticipantService
import de.lambda9.ready2race.backend.app.participant.control.ParticipantAdditionalClubRepo
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.app.club.seedClubChain
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationNamedParticipantRepo
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantError
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantSort
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantAdditionalClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Eine Person, mehrere Vereine (Migration V202608142000).
 *
 * Der Fall, der die Aufgabe ausgeloest hat: Bo Bothmann rudert in Flensburg und in Kiel. Bis
 * hierher legten beide Vereine ihn getrennt an, nur damit ihn beide melden konnten - mit zwei
 * Baendchen, zwei Aktivenpass-Pruefungen und zwei Ergebnisspuren.
 *
 * Geprueft wird die eine Grenze, die dabei nicht verrutschen darf: der Zweitverein SIEHT die
 * Person und MELDET sie, aber er AENDERT sie nicht.
 */
class ParticipantAdditionalClubTest {

    private val seedTime: LocalDateTime = LocalDateTime.of(2026, 8, 14, 12, 0)

    /** Der Stammverein und der Zweitverein - plus die Person, um die es geht. */
    private data class Seed(
        val homeClubId: UUID,
        val guestClubId: UUID,
        val strangerClubId: UUID,
        val participantId: UUID,
        val strangerParticipantId: UUID,
    )

    private fun TestComprehensionScope<JEnv>.club(name: String): UUID {
        val id = UUID.randomUUID()
        !CLUB.insert(ClubRecord(id = id, name = name, createdAt = seedTime, updatedAt = seedTime))
        return id
    }

    private fun TestComprehensionScope<JEnv>.participant(
        clubId: UUID,
        firstname: String,
        lastname: String,
        year: Int = 1990,
    ): UUID {
        val id = UUID.randomUUID()
        !PARTICIPANT.insert(
            ParticipantRecord(
                id = id,
                club = clubId,
                firstname = firstname,
                lastname = lastname,
                year = year,
                gender = Gender.M,
                external = false,
                phone = "0461 123456",
                email = "bo@example.org",
                createdAt = seedTime,
                updatedAt = seedTime,
            )
        )
        return id
    }

    private fun TestComprehensionScope<JEnv>.seed(): Seed {
        val homeClubId = club("Ruderklub Flensburg e.V.")
        val guestClubId = club("Erster Kieler Ruder-Club von 1862 e.V.")
        val strangerClubId = club("Rostocker Ruder-Club von 1885 e.V.")
        return Seed(
            homeClubId = homeClubId,
            guestClubId = guestClubId,
            strangerClubId = strangerClubId,
            participantId = participant(homeClubId, "Bo", "Bothmann"),
            strangerParticipantId = participant(strangerClubId, "Rolf", "Rostock"),
        )
    }

    private fun TestComprehensionScope<JEnv>.addToClub(participantId: UUID, clubId: UUID) {
        !ParticipantAdditionalClubRepo.create(
            ParticipantAdditionalClubRecord(
                participant = participantId,
                club = clubId,
                createdAt = seedTime,
                createdBy = null,
            )
        )
    }

    /** Ein Meldender des Vereins [clubId]: OWN-Recht, mehr nicht. */
    private fun user(clubId: UUID) = AppUserWithPrivilegesRecord(id = UUID.randomUUID(), club = clubId)

    private fun params(search: String? = null) = PaginationParameters<ParticipantSort>(
        limit = 50,
        offset = 0,
        sort = null,
        search = search,
    )

    private fun TestComprehensionScope<JEnv>.listFor(clubId: UUID): List<String> {
        val page = !ParticipantService.page(params(), clubId, user(clubId), Privilege.Scope.OWN)
        return page.data.map { it.lastname }
    }

    // ------------------------------------------------------------------------------------
    // Sehen
    // ------------------------------------------------------------------------------------

    @Test
    fun `der Zweitverein sieht die Person in seiner Personenliste`() = testComprehension {
        val s = seed()

        // Vorher: Bo steht nur bei seinem Stammverein.
        assertEquals(listOf("Bothmann"), listFor(s.homeClubId))
        assertEquals(emptyList(), listFor(s.guestClubId))

        addToClub(s.participantId, s.guestClubId)

        // Nachher steht er in beiden Listen - ein Datensatz, zwei Vereine.
        assertEquals(listOf("Bothmann"), listFor(s.homeClubId))
        assertEquals(listOf("Bothmann"), listFor(s.guestClubId))

        // Und weiterhin nicht bei einem Verein, der ihn nie eingetragen hat.
        assertEquals(listOf("Rostock"), listFor(s.strangerClubId))
    }

    /**
     * Der Zweitverein darf melden, nicht anrufen: Telefonnummer und E-Mail-Adresse bleiben beim
     * Stammverein.
     */
    @Test
    fun `der Zweitverein sieht die Kontaktdaten nicht`() = testComprehension {
        val s = seed()
        addToClub(s.participantId, s.guestClubId)

        val home = (!ParticipantService.page(params(), s.homeClubId, user(s.homeClubId), Privilege.Scope.OWN))
            .data.single()
        assertEquals("0461 123456", home.phone)
        assertEquals("bo@example.org", home.email)

        val guest = (!ParticipantService.page(params(), s.guestClubId, user(s.guestClubId), Privilege.Scope.OWN))
            .data.single()
        assertNull(guest.phone)
        assertNull(guest.email)

        // Wem die Person gehoert, steht trotzdem dabei - sonst waere in der Liste des
        // Zweitvereins nicht erkennbar, warum sie sich nicht bearbeiten laesst.
        assertEquals(s.homeClubId, guest.clubId)
        assertEquals("Ruderklub Flensburg e.V.", guest.clubName)
    }

    /** Die Zugehoerigkeit reist im DTO mit, damit die Pflegeoberflaeche sie anzeigen kann. */
    @Test
    fun `die weiteren Vereine stehen im Datensatz`() = testComprehension {
        val s = seed()
        addToClub(s.participantId, s.guestClubId)

        val dto = (!ParticipantService.page(params(), s.homeClubId, user(s.homeClubId), Privilege.Scope.OWN))
            .data.single()

        assertEquals(listOf(s.guestClubId), dto.additionalClubs.map { it.id })
        assertEquals(listOf("Erster Kieler Ruder-Club von 1862 e.V."), dto.additionalClubs.map { it.name })
    }

    // ------------------------------------------------------------------------------------
    // Melden
    // ------------------------------------------------------------------------------------

    @Test
    fun `der Zweitverein darf die Person melden`() = testComprehension {
        val s = seed()

        // Ohne Zugehoerigkeit weist die Meldeseite ab - das ist der Zustand von vorher.
        assertNull(!ParticipantRepo.findByIdAndClub(s.participantId, s.guestClubId))
        assertEquals(false, !ParticipantRepo.existsByIdAndClub(s.participantId, s.guestClubId))

        addToClub(s.participantId, s.guestClubId)

        assertNotNull(!ParticipantRepo.findByIdAndClub(s.participantId, s.guestClubId))
        assertEquals(true, !ParticipantRepo.existsByIdAndClub(s.participantId, s.guestClubId))

        // Der Stammverein verliert dadurch nichts.
        assertNotNull(!ParticipantRepo.findByIdAndClub(s.participantId, s.homeClubId))
    }

    /**
     * Der Ummelde-Vorrat: wer ueber den Zweitverein gemeldet wurde, muss dort auch ersetzt werden
     * koennen (siehe SubstitutionService.getPossibleSubstitutionsHelper).
     */
    @Test
    fun `der Vereinsbestand umfasst die Gaeste`() = testComprehension {
        val s = seed()
        assertEquals(emptyList(), (!ParticipantRepo.getByClubId(s.guestClubId)).map { it.lastname })

        addToClub(s.participantId, s.guestClubId)

        assertEquals(listOf("Bothmann"), (!ParticipantRepo.getByClubId(s.guestClubId)).map { it.lastname })
        assertEquals(listOf("Bothmann"), (!ParticipantRepo.getByClubId(s.homeClubId)).map { it.lastname })
    }

    // ------------------------------------------------------------------------------------
    // Stammdatenschutz - die wichtigste Grenze
    // ------------------------------------------------------------------------------------

    @Test
    fun `der Zweitverein darf die Stammdaten nicht aendern`() = testComprehension {
        val s = seed()
        addToClub(s.participantId, s.guestClubId)

        // Der Zweitverein sieht die Person (siehe oben) - aendern darf er sie trotzdem nicht.
        val changedByGuest = !ParticipantRepo.update(
            s.participantId,
            s.guestClubId,
            user(s.guestClubId),
            Privilege.Scope.OWN,
        ) { lastname = "Gekapert" }
        assertNull(changedByGuest)

        assertEquals("Bothmann", (!ParticipantRepo.get(s.participantId))!!.lastname)

        // Der Stammverein darf.
        val changedByHome = !ParticipantRepo.update(
            s.participantId,
            s.homeClubId,
            user(s.homeClubId),
            Privilege.Scope.OWN,
        ) { lastname = "Bothmann-Petersen" }
        assertNotNull(changedByHome)
        assertEquals("Bothmann-Petersen", (!ParticipantRepo.get(s.participantId))!!.lastname)
    }

    @Test
    fun `der Zweitverein darf die Person nicht loeschen`() = testComprehension {
        val s = seed()
        addToClub(s.participantId, s.guestClubId)

        assertEquals(0, !ParticipantRepo.delete(s.participantId, s.guestClubId, user(s.guestClubId), Privilege.Scope.OWN))
        assertNotNull(!ParticipantRepo.get(s.participantId))

        assertEquals(1, !ParticipantRepo.delete(s.participantId, s.homeClubId, user(s.homeClubId), Privilege.Scope.OWN))
        assertNull(!ParticipantRepo.get(s.participantId))
    }

    /**
     * Auch mit globalem Lesen bleibt der Schreibweg am Stammverein: ein Verwalter, der die Liste
     * des Zweitvereins offen hat, aendert dort nichts.
     */
    @Test
    fun `auch der Zweitverein-Pfad mit globalem Recht aendert nichts`() = testComprehension {
        val s = seed()
        addToClub(s.participantId, s.guestClubId)

        val changed = !ParticipantRepo.update(
            s.participantId,
            s.guestClubId,
            user(s.guestClubId),
            Privilege.Scope.GLOBAL,
        ) { lastname = "Gekapert" }

        assertNull(changed)
        assertEquals("Bothmann", (!ParticipantRepo.get(s.participantId))!!.lastname)
    }

    // ------------------------------------------------------------------------------------
    // Zugehoerigkeit pflegen
    // ------------------------------------------------------------------------------------

    @Test
    fun `nur der Stammverein pflegt die Zugehoerigkeit`() = testComprehension {
        val s = seed()
        // Der Systembenutzer statt einer erfundenen UUID: created_by zeigt auf app_user.
        val userId = SYSTEM_USER

        // Der Zweitverein kann sich nicht selbst eintragen: fuer ihn ist es nicht "seine" Person.
        assertKIOFails(ParticipantError.ParticipantNotFound) {
            ParticipantService.addAdditionalClub(s.participantId, s.guestClubId, s.strangerClubId, userId)
        }

        !ParticipantService.addAdditionalClub(s.participantId, s.homeClubId, s.guestClubId, userId)
        assertEquals(true, !ParticipantAdditionalClubRepo.exists(s.participantId, s.guestClubId))

        // Zweimal derselbe Verein: die Datenbank wuerde es ohnehin abweisen, der Service sagt
        // es freundlicher.
        assertKIOFails(ParticipantError.ClubAlreadyAdded) {
            ParticipantService.addAdditionalClub(s.participantId, s.homeClubId, s.guestClubId, userId)
        }

        // Der Stammverein gehoert nicht zusaetzlich in die Liste.
        assertKIOFails(ParticipantError.ClubIsHomeClub) {
            ParticipantService.addAdditionalClub(s.participantId, s.homeClubId, s.homeClubId, userId)
        }

        !ParticipantService.removeAdditionalClub(s.participantId, s.homeClubId, s.guestClubId)
        assertEquals(false, !ParticipantAdditionalClubRepo.exists(s.participantId, s.guestClubId))

        assertKIOFails(ParticipantError.ClubNotAdded) {
            ParticipantService.removeAdditionalClub(s.participantId, s.homeClubId, s.guestClubId)
        }
    }

    // ------------------------------------------------------------------------------------
    // Suche
    // ------------------------------------------------------------------------------------

    private fun TestComprehensionScope<JEnv>.event(crossClub: Boolean): UUID {
        val id = UUID.randomUUID()
        !EVENT.insert(
            EventRecord(
                id = id,
                name = "Testregatta",
                crossClubRegistration = crossClub,
                createdAt = seedTime,
                updatedAt = seedTime,
            )
        )
        return id
    }

    private fun TestComprehensionScope<JEnv>.search(eventId: UUID, clubId: UUID, term: String?): List<String> =
        (!ParticipantService.searchAcrossClubs(eventId, clubId, term)).data.map { it.lastname }

    @Test
    fun `ohne Schalter findet die Suche nichts`() = testComprehension {
        val s = seed()
        val eventId = event(crossClub = false)

        assertEquals(emptyList(), search(eventId, s.guestClubId, "Bothmann"))
        assertEquals(emptyList(), search(eventId, s.guestClubId, "Bo"))
    }

    @Test
    fun `mit Schalter findet die Suche ab zwei Zeichen`() = testComprehension {
        val s = seed()
        val eventId = event(crossClub = true)

        // Ohne Eingabe und mit einem einzelnen Zeichen bleibt die Suche stumm - sonst waere sie
        // eine durchblaetterbare Liste aller Personen aller Vereine.
        assertEquals(emptyList(), search(eventId, s.guestClubId, null))
        assertEquals(emptyList(), search(eventId, s.guestClubId, ""))
        assertEquals(emptyList(), search(eventId, s.guestClubId, "   "))
        assertEquals(emptyList(), search(eventId, s.guestClubId, "B"))

        // Zwei Zeichen genuegen - der Auftraggeber nennt genau dieses Beispiel.
        assertEquals(listOf("Bothmann"), search(eventId, s.guestClubId, "Bo"))
    }

    @Test
    fun `die Suche laesst den eigenen Bestand aus`() = testComprehension {
        val s = seed()
        val eventId = event(crossClub = true)

        // Fuer den Stammverein steht Bo schon in der regulaeren Liste.
        assertEquals(emptyList(), search(eventId, s.homeClubId, "Bo"))

        // Fuer den Zweitverein ist er ein Treffer - bis er eingetragen ist.
        assertEquals(listOf("Bothmann"), search(eventId, s.guestClubId, "Bo"))
        addToClub(s.participantId, s.guestClubId)
        assertEquals(emptyList(), search(eventId, s.guestClubId, "Bo"))
    }

    /** Der Treffer traegt genau das, was zum Melden noetig ist - und nichts darueber hinaus. */
    @Test
    fun `der Treffer traegt Name Jahrgang und Verein`() = testComprehension {
        val s = seed()
        val eventId = event(crossClub = true)

        val hit = (!ParticipantService.searchAcrossClubs(eventId, s.guestClubId, "Bo")).data.single()

        assertEquals("Bo", hit.firstname)
        assertEquals("Bothmann", hit.lastname)
        assertEquals(1990, hit.year)
        assertEquals("Ruderklub Flensburg e.V.", hit.clubName)
        // Telefonnummer und E-Mail-Adresse gibt es in diesem Typ gar nicht - genau das ist der
        // Grund fuer einen eigenen DTO statt eines ParticipantDto mit geleerten Feldern.
        assertTrue(hit::class.java.declaredFields.none { it.name == "phone" || it.name == "email" })
    }

    // ------------------------------------------------------------------------------------
    // Was NICHT gelockert wurde
    // ------------------------------------------------------------------------------------

    /**
     * Dieselbe Person zweimal im selben Wettkampf bleibt gesperrt - diese Sperre haengt an der
     * Meldung, nicht am Verein, und ist von der Mehrfach-Zugehoerigkeit unberuehrt. Sonst
     * koennte Bo ueber Flensburg UND ueber Kiel im selben Rennen sitzen.
     */
    @Test
    fun `dieselbe Person bleibt im selben Wettkampf gesperrt`() = testComprehension {
        val chain = seedClubChain()
        val member = chain.member("Albers")

        assertEquals(
            true,
            !CompetitionRegistrationNamedParticipantRepo.existsByParticipantIdAndCompetitionId(
                member.participantId,
                chain.competitionId,
            )
        )

        // Auch nachdem die Person einem weiteren Verein angehoert - die Sperre kennt gar keinen
        // Verein.
        addToClub(member.participantId, chain.registeringClubId)
        assertEquals(
            true,
            !CompetitionRegistrationNamedParticipantRepo.existsByParticipantIdAndCompetitionId(
                member.participantId,
                chain.competitionId,
            )
        )
    }
}
