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
    participantFunctions: ParticipantFunctions
    useStartTimeOffsets: boolean
    onParticipantsChanged: (teamCountChanged: boolean) => void
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
        const prevRoundIsGroupRound = formContext.getValues(`rounds.${prevRoundIndex}.isGroupRound`)

        const prevRoundGroupsOrMatches = prevRoundIsGroupRound
            ? formContext.getValues(`rounds.${prevRoundIndex}.groups`)
            : formContext.getValues(`rounds.${prevRoundIndex}.matches`)
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
    } else {
        participantFunctions.updateRoundParticipants(true, teamCounts.nextRound)
    }
}
