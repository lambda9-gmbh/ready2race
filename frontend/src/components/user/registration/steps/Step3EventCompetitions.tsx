import {Alert, Box, Divider, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {CheckboxButtonGroup, useFormContext} from 'react-hook-form-mui'
import {Controller} from 'react-hook-form'
import {FormInputCheckbox} from '@components/form/input/FormInputCheckbox.tsx'
import {FormInputSelect} from '@components/form/input/FormInputSelect.tsx'
import FormInputAutocomplete from '@components/form/input/FormInputAutocomplete.tsx'
import {
    EventPublicDto,
    GetCompetitionsForRegistrationResponse,
    GetRatingCategoriesForEventResponse,
} from '@api/types.gen.ts'
import {RegistrationForm} from '@components/user/registration/common.ts'
import Throbber from '@components/Throbber.tsx'
import {format} from 'date-fns'

interface Step3EventCompetitionsProps {
    availableEvents?: EventPublicDto[]
    competitionsData?: GetCompetitionsForRegistrationResponse
    competitionsLoading: boolean
    ratingCategories?: GetRatingCategoriesForEventResponse
    watchBirthYear: string
    /** PARTICIPANT must pick an event and at least one competition; CLUB selection is optional. */
    isParticipant: boolean
}

export const Step3EventCompetitions = ({
                                           availableEvents,
                                           competitionsData,
                                           competitionsLoading,
                                           ratingCategories,
                                           watchBirthYear,
                                           isParticipant,
                                       }: Step3EventCompetitionsProps) => {
    const {t} = useTranslation()
    const formContext = useFormContext<RegistrationForm>()

    const watchEvent = formContext.watch('event')
    const watchCompetitions = formContext.watch('competitions')

    /**
     * Check if a rating category's age restriction is valid for the current user's birth year
     */
    const isRatingCategoryValid = (ratingCategory: {
        yearFrom?: number
        yearTo?: number
    }): boolean => {
        if (!ratingCategory.yearFrom && !ratingCategory.yearTo) {
            return true
        }

        const birthYear = watchBirthYear ? Number(watchBirthYear) : null
        if (!birthYear) {
            return false
        }

        const meetsMinAge = !ratingCategory.yearFrom || birthYear >= ratingCategory.yearFrom
        const meetsMaxAge = !ratingCategory.yearTo || birthYear <= ratingCategory.yearTo

        return meetsMinAge && meetsMaxAge
    }

    const ratingCategoryOptions = (ratingCategoryRequired: boolean) => {
        if ((ratingCategories?.length ?? 0) === 0) {
            return []
        }

        const baseOptions = ratingCategoryRequired
            ? []
            : [
                {
                    id: 'none',
                    label: t('common.form.select.none'),
                },
            ]

        const filteredRatingCategories = ratingCategories?.filter(isRatingCategoryValid) ?? []

        return [
            ...baseOptions,
            ...filteredRatingCategories.map(dto => ({
                id: dto.ratingCategory.id,
                label: dto.ratingCategory.name,
            })),
        ]
    }

    const hasCompetitions = (competitionsData?.competitions.length ?? 0) > 0

    // The selected event's self-submission settings determine whether a participant can enter
    // their own result. Only then is providing an email address meaningful.
    const selectedEvent = availableEvents?.find(event => event.id === watchEvent?.id)
    const selfSubmissionPossible =
        (selectedEvent?.challengeEvent && selectedEvent?.allowSelfSubmission) ?? false

    // Show the email hint once at least one checked competition actually allows entering a result.
    const hasCheckedSelfSubmissionCompetition =
        selfSubmissionPossible &&
        (competitionsData?.competitions.some(
                (competition, index) =>
                    (watchCompetitions?.[index]?.checked ?? false) &&
                    competition.properties.challengeConfig != null,
            ) ??
            false)

    return (
        <Stack spacing={3}>
            <FormInputAutocomplete
                name="event"
                label={t('event.event')}
                required={isParticipant}
                options={(availableEvents ?? []).map(event => ({
                    id: event.id,
                    label: event.name,
                }))}
            />

            {!isParticipant && (
                <Alert severity={'info'}>
                    {t('user.registration.competitionSelectionOptional')}
                </Alert>
            )}

            {watchEvent &&
                (competitionsLoading ? (
                    <Throbber/>
                ) : hasCompetitions ? (
                    <Stack spacing={2}>
                        <Typography variant="subtitle2">
                            {t('event.competition.competitions')}
                        </Typography>
                        {competitionsData?.teamsEventOmitted && (
                            <Alert severity={'info'}>
                                {t('user.registration.teamCompetitionsOmitted')}
                            </Alert>
                        )}
                        <Stack spacing={3}>
                            {competitionsData?.competitions.map((competition, index) => {
                                const competitionReg = watchCompetitions?.[index]
                                const isChecked = competitionReg?.checked ?? false
                                const optionalFees =
                                    competition.properties.fees?.filter(f => !f.required) ?? []
                                const challengeConfig = competition.properties.challengeConfig

                                return (
                                    <Box key={competition.id}>
                                        {index > 0 && <Divider/>}
                                        <Controller
                                            name={`competitions.${index}.checked`}
                                            control={formContext.control}
                                            render={({field}) => (
                                                <FormInputCheckbox
                                                    name={field.name}
                                                    label={competition.properties.name}
                                                    checked={field.value}
                                                    onChange={(val, checked) => {
                                                        field.onChange(val)
                                                        if (checked) {
                                                            // Determine the rating category to use
                                                            let selectedRatingCategory = competition
                                                                .properties.ratingCategoryRequired
                                                                ? ''
                                                                : 'none'

                                                            // If rating category is required, check if there's exactly one valid option
                                                            if (
                                                                competition.properties
                                                                    .ratingCategoryRequired
                                                            ) {
                                                                const validRatingCategories =
                                                                    ratingCategories?.filter(rc =>
                                                                        isRatingCategoryValid(rc),
                                                                    )

                                                                // Auto-select if there's exactly one valid rating category
                                                                if (
                                                                    validRatingCategories?.length ===
                                                                    1
                                                                ) {
                                                                    selectedRatingCategory =
                                                                        validRatingCategories[0]
                                                                            .ratingCategory.id
                                                                }
                                                            }

                                                            formContext.setValue(
                                                                `competitions.${index}.ratingCategory`,
                                                                selectedRatingCategory,
                                                            )
                                                        }
                                                    }}
                                                />
                                            )}
                                        />
                                        {challengeConfig && (
                                            <Typography variant={'body2'}>
                                                {format(
                                                    new Date(challengeConfig.startAt),
                                                    t('format.datetime'),
                                                )}{' '}
                                                -{' '}
                                                {format(
                                                    new Date(challengeConfig.endAt),
                                                    t('format.datetime'),
                                                )}
                                            </Typography>
                                        )}
                                        {isChecked && (
                                            <Box sx={{ml: 4, mt: 2}}>
                                                <Stack spacing={2}>
                                                    {(ratingCategories?.length ?? 0) > 0 && (
                                                        <FormInputSelect
                                                            name={`competitions.${index}.ratingCategory`}
                                                            label={t(
                                                                'event.competition.registration.ratingCategory',
                                                            )}
                                                            options={ratingCategoryOptions(
                                                                competition.properties
                                                                    .ratingCategoryRequired,
                                                            )}
                                                            required={
                                                                competition.properties
                                                                    .ratingCategoryRequired
                                                            }
                                                        />
                                                    )}
                                                    {optionalFees.length > 0 && (
                                                        <CheckboxButtonGroup
                                                            label={t('event.registration.optionalFee')}
                                                            name={`competitions.${index}.optionalFees`}
                                                            labelKey={'name'}
                                                            options={optionalFees}
                                                            row
                                                        />
                                                    )}
                                                </Stack>
                                            </Box>
                                        )}
                                    </Box>
                                )
                            })}
                        </Stack>
                    </Stack>
                ) : (
                    <Box sx={{textAlign: 'center', py: 4}}>
                        <Typography variant="body1" color="text.secondary">
                            {t('event.competition.noCompetitionsAvailable')}
                        </Typography>
                    </Box>
                ))}

            {isParticipant && hasCheckedSelfSubmissionCompetition && (
                <Alert severity={'warning'}>
                    {t('user.registration.step.emailToSubmitResults')}
                </Alert>
            )}
        </Stack>
    )
}
