import {CheckboxElement, Controller, useFieldArray, UseFormReturn} from 'react-hook-form-mui'
import {Alert, Box, Button, Checkbox, FormControlLabel, Stack, Typography} from '@mui/material'
import {CompetitionSetupForm} from '@components/event/competition/setup/CompetitionSetup.tsx'
import CompetitionSetupMatch from '@components/event/competition/setup/CompetitionSetupMatch.tsx'
import {useEffect} from 'react'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {
    CompetitionSetupMatchOrGroupProps,
    getHighestTeamsCount,
    getLowest,
    getWeightings,
    setParticipantValuesForMatchOrGroup,
    updateParticipants,
    updatePreviousRoundParticipants,
} from '@components/event/competition/setup/common.ts'
import CompetitionSetupGroup from '@components/event/competition/setup/CompetitionSetupGroup.tsx'

type Props = {
    round: {index: number; id: string}
    formContext: UseFormReturn<CompetitionSetupForm>
    removeRound: (index: number) => void
    teamCounts: {
        thisRound: number
        nextRound: number
    }
    getRoundTeamCountWithoutThis: (ignoredIndex: number, isGroupRound: boolean) => number
}
const CompetitionSetupRound = ({round, formContext, removeRound, teamCounts, ...props}: Props) => {
    const defaultMatchTeamSize = 2
    const defaultGroupTeamSize = 4

    const watchUseDefaultSeeding = formContext.watch(
        ('rounds[' + round.index + '].useDefaultSeeding') as `rounds.${number}.useDefaultSeeding`,
    )

    const watchIsGroupRound = formContext.watch(
        ('rounds[' + round.index + '].isGroupRound') as `rounds.${number}.isGroupRound`,
    )

    const {
        fields: matchFields,
        append: appendMatch,
        remove: removeMatch,
    } = useFieldArray({
        control: formContext.control,
        name: `rounds.${round.index}.matches`,
    })

    const watchMatches = formContext.watch(`rounds.${round.index}.matches`)

    const {
        fields: groupFields,
        append: appendGroup,
        remove: removeGroup,
    } = useFieldArray({
        control: formContext.control,
        name: `rounds.${round.index}.groups`,
    })

    const watchGroups = formContext.watch(`rounds.${round.index}.groups`)

    const getLowestGroupMatchPosition = (takenPositions?: number[]) => {
        return getLowest(
            [
                ...watchGroups.map(g => g.matches.map(m => m.position)).flat(),
                ...(takenPositions ?? []),
            ],
            1,
        )
    }

    // When appending or removing a match/group, the outcomes are updated (if default seeding is active)
    useEffect(() => {
        if (watchUseDefaultSeeding) {
            updateParticipants(formContext, round.index, true, teamCounts.nextRound)
        }
    }, [watchMatches.length, watchGroups.length, watchIsGroupRound, watchUseDefaultSeeding])

    function findLowestMissingParticipant(
        isGroups: boolean,
        yetUnregisteredParticipants: number[],
        ignoreIndex?: number,
    ): number {
        const list = isGroups ? watchMatches : watchGroups
        const takenParticipants = list
            .filter((_, index) => index !== ignoreIndex)
            .map(v => v.participants)
            .flat()
            .map(v => v.seed)

        const set = new Set([...takenParticipants, ...yetUnregisteredParticipants])
        let i = 1
        while (set.has(i)) {
            i++
        }
        return i
    }

    // Default  outcomes that are inserted to a match or group when appended - When Default seeding is enabled they are overwritten by the useEffect above
    const appendPreparedParticipants: {seed: number}[] = []
    for (let i = 0; i < (watchIsGroupRound ? defaultGroupTeamSize : defaultMatchTeamSize); i++) {
        appendPreparedParticipants.push({
            seed: findLowestMissingParticipant(
                watchIsGroupRound,
                appendPreparedParticipants.map(v => v.seed),
            ),
        })
    }

    const roundHasDuplicatableMatch = watchMatches.find(v => v.duplicatable === true) !== undefined
    const roundHasDuplicatableGroup = watchGroups.find(v => v.duplicatable === true) !== undefined

    /*
    // Display the Teams that are participating in this match
    // Purely visual to show where the outcome-seeds from last round are playing in this round

    const watchPrevRoundIsGroupRound =
            round.index > 0
                ? formContext.watch(
                      `rounds[${round.index - 1}].isGroupRound` as `rounds.${number}.isGroupRound`,
                  )
                : undefined

        const watchPrevRoundMatches =
            round.index > 0
                ? formContext.watch(`rounds[${round.index - 1}].matches` as `rounds.${number}.matches`)
                : undefined

        const watchPrevRoundGroups =
            round.index > 0
                ? formContext.watch(`rounds[${round.index - 1}].groups` as `rounds.${number}.groups`)
                : undefined

        const prevRoundOutcomes = (
            watchPrevRoundIsGroupRound ? watchPrevRoundGroups : watchPrevRoundMatches
        )
            ?.map(v => (v.outcomes ? v.outcomes?.map(outcome => outcome.outcome).flat() : []))
            .flat()
            .sort((a, b) => a - b)
            .slice(0, teamCounts.thisRound)*/

    const highestTeamCount = (watchIsGroupRound ? watchGroups : watchMatches)
        ? getHighestTeamsCount(
              (watchIsGroupRound ? watchGroups : watchMatches).map(v => v.teams),
              teamCounts.nextRound,
          )
        : 0

    const results: number[][] = [] // todo put this together with "updateParticipants()"
    const groupsOrMatchesLength = watchIsGroupRound ? watchGroups.length : watchMatches.length
    for (let i = 0; i < (groupsOrMatchesLength ?? 0); i++) {
        results.push([])
    }
    let participantsTaken = 1
    for (let i = 0; i < highestTeamCount; i++) {
        const addToList = (index: number) => {
            if (
                Number(watchIsGroupRound ? watchGroups[index].teams : watchMatches[index].teams) >
                    results[index].length ||
                (watchIsGroupRound ? watchGroups[index].teams : watchMatches[index].teams) === ''
            ) {
                results[index].push(participantsTaken)
                participantsTaken += 1
            }
        }

        if (i % 2 === 0) {
            for (let j = 0; j < groupsOrMatchesLength; j++) {
                addToList(j)
            }
        } else {
            for (let j = groupsOrMatchesLength - 1; j > -1; j--) {
                addToList(j)
            }
        }
    }

    type MatchOrGroupInfo = {
        originalIndex: number
        fieldId: string
        outcomes: number[]
    }
    const matchInfos: MatchOrGroupInfo[] = watchMatches.map((_, index) => ({
        originalIndex: index,
        fieldId: matchFields[index]?.id ?? '',
        outcomes: !watchIsGroupRound ? results[index] : [],
    }))

    // This is just for the display. in the fieldArray the weightings are in order (1,2,3...)
    const matchInfosSortedByWeighting = getWeightings(matchInfos.length).map(v => matchInfos[v - 1])

    const groupInfos: MatchOrGroupInfo[] = watchGroups.map((_, index) => ({
        originalIndex: index,
        fieldId: groupFields[index]?.id ?? '',
        outcomes: watchIsGroupRound ? results[index] : [],
    }))

    // This is just for the display. in the fieldArray the weightings are in order (1,2,3...)
    const groupInfosSortedByWeighting = getWeightings(groupInfos.length).map(v => groupInfos[v - 1])

    const getGroupOrMatchProps = (
        isGroups: boolean,
        info: MatchOrGroupInfo,
    ): CompetitionSetupMatchOrGroupProps => {
        return {
            formContext: formContext,
            roundIndex: round.index,
            fieldInfo: {
                index: info.originalIndex,
                id: info.fieldId,
            },
            roundHasDuplicatable: isGroups ? roundHasDuplicatableGroup : roundHasDuplicatableMatch,
            outcomes: info.outcomes,
            teamCounts: {
                thisRoundWithoutThis: props.getRoundTeamCountWithoutThis(
                    info.originalIndex,
                    isGroups,
                ),
                nextRound: teamCounts.nextRound,
            },
            useDefaultSeeding: watchUseDefaultSeeding,
            outcomeFunctions: {
                findLowestMissingParticipant: yetUnregisteredOutcomes =>
                    findLowestMissingParticipant(
                        isGroups,
                        yetUnregisteredOutcomes,
                        info.originalIndex,
                    ),
                updateRoundParticipants: (repeatForPreviousRound, nextRoundTeams) =>
                    updateParticipants(
                        formContext,
                        round.index,
                        repeatForPreviousRound,
                        nextRoundTeams,
                    ),
                setParticipantValuesForThis: outcomes =>
                    setParticipantValuesForMatchOrGroup(
                        formContext,
                        round.index,
                        isGroups,
                        info.originalIndex,
                        outcomes,
                    ),
                updatePreviousRoundParticipants: thisRoundTeams =>
                    updatePreviousRoundParticipants(formContext, round.index - 1, thisRoundTeams),
            },
        }
    }

    return (
        <Controller
            name={'rounds[' + round.index + '].useDefaultSeeding'}
            render={({
                field: {onChange: useDefSeedingOnChange, value: useDefSeedingValue = true},
            }) => (
                <Stack spacing={2} sx={{border: 1, borderColor: 'blue', p: 2}}>
                    <Stack
                        spacing={2}
                        direction="row"
                        sx={{
                            justifyContent: 'space-between',
                            alignItems: 'center',
                        }}>
                        {/*todo: make this look the same as the isGroupRound Checkbox*/}
                        <FormControlLabel
                            control={
                                <Checkbox
                                    checked={useDefSeedingValue}
                                    onChange={useDefSeedingOnChange}
                                />
                            }
                            label={
                                <FormInputLabel
                                    label={'Use default seeding'}
                                    required={true}
                                    horizontal
                                />
                            }
                        />
                        <Typography>
                            Max Teams:{' '}
                            {teamCounts.thisRound === 0 ? 'Unknown' : teamCounts.thisRound}
                        </Typography>
                        <Box>
                            <Button
                                variant="outlined"
                                onClick={() => {
                                    removeRound(round.index)
                                }}>
                                Remove Round
                            </Button>
                        </Box>
                    </Stack>
                    <CheckboxElement
                        name={`rounds[${round.index}].isGroupRound`}
                        label={<FormInputLabel label={'Group Phase'} required={true} horizontal />}
                    />
                    <FormInputText name={`rounds[${round.index}].name`} label={'Round name'} />
                    <CheckboxElement
                        name={`rounds[${round.index}].required`}
                        label={
                            <FormInputLabel
                                label={'Round must be held'}
                                required={true}
                                horizontal
                            />
                        }
                    />
                    {round.index === 0 && watchMatches.filter(v => v.teams === '').length > 1 && (
                        <Alert severity="info">
                            Participants are filled into the matches equally starting from weighting
                            1
                        </Alert>
                    )}
                    <Stack
                        direction="row"
                        spacing={2}
                        justifyContent="space-between"
                        sx={{alignItems: 'center'}}>
                        <Stack direction="row" spacing={2}>
                            {watchIsGroupRound === false ? (
                                <>
                                    {matchInfosSortedByWeighting.map((matchInfo, _) => (
                                        <Stack
                                            key={matchInfo.fieldId}
                                            direction="column"
                                            spacing={1}
                                            sx={{maxWidth: 300}}>
                                            <CompetitionSetupMatch
                                                {...getGroupOrMatchProps(false, matchInfo)}
                                            />
                                            <Button
                                                variant="outlined"
                                                onClick={() => {
                                                    removeMatch(matchInfo.originalIndex)
                                                }}>
                                                Remove Match
                                            </Button>
                                        </Stack>
                                    ))}
                                    <Box sx={{alignSelf: 'center'}}>
                                        <Button
                                            variant="outlined"
                                            onClick={() => {
                                                appendMatch({
                                                    duplicatable: false,
                                                    weighting: matchFields.length + 1,
                                                    teams: `${defaultMatchTeamSize}`,
                                                    name: '',
                                                    participants: appendPreparedParticipants,
                                                    position: matchFields.length,
                                                })
                                            }}
                                            sx={{width: 1}}>
                                            Add Match
                                        </Button>
                                    </Box>
                                </>
                            ) : (
                                <>
                                    {groupInfosSortedByWeighting.map((groupInfo, _) => (
                                        <Stack
                                            key={groupInfo.fieldId}
                                            direction="column"
                                            spacing={1}
                                            sx={{maxWidth: 450}}>
                                            <CompetitionSetupGroup
                                                {...getGroupOrMatchProps(true, groupInfo)}
                                                getLowestGroupMatchPosition={
                                                    getLowestGroupMatchPosition
                                                }
                                            />
                                            <Button
                                                variant="outlined"
                                                onClick={() => {
                                                    removeGroup(groupInfo.originalIndex)
                                                }}>
                                                Remove Group
                                            </Button>
                                        </Stack>
                                    ))}
                                    <Box sx={{alignSelf: 'center'}}>
                                        <Button
                                            variant="outlined"
                                            onClick={() => {
                                                appendGroup({
                                                    duplicatable: false,
                                                    weighting: groupFields.length + 1,
                                                    teams: `${defaultGroupTeamSize}`,
                                                    name: '',
                                                    matches: [],
                                                    participants: appendPreparedParticipants,
                                                    matchTeams: 2,
                                                })
                                            }}
                                            sx={{width: 1}}>
                                            Add Group
                                        </Button>
                                    </Box>
                                </>
                            )}
                        </Stack>
                    </Stack>
                </Stack>
            )}
        />
    )
}

export default CompetitionSetupRound
