import {CompetitionMatchDto, CompetitionMatchTeamDto} from '@api/types.gen.ts'

export type EditMatchTeam = {
    registrationId: string
    startNumber: string
}
export type EditMatchForm = {
    selectedMatchDto: CompetitionMatchDto | null
    startTime: string
    teams: EditMatchTeam[]
}

export const emptyEditMatchForm: EditMatchForm = {
    selectedMatchDto: null,
    startTime: '',
    teams: [],
}

export const mapTeamDtoToFormTeamData = (teams: CompetitionMatchTeamDto[]): EditMatchTeam[] =>
    [...teams]
        .sort((a, b) => a.startNumber - b.startNumber)
        .map(team => ({
            registrationId: team.registrationId,
            startNumber: team.startNumber?.toString() ?? '',
        }))

/**
 * Belegt jedes Feld von [EditMatchForm]: `reset()` ersetzt die Formularwerte vollständig, ein
 * fehlendes Feld wäre also leer — und eine leere Startzeit löscht sie beim Speichern in der
 * Datenbank.
 */
export const mapMatchDtoToEditMatchForm = (match: CompetitionMatchDto): EditMatchForm => ({
    selectedMatchDto: match,
    startTime: match.startTime ?? '',
    teams: mapTeamDtoToFormTeamData(match.teams),
})
