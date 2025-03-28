import {CompetitionSetupForm} from '@components/event/competition/setup/CompetitionSetup.tsx'
import {UseFormReturn} from 'react-hook-form-mui'

export type CompetitionSetupMatchOrGroupProps = {
    formContext: UseFormReturn<CompetitionSetupForm>
    roundIndex: number
    fieldInfo: {index: number; id: string} // The index is the ORIGINAL index that refers to the fieldArray - It may differ from the displayed order
    roundHasDuplicatable: boolean
    participantsString: string
    teamCounts: {thisRoundWithoutThis: number; nextRound: number}
    useDefaultSeeding: boolean
    outcomeFunctions: OutcomeFunctions
}

export type OutcomeFunctions = {
    findLowestMissingOutcome: (yetUnregisteredOutcomes: number[]) => number
    updateRoundOutcomes: (repeatForPreviousRound: boolean, nextRoundTeams: number) => void
    setOutcomeValuesForThis: (outcomes: number[]) => void
    updatePreviousRoundOutcomes: (thisRoundTeams: number) => void
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

export const setOutcomeValuesForMatchOrGroup = (
    formContext: UseFormReturn<CompetitionSetupForm>,
    roundIndex: number,
    isGroupRound: boolean,
    index: number,
    outcomes: number[],
) => {
    const outcomesFormPath = isGroupRound
        ? (`rounds[${roundIndex}].groups[${index}].outcomes` as `rounds.${number}.groups.${number}.outcomes`)
        : (`rounds[${roundIndex}].matches[${index}].outcomes` as `rounds.${number}.matches.${number}.outcomes`)

    formContext.setValue(
        outcomesFormPath,
        outcomes.map(v => ({outcome: v})),
    )

    outcomes.forEach((_, i) => {
        const path = isGroupRound
            ? (`rounds[${roundIndex}].groups[${index}].outcomes[${i}].outcome` as `rounds.${number}.groups.${number}.outcomes.${number}.outcome`)
            : (`rounds[${roundIndex}].matches[${index}].outcomes[${i}].outcome` as `rounds.${number}.matches.${number}.outcomes.${number}.outcome`)
        formContext.setValue(path, outcomes[i])
    })
}

export const getLowest = (taken: number[], startIndex: number) => {
    const set = new Set(taken)
    let i = startIndex
    while (set.has(i)) {
        i++
    }
    return i
}

// Updates the outcome fields by default seeding - Depends on the "Teams" values of this round
export const updateOutcomes = (
    formContext: UseFormReturn<CompetitionSetupForm>,
    roundIndex: number,
    repeatForPreviousRound: boolean,
    nextRoundTeams: number,
) => {
    const isGroupRound = formContext.getValues(
        `rounds[${roundIndex}].isGroupRound` as `rounds.${number}.isGroupRound`,
    )

    const matches = formContext.getValues(
        `rounds[${roundIndex}].matches` as `rounds.${number}.matches`,
    )

    const groups = formContext.getValues(
        `rounds[${roundIndex}].groups` as `rounds.${number}.groups`,
    )

    if ((isGroupRound && groups) || (!isGroupRound && matches)) {
        const groupsOrMatches = isGroupRound ? groups : matches

        const highestTeamsValue = getHighestTeamsCount(
            groupsOrMatches.map(v => v.teams),
            nextRoundTeams,
        )

        const newOutcomes: {outcomes: number[]}[] =
            groupsOrMatches.map(() => ({outcomes: []})) ?? []
        const takenOutcomes: number[] = []
        for (let i = 0; i < highestTeamsValue; i++) {
            const addOutcome = (j: number) => {
                const lowest = getLowest(takenOutcomes, 1)

                const teamCountUndefined = Number(groupsOrMatches[j].teams) === 0
                const undefinedTeamsCount = groupsOrMatches.filter(v => v.teams === '').length

                // If the match or group does not yet have the desired amount of outcomes and there are still outcomes to be handed out - based on next round
                if (
                    (Number(groupsOrMatches[j].teams) > newOutcomes[j].outcomes.length ||
                        teamCountUndefined) &&
                    (undefinedTeamsCount === 0 || takenOutcomes.length < nextRoundTeams)
                ) {
                    newOutcomes[j].outcomes.push(lowest)
                    takenOutcomes.push(lowest)
                }
            }
            // This creates the default seeding (A -> B -> C -> B -> A -> B...)
            if (i % 2 === 0) {
                for (let j = 0; j < newOutcomes.length; j++) {
                    addOutcome(j)
                }
            } else {
                for (let j = newOutcomes.length - 1; j > -1; j--) {
                    addOutcome(j)
                }
            }
        }

        newOutcomes?.forEach((val, index) => {
            setOutcomeValuesForMatchOrGroup(
                formContext,
                roundIndex,
                isGroupRound,
                index,
                val.outcomes,
            )
        })

        // To prevent infinit loop because of the recursive call
        if (repeatForPreviousRound) {
            const newTeamsCount = newOutcomes
                .flat()
                .map(v => v.outcomes)
                .flat().length
            updatePreviousRoundOutcomes(formContext, roundIndex - 1, newTeamsCount)
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
    // This value defines, how often the for loop goes through each match to distribute the outcomes
    return highestDefinedTeamCount > teamsForEachUndefinedTeams
        ? highestDefinedTeamCount
        : teamsForEachUndefinedTeams
}

export const updatePreviousRoundOutcomes = (
    formContext: UseFormReturn<CompetitionSetupForm>,
    prevRoundIndex: number,
    thisRoundTeams: number,
) => {
    if (prevRoundIndex >= 0) {
        const prevRoundIsGroupRound = formContext.getValues(
            `rounds[${prevRoundIndex}].isGroupRound` as `rounds.${number}.isGroupRound`,
        )

        const prevRoundGroupsOrMatches = prevRoundIsGroupRound
            ? formContext.getValues(`rounds[${prevRoundIndex}].groups` as `rounds.${number}.groups`)
            : formContext.getValues(
                  `rounds[${prevRoundIndex}].matches` as `rounds.${number}.matches`,
              )
        if (prevRoundGroupsOrMatches) {
            // If the previous round has a match with undefined teams, the outcomes of that round are updated
            if (prevRoundGroupsOrMatches.filter(v => v.teams === '').length > 0) {
                updateOutcomes(formContext, prevRoundIndex, false, thisRoundTeams)
            }
        }
    }
}

export const getMatchupsString = (participants: number[]) => {
    const matchupStrings: string[] = []
    participants.forEach((p, index) => {
        if (index !== 0) {
            matchupStrings.push(' vs ')
        }
        matchupStrings.push(`#${p}`)
    })
    return matchupStrings.join('')
}

export const onTeamsChanged = (
    teamsValue: number,
    useDefaultSeeding: boolean,
    outcomeFunctions: OutcomeFunctions,
    teamCounts: {
        thisRoundWithoutThis: number
        nextRound: number
    },
) => {
    if (!useDefaultSeeding) {
        const outcomes: number[] = []
        // if teamsValue is 0 (undefined) the necessaryTeamsValue is calculated from the needed teams for next round
        const necessaryTeams =
            teamsValue > 0 ? teamsValue : teamCounts.nextRound - teamCounts.thisRoundWithoutThis

        for (let i = 0; i < necessaryTeams; i++) {
            outcomes.push(outcomeFunctions.findLowestMissingOutcome(outcomes))
        }

        outcomeFunctions.setOutcomeValuesForThis(outcomes)
        outcomeFunctions.updatePreviousRoundOutcomes(
            outcomes.length + teamCounts.thisRoundWithoutThis,
        )
    } else {
        outcomeFunctions.updateRoundOutcomes(true, teamCounts.nextRound)
    }
}

export const competitionSetupDummyData: CompetitionSetupForm = {
    rounds: [
        {
            name: 'Gruppenphase',
            required: true,
            matches: [],
            groups: [
                {
                    duplicatable: false,
                    weighting: 1,
                    teams: '4',
                    name: 'Gruppe A',
                    matches: [
                        {
                            duplicatable: false,
                            weighting: 1,
                            teams: '2',
                            name: 'GA-M1',
                            position: 1,
                        },
                        {
                            duplicatable: false,
                            weighting: 2,
                            teams: '2',
                            name: 'GA-M2',
                            position: 2,
                        },
                        {
                            duplicatable: false,
                            weighting: 3,
                            teams: '2',
                            name: 'GA-M3',
                            position: 5,
                        },
                        {
                            duplicatable: false,
                            weighting: 4,
                            teams: '2',
                            name: 'GA-M4',
                            position: 6,
                        },
                        {
                            duplicatable: false,
                            weighting: 5,
                            teams: '2',
                            name: 'GA-M5',
                            position: 9,
                        },
                        {
                            duplicatable: false,
                            weighting: 6,
                            teams: '2',
                            name: 'GA-M6',
                            position: 10,
                        },
                    ],
                    outcomes: [{outcome: 1}, {outcome: 4}, {outcome: 5}, {outcome: 8}],
                    matchTeams: 2,
                },
                {
                    duplicatable: false,
                    weighting: 1,
                    teams: '4',
                    name: 'Gruppe B',
                    matches: [
                        {
                            duplicatable: false,
                            weighting: 1,
                            teams: '2',
                            name: 'GB-M1',
                            position: 3,
                        },
                        {
                            duplicatable: false,
                            weighting: 2,
                            teams: '2',
                            name: 'GB-M2',
                            position: 4,
                        },
                        {
                            duplicatable: false,
                            weighting: 3,
                            teams: '2',
                            name: 'GB-M3',
                            position: 7,
                        },
                        {
                            duplicatable: false,
                            weighting: 4,
                            teams: '2',
                            name: 'GB-M4',
                            position: 8,
                        },
                        {
                            duplicatable: false,
                            weighting: 5,
                            teams: '2',
                            name: 'GB-M5',
                            position: 11,
                        },
                        {
                            duplicatable: false,
                            weighting: 6,
                            teams: '2',
                            name: 'GB-M6',
                            position: 12,
                        },
                    ],
                    outcomes: [{outcome: 2}, {outcome: 3}, {outcome: 6}, {outcome: 7}],
                    matchTeams: 2,
                },
            ],
            useDefaultSeeding: true,
            isGroupRound: true,
        },
        {
            name: 'Vorrunde',
            required: true,
            matches: [
                {
                    duplicatable: false,
                    weighting: 1,
                    teams: '',
                    name: undefined,
                    outcomes: [
                        {outcome: 1},
                        {outcome: 2},
                        {outcome: 3},
                        {outcome: 4},
                        {outcome: 5},
                        {outcome: 6},
                        {outcome: 7},
                        {outcome: 8},
                    ],
                    position: 1,
                },
            ],
            groups: [],
            useDefaultSeeding: true,
            isGroupRound: false,
        },
        {
            name: 'Viertelfinale',
            required: false,
            matches: [
                {
                    duplicatable: false,
                    weighting: 1,
                    teams: '2',
                    name: 'VF1',
                    outcomes: [{outcome: 1}, {outcome: 8}],
                    position: 1,
                },
                {
                    duplicatable: false,
                    weighting: 4,
                    teams: '2',
                    name: 'VF2',
                    outcomes: [{outcome: 4}, {outcome: 5}],
                    position: 2,
                },
                {
                    duplicatable: false,
                    weighting: 3,
                    teams: '2',
                    name: 'VF3',
                    outcomes: [{outcome: 3}, {outcome: 6}],
                    position: 3,
                },
                {
                    duplicatable: false,
                    weighting: 2,
                    teams: '2',
                    name: 'VF4',
                    outcomes: [{outcome: 2}, {outcome: 7}],
                    position: 4,
                },
            ],
            groups: [],
            useDefaultSeeding: true,
            isGroupRound: false,
        },
        {
            name: 'Halbfinale',
            required: false,
            matches: [
                {
                    duplicatable: false,
                    weighting: 1,
                    teams: '2',
                    name: 'HF1',
                    outcomes: [{outcome: 1}, {outcome: 4}],
                    position: 1,
                },
                {
                    duplicatable: false,
                    weighting: 2,
                    teams: '2',
                    name: 'HF2',
                    outcomes: [{outcome: 2}, {outcome: 3}],
                    position: 2,
                },
            ],
            groups: [],
            useDefaultSeeding: true,
            isGroupRound: false,
        },
        {
            name: 'Finale',
            required: true,
            matches: [
                {
                    duplicatable: false,
                    weighting: 1,
                    teams: '2',
                    name: 'F',
                    outcomes: [{outcome: 1}, {outcome: 2}],
                    position: 1,
                },
            ],
            groups: [],
            useDefaultSeeding: true,
            isGroupRound: false,
        },
    ],
}
