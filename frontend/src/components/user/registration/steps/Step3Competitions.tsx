import {Box, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {CheckboxButtonGroup, useFormContext} from 'react-hook-form-mui'
import {Controller} from 'react-hook-form'
import {FormInputCheckbox} from '@components/form/input/FormInputCheckbox.tsx'
import {FormInputSelect} from '@components/form/input/FormInputSelect.tsx'
import {
    GetCompetitionsForRegistrationResponse,
    GetRatingCategoriesForEventResponse,
} from '@api/types.gen.ts'
import {RegistrationForm} from '@components/user/registration/common.ts'

interface Step3CompetitionsProps {
    competitionsData?: GetCompetitionsForRegistrationResponse
    ratingCategories?: GetRatingCategoriesForEventResponse
    watchBirthYear: string
}

export const Step3Competitions = ({
    competitionsData,
    ratingCategories,
    watchBirthYear,
}: Step3CompetitionsProps) => {
    const {t} = useTranslation()
    const formContext = useFormContext<RegistrationForm>()

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

    if (!competitionsData || competitionsData.length === 0) {
        return (
            <Box sx={{textAlign: 'center', py: 4}}>
                <Typography variant="body1" color="text.secondary">
                    {t('event.competition.noCompetitionsAvailable')}
                </Typography>
            </Box>
        )
    }

    return (
        <Stack spacing={3}>
            <Box>
                <Typography variant="body2" sx={{mb: 2}}>
                    {t('event.competition.competitions')}
                </Typography>
                <Stack spacing={3}>
                    {competitionsData.map((competition, index) => {
                        const competitionReg = watchCompetitions?.[index]
                        const isChecked = competitionReg?.checked ?? false
                        const optionalFees =
                            competition.properties.fees?.filter(f => !f.required) ?? []

                        return (
                            <Box key={competition.id}>
                                <Controller
                                    name={`competitions.${index}.checked`}
                                    control={formContext.control}
                                    render={({field}) => (
                                        <FormInputCheckbox
                                            name={field.name}
                                            label={competition.properties.name}
                                            checked={field.value}
                                            onChange={field.onChange}
                                        />
                                    )}
                                />
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
            </Box>
        </Stack>
    )
}
