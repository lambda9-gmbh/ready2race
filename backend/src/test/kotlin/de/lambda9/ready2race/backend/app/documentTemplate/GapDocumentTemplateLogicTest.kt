package de.lambda9.ready2race.backend.app.documentTemplate

import de.lambda9.ready2race.backend.app.documentTemplate.boundary.GapDocumentTemplateLogic
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderType
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import de.lambda9.ready2race.backend.text.TextAlign
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GapDocumentTemplateLogicTest {

    private fun placeholder(page: Int, type: GapDocumentPlaceholderType = GapDocumentPlaceholderType.PLACE) =
        GapDocumentPlaceholderRequest(
            name = null,
            type = type,
            page = page,
            relLeft = 0.1,
            relTop = 0.2,
            relWidth = 0.8,
            relHeight = 0.05,
            textAlign = TextAlign.CENTER,
        )

    @Test
    fun awardCertificateWithPlaceholderOnPageTwoIsRejected() {
        // Der Serien-Renderer zeichnet nur Seite 1 je Urkunde, eine Siegerurkunde ist einseitig.
        assertFalse(
            GapDocumentTemplateLogic.placeholdersFitOnSinglePage(
                GapDocumentType.AWARD_CERTIFICATE,
                listOf(placeholder(1), placeholder(2)),
            )
        )
    }

    @Test
    fun awardCertificateWithEverythingOnPageOneIsAccepted() {
        assertTrue(
            GapDocumentTemplateLogic.placeholdersFitOnSinglePage(
                GapDocumentType.AWARD_CERTIFICATE,
                listOf(placeholder(1), placeholder(1)),
            )
        )
    }

    @Test
    fun certificateOfParticipationWithPlaceholderOnPageTwoIsAccepted() {
        // Die Teilnahmeurkunde darf mehrseitig sein, die Prüfung greift nur bei der Siegerurkunde.
        assertTrue(
            GapDocumentTemplateLogic.placeholdersFitOnSinglePage(
                GapDocumentType.CERTIFICATE_OF_PARTICIPATION,
                listOf(placeholder(1), placeholder(2)),
            )
        )
    }

    @Test
    fun certificateOfParticipationWithPlacePlaceholderIsRejected() {
        // PLACE gehört nicht zu den für die Teilnahmeurkunde erlaubten Platzhaltertypen (siehe
        // GapDocumentType.allowedPlaceholders) und würde beim Druck als leere Box erscheinen.
        assertFalse(
            GapDocumentTemplateLogic.placeholderTypesAreAllowed(
                GapDocumentType.CERTIFICATE_OF_PARTICIPATION,
                listOf(placeholder(1, GapDocumentPlaceholderType.PLACE)),
            )
        )
    }

    @Test
    fun awardCertificateWithPlacePlaceholderIsAccepted() {
        // Die Siegerurkunde erlaubt PLACE ausdrücklich.
        assertTrue(
            GapDocumentTemplateLogic.placeholderTypesAreAllowed(
                GapDocumentType.AWARD_CERTIFICATE,
                listOf(placeholder(1, GapDocumentPlaceholderType.PLACE)),
            )
        )
    }

    @Test
    fun templateTypeMismatchIsRejected() {
        // Eine Teilnahmeurkunden-Vorlage darf nicht unter AWARD_CERTIFICATE eingehängt werden - sonst
        // würden beim Druck nur die zufällig überlappenden Platzhalter befüllt, ohne Fehlermeldung.
        assertFalse(
            GapDocumentTemplateLogic.templateTypeMatches(
                GapDocumentType.CERTIFICATE_OF_PARTICIPATION,
                GapDocumentType.AWARD_CERTIFICATE,
            )
        )
    }

    @Test
    fun matchingTemplateTypeIsAccepted() {
        assertTrue(
            GapDocumentTemplateLogic.templateTypeMatches(
                GapDocumentType.AWARD_CERTIFICATE,
                GapDocumentType.AWARD_CERTIFICATE,
            )
        )
    }

    @Test
    fun certificateOfParticipationWithOnlyItsOwnTypesIsAccepted() {
        // Alle fünf für die Teilnahmeurkunde erlaubten Typen zusammen sind zulässig.
        assertTrue(
            GapDocumentTemplateLogic.placeholderTypesAreAllowed(
                GapDocumentType.CERTIFICATE_OF_PARTICIPATION,
                listOf(
                    placeholder(1, GapDocumentPlaceholderType.FIRST_NAME),
                    placeholder(1, GapDocumentPlaceholderType.LAST_NAME),
                    placeholder(1, GapDocumentPlaceholderType.FULL_NAME),
                    placeholder(1, GapDocumentPlaceholderType.RESULT),
                    placeholder(1, GapDocumentPlaceholderType.EVENT_NAME),
                ),
            )
        )
    }
}
