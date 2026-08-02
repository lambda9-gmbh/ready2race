/**
 * Der Ausscheidungsgrund einer Mannschaft ist in der Datenbank Freitext. Alle Quellen — manuelle
 * Eingabe, Datei-Import und die Zeitnahme — schreiben ihre Kürzel dort hinein. Diese Funktionen
 * lesen den Status für die Anzeige daraus, ohne den gespeicherten Text zu verändern.
 */

export type MatchResultStatus = 'DNS' | 'DNF' | 'DQ'

export const matchResultStatuses: readonly MatchResultStatus[] = ['DNS', 'DNF', 'DQ']

const statusAliases: Record<string, MatchResultStatus> = {
    DNS: 'DNS',
    DNF: 'DNF',
    DQ: 'DQ',
    DSQ: 'DQ',
    DISQ: 'DQ',
}

/**
 * Nur ein führendes Kürzel zählt: "DNS beantragt, Wertung strittig" ist eine Notiz, kein Status.
 * Hinter dem Kürzel dürfen übliche Trennzeichen stehen, bevor die Notiz beginnt.
 */
const leadingStatus = /^(DNS|DNF|DISQ|DSQ|DQ)\b[\s:;,.\-–—]*/i

export const matchResultStatus = (
    failedReason: string | null | undefined,
): {status: MatchResultStatus | null; note: string | null} => {
    const reason = failedReason?.trim()

    if (!reason) {
        return {status: null, note: null}
    }

    const match = leadingStatus.exec(reason)

    if (!match) {
        return {status: null, note: reason}
    }

    return {
        status: statusAliases[match[1].toUpperCase()],
        note: reason.slice(match[0].length).trim() || null,
    }
}

/**
 * Beschriftung für Ergebnislisten: das erkannte Kürzel führt, die Notiz steht dahinter. Ohne
 * Kürzel bleibt es beim übergebenen Ersatztext ("Ausgeschieden") wie vor der Statuserkennung.
 */
export const failedLabel = (failedReason: string | null | undefined, fallback: string): string => {
    const {status, note} = matchResultStatus(failedReason)
    const head = status ?? fallback

    return note ? `${head} (${note})` : head
}

/** Gegenstück zu {@link matchResultStatus} für die Ergebniseingabe. */
export const formatFailedReason = (
    status: MatchResultStatus | null,
    note: string | null | undefined,
): string | null => [status, note?.trim()].filter(Boolean).join(' ') || null
