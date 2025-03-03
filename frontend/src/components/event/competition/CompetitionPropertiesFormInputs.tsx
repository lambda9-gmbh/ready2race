import {AutocompleteOption} from '@utils/types.ts'
import {SwitchElement, useFieldArray, UseFormReturn} from 'react-hook-form-mui'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {Box, Button, Divider, Grid2, IconButton, Stack, Tooltip, Zoom} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import {CompetitionForm} from './common.ts'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {getNamedParticipants, getCompetitionCategories, getFees} from '@api/sdk.gen.ts'
import FormInputAutocomplete from '@components/form/input/FormInputAutocomplete.tsx'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {FormInputCurrency} from '@components/form/input/FormInputCurrency.tsx'

type Props = {
    formContext: UseFormReturn<CompetitionForm>
    fieldArrayModified?: () => void
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
                        t('common.load.error.multiple', {
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
                    t('common.load.error.multiple', {
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
                        t('common.load.error.multiple', {
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

    const {
        fields: namedParticipantFields,
        append: appendNamedParticipant,
        remove: removeNamedParticipant,
    } = useFieldArray({
        control: props.formContext.control,
        name: 'namedParticipants',
        keyName: 'fieldId',
    })

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
            <FormInputText name="name" label={t('entity.name')} required />
            <FormInputText name="shortName" label={t('event.competition.shortName')} />
            <FormInputText name="description" label={t('entity.description')} />
            <FormInputAutocomplete
                name="competitionCategory"
                options={categories}
                label={t('event.competition.category.category')}
                loading={categoriesPending}
                autocompleteProps={{
                    getOptionKey: field => field.id,
                }}
            />
            <Divider />
            <FormInputLabel label={t('event.competition.namedParticipant.namedParticipants')}>
                {namedParticipantFields.map((field, index) => (
                    <Stack direction="row" spacing={2} alignItems={'center'} key={field.fieldId}>
                        <Box sx={{p: 2, border: 1, borderRadius: 5, boxSizing: 'border-box'}}>
                            <Stack spacing={4}>
                                <FormInputAutocomplete
                                    name={'namedParticipants[' + index + '].namedParticipant'}
                                    options={namedParticipants}
                                    label={t('event.competition.namedParticipant.role')}
                                    loading={namedParticipantsPending}
                                    required
                                />
                                <Stack direction="row" spacing={2}>
                                    <FormInputNumber
                                        name={'namedParticipants[' + index + '].countMales'}
                                        label={t('event.competition.count.males')}
                                        min={0}
                                        integer={true}
                                        required
                                        sx={{flex: 1}}
                                    />
                                    <FormInputNumber
                                        name={'namedParticipants[' + index + '].countFemales'}
                                        label={t('event.competition.count.females')}
                                        min={0}
                                        integer={true}
                                        required
                                        sx={{flex: 1}}
                                    />
                                </Stack>
                                <Stack direction="row" spacing={2}>
                                    <FormInputNumber
                                        name={'namedParticipants[' + index + '].countNonBinary'}
                                        label={t('event.competition.count.nonBinary')}
                                        min={0}
                                        integer={true}
                                        required
                                        sx={{flex: 1}}
                                    />
                                    <FormInputNumber
                                        name={'namedParticipants[' + index + '].countMixed'}
                                        label={t('event.competition.count.mixed')}
                                        min={0}
                                        integer={true}
                                        required
                                        sx={{flex: 1}}
                                    />
                                </Stack>
                            </Stack>
                        </Box>
                        <Tooltip
                            title={t('common.delete')}
                            disableInteractive
                            slots={{
                                transition: Zoom,
                            }}>
                            <IconButton
                                onClick={() => {
                                    removeNamedParticipant(index)
                                    props.fieldArrayModified?.()
                                }}>
                                <DeleteIcon />
                            </IconButton>
                        </Tooltip>
                    </Stack>
                ))}
            </FormInputLabel>
            <Box sx={{minWidth: 200, margin: 'auto'}}>
                <Button
                    onClick={() => {
                        appendNamedParticipant({
                            namedParticipant: {id: '', label: ''},
                            countMales: '0',
                            countFemales: '0',
                            countNonBinary: '0',
                            countMixed: '0',
                        })
                        props.fieldArrayModified?.()
                    }}
                    sx={{width: 1}}>
                    {t('event.competition.namedParticipant.add')}
                </Button>
            </Box>
            <Divider />
            <FormInputLabel label={t('event.competition.fee.fees')}>
                {feeFields.map((field, index) => (
                    <Stack direction="row" spacing={2} alignItems={'center'} key={field.fieldId}>
                        <Box sx={{p: 2, border: 1, borderRadius: 5, boxSizing: 'border-box'}}>
                            <Grid2 container flexDirection="row" spacing={2} sx={{mb: 4}}>
                                <Grid2 size="grow" sx={{minWidth: 250}}>
                                    <FormInputAutocomplete
                                        name={'fees[' + index + '].fee'}
                                        options={fees}
                                        label={t('event.competition.fee.type')}
                                        loading={feesPending}
                                        required
                                    />
                                </Grid2>
                                <Box sx={{my: 'auto'}}>
                                    <SwitchElement
                                        name={'fees[' + index + '].required'}
                                        label={
                                            <FormInputLabel
                                                label={t('event.competition.fee.required.required')}
                                                required={true}
                                            />
                                        }
                                    />
                                </Box>
                            </Grid2>
                            <Stack spacing={4}>
                                <FormInputCurrency
                                    name={'fees[' + index + '].amount'}
                                    label={t('event.competition.fee.amount')}
                                    required
                                />
                            </Stack>
                        </Box>
                        <Tooltip
                            title={t('common.delete')}
                            disableInteractive
                            slots={{
                                transition: Zoom,
                            }}>
                            <IconButton
                                onClick={() => {
                                    removeFee(index)
                                    props.fieldArrayModified?.()
                                }}>
                                <DeleteIcon />
                            </IconButton>
                        </Tooltip>
                    </Stack>
                ))}
            </FormInputLabel>
            <Box sx={{minWidth: 200, margin: 'auto'}}>
                <Button
                    onClick={() => {
                        appendFee({
                            fee: {id: '', label: ''},
                            required: false,
                            amount: '0',
                        })
                        props.fieldArrayModified?.()
                    }}
                    sx={{width: 1}}>
                    {t('event.competition.fee.add')}
                </Button>
            </Box>
        </>
    )
}
