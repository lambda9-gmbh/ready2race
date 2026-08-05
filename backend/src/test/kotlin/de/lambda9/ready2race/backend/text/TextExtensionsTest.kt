package de.lambda9.ready2race.backend.text

import kotlin.test.Test
import kotlin.test.assertEquals

class TextExtensionsTest {

    @Test
    fun eventDateRangeKeepsItsEnDash() {
        // Der eigentliche Fund: der Filterbereich 0x200E..0x206F hat den Gedankenstrich mitgelöscht,
        // aus "5.–16. August 2026" (AwardCertificateLogic.formatEventDate) wurde auf jeder Urkunde
        // einer mehrtägigen Veranstaltung "5.16. August 2026".
        assertEquals("5.–16. August 2026", "5.–16. August 2026".sanitizeNonPrintable())
        assertEquals(
            "31. Juli – 1. August 2026",
            "31. Juli – 1. August 2026".sanitizeNonPrintable(),
        )
    }

    @Test
    fun visibleTypographyIsKept() {
        // Alle sichtbaren Zeichen aus dem früheren Filterbereich müssen erhalten bleiben:
        // Bindestrich, Non-Breaking Hyphen, Gedankenstrich, Geviertstrich, die typografischen
        // Anführungszeichen, Aufzählungspunkt, Auslassungspunkte, Guillemets, Kreuz/Doppelkreuz.
        val text = "‐‑‒–—―" +
            "‘’‚“”„‟" +
            "†‡•…‹›⁄"

        assertEquals(text, text.sanitizeNonPrintable())
    }

    @Test
    fun zeroWidthAndBidiCharactersStillDisappear() {
        // Der ursprüngliche Zweck bleibt erhalten: unsichtbarer Müll darf nicht ins PDF.
        // ZWSP, ZWNJ, ZWJ, LRM, RLM, LRE, RLO, Word Joiner, LRI, Nominal Digit Shapes.
        val text = "Ruder​klub‌‍ ‎Flens‏burg" +
            "‪‮⁠⁦⁯"

        assertEquals("Ruderklub Flensburg", text.sanitizeNonPrintable())
    }

    @Test
    fun isoControlCharactersStillDisappear() {
        assertEquals("AB", "A\u0007\u0000B".sanitizeNonPrintable())
    }

    @Test
    fun narrowNoBreakSpaceStaysASpace() {
        // 0x202F grenzt direkt an den Bidi-Bereich und wird oben auf ein normales Leerzeichen
        // abgebildet - es darf nicht mitgefiltert werden.
        assertEquals("1 000", "1 000".sanitizeNonPrintable())
        assertEquals("1 000", "1 000".sanitizeNonPrintable())
    }

    @Test
    fun foreignClubNameKeepsItsLetters() {
        // Sanitizing entfernt keine Buchstaben - die Abbildung auf ASCII passiert erst
        // schriftabhängig im PDF-Renderer (sanitizeForFont).
        val text = "AZS Łódź – Sekcja Wioślarska"

        assertEquals(text, text.sanitizeNonPrintable())
    }

    @Test
    fun alreadyPlainTextIsUnchanged() {
        val text = "Ruderklub Flensburg von 1877 e.V. - 1. Platz"

        assertEquals(text, text.sanitizeNonPrintable())
    }
}
