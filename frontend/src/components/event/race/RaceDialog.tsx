import {
    addRace,
    getNamedParticipants,
    getRaceCategories,
    getRaceTemplates,
    RaceDto,
    RacePropertiesDto,
    RaceRequest,
    updateRace,
} from '../../../api'
import {AutocompleteList, AutocompleteField, BaseEntityDialogProps} from '../../../utils/types.ts'
import EntityDialog from '../../EntityDialog.tsx'
import {
    Autocomplete,
    Box,
    Button,
    Grid2,
    IconButton,
    Stack,
    TextField,
    Tooltip,
    Zoom,
} from '@mui/material'
import {FormInputText} from '../../form/input/FormInputText.tsx'
import {useTranslation} from 'react-i18next'
import {eventIndexRoute} from '../../../routes.tsx'
import {AutocompleteElement, SwitchElement, useFieldArray, useForm} from 'react-hook-form-mui'
import FormInputNumber from '../../form/input/FormInputNumber.tsx'
import {FormInputCurrency} from '../../form/input/FormInputCurrency.tsx'
import {useFeedback, useFetch} from '../../../utils/hooks.ts'
import DeleteIcon from '@mui/icons-material/Delete'
import {useEffect, useState} from 'react'
import {takeIfNotEmpty} from '../../../utils/ApiUtils.ts'

type RaceForm = {
    identifier: string
    name: string
    shortName: string
    description: string
    countMales: string
    countFemales: string
    countNonBinary: string
    countMixed: string
    participationFee: string
    rentalFee: string
    raceCategory: AutocompleteField
    namedParticipants: {
        namedParticipant: AutocompleteField
        required: boolean
        countMales: string
        countFemales: string
        countNonBinary: string
        countMixed: string
    }[]
}

const RaceDialog = (props: BaseEntityDialogProps<RaceDto>) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventIndexRoute.useParams()

    const addAction = (formData: RaceForm) => {
        return addRace({
            path: {eventId: eventId},
            body: mapFormToRequest(formData, template?.id),
        })
    }

    const editAction = (formData: RaceForm, entity: RaceDto) => {
        return updateRace({
            path: {eventId: entity.event, raceId: entity.id},
            body: mapFormToRequest(formData, template?.id),
        })
    }

    const defaultValues: RaceForm = {
        identifier: '',
        name: '',
        shortName: '',
        description: '',
        countMales: '0',
        countFemales: '0',
        countNonBinary: '0',
        countMixed: '0',
        participationFee: '',
        rentalFee: '',
        raceCategory: {id: '', label: ''},
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
                } else if (result.data) {
                    const t = result.data.data.find(dto => dto.id === props.entity?.template)
                    setTemplate(t ? {id: t.id, label: t.properties.name} : null)
                }
            },
        },
        [], // todo: consider if the templates, raceCategories and namedParticipants are stale data and should be reloaded
    )
    const templates: AutocompleteList =
        templatesData?.data.map(dto => ({
            id: dto.id,
            label: dto.properties.name,
        })) ?? []

    useEffect(() => {
        const t = templates.find(t => t.id === props.entity?.template)
        setTemplate(t ?? null)
    }, [props.dialogIsOpen, templatesData])

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
        [],
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
        [],
    )
    const categories: AutocompleteList =
        categoriesData?.map(dto => ({
            id: dto.id,
            label: dto.name,
        })) ?? []

    const values = props.entity
        ? mapDtoToForm(props.entity.properties, t('decimal.point'))
        : undefined

    const formContext = useForm<RaceForm>({
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

    const entityNameKey = {entity: t('event.race.race')}

    const [template, setTemplate] = useState<AutocompleteField | null>(null)

    useEffect(() => {
        let templateLoaded = false
        const subscription = formContext.watch((_, foo) => {
            // Process to have the template removed when a field in the formContext is changed
            // Needs to be this way because Lists (namedParticipants) trigger the watch an additional time
            if (foo.name === undefined) {
                // This is triggered when the form opens. This is the general watch triggered by the form
                templateLoaded = true
            } else if (templateLoaded) {
                // This is triggered when the form opens. This is the watch triggered by the list (namedParticipants)
                templateLoaded = false
            } else {
                // This will now only be triggered after the first two watch-triggers are done. This triggers when a value in the form is changed
                setTemplate(null)
            }
        })
        return () => subscription.unsubscribe()
    }, [formContext.watch])

    function fillFormWithTemplate(templateId: string) {
        console.log('fill form function')
        const template = templatesData?.data.find(dto => dto?.id === templateId)
        if (template) {
            formContext.reset(mapDtoToForm(template.properties, t('decimal.point')))
        }
    }

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            title={action =>
                action === 'add'
                    ? t('entity.add.action', entityNameKey)
                    : t('entity.edit.action', entityNameKey)
            } // could be shortened but then the translation key can not be found by intellij-search
            addAction={addAction}
            editAction={editAction}
            onSuccess={() => {}}
            closeAction={() => {
                setTemplate(null)
            }}
            defaultValues={defaultValues}>
            <Stack spacing={2}>
                <Box sx={{pb: 4}}>
                    <Autocomplete
                        options={templates}
                        renderInput={params => (
                            <TextField {...params} label={t('event.race.template.template')} />
                        )}
                        value={template}
                        onChange={(_e, newValue: AutocompleteField | null) => {
                            setTemplate(newValue)
                            if (newValue) {
                                fillFormWithTemplate(newValue.id)
                            }
                        }}
                        loading={templatesPending}
                    />
                </Box>
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
                                namedParticipant: {id: '', label: ''},
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

function mapFormToRequest(formData: RaceForm, templateId: string | undefined): RaceRequest {
    return {
        properties: {
            identifier: formData.identifier!!, // !! is safe because of FormValidation
            name: formData.name!!,
            shortName: takeIfNotEmpty(formData.shortName),
            description: takeIfNotEmpty(formData.description),
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
        template: templateId,
    }
}

function mapDtoToForm(dto: RacePropertiesDto, decimalPoint: string): RaceForm {
    return {
        identifier: dto.identifier,
        name: dto.name,
        shortName: dto.shortName ?? '',
        description: dto.description ?? '',
        countMales: dto.countMales.toString(),
        countFemales: dto.countFemales.toString(),
        countNonBinary: dto.countNonBinary.toString(),
        countMixed: dto.countMixed.toString(),
        participationFee: dto.participationFee.replace('.', decimalPoint),
        rentalFee: dto.rentalFee.replace('.', decimalPoint),
        raceCategory: dto.raceCategory
            ? {
                  id: dto.raceCategory?.id,
                  label: dto.raceCategory.name,
              }
            : {id: '', label: ''},
        namedParticipants: dto.namedParticipants.map(value => ({
            namedParticipant: {id: value.id, label: value.name},
            required: value.required,
            countMales: value.countMales.toString(),
            countFemales: value.countFemales.toString(),
            countNonBinary: value.countNonBinary.toString(),
            countMixed: value.countMixed.toString(),
        })),
    }
}

export default RaceDialog
