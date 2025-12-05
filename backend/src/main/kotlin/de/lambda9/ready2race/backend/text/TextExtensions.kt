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
            it !in 0x200E..0x206F
    }
    .joinToString("") { Character.toString(it) }