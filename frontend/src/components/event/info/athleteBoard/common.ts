import {TFunction} from 'i18next'

export const formatClockTime = (value: string) =>
    new Date(value).toLocaleTimeString(undefined, {hour: '2-digit', minute: '2-digit'})

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
