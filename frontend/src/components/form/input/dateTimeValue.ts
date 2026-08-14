import {formatISO, isValid} from 'date-fns'

/**
 * Zwischenstand einer Tastatureingabe: Sobald eine Stelle fehlt oder nicht zusammenpasst
 * ("31.02."), meldet der Picker ein ungültiges Datum. Genau dieser Marker muss dann im
 * Formular landen - er ergibt auf dem Weg zurück ins Feld wieder ein ungültiges Datum, und
 * MUI hält zwei ungültige Werte für gleich (areDatesEqual). Nur deshalb bleiben die schon
 * getippten Stellen stehen; mit `undefined` oder `null` würde MUI die Anzeige bei jedem
 * Tastendruck auf die Platzhalter zurücksetzen und Tippen wäre unmöglich.
 */
export const invalidDateTimeValue = 'Invalid Date'

/**
 * Formularwert eines Datums-/Zeitfelds: lokale Zeit ohne Zonenangabe (2026-08-14T10:35:00),
 * so wie das Backend sie erwartet.
 */
export const dateTimeToFormValue = (value: Date | null): string | undefined => {
    if (value === null) {
        return undefined
    }
    // formatISO wirft bei einem ungültigen Datum eine RangeError - unbehandelt in einem
    // onChange-Handler reißt das die ganze Seite mit. Ungültige Eingaben blockiert
    // stattdessen die Prüfung des Pickers beim Abschicken.
    if (!isValid(value)) {
        return invalidDateTimeValue
    }
    return formatISO(value).slice(0, 19)
}

/**
 * "Jetzt" als Vorbelegung eines Datums-/Zeitfelds. `toLocaleString()` sieht zwar richtig aus,
 * ist aber kein Format, das der Picker zurücklesen kann - das Feld blieb dadurch leer.
 * Die Sekunden fallen weg, weil das Feld nur bis zur Minute geht: sonst stünde in der
 * Vorbelegung eine krumme Sekundenzahl, die niemand sieht und trotzdem gespeichert wird.
 */
export const nowAsFormValue = (): string => {
    const now = new Date()
    now.setSeconds(0, 0)
    return dateTimeToFormValue(now)!
}
