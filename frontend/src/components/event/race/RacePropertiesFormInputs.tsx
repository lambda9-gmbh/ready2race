import {AutocompleteOption} from '@utils/types.ts'
import {AutocompleteElement, SwitchElement, useFieldArray, UseFormReturn} from 'react-hook-form-mui'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {Box, Button, Grid2, IconButton, Stack, Tooltip, Zoom} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import {RaceForm} from './common.ts'
import {FormInputText} from "@components/form/input/FormInputText.tsx";
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {FormInputCurrency} from '@components/form/input/FormInputCurrency.tsx'
import {getNamedParticipants, getRaceCategories} from "@api/sdk.gen.ts";

type Props = {
    formContext: UseFormReturn<RaceForm>
    fieldArrayModified?: () => void
}
export const RacePropertiesFormInputs = (props: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {data: namedParticipantsData, pending: namedParticipantsPending} = useFetch(
        signal => getNamedParticipants({signal}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple', {
                            entity: t('event.race.namedParticipant.namedParticipants'),
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

    const {data: categoriesData, pending: categoriesPending} = useFetch(
        signal => getRaceCategories({signal}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple', {
                            entity: t('event.race.category.categories'),
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

    return (
        <>
            <FormInputText name="identifier" label={t('event.race.identifier')} required />
            <FormInputText name="name" label={t('entity.name')} required />
            <FormInputText name="shortName" label={t('event.race.shortName')} />
            <FormInputText name="description" label={t('entity.description')} />
            <Stack direction="row" spacing={2} sx={{mb: 4}}>
                <FormInputNumber
                    name="countMales"
                    label={t('event.race.count.males')}
                    min={0}
                    integer={true}
                    required
                    sx={{flex: 1}}
                />
                <FormInputNumber
                    name="countFemales"
                    label={t('event.race.count.females')}
                    min={0}
                    integer={true}
                    required
                    sx={{flex: 1}}
                />
            </Stack>
            <Stack direction="row" spacing={2} sx={{mb: 4}}>
                <FormInputNumber
                    name="countNonBinary"
                    label={t('event.race.count.nonBinary')}
                    min={0}
                    integer={true}
                    required
                    sx={{flex: 1}}
                />
                <FormInputNumber
                    name="countMixed"
                    label={t('event.race.count.mixed')}
                    min={0}
                    integer={true}
                    required
                    sx={{flex: 1}}
                />
            </Stack>
            <FormInputCurrency
                name="participationFee"
                label={t('event.race.participationFee')}
                required
            />
            <FormInputCurrency name="rentalFee" label={t('event.race.rentalFee')} required />
            <AutocompleteElement
                name="raceCategory"
                options={categories}
                label={t('event.race.category.category')}
                loading={categoriesPending}
                autocompleteProps={{
                    getOptionKey: field => field.id,
                }}
            />
            {namedParticipantFields.map((field, index) => (
                <Stack direction="row" spacing={2} alignItems={'center'} key={field.fieldId}>
                    <Box sx={{p: 2, border: 1, borderRadius: 5, boxSizing: 'border-box'}}>
                        <Grid2 container flexDirection="row" spacing={2} sx={{mb: 2}}>
                            <Grid2 size="grow" sx={{minWidth: 250}}>
                                <AutocompleteElement
                                    name={'namedParticipants[' + index + '].namedParticipant'}
                                    options={namedParticipants}
                                    label={t('event.race.namedParticipant.role')}
                                    loading={namedParticipantsPending}
                                />
                            </Grid2>
                            <Box sx={{my: 'auto'}}>
                                <SwitchElement
                                    name={'namedParticipants[' + index + '].required'}
                                    label={t('event.race.namedParticipant.required.required')}
                                />
                            </Box>
                        </Grid2>
                        <Stack direction="column" spacing={2}>
                            <Stack direction="row" spacing={2}>
                                <FormInputNumber
                                    name={'namedParticipants[' + index + '].countMales'}
                                    label={t('event.race.count.males')}
                                    min={0}
                                    integer={true}
                                    required
                                    sx={{flex: 1}}
                                />
                                <FormInputNumber
                                    name={'namedParticipants[' + index + '].countFemales'}
                                    label={t('event.race.count.females')}
                                    min={0}
                                    integer={true}
                                    required
                                    sx={{flex: 1}}
                                />
                            </Stack>
                            <Stack direction="row" spacing={2}>
                                <FormInputNumber
                                    name={'namedParticipants[' + index + '].countNonBinary'}
                                    label={t('event.race.count.nonBinary')}
                                    min={0}
                                    integer={true}
                                    required
                                    sx={{flex: 1}}
                                />
                                <FormInputNumber
                                    name={'namedParticipants[' + index + '].countMixed'}
                                    label={t('event.race.count.mixed')}
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
            <Box sx={{minWidth: 200, margin: 'auto'}}>
                <Button
                    onClick={() => {
                        appendNamedParticipant({
                            namedParticipant: {id: '', label: ''},
                            required: false,
                            countMales: '0',
                            countFemales: '0',
                            countNonBinary: '0',
                            countMixed: '0',
                        })
                        props.fieldArrayModified?.()
                    }}
                    sx={{width: 1}}>
                    {t('event.race.namedParticipant.add')}
                </Button>
            </Box>
        </>
    )
}
