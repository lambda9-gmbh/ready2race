import {UseFormReturn} from 'react-hook-form-mui'
import {
    CompetitionSetupDto,
    CompetitionSetupGroupStatisticEvaluationDto,
    CompetitionSetupMatchDto,
    CompetitionSetupRoundDto,
    CompetitionSetupTemplateDto,
    CompetitionSetupTemplateRequest,
} from '@api/types.gen.ts'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'

// Form Types

export type CompetitionSetupForm = {
    name?: string
    description?: string
    rounds: Array<FormSetupRound>
}
export type FormSetupRound = {
    name: string
    required: boolean
    matches: Array<FormSetupMatch>
    groups: Array<FormSetupGroup>
    statisticEvaluations?: Array<CompetitionSetupGroupStatisticEvaluationDto>
    useDefaultSeeding: boolean
    places: Array<{
        roundOutcome: number
        place: number
    }>
    isGroupRound: boolean
    useStartTimeOffsets: boolean
}
export type FormSetupMatch = {
    duplicatable: boolean
    weighting: number | null // in round: number; in group: null
    teams: string // String so it's easier to work with '' as an empty field instead of undefined
    name?: string
    participants: Array<{seed: number}> // in round 1 the list will be empty
    position: number // Will be translated to the array order in the dto
    startTimeOffset?: number
}
export type FormSetupGroup = {
    duplicatable: boolean
    weighting: number
    teams: string // String so it's easier to work with '' as an empty field instead of undefined
    name?: string
    matches: Array<FormSetupMatch>
    participants: Array<{seed: number}> // in round 1 the list will be empty
    matchTeams: number
}

export type CompetitionSetupMatchOrGroupProps = {
    formContext: UseFormReturn<CompetitionSetupForm>
    roundIndex: number
    fieldInfo: {index: number; id: string} // The index is the ORIGINAL index that refers to the fieldArray - It may differ from the displayed order
    roundHasDuplicatable: boolean
    outcomes: number[]
    teamCounts: {thisRoundWithoutThis: number; nextRound: number}
    useDefaultSeeding: boolean
    participantFunctions: ParticipantFunctions
    useStartTimeOffsets: boolean
}

export type ParticipantFunctions = {
    findLowestMissingParticipant: (yetUnregisteredParticipants: number[]) => number
    updateRoundParticipants: (repeatForPreviousRound: boolean, nextRoundTeams: number) => void
    setParticipantValuesForThis: (participants: number[]) => void
    updatePreviousRoundParticipants: (thisRoundTeams: number) => void
    updatePlaces: (updateThisRound: boolean, newTeamsCount?: number) => void
}

// Form map functions

export function mapFormToCompetitionSetupDto(formData: CompetitionSetupForm): CompetitionSetupDto {
    return {
        rounds: mapFormRoundsToDtoRounds(formData),
    }
}

export function mapFormToCompetitionSetupTemplateRequest(
    formData: CompetitionSetupForm,
): CompetitionSetupTemplateRequest {
    return {
        name: formData.name ?? '',
        description: takeIfNotEmpty(formData.description),
        rounds: mapFormRoundsToDtoRounds(formData),
    }
}

export function mapFormRoundsToDtoRounds(
    formData: CompetitionSetupForm,
): Array<CompetitionSetupRoundDto> {
    return formData.rounds.map(round => ({
        name: round.name,
        required: round.required,
        matches: !round.isGroupRound
            ? [...round.matches]
                  .sort((a, b) => a.position - b.position)
                  .map(match => mapFormMatchToDtoMatch(match, round.useStartTimeOffsets))
            : undefined,
        groups: round.isGroupRound
            ? round.groups?.map(group => ({
                  duplicatable: group.duplicatable,
                  weighting: group.weighting,
                  teams: group.teams !== '' ? Number(group.teams) : undefined,
                  name: group.name,
                  matches: group.matches.map(match =>
                      mapFormMatchToDtoMatch(match, round.useStartTimeOffsets, group.matchTeams),
                  ),
                  participants: group.participants.map(participant => participant.seed),
              }))
            : undefined,
        statisticEvaluations: round.statisticEvaluations,
        useDefaultSeeding: round.useDefaultSeeding,
        places: round.places,
    }))
}

