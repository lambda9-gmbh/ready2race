import {MatchByeDto} from '@api/types.gen.ts'

/** Ein Satz als Datensatz — die aufrufende Komponente übersetzt und malt, wie bei [MatchChip]. */
export type ByeExplanation = {key: string; values?: Record<string, string>}

/**
 * Der Satz, der unter einem Freilos steht — die Übersetzung der Ursache in etwas, das am Steg
 * jemand lesen kann.
 *
 * Ohne Mannschaftsnamen fällt auch eine als Abmeldung gemeldete Ursache auf den neutralen Satz
 * zurück: „Freilos wegen Abmeldung —" ohne Namen behauptet eine Ursache und liefert sie nicht.
 * Dasselbe Prinzip wie im Backend, wo der Freitext-Grund bei mehreren Abmeldungen entfällt.
 */
export const byeExplanation = (bye: MatchByeDto | null | undefined): ByeExplanation | null => {
    if (!bye) return null
    if (bye.cause === 'DEREGISTRATION' && bye.teamName) {
        return bye.reason
            ? {
                  key: 'event.match.bye.deregistrationWithReason',
                  values: {team: bye.teamName, reason: bye.reason},
              }
            : {key: 'event.match.bye.deregistration', values: {team: bye.teamName}}
    }
    return {key: 'event.match.bye.noOpponent'}
}
