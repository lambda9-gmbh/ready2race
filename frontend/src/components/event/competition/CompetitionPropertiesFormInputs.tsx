import {AutocompleteOption} from '@utils/types.ts'
import {ErrorOption, FieldValues, useFieldArray, UseFormReturn} from 'react-hook-form-mui'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {Box, Button, Card, Divider, IconButton, Stack, Tooltip, Typography} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import {CompetitionForm} from './common.ts'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {
    getNamedParticipants,
    getCompetitionCategories,
    getFees,
    getCompetitionSetupTemplateOverview,
} from '@api/sdk.gen.ts'
import FormInputAutocomplete from '@components/form/input/FormInputAutocomplete.tsx'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {FormInputCurrency} from '@components/form/input/FormInputCurrency.tsx'
import {useState} from 'react'
import FormInputSwitch from '@components/form/input/FormInputSwitch.tsx'

type Props = {
    formContext: UseFormReturn<CompetitionForm>
    hideCompetitionSetupTemplate?: boolean
}

// todo: rework styling
export const CompetitionPropertiesFormInputs = (props: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {data: namedParticipantsData, pending: namedParticipantsPending} = useFetch(
        signal => getNamedParticipants({signal}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('event.competition.namedParticipant.namedParticipants'),
                        }),
                    )
                }
            },
        },
    )

    const namedParticipants: AutocompleteOption[] =
        namedParticipantsData?.data.map(dto => ({
            id: dto.id,
            label: dto.name,
        })) ?? []

    const {data: feesData, pending: feesPending} = useFetch(signal => getFees({signal}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(
                    t('common.load.error.multiple.short', {
                        entity: t('event.competition.fee.fees'),
                    }),
                )
            }
        },
    })

    const fees: AutocompleteOption[] =
        feesData?.data.map(dto => ({
            id: dto.id,
            label: dto.name,
        })) ?? []

    const {data: categoriesData, pending: categoriesPending} = useFetch(
        signal => getCompetitionCategories({signal}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('event.competition.category.categories'),
                        }),
                    )
                }
            },
        },
    )
    const categories: AutocompleteOption[] =
        categoriesData?.data.map(dto => ({
            id: dto.id,
            label: dto.name,
        })) ?? []

    const {data: competitionSetupTemplatesData, pending: competitionSetupTemplatesPending} =
        useFetch(signal => getCompetitionSetupTemplateOverview({signal}), {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('event.competition.setup.template.templates'),
                        }),
                    )
                }
            },
            preCondition: () => props.hideCompetitionSetupTemplate !== true,
        })
    const setupTemplates: AutocompleteOption[] =
        competitionSetupTemplatesData?.map(dto => ({
            id: dto.id,
            label: dto.name,
        })) ?? []

    const [namedParticipantsError, setNamedParticipantsError] = useState<string | null>(null)

    const {
        fields: namedParticipantFields,
        append: appendNamedParticipant,
        remove: removeNamedParticipant,
    } = useFieldArray({
        control: props.formContext.control,
        name: 'namedParticipants',
        keyName: 'fieldId',
        rules: {
            validate: values => {
                if (values.length < 1) {
                    setNamedParticipantsError(
                        t('event.competition.namedParticipant.error.emptyList'),
                    )
                    return 'empty'
                }

                const countedMap = values.reduce<Map<string, number>>((acc, val) => {
                    if (val.namedParticipant) {
                        const id = val.namedParticipant.id
                        const old = acc.get(id)
                        acc.set(id, old ? old + 1 : 1)
                    }
                    return acc
                }, new Map())
                const duplicates = Array.from(countedMap.entries())
                    .filter(([, count]) => count > 1)
                    .map(([id]) => namedParticipants.find(x => x?.id === id)?.label)
                    .filter(label => label !== undefined)

                if (duplicates.length > 0) {
                    setNamedParticipantsError(
                        duplicates.length > 1
                            ? t('event.competition.namedParticipant.error.duplicates.multiple', {
                                  labels: duplicates.reduce((acc, val) => acc + val + ' '),
                              })
                            : t('event.competition.namedParticipant.error.duplicates.one', {
                                  label: duplicates[0],
                              }) +
                                  ' ' +
                                  t('event.competition.namedParticipant.error.duplicates.message'),
                    )
                    return 'duplicates'
                }

                setNamedParticipantsError(null)
                return undefined
            },
        },
    })

    const validateCounts = (vals: FieldValues, index: number): string | undefined => {
        if (
            vals['namedParticipants'][index]['countMales'] +
                vals['namedParticipants'][index]['countFemales'] +
                vals['namedParticipants'][index]['countNonBinary'] +
                vals['namedParticipants'][index]['countMixed'] <
            1
        ) {
            const noCountError: ErrorOption = {
                type: 'validate',
                message: t('event.competition.namedParticipant.error.noCount'),
            }
            props.formContext.setError(
                `namedParticipants[${index}].countMales` as `namedParticipants.${number}.countMales`,
                noCountError,
            )
            props.formContext.setError(
                `namedParticipants[${index}].countFemales` as `namedParticipants.${number}.countFemales`,
                noCountError,
            )
            props.formContext.setError(
                `namedParticipants[${index}].countNonBinary` as `namedParticipants.${number}.countNonBinary`,
                noCountError,
            )
            props.formContext.setError(
                `namedParticipants[${index}].countMixed` as `namedParticipants.${number}.countMixed`,
                noCountError,
            )
            return t('event.competition.namedParticipant.error.noCount')
        } else {
            props.formContext.clearErrors([
                `namedParticipants[${index}].countMales` as `namedParticipants.${number}.countMales`,
                `namedParticipants[${index}].countFemales` as `namedParticipants.${number}.countFemales`,
                `namedParticipants[${index}].countNonBinary` as `namedParticipants.${number}.countNonBinary`,
                `namedParticipants[${index}].countMixed` as `namedParticipants.${number}.countMixed`,
            ])
            return undefined
        }
    }

    const {
        fields: feeFields,
        append: appendFee,
        remove: removeFee,
    } = useFieldArray({
        control: props.formContext.control,
        name: 'fees',
        keyName: 'fieldId',
    })

    return (
        <>
            <FormInputText name="identifier" label={t('event.competition.identifier')} required />
            <FormInputText name="name" label={t('event.competition.name')} required />
            <FormInputText name="shortName" label={t('event.competition.shortName')} />
            <FormInputText name="description" label={t('event.competition.description')} />
            <FormInputAutocomplete
                name="competitionCategory"
                options={categories}
                label={t('event.competition.category.category')}
                loading={categoriesPending}
                autocompleteProps={{
                    getOptionKey: field => field.id,
                }}
            />
            {props.hideCompetitionSetupTemplate !== true && (
                <FormInputAutocomplete
                    name="setupTemplate"
                    options={setupTemplates}
                    label={t('event.competition.setup.template.template')}
                    loading={competitionSetupTemplatesPending}
                    autocompleteProps={{
                        getOptionKey: field => field.id,
                    }}
                />
            )}
            <Divider />
            <FormInputLabel label={t('event.competition.namedParticipant.namedParticipants')}>
                {namedParticipantsError && (
                    <Typography color={'error'}>{namedParticipantsError}</Typography>
                )}
                <Stack spacing={2}>
                    {namedParticipantFields.map((field, index) => (
                        <Stack
                            direction="row"
                            spacing={2}
                            alignItems={'center'}
                            key={field.fieldId}>
                            <Card
                                sx={{
                                    p: 2,
                                    boxSizing: 'border-box',
                                    flex: 1,
                                }}>
                                <Stack spacing={4}>
                                    <FormInputAutocomplete
                                        name={'namedParticipants[' + index + '].namedParticipant'}
                                        options={namedParticipants}
                                        label={t('event.competition.namedParticipant.role')}
                                        loading={namedParticipantsPending}
                                        required
                                        rules={{
                                            validate: val => {
                                                // Extra Required Check (the normal rule wasn't consistent when a new entry was created)
                                                if (val.id === '') {
                                                    return t('common.form.required')
                                                }
                                            },
                                        }}
                                    />
                                    <Stack direction="row" spacing={2} sx={{alignItems: 'end'}}>
                                        <FormInputNumber
                                            name={'namedParticipants[' + index + '].countMales'}
                                            label={t('event.competition.count.males')}
                                            min={0}
                                            integer={true}
                                            required
                                            sx={{flex: 1}}
                                            rules={{
                                                validate: (_, vals) => validateCounts(vals, index),
                                            }}
                                        />
                                        <FormInputNumber
                                            name={'namedParticipants[' + index + '].countFemales'}
                                            label={t('event.competition.count.females')}
                                            min={0}
                                            integer={true}
                                            required
                                            sx={{flex: 1}}
                                            rules={{
                                                validate: (_, vals) => validateCounts(vals, index),
                                            }}
                                        />
                                    </Stack>
                                    <Stack direction="row" spacing={2} sx={{alignItems: 'end'}}>
                                        <FormInputNumber
                                            name={'namedParticipants[' + index + '].countNonBinary'}
                                            label={t('event.competition.count.nonBinary')}
                                            min={0}
                                            integer={true}
                                            required
                                            sx={{flex: 1}}
                                            rules={{
                                                validate: (_, vals) => validateCounts(vals, index),
                                            }}
                                        />
                                        <FormInputNumber
                                            name={'namedParticipants[' + index + '].countMixed'}
                                            label={t('event.competition.count.mixed')}
                                            min={0}
                                            integer={true}
                                            required
                                            sx={{flex: 1}}
                                            rules={{
                                                validate: (_, vals) => validateCounts(vals, index),
                                            }}
                                        />
                                    </Stack>
                                </Stack>
                            </Card>
                            <Tooltip title={t('common.delete')}>
                                <IconButton
                                    onClick={() => {
                                        removeNamedParticipant(index)
                                    }}>
                                    <DeleteIcon />
                                </IconButton>
                            </Tooltip>
                        </Stack>
                    ))}
                </Stack>
            </FormInputLabel>
            <Box sx={{minWidth: 200, margin: 'auto'}}>
                <Button
                    onClick={() => {
                        appendNamedParticipant({
                            namedParticipant: null,
                            countMales: '0',
                            countFemales: '0',
                            countNonBinary: '0',
                            countMixed: '0',
                        })
                    }}
                    sx={{width: 1}}>
                    {t('event.competition.namedParticipant.add')}
                </Button>
            </Box>
            <Divider />
            <FormInputLabel label={t('event.competition.fee.fees')}>
                <Stack spacing={2}>
                    {feeFields.map((field, index) => (
                        <Stack
                            direction="row"
                            spacing={2}
                            alignItems={'center'}
                            key={field.fieldId}>
                            <Card
                                sx={{
                                    p: 2,
                                    boxSizing: 'border-box',
                                    flex: 1,
                                }}>
                                <Stack spacing={4}>
                                    <FormInputAutocomplete
                                        name={'fees[' + index + '].fee'}
                                        options={fees}
                                        label={t('event.competition.fee.type')}
                                        loading={feesPending}
                                        required
                                    />
                                    <FormInputSwitch
                                        name={'fees[' + index + '].required'}
                                        label={t('event.competition.fee.required.required')}
                                        horizontal
                                        reverse
                                    />
                                    <FormInputCurrency
                                        name={'fees[' + index + '].amount'}
                                        label={t('event.competition.fee.amount')}
                                        required
                                    />
                                </Stack>
                            </Card>
                            <Tooltip title={t('common.delete')}>
                                <IconButton
                                    onClick={() => {
                                        removeFee(index)
                                    }}>
                                    <DeleteIcon />
                                </IconButton>
                            </Tooltip>
                        </Stack>
                    ))}
                </Stack>
            </FormInputLabel>
            <Box sx={{minWidth: 200, margin: 'auto'}}>
                <Button
                    onClick={() => {
                        appendFee({
                            fee: null,
                            required: false,
                            amount: '0',
                        })
                    }}
                    sx={{width: 1}}>
                    {t('event.competition.fee.add')}
                </Button>
            </Box>
        </>
    )
}
