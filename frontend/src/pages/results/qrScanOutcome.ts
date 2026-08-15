import {CheckQrCodeResponse} from '@api/types.gen.ts'

/**
 * Was ein gescannter Bändchen-Code bedeutet. Die Unterscheidung ist bewusst eine reine
 * Funktion, damit sie ohne Netz und Komponenten testbar bleibt:
 *
 * - 'unlinked': Der Code ist noch keiner Person zugeordnet (das Backend antwortet mit
 *   204 ohne Inhalt — siehe QrCodeAppService.loadQrCode). Das ist ein erwarteter Zustand
 *   vor dem Check-in in der Meldestelle und darf nicht wie ein Fehler aussehen.
 * - 'error': Eine echte Fehlerantwort (4xx/5xx) — hier bleibt die Fehlermeldung richtig.
 * - 'user': Ein Helferband ohne Teilnahme, führt auf die Ergebnisseite ohne Merken.
 * - 'participant': Ein Teilnahmeband, wird gemerkt und öffnet „Mein Event".
 */
export type QrScanOutcome =
    | {kind: 'unlinked'}
    | {kind: 'error'}
    | {kind: 'user'; eventId: string}
    | {kind: 'participant'; eventId: string}

type CheckQrCodeResult = {
    data?: CheckQrCodeResponse
    error?: unknown
    status: number
}

export const qrScanOutcome = ({data, error, status}: CheckQrCodeResult): QrScanOutcome => {
    if (error !== undefined) {
        return {kind: 'error'}
    }
    // 204 trägt keinen Body — hey-api liefert dann data === undefined. Beides zusammen
    // abzufragen schützt auch vor einem Client, der den leeren Body anders meldet.
    if (status === 204 || data == null) {
        return {kind: 'unlinked'}
    }
    if (data.type === 'User') {
        return {kind: 'user', eventId: data.eventId}
    }
    return {kind: 'participant', eventId: data.eventId}
}
