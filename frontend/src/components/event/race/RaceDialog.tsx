import {
    addRace,
    getNamedParticipants,
    getRaceCategories,
    getRaceTemplates,
    RaceDto,
    RaceRequest,
    updateRace,
} from '../../../api'
import {
    AutocompleteList,
    AutocompleteListElement,
    BaseEntityDialogProps,
} from '../../../utils/types.ts'
import EntityDialog from '../../EntityDialog.tsx'
import {Box, Button, Grid2, IconButton, Stack, Tooltip, Zoom} from '@mui/material'
import {FormInputText} from '../../form/input/FormInputText.tsx'
import {useTranslation} from 'react-i18next'
import {eventIndexRoute} from '../../../routes.tsx'
import {AutocompleteElement, SwitchElement, useFieldArray, useForm} from 'react-hook-form-mui'
import FormInputNumber from '../../form/input/FormInputNumber.tsx'
import {FormInputCurrency} from '../../form/input/FormInputCurrency.tsx'
import {useFeedback, useFetch} from '../../../utils/hooks.ts'
import DeleteIcon from '@mui/icons-material/Delete'

type RaceForm = {
    identifier?: string
    name?: string
    shortName?: string
    description?: string
    countMales?: string
    countFemales?: string
    countNonBinary?: string
    countMixed?: string
    participationFee?: string
    rentalFee?: string
    raceCategory?: AutocompleteListElement
    namedParticipants: {
        namedParticipant?: AutocompleteListElement
        required?: boolean
        countMales?: string
        countFemales?: string
        countNonBinary?: string
        countMixed?: string
    }[]
    template?: AutocompleteListElement
}

const RaceDialog = (props: BaseEntityDialogProps<RaceDto>) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventIndexRoute.useParams()

    const addAction = (formData: RaceForm) => {
        return addRace({
            path: {eventId: eventId},
            body: mapFormToRequest(formData),
        })
    }

    const editAction = (formData: RaceForm, entity: RaceDto) => {
        return updateRace({
            path: {eventId: entity.event, raceId: entity.id},
            body: mapFormToRequest(formData),
        })
    }

    const defaultValues: RaceForm = {
        identifier: '',
        name: '',
        countMales: '0',
        countFemales: '0',
        countNonBinary: '0',
        countMixed: '0',
        participationFee: '',
        rentalFee: '',
        namedParticipants: [],
    }

    const {data: templatesData, pending: templatesPending} = useFetch(
        signal => getRaceTemplates({signal}),
        {
            onResponse: result => {
                if (result.error) {
                    feedback.error(
                        t('common.load.error.multiple', {
                            entity: t('event.race.template.templates'),
                        }),
                    )
                }
            },
        },
        [], // todo: reload when opening the dialog
    )
    const templates: AutocompleteList =
        templatesData?.data.map(dto => ({
            id: dto.id,
            label: dto.properties.name,
        })) ?? []

    const {data: namedParticipantsData, pending: namedParticipantsPending} = useFetch(
        signal => getNamedParticipants({signal}),
        {
            onResponse: result => {
                if (result.error) {
                    feedback.error(
                        t('common.load.error.multiple', {
                            entity: t('event.race.namedParticipant.namedParticipants'),
                        }),
                    )
                }
            },
        },
        [], // todo: reload when opening the dialog
    )

    const namedParticipants: AutocompleteList =
        namedParticipantsData?.map(dto => ({
            id: dto.id,
            label: dto.name,
        })) ?? []

    const {data: categoriesData, pending: categoriesPending} = useFetch(
        signal => getRaceCategories({signal}),
        {
            onResponse: result => {
                if (result.error) {
                    feedback.error(
                        t('common.load.error.multiple', {
                            entity: t('event.race.category.categories'),
                        }),
                    )
                }
            },
        },
        [], // todo: reload when opening the dialog
    )
    const categories: AutocompleteList =
        categoriesData?.map(dto => ({
            id: dto.id,
            label: dto.name,
        })) ?? []

    const values = props.entity ? mapDtoToForm(props.entity, templates) : undefined

    const formContext = useForm<RaceForm>({
        defaultValues: defaultValues,
        values: values,
    })

    const {
        fields: namedParticipantFields,
        append: appendNamedParticipant,
        remove: removeNamedParticipant,
    } = useFieldArray({
        control: formContext.control,
        name: 'namedParticipants',
        keyName: 'fieldId',
    })

    const entityNameKey = {entity: t('event.event')}

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            title={action =>
                action === 'add'
                    ? t('entity.add.action', entityNameKey)
                    : t('entity.edit.action', entityNameKey)
            } // could be shortened but then the translation key can not be found by search
            addAction={addAction}
            editAction={editAction}
            onSuccess={() => {}}>
            <Stack spacing={2}>
                <Box sx={{pb: 4}}>
                    <AutocompleteElement
                        name="template"
                        options={templates}
                        label={t('event.race.template.template')}
                        loading={templatesPending}
                    />
                </Box>
                <FormInputText name="identifier" label={t('event.race.identifier')} required />
                <FormInputText name="name" label={t('event.race.name')} required />
                <FormInputText name="shortName" label={t('event.race.shortName')} />
                <FormInputText name="description" label={t('event.race.description')} />
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
                />
                {namedParticipantFields.map((field, index) => (
                    <Stack direction="row" spacing={2} alignItems={'center'}>
                        <Box
                            key={'remove' + field.fieldId}
                            sx={{p: 2, border: 1, borderRadius: 5, boxSizing: 'border-box'}}>
                            <Grid2 container flexDirection="row" spacing={2} sx={{mb: 2}}>
                                <Grid2 size="grow" sx={{minWidth: 250}}>
                                    <AutocompleteElement
                                        name={'namedParticipants[' + index + '].namedParticipant'}
                                        options={namedParticipants}
                                        label={t('event.race.namedParticipant.role')}
                                        autocompleteProps={{
                                            noOptionsText: t('common.form.autocomplete.noOptions'),
                                        }}
                                        loading={namedParticipantsPending}
                                    />
                                </Grid2>
                                <Box sx={{my: 'auto'}}>
                                    <SwitchElement
                                        name={'namedParticipants[' + index + '].required'}
                                        label={t('event.race.namedParticipant.required')}
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
                                }}>
                                <DeleteIcon />
                            </IconButton>
                        </Tooltip>
                    </Stack>
                ))}
                <Box sx={{minWidth: 200, margin: 'auto'}}>
                    <Button
                        onClick={() =>
                            appendNamedParticipant({
                                required: false,
                                countMales: '0',
                                countFemales: '0',
                                countNonBinary: '0',
                                countMixed: '0',
                            })
                        }
                        sx={{width: 1}}>
                        {t('event.race.namedParticipant.add')}
                    </Button>
                </Box>
            </Stack>
        </EntityDialog>
    )
}