function mapFormMatchToDtoMatch(
    formMatch: FormSetupMatch,
    useStartTimeOffsets: boolean,
    setTeamsValue?: number,
): CompetitionSetupMatchDto {
    return {
        duplicatable: formMatch.duplicatable,
        weighting: formMatch.weighting ?? undefined,
        teams:
            setTeamsValue === undefined // Groups provide a set value for the teams since all matches in one group need to have the same amount of participants
                ? formMatch.teams !== ''
                    ? Number(formMatch.teams)
                    : undefined
                : setTeamsValue,
        name: formMatch.name,
        participants: formMatch.participants.map(p => p.seed),
        startTimeOffset: useStartTimeOffsets ? formMatch.startTimeOffset : undefined,
    }
}

export function mapCompetitionSetupDtoToForm(dto: CompetitionSetupDto): CompetitionSetupForm {
    return {
        rounds: mapDtoRoundsToFormRounds(dto.rounds),
    }
}

export function mapCompetitionSetupTemplateDtoToForm(
    dto: CompetitionSetupTemplateDto,
): CompetitionSetupForm {
    return {
        name: dto.name,
        description: dto.description ?? '',
        rounds: mapDtoRoundsToFormRounds(dto.rounds),
    }
}

function mapDtoRoundsToFormRounds(dtoRounds: Array<CompetitionSetupRoundDto>) {
    return dtoRounds.map(round => ({
        name: round.name,
        required: round.required,
        matches: round.matches?.map((match, index) => mapDtoMatchToFormMatch(match, index)) ?? [],
        groups:
            round.groups?.map(group => ({
                duplicatable: group.duplicatable,
                weighting: group.weighting,
                teams: group.teams?.toString() ?? '',
                name: group.name,
                matches: group.matches.map((match, index) => mapDtoMatchToFormMatch(match, index)),
                participants: group.participants.map(participant => ({seed: participant})),
                matchTeams: group.matches[0]?.teams ?? 0,
            })) ?? [],
        statisticEvaluations: round.statisticEvaluations,
        useDefaultSeeding: round.useDefaultSeeding,
        places: round.places,
        isGroupRound: round.groups !== undefined,
        // If a match has an offset, useStartTimeOffsets is set to true
        useStartTimeOffsets:
            (round.matches?.filter(m => m.startTimeOffset !== undefined).length ?? 0) > 0 ||
            (round.groups
                ?.map(g => g.matches)
                .flat()
                .filter(m => m.startTimeOffset !== undefined).length ?? 0) > 0,
    }))
}

function mapDtoMatchToFormMatch(matchDto: CompetitionSetupMatchDto, order: number): FormSetupMatch {
    return {
        duplicatable: matchDto.duplicatable,
        weighting: matchDto.weighting ?? null,
        teams: matchDto.teams?.toString() ?? '',
        name: matchDto.name,
        participants: matchDto.participants?.map(participant => ({seed: participant})),
        position: order,
        startTimeOffset: matchDto.startTimeOffset,
    }
}

export const getWeightings = (matchCount: number) => {
    if (matchCount < 1) return []
    else if (matchCount === 1) return [1]

    const rounds = Math.ceil(Math.log(matchCount) / Math.log(2))

    let matches: number[][] = [[1, 2]]

    // If that weighting does not exist in the maxMatchIndex range, it is replaced by -1
    const getIfInRange = (weighting: number) => {
        if (weighting > matchCount) return -1

        return weighting
    }

    for (let round = 1; round < rounds; round++) {
        const roundMatches: number[][] = []
        const sum = Math.pow(2, round + 1) + 1

        for (let i = 0; i < matches.length; i++) {
            let home = getIfInRange(matches[i][0])
            let away = getIfInRange(sum - matches[i][0])
            roundMatches.push([home, away])
            home = getIfInRange(sum - matches[i][1])
            away = getIfInRange(matches[i][1])
            roundMatches.push([home, away])
        }
        matches = roundMatches
    }

    return matches.flat().filter(v => v !== -1)
}

