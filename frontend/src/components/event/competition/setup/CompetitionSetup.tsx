import {Box, Button, Stack} from '@mui/material'
import {useFieldArray, UseFormReturn} from 'react-hook-form-mui'
import CompetitionSetupRound from '@components/event/competition/setup/CompetitionSetupRound.tsx'
import {RefObject, useRef} from 'react'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import CompetitionSetupTreeHelper from '@components/event/competition/setup/CompetitionSetupTreeHelper.tsx'
import {CompetitionSetupForm} from '@components/event/competition/setup/common.ts'
import CompetitionSetupContainersWrapper from '@components/event/competition/setup/CompetitionSetupContainersWrapper.tsx'

type Props = {
    formContext: UseFormReturn<CompetitionSetupForm>
    handleFormSubmission: boolean
    handleSubmit?: (formData: CompetitionSetupForm) => Promise<void> // if needsContainersWrapper === true
    submitting?: boolean // if needsContainersWrapper === true
    treeHelperPortalContainer?: RefObject<HTMLDivElement> // needsContainersWrapper === false
}
const CompetitionSetup = ({formContext, ...props}: Props) => {
    const {
        fields: roundFields,
        insert: insertRound,
        remove: removeRound,
    } = useFieldArray({
        control: formContext.control,
        name: 'rounds',
    })

    const formWatch = formContext.watch('rounds')

    // Returns the Team Count for the specified round (not always THIS round)
    // IgnoredIndex can be provided to keep one match or group out of the calculation
    // Returns 0 when the Team Count is unknown (Undefined team field(s)) or an error occurred
    function getTeamCountForRound(
        roundIndex: number,
        isGroupRound: boolean,
        ignoredIndex?: number,
    ) {
        if (formWatch[roundIndex] === undefined || roundIndex > formWatch.length - 1) {
            return 0
        }
        const arrayLength = isGroupRound
            ? formWatch[roundIndex].groups.length
            : formWatch[roundIndex].matches.length

        if (arrayLength < 1) {
            return 0
        }

        // If ignoredIndex is provided, the team value of that match/group will not be added
        const teams = isGroupRound
            ? (ignoredIndex === undefined
                  ? formWatch[roundIndex].groups
                  : formWatch[roundIndex].groups.filter((_, index) => index !== ignoredIndex)
              ).map(g => g.teams)
            : (ignoredIndex === undefined
                  ? formWatch[roundIndex].matches
                  : formWatch[roundIndex].matches.filter((_, index) => index !== ignoredIndex)
              ).map(m => m.teams)

        if (teams.length < 1) {
            return 0
        }

        if (teams.find(v => v === '') !== undefined) {
            return 0
        }

        return (
            teams
                .map(v => Number(v))
                .reduce((acc, val) => {
                    if (val === undefined) {
                        return acc
                    } else if (acc === undefined) {
                        return val
                    } else {
                        return +acc + +val
                    }
                }) ?? 0
        )
    }

    // This allows the Tournament Tree Generator Form to exist outside the CompetitionSetup Form while being rendered inside
    const ownTreeHelperPortalContainer = useRef<HTMLDivElement>(null)

    const AddRoundButton = ({addIndex}: {addIndex: number}) => {
        return (
            <Box sx={{maxWidth: 200}}>
                <Button
                    variant="outlined"
                    onClick={() => {
                        insertRound(addIndex, {
                            name: '',
                            required: false,
                            matches: [],
                            groups: [],
                            useDefaultSeeding: true,
                            places: [],
                            isGroupRound: false,
                            useStartTimeOffsets: false,
                        })
                    }}
                    sx={{width: 1}}>
                    Add Round
                </Button>
            </Box>
        )
    }

    return (
        <CompetitionSetupContainersWrapper
            formContext={formContext}
            handleFormSubmission={props.handleFormSubmission}
            handleSubmit={props.handleSubmit}
            treeHelperPortalContainer={ownTreeHelperPortalContainer}>
            <Stack direction="row" spacing={2} sx={{justifyContent: 'end', mb: 4}}>
                <Button variant="outlined" onClick={() => formContext.reset({rounds: []})}>
                    Click to reset
                </Button>
                <CompetitionSetupTreeHelper
                    resetSetupForm={(formData: CompetitionSetupForm) => {
                        formContext.reset(formData)
                    }}
                    currentFormData={formWatch}
                    portalContainer={
                        props.treeHelperPortalContainer ?? ownTreeHelperPortalContainer
                    }
                />
                {props.handleFormSubmission && (
                    <SubmitButton label={'[todo] Save'} submitting={props.submitting ?? false} />
                )}
            </Stack>
            <Stack spacing={4}>
                <AddRoundButton addIndex={0} />
                {roundFields.map((roundField, roundIndex) => (
                    <Stack spacing={2} key={roundField.id}>
                        <CompetitionSetupRound
                            round={{index: roundIndex, id: roundField.id}}
                            formContext={formContext}
                            removeRound={removeRound}
                            teamCounts={{
                                prevRound: getTeamCountForRound(
                                    roundIndex - 1,
                                    formWatch[roundIndex - 1]?.isGroupRound ?? false,
                                ),
                                thisRound: getTeamCountForRound(
                                    roundIndex,
                                    formWatch[roundIndex]?.isGroupRound ?? false,
                                ),
                                nextRound: getTeamCountForRound(
                                    roundIndex + 1,
                                    formWatch[roundIndex + 1]?.isGroupRound ?? false,
                                ),
                            }}
                            getRoundTeamCountWithoutThis={(ignoredIndex, isGroupRound) =>
                                getTeamCountForRound(roundIndex, isGroupRound, ignoredIndex)
                            }
                        />
                        <AddRoundButton addIndex={roundIndex + 1} />
                    </Stack>
                ))}
            </Stack>
        </CompetitionSetupContainersWrapper>
    )
}

export default CompetitionSetup
