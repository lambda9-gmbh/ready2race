import {CompetitionSetupForm} from '@components/event/competition/setup/CompetitionSetup.tsx'
import {UseFormReturn} from 'react-hook-form-mui'

export const getWeightings = (matchCount: number) => {
    if (matchCount === 1) return [1]

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

export const setOutcomeValuesForMatch = (
    formContext: UseFormReturn<CompetitionSetupForm>,
    roundIndex: number,
    matchIndex: number,
    outcomes: number[],
) => {
    const outcomesFormPath =
        `rounds[${roundIndex}].matches[${matchIndex}].outcomes` as `rounds.${number}.matches.${number}.outcomes`

    formContext.setValue(
        outcomesFormPath,
        outcomes.map(v => ({outcome: v})),
    )

    outcomes.forEach((_, i) => {
        const path =
            `rounds[${roundIndex}].matches[${matchIndex}].outcomes[${i}].outcome` as `rounds.${number}.matches.${number}.outcomes.${number}.outcome`
        formContext.setValue(path, outcomes[i])
    })
}

// Updates the outcome fields by default seeding - Depends on the "Teams" values of this round
export const updateOutcomes = (
    formContext: UseFormReturn<CompetitionSetupForm>,
    roundIndex: number,
    repeatForPreviousRound: boolean,
    nextRoundTeams: number,
) => {
    const matches = formContext.getValues(
        `rounds[${roundIndex}].matches` as `rounds.${number}.matches`,
    )

    if (matches) {
        const highestTeamsValue = getHighestTeamsCount(
            matches.map(v => v.teams),
            nextRoundTeams,
        )

        const getLowest = (taken: number[]) => {
            const set = new Set(taken)
            let i = 1
            while (set.has(i)) {
                i++
            }
            return i
        }

        const newOutcomes: {outcomes: number[]}[] = matches.map(() => ({outcomes: []})) ?? []
        const takenOutcomes: number[] = []
        for (let i = 0; i < highestTeamsValue; i++) {
            const addOutcomeToMatch = (j: number) => {
                const lowest = getLowest(takenOutcomes)

                const teamCountUndefined = Number(matches[j].teams) === 0
                const undefinedTeamsMatchesCount = matches.filter(v => v.teams === '').length

                // If the match does not yet have the desired amount of outcomes and there are still outcomes to be handed out - based on next round
                if (
                    (Number(matches[j].teams) > newOutcomes[j].outcomes.length ||
                        teamCountUndefined) &&
                    (undefinedTeamsMatchesCount === 0 || takenOutcomes.length < nextRoundTeams)
                ) {
                    newOutcomes[j].outcomes.push(lowest)
                    takenOutcomes.push(lowest)
                }
            }
            // This creates the default seeding (A -> B -> C -> B -> A -> B...)
            if (i % 2 === 0) {
                for (let j = 0; j < newOutcomes.length; j++) {
                    addOutcomeToMatch(j)
                }
            } else {
                for (let j = newOutcomes.length - 1; j > -1; j--) {
                    addOutcomeToMatch(j)
                }
            }
        }

        newOutcomes?.forEach((val, matchIndex) => {
            setOutcomeValuesForMatch(formContext, roundIndex, matchIndex, val.outcomes)
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

    const definedTeamsMatches = teams.filter(v => v !== '')

    const definedTeamsCount =
        definedTeamsMatches.length > 0
            ? definedTeamsMatches?.map(v => Number(v)).reduce((acc, val) => +acc + +val)
            : 0

    const teamsForUndefinedTeamsMatches = nextRoundTeams - (definedTeamsCount ?? 0)

    const undefinedTeamsMatchesCount = teams.filter(v => v === '').length
    const teamsForEachUndefinedTeamsMatch =
        undefinedTeamsMatchesCount !== 0
            ? Math.ceil(teamsForUndefinedTeamsMatches / undefinedTeamsMatchesCount)
            : 0

    // The highest "Teams" value a match in this round has. Matches with undefined teams are also taken into account, based on the following round
    // This value defines, how often the for loop goes through each match to distribute the outcomes
    return highestDefinedTeamCount > teamsForEachUndefinedTeamsMatch
        ? highestDefinedTeamCount
        : teamsForEachUndefinedTeamsMatch
}

export const updatePreviousRoundOutcomes = (
    formContext: UseFormReturn<CompetitionSetupForm>,
    prevRoundIndex: number,
    thisRoundTeams: number,
) => {
    if (prevRoundIndex >= 0) {
        const prevRoundMatches = formContext.getValues(
            `rounds[${prevRoundIndex}].matches` as `rounds.${number}.matches`,
        )
        if (prevRoundMatches) {
            // If the previous round has a match with undefined teams, the outcomes of that round are updated
            if (prevRoundMatches.filter(v => v.teams === '').length > 0) {
                updateOutcomes(formContext, prevRoundIndex, false, thisRoundTeams)
            }
        }
    }
}

export const competitionSetupDummyData: CompetitionSetupForm = {
    rounds: [
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
                    position: 0,
                },
            ],
            useDefaultSeeding: true,
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
                    position: 0,
                },
                {
                    duplicatable: false,
                    weighting: 4,
                    teams: '2',
                    name: 'VF2',
                    outcomes: [{outcome: 4}, {outcome: 5}],
                    position: 1,
                },
                {
                    duplicatable: false,
                    weighting: 3,
                    teams: '2',
                    name: 'VF3',
                    outcomes: [{outcome: 3}, {outcome: 6}],
                    position: 2,
                },
                {
                    duplicatable: false,
                    weighting: 2,
                    teams: '2',
                    name: 'VF4',
                    outcomes: [{outcome: 2}, {outcome: 7}],
                    position: 3,
                },
            ],
            useDefaultSeeding: true,
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
                    position: 0,
                },
                {
                    duplicatable: false,
                    weighting: 2,
                    teams: '2',
                    name: 'HF2',
                    outcomes: [{outcome: 2}, {outcome: 3}],
                    position: 1,
                },
            ],
            useDefaultSeeding: true,
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
                    position: 0,
                },
            ],
            useDefaultSeeding: true,
        },
    ],
}
