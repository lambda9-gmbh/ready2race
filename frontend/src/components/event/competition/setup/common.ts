import {CompetitionSetupForm} from '@components/event/competition/setup/CompetitionSetup.tsx'
import {UseFormReturn} from 'react-hook-form-mui'

export type CompetitionSetupMatchOrGroupProps = {
    formContext: UseFormReturn<CompetitionSetupForm>
    roundIndex: number
    fieldInfo: {index: number; id: string} // The index is the ORIGINAL index that refers to the fieldArray - It may differ from the displayed order
    roundHasDuplicatable: boolean
    outcomes: number[]
    teamCounts: {thisRoundWithoutThis: number; nextRound: number}
    useDefaultSeeding: boolean
    outcomeFunctions: ParticipantFunctions
}

export type ParticipantFunctions = {
    findLowestMissingParticipant: (yetUnregisteredParticipants: number[]) => number
    updateRoundParticipants: (repeatForPreviousRound: boolean, nextRoundTeams: number) => void
    setParticipantValuesForThis: (participants: number[]) => void
    updatePreviousRoundParticipants: (thisRoundTeams: number) => void
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
    const participantsFormPath = isGroupRound
        ? (`rounds[${roundIndex}].groups[${index}].participants` as `rounds.${number}.groups.${number}.participants`)
        : (`rounds[${roundIndex}].matches[${index}].participants` as `rounds.${number}.matches.${number}.participants`)

    formContext.setValue(
        participantsFormPath,
        participants.map(v => ({seed: v})),
    )

    participants.forEach((_, i) => {
        const path = isGroupRound
            ? (`rounds[${roundIndex}].groups[${index}].participants[${i}].seed` as `rounds.${number}.groups.${number}.participants.${number}.seed`)
            : (`rounds[${roundIndex}].matches[${index}].participants[${i}].seed` as `rounds.${number}.matches.${number}.participants.${number}.seed`)
        formContext.setValue(path, participants[i])
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

export const fillSeedingList = (
    groupOrMatchCount: number,
    highestTeamCount: number,
    teams: number[],
    nextRoundTeams: number,
) => {
    const seedings: number[][] = []

    for (let i = 0; i < (groupOrMatchCount ?? 0); i++) {
        seedings.push([])
    }

    const undefinedTeamsCount = teams.filter(v => v === 0).length

    let seedingsTaken = 0

    for (let i = 0; i < highestTeamCount; i++) {
        const addToList = (index: number) => {
            if (
                teams[index] > seedings[index].length ||
                undefinedTeamsCount === 0 ||
                seedingsTaken < nextRoundTeams
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

        // In Round 1 (index=0) there are no participants since no seeds are coming from the previous round
       /* if (roundIndex !== 0) {
            const undefinedTeamsCount = groupsOrMatches.filter(v => v.teams === '').length

            const takenParticipants: number[] = []
            for (let i = 0; i < highestTeamsValue; i++) {
                const addParticipant = (j: number) => {
                    const lowest = getLowest(takenParticipants, 1)

                    const teamCountUndefined = Number(groupsOrMatches[j].teams) === 0

                    // If the match or group does not yet have the desired amount of participants and there are still participants to be handed out - based on next round
                    if (
                        (Number(groupsOrMatches[j].teams) >
                            newParticipants[j].participants.length ||
                            teamCountUndefined) &&
                        (undefinedTeamsCount === 0 || takenParticipants.length < nextRoundTeams)
                    ) {
                        newParticipants[j].participants.push(lowest)
                        takenParticipants.push(lowest)
                    }
                }
                // This creates the default seeding (A(W1) -> B(W2) -> C(W3) -> B -> A -> B...)
                if (i % 2 === 0) {
                    for (let j = 0; j < newParticipants.length; j++) {
                        addParticipant(j)
                    }
                } else {
                    for (let j = newParticipants.length - 1; j > -1; j--) {
                        addParticipant(j)
                    }
                }
            }
        }*/

        newParticipants?.forEach((val, index) => {
            setParticipantValuesForMatchOrGroup(
                formContext,
                roundIndex,
                isGroupRound,
                index,
                val.participants,
            )
        })

        // To prevent infinit loop because of the recursive call
        if (repeatForPreviousRound) {
            const newTeamsCount = newParticipants
                .flat()
                .map(v => v.participants)
                .flat().length
            updatePreviousRoundParticipants(formContext, roundIndex - 1, newTeamsCount)
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
            // If the previous round has a match with undefined teams, the participants of that round are updated
            if (prevRoundGroupsOrMatches.filter(v => v.teams === '').length > 0) {
                updateParticipants(formContext, prevRoundIndex, false, thisRoundTeams)
            }
        }
    }
}

export const getMatchupsString = (participants: number[]) => {
    return participants.map(v => `#${v}`).join(' vs ')
}

export const onTeamsChanged = (
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
        // if teamsValue is 0 (undefined) the necessaryTeamsValue is calculated from the needed teams for next round
        const necessaryTeams =
            teamsValue > 0 ? teamsValue : teamCounts.nextRound - teamCounts.thisRoundWithoutThis

        for (let i = 0; i < necessaryTeams; i++) {
            participants.push(participantFunctions.findLowestMissingParticipant(participants))
        }

        participantFunctions.setParticipantValuesForThis(participants)
        participantFunctions.updatePreviousRoundParticipants(
            participants.length + teamCounts.thisRoundWithoutThis,
        )
    } else {
        participantFunctions.updateRoundParticipants(true, teamCounts.nextRound)
    }
}

export const competitionSetupDummyData: CompetitionSetupForm = {
    rounds: [],
}
/*{
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
*/