function mapFormToRequest(formData: RaceForm): RaceRequest {
    console.log(formData.namedParticipants[0])
    return {
        properties: {
            identifier: formData.identifier!!, // !! is safe because of FormValidation
            name: formData.name!!,
            shortName: formData.shortName,
            description: formData.description,
            countMales: Number(formData.countMales),
            countFemales: Number(formData.countFemales),
            countNonBinary: Number(formData.countNonBinary),
            countMixed: Number(formData.countMixed),
            participationFee: formData.participationFee!!.replace(',', '.'),
            rentalFee: formData.rentalFee!!.replace(',', '.'),
            raceCategory: formData.raceCategory?.id,
            namedParticipants: formData.namedParticipants.map(value => ({
                namedParticipant: value.namedParticipant!!.id,
                required: value.required!!,
                countMales: Number(value.countMales),
                countFemales: Number(value.countFemales),
                countNonBinary: Number(value.countNonBinary),
                countMixed: Number(value.countMixed),
            })),
        },
        template: formData.template?.id,
    }
}

function mapDtoToForm(dto: RaceDto, templates: AutocompleteList): RaceForm {
    const template = templates.find(t => t.id === dto.template)
    return {
        identifier: dto.properties.identifier,
        name: dto.properties.name,
        shortName: dto.properties.shortName,
        description: dto.properties.description,
        countMales: dto.properties.countMales.toString(),
        countFemales: dto.properties.countFemales.toString(),
        countNonBinary: dto.properties.countNonBinary.toString(),
        countMixed: dto.properties.countMixed.toString(),
        participationFee: dto.properties.participationFee,
        rentalFee: dto.properties.rentalFee,
        raceCategory: dto.properties.raceCategory
            ? {
                  id: dto.properties.raceCategory?.id,
                  label: dto.properties.raceCategory.name,
              }
            : undefined,
        namedParticipants: dto.properties.namedParticipants.map(value => ({
            namedParticipant: {id: value.id, label: value.name},
            required: value.required,
            countMales: value.countMales.toString(),
            countFemales: value.countFemales.toString(),
            countNonBinary: value.countNonBinary.toString(),
            countMixed: value.countMixed.toString(),
        })),
        template: template ? {id: template.id, label: template.label} : undefined,
    }
}

export default RaceDialog
