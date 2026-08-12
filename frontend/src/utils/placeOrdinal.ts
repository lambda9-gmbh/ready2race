/**
 * Platzierungen auf öffentlichen Anzeigen als englische Ordinale („1st", „2nd", „3rd").
 *
 * Bewusst OHNE i18n-Split der Suffixe: der Nutzer hat die englischen Ordinale am
 * 12.08.2026 ausdrücklich auch für de/da gewählt — die Boards hängen auf einer
 * internationalen Regatta, und „1st" ist in jeder Sprache auf einen Blick als
 * Platzierung lesbar, während eine nackte Zahl von der Startnummer nicht zu
 * unterscheiden ist. Geteilt von Boards, Sprecher-Kachel und Mein-Event-Ansicht.
 */
const ordinalSuffix = (place: number): string => {
    // 11–13 sind die englischen Ausnahmen: immer „th", egal welche Endziffer.
    const mod100 = Math.abs(place) % 100
    if (mod100 >= 11 && mod100 <= 13) return 'th'
    switch (Math.abs(place) % 10) {
        case 1:
            return 'st'
        case 2:
            return 'nd'
        case 3:
            return 'rd'
        default:
            return 'th'
    }
}

/** Als ein Textstück — für einzeilige Labels und Tooltips. */
export const formatPlaceOrdinal = (place: number): string => `${place}${ordinalSuffix(place)}`

/**
 * Ziffer und Suffix getrennt — für die große Platz-Typografie, die das Suffix klein
 * hochstellt (PlaceOrdinal-Komponente; Nutzer-Feedback vom 12.08.2026: „1st" in voller
 * Größe wirkt klobig).
 */
export const placeOrdinalParts = (place: number): {number: string; suffix: string} => ({
    number: `${place}`,
    suffix: ordinalSuffix(place),
})
