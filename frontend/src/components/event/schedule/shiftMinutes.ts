import {formRegexInteger} from '@utils/helpers.ts'

/**
 * Der Minuten-Wert des Verschiebe-Dialogs lebt im Formular als TEXT, nicht als Zahl.
 *
 * Die frühere Variante parste je Tastendruck (`Number(e.target.value)`): Aus dem Zwischenzustand
 * „-" wurde damit NaN, das Feld zeigte den Unsinn sofort an, und negative Minuten (Zeitplan nach
 * VORN ziehen) waren gar nicht erst eintippbar. Deshalb bleibt der Tippzustand jetzt unangetastet
 * Text - inklusive „-" allein - und geparst wird erst beim Absenden. Ob der Text dann eine
 * gültige (auch negative) Ganzzahl ist, prüft die Integer-Pattern-Validierung des Feldes
 * (formRegexInteger, dieselbe Regel wie hier).
 */
export const parseShiftMinutes = (text: string): number | null => {
    const trimmed = text.trim()
    return formRegexInteger.test(trimmed) ? Number(trimmed) : null
}
