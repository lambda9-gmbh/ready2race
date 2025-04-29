import {Box, Button, Menu, MenuItem, Stack, Typography, useTheme} from '@mui/material'
import {useFieldArray, UseFormReturn} from 'react-hook-form-mui'
import CompetitionSetupRound from '@components/event/competition/setup/CompetitionSetupRound.tsx'
import {RefObject, useRef, useState, MouseEvent} from 'react'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import CompetitionSetupTreeHelper from '@components/event/competition/setup/CompetitionSetupTreeHelper.tsx'
import {
    CompetitionSetupForm,
    FormSetupRound,
    mapCompetitionSetupTemplateDtoToForm,
} from '@components/event/competition/setup/common.ts'
import CompetitionSetupContainersWrapper from '@components/event/competition/setup/CompetitionSetupContainersWrapper.tsx'
import AddRoundButton from './AddRoundButton'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getCompetitionSetupTemplates} from '@api/sdk.gen.ts'
import {useTranslation} from 'react-i18next'
import {CompetitionSetupTemplateDto} from '@api/types.gen.ts'

type Props = {
    formContext: UseFormReturn<CompetitionSetupForm>
    handleFormSubmission: boolean
    handleSubmit?: (formData: CompetitionSetupForm) => Promise<void> // if needsContainersWrapper === true
    submitting?: boolean // if needsContainersWrapper === true
    treeHelperPortalContainer?: RefObject<HTMLDivElement> // needsContainersWrapper === false
}
const CompetitionSetup = ({formContext, ...props}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const theme = useTheme()

    const [roundsError, setRoundsError] = useState<string | null>(null)

    const {
        fields: roundFields,
        insert: insertRound,
        remove: removeRound,
    } = useFieldArray({
        control: formContext.control,
        name: 'rounds',
        rules: {
            validate: value => {
                const roundHasUndefinedTeams = (round: FormSetupRound) => {
                    return (
                        round.matches.filter(match => match.teams === '').length > 0 || round.hasDuplicatable
                    )
                }

                for (let i = 1; i < value.length; i++) {
                    if (roundHasUndefinedTeams(value[i]) && roundHasUndefinedTeams(value[i - 1])) {
                        setRoundsError(
                            t('event.competition.setup.validation.noFollowingUnlimitedTeamsRounds'),
                        )

                        return 'noFollowingUnlimitedTeamsRounds'
                    }
                }

                if (value.length > 0 && !roundHasUndefinedTeams(value[0])) {
                    setRoundsError(t('event.competition.setup.validation.firstRoundUnlimitedTeams'))
                    return 'firstRoundUnlimitedTeams'

                }

                setRoundsError(null)
                return undefined
            },
        },
    })

    const formWatch = formContext.watch('rounds')

    const [allowRoundUpdates, setAllowRoundUpdates] = useState(true)

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

    const {data: templatesData} = useFetch(signal => getCompetitionSetupTemplates({signal}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(
                    t('common.load.error.multiple.short', {
                        entity: t('event.competition.setup.template.templates'),
                    }),
                )
            }
        },
    })

    const watchTemplateFields = formContext.watch(['name', 'description'])

    const resetForm = (rounds: Array<FormSetupRound>) => {
        formContext.reset({
            name: watchTemplateFields[0],
            description: watchTemplateFields[1],
            rounds: rounds,
        })
        setAllowRoundUpdates(false)
    }

    const handleSelectTemplate = async (template: CompetitionSetupTemplateDto) => {
        resetForm(mapCompetitionSetupTemplateDtoToForm(template).rounds)
    }

    const [templateMenuAnchorEl, setTemplateMenuAnchorEl] = useState<HTMLElement | null>(null)
    const templateMenuOpen = Boolean(templateMenuAnchorEl)
    const handleTemplateMenuClick = (event: MouseEvent<HTMLButtonElement>) => {
        setTemplateMenuAnchorEl(event.currentTarget)
    }
    const handleTemplateMenuClose = () => {
        setTemplateMenuAnchorEl(null)
    }

    console.log(formWatch)

    return (
        <CompetitionSetupContainersWrapper
            formContext={formContext}
            handleFormSubmission={props.handleFormSubmission}
            handleSubmit={props.handleSubmit}
            treeHelperPortalContainer={ownTreeHelperPortalContainer}>
            <Stack
                direction="row"
                sx={{
                    justifyContent: 'end',
                    mb: 2,
                    gap: 1,
                    [theme.breakpoints.down('md')]: {flexDirection: 'column', alignItems: 'start'},
                }}>
                <Button variant="outlined" onClick={() => resetForm([])}>
                    {t('event.competition.setup.reset')}
                </Button>
                <Box>
                    <Button
                        id="competition-setup-template-selection-button"
                        variant="outlined"
                        aria-controls={
                            templateMenuOpen
                                ? 'id="competition-setup-template-selection-menu"'
                                : undefined
                        }
                        aria-haspopup={'true'}
                        aria-expanded={templateMenuOpen ? 'true' : undefined}
                        onClick={handleTemplateMenuClick}>
                        {t('event.competition.setup.template.select')}
                    </Button>
                    <Menu
                        id="competition-setup-template-selection-menu"
                        anchorEl={templateMenuAnchorEl}
                        open={templateMenuOpen}
                        onClose={handleTemplateMenuClose}
                        disableScrollLock={true}
                        MenuListProps={{
                            'aria-labelledby': 'competition-setup-template-selection-button',
                        }}>
                        {templatesData?.data?.map((template, templateIndex) => (
                            <MenuItem
                                key={templateIndex + template.id}
                                onClick={() => {
                                    handleSelectTemplate(template).then(_ => {})
                                    handleTemplateMenuClose()
                                }}
                                value={template.id}>
                                {template.name}
                            </MenuItem>
                        ))}
                    </Menu>
                </Box>
                <CompetitionSetupTreeHelper
                    resetSetupForm={(formDataRounds: Array<FormSetupRound>) => {
                        resetForm(formDataRounds)
                    }}
                    currentFormData={formWatch}
                    portalContainer={
                        props.treeHelperPortalContainer ?? ownTreeHelperPortalContainer
                    }
                />
                {props.handleFormSubmission && (
                    <SubmitButton
                        label={t('event.competition.setup.save.save')}
                        submitting={props.submitting ?? false}
                    />
                )}
            </Stack>
            <Stack spacing={4}>
                <AddRoundButton index={0} insertRound={insertRound} />
                {roundsError && <Typography color={'error'}>{roundsError}</Typography>}
                {roundFields.map((roundField, roundIndex) => (
                    <Stack spacing={4} key={roundField.id}>
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
                            allowRoundUpdates={{
                                value: allowRoundUpdates,
                                set: setAllowRoundUpdates,
                            }}
                        />
                        <AddRoundButton index={roundIndex + 1} insertRound={insertRound} />
                    </Stack>
                ))}
            </Stack>
        </CompetitionSetupContainersWrapper>
    )
}

export default CompetitionSetup
