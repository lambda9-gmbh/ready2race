package de.lambda9.ready2race.backend.app.eventRegistration

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.club.CHAIN_SEED_TIME
import de.lambda9.ready2race.backend.app.club.seedClub
import de.lambda9.ready2race.backend.app.email.entity.EmailAttachment
import de.lambda9.ready2race.backend.app.eventRegistration.boundary.RegistrationMailService
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationError
import de.lambda9.ready2race.backend.app.eventRegistration.entity.RegistrationMailRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.EMAIL
import de.lambda9.ready2race.backend.database.generated.tables.references.EMAIL_ATTACHMENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Die Rundmail an die Melder gegen ein echtes Postgres.
 *
 * Zwei Dinge lassen sich ohne Datenbank nicht festhalten und gehen genau hier schief: der
 * Empfängerkreis hängt an `event_registration.created_by` (ein `on delete set null` macht daraus
 * jederzeit NULL, ohne dass die Meldung verschwindet), und der Versand legt je Empfänger eine
 * eigene Zeile in `email` samt kopierter Anhänge an - eine Schleife, die man leicht so baut, dass
 * alle Mails denselben Text oder nur die letzte einen Anhang bekommt.
 *
 * Die Vorrichtung ist absichtlich zweiveranstaltungig: dieselben Vereine und derselbe Nutzer
 * melden auch zu einer zweiten Regatta. Wer den Join über den Verein statt über die Meldung führt,
 * besteht alle Einzelprüfungen und schreibt am Regattatag trotzdem die halbe Nachbarregatta an.
 */
class RegistrationMailTest {

    private val kielEmail = "kiel@example.org"
    private val flensburgEmail = "flensburg@example.org"

    private data class SeededMailEvent(
        val eventId: UUID,
        val otherEventId: UUID,
        val kielRegistrationId: UUID,
        val flensburgRegistrationId: UUID,
        val rostockRegistrationId: UUID,
    )

