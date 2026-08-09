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
    // Eine Kette statt eines einzelnen Vereins: das Feld trägt die Vereine aller Athleten eines
    // Bootes. Der Kasten schneidet den Beispieltext hier ab — die gedruckte Urkunde tut das nicht,
    // sie lässt ihn über den Rand hinauslaufen. Wer hier anstößt, sieht ihn also im Editor gerade
    // noch enden und muss die Vorschau (Server-PDF) heranziehen, um das Ausmaß zu sehen.
    CLUB_NAME: 'Ruderklub Flensburg / Rostocker Ruderclub / Ruderclub Nürtingen',
    TEAM_NAME: 'Flensburg I',
    EVENT_DATE: '16.–17. August 2026',
    EVENT_LOCATION: 'Flensburg',
}

/** Beispieltext für einen Platzhalter im Editor. Bei `FREE_TEXT` der eingegebene Text, oder,
 * solange keiner eingegeben wurde, `freeTextFallback` — vom Aufrufer übersetzt übergeben, da diese
 * Stelle als UI-Text auch englische und dänische Nutzer erreicht (siehe
 * `gap.document.placeholder.staticText`). */
/** Der Trenner, mit dem das Backend die Vereine eines Bootes verkettet (`GapTextWrap`). */
export const CHAIN_SEPARATOR = ' / '

/**
 * Die Glieder einer Vereinskette. Die Vorschau setzt jedes Glied für sich unzerbrechlich und lässt
 * den Browser nur dazwischen umbrechen — dieselbe Regel, nach der der Renderer umbricht: ein
 * Vereinsname wird nie zerrissen. Ohne das bräche der Browser an Wortgrenzen und die Vorschau
 * zeigte einen Umbruch ("Marburger Ruderverein von" / "1911 e.V."), den es auf dem Papier nie gibt.
 *
 * Ein Text ohne Trenner ergibt genau ein Glied, bleibt also unverändert.
 */
export const chainSegments = (text: string): string[] => text.split(CHAIN_SEPARATOR)

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
