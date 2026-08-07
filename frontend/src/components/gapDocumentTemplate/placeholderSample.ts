import {GapDocumentPlaceholderType} from '@api/types.gen.ts'

/**
 * Beispieltexte für die Editor-Vorschau. Die Werte entsprechen `previewValues` in
 * `GapDocumentTemplateService.kt` — Editor und die serverseitig gerenderte Vorschau sollen
 * dieselben Beispieldaten zeigen.
 */
const SAMPLE_VALUES: Record<Exclude<GapDocumentPlaceholderType, 'FREE_TEXT'>, string> = {
    FIRST_NAME: 'Max',
    LAST_NAME: 'Mustermann',
    FULL_NAME: 'Max Mustermann',
    RESULT: '3492 m',
    EVENT_NAME: 'Summer Sport Festival',
    PLACE: '1. Platz',
    COMPETITION_NAME: 'CF 1x Frauen-Einer',
    COMPETITION_SHORT_NAME: 'CF 1x',
    CLUB_NAME: 'Ruderklub Flensburg',
    TEAM_NAME: 'Flensburg I',
    EVENT_DATE: '16.–17. August 2026',
    EVENT_LOCATION: 'Flensburg',
}

/** Beispieltext für einen Platzhalter im Editor. Bei `FREE_TEXT` der eingegebene Text, oder,
 * solange keiner eingegeben wurde, `freeTextFallback` — vom Aufrufer übersetzt übergeben, da diese
 * Stelle als UI-Text auch englische und dänische Nutzer erreicht (siehe
 * `gap.document.placeholder.staticText`). */
export const sampleTextFor = (
    type: GapDocumentPlaceholderType,
    staticText: string | undefined,
    freeTextFallback: string,
): string => {
    if (type === 'FREE_TEXT') {
        return staticText || freeTextFallback
    }
    return SAMPLE_VALUES[type]
}
