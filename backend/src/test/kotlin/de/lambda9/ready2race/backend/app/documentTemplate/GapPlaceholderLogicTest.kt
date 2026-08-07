package de.lambda9.ready2race.backend.app.documentTemplate

import de.lambda9.ready2race.backend.app.documentTemplate.boundary.GapPlaceholderLogic
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderType
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapPlaceholder
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapPlaceholderValues
import de.lambda9.ready2race.backend.text.TextAlign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GapPlaceholderLogicTest {

    private fun placeholder(
        type: GapDocumentPlaceholderType,
        fontSize: Int? = null,
        bold: Boolean = false,
        italic: Boolean = false,
        staticText: String? = null,
    ) = GapPlaceholder(
        type = type,
        page = 1,
        relLeft = 0.1,
        relTop = 0.2,
        relWidth = 0.8,
        relHeight = 0.05,
        textAlign = TextAlign.CENTER,
        fontSize = fontSize,
        bold = bold,
        italic = italic,
        staticText = staticText,
    )

    private val values = GapPlaceholderValues(
        firstName = "Carina",
        lastName = "Hein",
        fullName = "Carina Hein",
        result = "33:17,7 min",
        eventName = "Deutsche Coastal Meisterschaften",
        place = "1. Platz",
        competitionName = "CF 1x Frauen-Einer",
        competitionShortName = "CF 1x",
        clubName = "RC Allemannia Hamburg v. 1866",
        teamName = null,
        eventDate = "16.–17. August 2025",
        eventLocation = "Flensburg",
    )

    @Test
    fun everyPlaceholderTypeYieldsNonBlankContentWhenEveryValueIsPopulated() {
        // Der alte Test hier prüfte nur, dass die Ergebnisliste so viele Einträge hat wie es
        // Platzhaltertypen gibt - das garantiert jede größenerhaltende `map`-Abbildung unabhängig
        // vom tatsächlichen Inhalt, und dupliziert außerdem eine Garantie, die der Compiler durch
        // das erschöpfende `when` in GapPlaceholderLogic.content ohnehin schon gibt. Stattdessen wird
        // hier geprüft, dass jeder Platzhaltertyp echten (nicht-leeren) Inhalt liefert, wenn alle
        // Werte gesetzt sind - das hätte einen vergessenen oder falsch verdrahteten Typ tatsächlich
        // auffliegen lassen.
        val fullyPopulatedValues = values.copy(teamName = "Flensburg I")
        val placeholders = GapDocumentPlaceholderType.entries.map { placeholder(it, staticText = "Fest") }

        val filled = GapPlaceholderLogic.fill(placeholders, fullyPopulatedValues)

        filled.forEachIndexed { index, additionalText ->
            assertTrue(
                additionalText.content.isNotBlank(),
                "Platzhaltertyp ${GapDocumentPlaceholderType.entries[index]} lieferte leeren Inhalt",
            )
        }
    }

    @Test
    fun contentComesFromValues() {
        val filled = GapPlaceholderLogic.fill(
            listOf(
                placeholder(GapDocumentPlaceholderType.PLACE),
                placeholder(GapDocumentPlaceholderType.COMPETITION_NAME),
                placeholder(GapDocumentPlaceholderType.FULL_NAME),
                placeholder(GapDocumentPlaceholderType.CLUB_NAME),
                placeholder(GapDocumentPlaceholderType.RESULT),
                placeholder(GapDocumentPlaceholderType.EVENT_LOCATION),
                placeholder(GapDocumentPlaceholderType.EVENT_DATE),
            ),
            values,
        )

        assertEquals(
            listOf(
                "1. Platz",
                "CF 1x Frauen-Einer",
                "Carina Hein",
                "RC Allemannia Hamburg v. 1866",
                "33:17,7 min",
                "Flensburg",
                "16.–17. August 2025",
            ),
            filled.map { it.content },
        )
    }

    @Test
    fun missingValueBecomesEmptyString() {
        val filled = GapPlaceholderLogic.fill(
            listOf(placeholder(GapDocumentPlaceholderType.TEAM_NAME)),
            values,
        )
        assertEquals("", filled.single().content)
    }

    @Test
    fun freeTextUsesStaticText() {
        val filled = GapPlaceholderLogic.fill(
            listOf(placeholder(GapDocumentPlaceholderType.FREE_TEXT, staticText = "Moritz Petri – Präsident")),
            values,
        )
        assertEquals("Moritz Petri – Präsident", filled.single().content)
    }

    @Test
    fun styleAttributesArePassedThrough() {
        val filled = GapPlaceholderLogic.fill(
            listOf(placeholder(GapDocumentPlaceholderType.PLACE, fontSize = 20, bold = true, italic = true)),
            values,
        ).single()

        assertEquals(20f, filled.fontSize)
        assertTrue(filled.bold)
        assertTrue(filled.italic)
        assertEquals(TextAlign.CENTER, filled.textAlign)
        assertEquals(1, filled.page)
    }

    @Test
    fun awardCertificateAllowsPlaceAndClub() {
        val allowed = GapDocumentType.AWARD_CERTIFICATE.allowedPlaceholders
        assertTrue(allowed.contains(GapDocumentPlaceholderType.PLACE))
        assertTrue(allowed.contains(GapDocumentPlaceholderType.CLUB_NAME))
    }

    @Test
    fun certificateOfParticipationKeepsItsOriginalPlaceholders() {
        // Die Teilnahmeurkunde soll sich durch die neuen Typen nicht verändern.
        assertEquals(
            setOf(
                GapDocumentPlaceholderType.FIRST_NAME,
                GapDocumentPlaceholderType.LAST_NAME,
                GapDocumentPlaceholderType.FULL_NAME,
                GapDocumentPlaceholderType.RESULT,
                GapDocumentPlaceholderType.EVENT_NAME,
            ),
            GapDocumentType.CERTIFICATE_OF_PARTICIPATION.allowedPlaceholders,
        )
    }
}