    private fun TestComprehensionScope<JEnv>.seedUser(email: String, firstname: String, lastname: String): UUID {
        val id = UUID.randomUUID()
        !APP_USER.insert(
            AppUserRecord(
                id = id,
                email = email,
                password = "x",
                firstname = firstname,
                lastname = lastname,
                language = "de",
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
        return id
    }

    private fun TestComprehensionScope<JEnv>.seedRegistration(eventId: UUID, clubId: UUID, createdBy: UUID?): UUID {
        val id = UUID.randomUUID()
        !EVENT_REGISTRATION.insert(
            EventRegistrationRecord(
                id = id,
                event = eventId,
                club = clubId,
                createdAt = CHAIN_SEED_TIME,
                createdBy = createdBy,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
        return id
    }

    /**
     * Zwei Regatten, drei meldende Vereine. Rostock hat gemeldet, sein Nutzer ist aber gelöscht -
     * das ist der Fall, den die Liste zeigen und der Versand ablehnen muss.
     */
    private fun TestComprehensionScope<JEnv>.seedMailEvent(): SeededMailEvent {
        val eventId = UUID.randomUUID()
        val otherEventId = UUID.randomUUID()
        !EVENT.insert(
            EventRecord(id = eventId, name = "Testregatta", createdAt = CHAIN_SEED_TIME, updatedAt = CHAIN_SEED_TIME)
        )
        !EVENT.insert(
            EventRecord(
                id = otherEventId,
                name = "Nachbarregatta",
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )

        val kielId = seedClub("Kieler Ruder-Club")
        val flensburgId = seedClub("Ruderklub Flensburg")
        val rostockId = seedClub("Rostocker Ruder-Club")

        val kielUser = seedUser(kielEmail, "Kai", "Kieler")
        val flensburgUser = seedUser(flensburgEmail, "Frauke", "Flensburger")

        // Dieselben Vereine und derselbe Nutzer melden auch nebenan - diese Meldungen dürfen in
        // der Liste der Testregatta nicht auftauchen.
        seedRegistration(otherEventId, kielId, kielUser)
        seedRegistration(otherEventId, flensburgId, flensburgUser)

        return SeededMailEvent(
            eventId = eventId,
            otherEventId = otherEventId,
            kielRegistrationId = seedRegistration(eventId, kielId, kielUser),
            flensburgRegistrationId = seedRegistration(eventId, flensburgId, flensburgUser),
            rostockRegistrationId = seedRegistration(eventId, rostockId, null),
        )
    }

    private fun request(
        registrationIds: List<UUID>,
        additionalAddresses: List<String> = emptyList(),
        subject: String = "Hinweise zum Regattatag",
        body: String = "Moin,\nbitte denkt an die Waage.",
    ) = RegistrationMailRequest(
        subject = subject,
        body = body,
        registrationIds = registrationIds,
        additionalAddresses = additionalAddresses,
    )

    @Test
    fun theRecipientListNamesEveryRegistrantOfTheEventAndNobodyElse() = testComprehension {
        val seeded = seedMailEvent()

        val recipients = (!RegistrationMailService.getRecipients(seeded.eventId)).data

        assertContentEquals(
            listOf("Kieler Ruder-Club", "Rostocker Ruder-Club", "Ruderklub Flensburg"),
            recipients.map { it.clubName },
            "alle Melder der Testregatta, nach Verein sortiert - und nur die",
        )

        val kiel = recipients.single { it.clubName == "Kieler Ruder-Club" }
        assertEquals(seeded.kielRegistrationId, kiel.registrationId)
        assertEquals(kielEmail, kiel.email)
        assertEquals("Kai Kieler", kiel.name)

        // Gelöschter Melder: die Meldung bleibt sichtbar, die Adresse fehlt. Ohne diese Zeile
        // wüsste die Regattaleitung nicht, dass dieser Verein die Rundmail nicht bekommt.
        val rostock = recipients.single { it.clubName == "Rostocker Ruder-Club" }
        assertNull(rostock.email)
        assertNull(rostock.name)
    }

    @Test
    fun everySelectedRecipientGetsAnOwnMailIncludingTheAttachments() = testComprehension {
        val seeded = seedMailEvent()
        val sender = seedUser("leitung@example.org", "Ilka", "Regattaleitung")

        val result = !RegistrationMailService.send(
            eventId = seeded.eventId,
            request = request(
                registrationIds = listOf(seeded.kielRegistrationId, seeded.flensburgRegistrationId),
                additionalAddresses = listOf("presse@example.org"),
            ),
            attachments = listOf(
                EmailAttachment(name = "zeitplan.pdf", data = byteArrayOf(1, 2, 3)),
                EmailAttachment(name = "anfahrt.pdf", data = byteArrayOf(4, 5)),
            ),
            userId = sender,
        )

        assertEquals(3, result.dto.enqueued)

        val mails = !Jooq.query { selectFrom(EMAIL).orderBy(EMAIL.RECIPIENT).fetch() }
        assertContentEquals(
            listOf("flensburg@example.org", "kiel@example.org", "presse@example.org"),
            mails.map { it.recipient },
        )
        assertTrue(mails.all { it.subject == "Hinweise zum Regattatag" })
        assertTrue(mails.none { it.bodyIsHtml }, "der frei getippte Text geht als Klartext raus")

        // Ein Fehlversand muss am Tag danach noch nachweisbar sein - deshalb überlebt die Zeile
        // das Senden, statt wie sonst sofort von deleteSent() geholt zu werden.
        assertTrue(mails.all { it.keepAfterSending == RegistrationMailService.KEEP_AFTER_SENDING_SECONDS })

        mails.forEach { mail ->
            val attachments = !Jooq.query {
                selectFrom(EMAIL_ATTACHMENT)
                    .where(EMAIL_ATTACHMENT.EMAIL.eq(mail.id))
                    .orderBy(EMAIL_ATTACHMENT.NAME)
                    .fetch()
            }
            assertContentEquals(
                listOf("anfahrt.pdf", "zeitplan.pdf"),
                attachments.map { it.name },
                "auch die letzte Mail trägt noch beide Anhänge (${mail.recipient})",
            )
            assertContentEquals(byteArrayOf(4, 5), attachments.first().data)
        }
    }

    @Test
    fun placeholdersAreFilledPerRecipientAndStayEmptyForFreeAddresses() = testComprehension {
        val seeded = seedMailEvent()
        val sender = seedUser("leitung@example.org", "Ilka", "Regattaleitung")

        !RegistrationMailService.send(
            eventId = seeded.eventId,
            request = request(
                registrationIds = listOf(seeded.kielRegistrationId),
                additionalAddresses = listOf("presse@example.org"),
                subject = "##event##: Hinweise",
                body = "Hallo ##recipient##, für ##club## zur ##event##.",
            ),
            attachments = emptyList(),
            userId = sender,
        )

        val byRecipient = (!Jooq.query { selectFrom(EMAIL).fetch() }).associateBy { it.recipient }

        assertEquals("Testregatta: Hinweise", byRecipient[kielEmail]?.subject)
        assertEquals("Hallo Kai Kieler, für Kieler Ruder-Club zur Testregatta.", byRecipient[kielEmail]?.body)

        // Freie Adressen kennen weder Person noch Verein. Die Platzhalter müssen trotzdem
        // verschwinden - sonst steht "##club##" wörtlich in der Mail an die Presse.
        assertEquals("Hallo , für  zur Testregatta.", byRecipient["presse@example.org"]?.body)
    }

    @Test
    fun aRegistrationWithoutAUserIsRefusedInsteadOfSilentlySkipped() = testComprehension {
        val seeded = seedMailEvent()
        val sender = seedUser("leitung@example.org", "Ilka", "Regattaleitung")

        assertKIOFails(EventRegistrationError.MailRecipientWithoutUser(seeded.rostockRegistrationId)) {
            RegistrationMailService.send(
                eventId = seeded.eventId,
                request = request(listOf(seeded.kielRegistrationId, seeded.rostockRegistrationId)),
                attachments = emptyList(),
                userId = sender,
            )
        }

        assertEquals(0, !Jooq.query { fetchCount(EMAIL) }, "entweder alle oder keine")
    }

    @Test
    fun aRegistrationOfAnotherEventIsRefused() = testComprehension {
        val seeded = seedMailEvent()
        val sender = seedUser("leitung@example.org", "Ilka", "Regattaleitung")

        val foreign = !Jooq.query {
            selectFrom(EVENT_REGISTRATION)
                .where(EVENT_REGISTRATION.EVENT.eq(seeded.otherEventId))
                .fetch()
                .first()
                .id
        }

        assertKIOFails(EventRegistrationError.MailRecipientNotFound(foreign)) {
            RegistrationMailService.send(
                eventId = seeded.eventId,
                request = request(listOf(seeded.kielRegistrationId, foreign)),
                attachments = emptyList(),
                userId = sender,
            )
        }
    }

    @Test
    fun sendingWithoutAnyRecipientFails() = testComprehension {
        val seeded = seedMailEvent()
        val sender = seedUser("leitung@example.org", "Ilka", "Regattaleitung")

        assertKIOFails(EventRegistrationError.MailWithoutRecipients) {
            RegistrationMailService.send(
                eventId = seeded.eventId,
                request = request(emptyList()),
                attachments = emptyList(),
                userId = sender,
            )
        }
    }

    @Test
    fun anUnusableFreeAddressFails() = testComprehension {
        val seeded = seedMailEvent()
        val sender = seedUser("leitung@example.org", "Ilka", "Regattaleitung")

        assertKIOFails(EventRegistrationError.MailAddressInvalid("presse(at)example.org")) {
            RegistrationMailService.send(
                eventId = seeded.eventId,
                request = request(
                    registrationIds = listOf(seeded.kielRegistrationId),
                    additionalAddresses = listOf("presse(at)example.org"),
                ),
                attachments = emptyList(),
                userId = sender,
            )
        }
    }

    @Test
    fun anAddressIsWrittenOnlyOnce() = testComprehension {
        val seeded = seedMailEvent()
        val sender = seedUser("leitung@example.org", "Ilka", "Regattaleitung")

        // Die Regattaleitung tippt eine Adresse dazu, die schon als Melder in der Liste steht -
        // und schreibt sie versehentlich groß.
        val result = !RegistrationMailService.send(
            eventId = seeded.eventId,
            request = request(
                registrationIds = listOf(seeded.kielRegistrationId),
                additionalAddresses = listOf("KIEL@example.org"),
            ),
            attachments = emptyList(),
            userId = sender,
        )

        assertEquals(1, result.dto.enqueued)
        assertEquals(1, !Jooq.query { fetchCount(EMAIL) })
        // Der Melder gewinnt: seine Mail ist die mit Namen und Verein in den Platzhaltern.
        assertEquals(kielEmail, !Jooq.query { selectFrom(EMAIL).fetchSingle().recipient })
    }
}