export const setParticipantValuesForMatchOrGroup = (
    formContext: UseFormReturn<CompetitionSetupForm>,
    roundIndex: number,
    isGroupRound: boolean,
    index: number,
    participants: number[],
) => {
    formContext.setValue(
        isGroupRound
            ? `rounds.${roundIndex}.groups.${index}.participants`
            : `rounds.${roundIndex}.matches.${index}.participants`,
        participants.map(v => ({seed: v})),
    )

    participants.forEach((_, i) => {
        formContext.setValue(
            isGroupRound
                ? `rounds.${roundIndex}.groups.${index}.participants.${i}.seed`
                : `rounds.${roundIndex}.matches.${index}.participants.${i}.seed`,
            participants[i],
        )
    })
}

export const fillSeedingList = (
    groupOrMatchCount: number,
    highestTeamCount: number,
    teams: number[],
    nextRoundTeams: number,
) => {
    const seedings: number[][] = []

    for (let i = 0; i < groupOrMatchCount; i++) {
        seedings.push([])
    }

    let seedingsTaken = 0

    for (let i = 0; i < highestTeamCount; i++) {
        const addToList = (index: number) => {
            if (
                teams[index] > seedings[index].length ||
                (teams[index] === 0 && seedingsTaken < nextRoundTeams)
            ) {
                seedingsTaken += 1
                seedings[index].push(seedingsTaken)
            }
        }

        if (i % 2 === 0) {
            for (let j = 0; j < groupOrMatchCount; j++) {
                addToList(j)
            }
        } else {
            for (let j = groupOrMatchCount - 1; j > -1; j--) {
                addToList(j)
            }
        }
    }

    return seedings
}

// Updates the participant fields by default seeding - Depends on the "Teams" values of this round
export const updateParticipants = (
    formContext: UseFormReturn<CompetitionSetupForm>,
    roundIndex: number,
    repeatForPreviousRound: boolean,
    nextRoundTeams: number,
    updatePlaces: (updateThisRound: boolean, newTeamsCount: number) => void,
) => {
    const isGroupRound = formContext.getValues(`rounds.${roundIndex}.isGroupRound`)

    const matches = formContext.getValues(`rounds.${roundIndex}.matches`)

    const groups = formContext.getValues(`rounds.${roundIndex}.groups`)

    if ((isGroupRound && groups) || (!isGroupRound && matches)) {
        const groupsOrMatches = isGroupRound ? groups : matches

        const highestTeamsValue = getHighestTeamsCount(
            groupsOrMatches.map(v => v.teams),
            nextRoundTeams,
        )

        const newParticipants: {
            participants: number[]
        }[] =
            roundIndex !== 0
                ? fillSeedingList(
                      groupsOrMatches.length,
                      highestTeamsValue,
                      groupsOrMatches.map(v => Number(v.teams)),
                      nextRoundTeams,
                  ).map(v => ({participants: v}))
                : (groupsOrMatches.map(() => ({participants: []})) ?? [])

        newParticipants?.forEach((val, index) => {
            setParticipantValuesForMatchOrGroup(
                formContext,
                roundIndex,
                isGroupRound,
                index,
                val.participants,
            )
        })

        // In the first round there are no participants so the teamsCount is calculated by the "teams" values instead of the new participants count
        const teamsCount =
            roundIndex === 0
                ? getTeamsCountInMatches(matches)
                : newParticipants.map(p => p.participants).flat().length

        updatePlaces(true, teamsCount)

        // To prevent infinit loop because of the recursive call
        if (repeatForPreviousRound) {
            const newTeamsCount = newParticipants
                .flat()
                .map(v => v.participants)
                .flat().length
            updatePreviousRoundParticipants(
                formContext,
                roundIndex - 1,
                newTeamsCount,
                updatePlaces,
            )
        }
    }
}

