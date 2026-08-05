package de.lambda9.ready2race.backend.text

import kotlin.streams.toList

fun String.sanitizeNonPrintable() = codePoints()
    .toList()
    .map { codePoint ->
        when (codePoint) {
            // Replace various Unicode spaces with regular space
            0x00A0, // Non-breaking space
            0x2002, // En space
            0x2003, // Em space
            0x2004, // Three-per-em space
            0x2005, // Four-per-em space
            0x2006, // Six-per-em space
            0x2007, // Figure space
            0x2008, // Punctuation space
            0x2009, // Thin space
            0x200A, // Hair space
            0x202F, // Narrow no-break space
            0x205F, // Medium mathematical space
            0x3000  // Ideographic space
                -> 0x0020 // Regular space

            else -> codePoint
        }
    }
    .filter {
        !Character.isISOControl(it) &&
            Character.UnicodeBlock.of(it) != null &&
            !isInvisibleFormatCharacter(it)
    }
    .joinToString("") { Character.toString(it) }

/**
 * Unsichtbare Steuer- und Formatierungszeichen, die im PDF nur Müll erzeugen (keine Glyphe, aber
 * je nach Schrift eine Ersatzdarstellung oder ein Kodierungsfehler) und deshalb ersatzlos entfallen.
 *
 * Vorher stand hier der Bereich `0x200E..0x206F` am Stück. Der greift viel zu weit: dazwischen
 * liegen lauter *sichtbare* Zeichen — Bindestrich U+2010, Gedankenstrich U+2013, Geviertstrich
 * U+2014, die typografischen Anführungszeichen U+2018/2019/201C/201D/201E, der Aufzählungspunkt
 * U+2022, die Auslassungspunkte U+2026 und die Guillemets U+2039/203A. Die wurden aus jedem
 * PDF-Text gelöscht, weshalb aus „5.–16. August 2026" auf der Urkunde „5.16. August 2026" wurde.
 *
 * Gefiltert wird deshalb nur noch, was tatsächlich keine Breite und keine Glyphe hat:
 *
 * - `0x200B..0x200F` — Zero Width Space, Zero Width Non-Joiner, Zero Width Joiner sowie die
 *   Richtungsmarken LRM/RLM. Alle nulldimensional; 0x200B..0x200D kamen bisher sogar durch.
 * - `0x202A..0x202E` — Bidi-Einbettungen und -Overrides (LRE, RLE, PDF, LRO, RLO). Bewusst nicht
 *   bis 0x202F: das schmale geschützte Leerzeichen wird oben schon auf ein normales abgebildet und
 *   soll als Leerzeichen erhalten bleiben. Der Bereich davor (0x2020..0x2029) bleibt ebenfalls
 *   außen vor — Kreuz, Doppelkreuz, Aufzählungspunkte und Auslassungspunkte sind sichtbar.
 * - `0x2060..0x206F` — Word Joiner, die unsichtbaren Operatoren, die Bidi-Isolate LRI/RLI/FSI/PDI
 *   und die veralteten Formatierungszeichen ab 0x206A. Durchgehend Kategorie Cf bzw. unbelegt,
 *   also nichts Sichtbares.
 *
 * Alles andere bleibt stehen und wird — falls die Zielschrift es nicht kodieren kann — erst im
 * PDF-Renderer durch `sanitizeForFont` auf eine lesbare ASCII-Entsprechung abgebildet.
 */
private fun isInvisibleFormatCharacter(codePoint: Int): Boolean =
    codePoint in 0x200B..0x200F ||
        codePoint in 0x202A..0x202E ||
        codePoint in 0x2060..0x206F