import {TFunction} from 'i18next'

export const formatClockTime = (value: string) =>
    new Date(value).toLocaleTimeString(undefined, {hour: '2-digit', minute: '2-digit'})

/** Tag und Monat ohne Jahr — genug, um "heute" von "nächste Woche" zu unterscheiden. */
export const formatShortDate = (value: string) =>
    new Date(value).toLocaleDateString(undefined, {day: '2-digit', month: '2-digit'})

/** Gleicher Kalendertag in der lokalen Zeitzone. */
export const isSameDay = (a: Date, b: Date): boolean =>
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()

/**
 * Ab dieser Entfernung trägt ein Countdown nichts mehr bei: Statt einer sechsstelligen
 * Minutenzahl zeigt die Karte dann das Datum. Ein Lauf, der morgen stattfindet, braucht
 * keine Restzeit — er braucht ein Datum.
 */
export const COUNTDOWN_MAX_SECONDS = 24 * 60 * 60

/**
 * Restzeit bis zum Start, gestuft nach Größenordnung.
 *
 * Ohne Stundenstufe entstand für einen Lauf in einer Woche die Anzeige "in 9886 min" —
 * formal richtig, praktisch unlesbar. Aufrufer prüfen zusätzlich COUNTDOWN_MAX_SECONDS
 * und zeigen jenseits davon das Datum statt einer Restzeit.
 */
export const formatRemaining = (seconds: number, t: TFunction): string => {
    const total = Math.max(0, Math.floor(seconds))
    const hours = Math.floor(total / 3600)
    const minutes = Math.floor((total % 3600) / 60)

    if (hours > 0) {
        const h = `${hours} ${t('event.info.athleteBoard.hoursUnit')}`
        return minutes > 0 ? `${h} ${minutes} ${t('event.info.athleteBoard.minutesUnit')}` : h
    }
    if (minutes > 0) {
        return `${minutes} ${t('event.info.athleteBoard.minutesUnit')}`
    }
    return `${total % 60} ${t('event.info.athleteBoard.secondsUnit')}`
}

/**
 * Anzeigename einer Mannschaft. Fehlt der gepflegte Name, tritt die Nummer der Mannschaft
 * an seine Stelle — der Verein allein unterscheidet sonst zwei Boote desselben Vereins nicht.
 */
export const teamLabel = (
    team: {clubName?: string | null; teamName?: string | null; teamNumber?: number | null},
    t: TFunction,
): string => {
    const name =
        team.teamName ??
        (team.teamNumber != null
            ? t('event.info.athleteBoard.teamNumber', {number: team.teamNumber})
            : null)
    return [team.clubName, name].filter(Boolean).join(' | ')
}