export const getHighestTeamsCount = (teams: string[], nextRoundTeams: number) => {
    const highestDefinedTeamCount =
        teams.length > 0
            ? Number(
                  teams.reduce((acc, val) => {
                      if (val === undefined) {
                          return acc
                      } else if (acc === undefined) {
                          return val
                      } else {
                          return Number(val) > Number(acc) ? val : acc
                      }
                  }),
              )
            : 0

    const definedTeams = teams.filter(v => v !== '')

    const definedTeamsCount =
        definedTeams.length > 0
            ? definedTeams.map(v => Number(v)).reduce((acc, val) => +acc + +val)
            : 0

    const teamsForUndefinedTeams = nextRoundTeams - (definedTeamsCount ?? 0)

    const undefinedTeamsCount = teams.filter(v => v === '').length
    // How multiple undefined matches or groups split up the remaining teams that are not taken by the defined teams
    const teamsForEachUndefinedTeams =
        undefinedTeamsCount !== 0 ? Math.ceil(teamsForUndefinedTeams / undefinedTeamsCount) : 0

    // The highest "Teams" value a match in this round has. Matches with undefined teams are also taken into account, based on the following round
    // This value defines, how often the for loop goes through each match to distribute the participants
    return highestDefinedTeamCount > teamsForEachUndefinedTeams
        ? highestDefinedTeamCount
        : teamsForEachUndefinedTeams
}

export const updatePreviousRoundParticipants = (
    formContext: UseFormReturn<CompetitionSetupForm>,
    prevRoundIndex: number,
    thisRoundTeams: number,
    updatePlaces: (updateThisRound: boolean, newTeamsCount: number) => void,
) => {
    if (prevRoundIndex >= 0) {
        const prevRoundIsGroupRound = formContext.getValues(`rounds.${prevRoundIndex}.isGroupRound`)

        const prevRoundGroupsOrMatches = prevRoundIsGroupRound
            ? formContext.getValues(`rounds.${prevRoundIndex}.groups`)
            : formContext.getValues(`rounds.${prevRoundIndex}.matches`)
        if (prevRoundGroupsOrMatches) {
            // If the previous round has a match with undefined teams, the participants of that round are updated
            if (prevRoundGroupsOrMatches.filter(v => v.teams === '').length > 0) {
                updateParticipants(formContext, prevRoundIndex, false, thisRoundTeams, updatePlaces)
            }
        }
    }
}

export const getMatchupsString = (participants: number[]) => {
    return participants.map(v => `#${v}`).join(' vs ')
}

export const onTeamsChanged = (
    roundIndex: number,
    teamsValue: number,
    useDefaultSeeding: boolean,
    participantFunctions: ParticipantFunctions,
    teamCounts: {
        thisRoundWithoutThis: number
        nextRound: number
    },
) => {
    if (!useDefaultSeeding) {
        const participants: number[] = []

        // The first round has no participants
        if (roundIndex !== 0) {
            // if teamsValue is 0 (undefined) the necessaryTeamsValue is calculated from the needed teams for next round
            const necessaryTeams =
                teamsValue > 0 ? teamsValue : teamCounts.nextRound - teamCounts.thisRoundWithoutThis

            for (let i = 0; i < necessaryTeams; i++) {
                participants.push(participantFunctions.findLowestMissingParticipant(participants))
            }
        }

        participantFunctions.setParticipantValuesForThis(participants)
        participantFunctions.updatePreviousRoundParticipants(
            participants.length + teamCounts.thisRoundWithoutThis,
        )
        participantFunctions.updatePlaces(
            true,
            participants.length + teamCounts.thisRoundWithoutThis,
        )
    } else {
        participantFunctions.updateRoundParticipants(true, teamCounts.nextRound)
    }
}

export const getParticipantsFromMatchOrGroup = (
    matches?: FormSetupMatch[],
    groups?: FormSetupGroup[],
) => {
    return (matches !== undefined ? matches : (groups ?? []))
        .map(m => m.participants.map(p => p.seed))
        .flat()
}

export const getNewPlaces = (
    participants: number[],
    thisRoundTeams: number,
    nextRoundTeams: number,
) => {
    return new Array(thisRoundTeams)
        .fill(null)
        .map((_, i) => ({
            roundOutcome: i + 1,
            place: (nextRoundTeams !== 0 ? nextRoundTeams : i) + 1, // If there is no next round the places are set by the roundOutcome
        }))
        .filter(v => !participants.includes(v.roundOutcome))
}

export const getTeamsCountInMatches = (
    matches: FormSetupMatch[],
) => {
    return matches
        .map(m => Number(m.teams))
        .reduce((acc, val) => {
            return acc + val
        }, 0)
}