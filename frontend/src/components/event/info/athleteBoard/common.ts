import {TFunction} from 'i18next'

export const formatClockTime = (value: string) =>
    new Date(value).toLocaleTimeString(undefined, {hour: '2-digit', minute: '2-digit'})

/**
 * Restzeit bis zum Start, grob gerundet: ab einer Minute nur noch Minuten. Eine Sekundenzahl
 * neben einer Stundenangabe würde eine Genauigkeit vortäuschen, die der Zeitplan nicht hat.
 *
 * Steht hier und nicht in der Karte, weil „Mein Event" dieselbe Angabe zeigt und beide
 * Ansichten dieselbe Formulierung tragen müssen.
 */
export const formatRemaining = (seconds: number, t: TFunction) => {
    const total = Math.max(0, Math.floor(seconds))
    const minutes = Math.floor(total / 60)
    const rest = total % 60
    return minutes > 0
        ? `${minutes} ${t('event.info.athleteBoard.minutesUnit')}`
        : `${rest} ${t('event.info.athleteBoard.secondsUnit')}`
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
