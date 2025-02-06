import {
    addRace,
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
import {Box, Button, Grid2, Stack} from '@mui/material'
import {FormInputText} from '../../form/input/FormInputText.tsx'
import {useTranslation} from 'react-i18next'
import {eventIndexRoute} from '../../../routes.tsx'
import {AutocompleteElement, SwitchElement, useFieldArray, useForm} from 'react-hook-form-mui'
import FormInputNumber from '../../form/input/FormInputNumber.tsx'
import {FormInputCurrency} from '../../form/input/FormInputCurrency.tsx'

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

    //todo...
    const templatesList: AutocompleteList = [
        {id: 'testId', label: 'Test Template'},
        {id: 'testId2', label: 'Test2 Template'},
    ]

    const namedPsList: AutocompleteList = [
        {id: 'e8413adc-72f9-4a05-a4ba-f12ada4c3639', label: 'Steuermann'},
        {id: '3f15ca3d-641a-4d87-a12d-9f16d27edcb7', label: 'Steuerfrau'},
        {id: '4b3d7747-e0f3-4803-bf18-d73807d48f8e', label: 'Mensch'},
    ]

    const values = props.entity ? mapDtoToForm(props.entity, templatesList) : undefined

    const formContext = useForm<RaceForm>({
        defaultValues: defaultValues,
        values: values,
    })

    const {
        fields: namedParticipantFields,
        append: appendNamedParticipant,
        //remove: removeNamedParticipant
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
            <Stack spacing={2} pt={2}>
                <AutocompleteElement
                    name="template"
                    options={templatesList}
                    label={t('event.race.template')}
                />
                <FormInputText name="identifier" label={t('event.race.identifier')} required />
                <FormInputText name="name" label={t('event.race.name')} required />
                <FormInputText name="shortName" label={t('event.race.shortName')} />
                <FormInputText name="description" label={t('event.race.description')} />
                <FormInputNumber
                    name="countMales"
                    label={t('event.race.count.males')}
                    min={0}
                    required
                />
                {
                    // todo only ints
                }
                <FormInputNumber
                    name="countFemales"
                    label={t('event.race.count.females')}
                    min={0}
                    required
                />
                <FormInputNumber
                    name="countNonBinary"
                    label={t('event.race.count.nonBinary')}
                    min={0}
                    required
                />
                <FormInputNumber
                    name="countMixed"
                    label={t('event.race.count.mixed')}
                    min={0}
                    required
                />
                <FormInputCurrency
                    name="participationFee"
                    label={t('event.race.participationFee')}
                    required
                />
                <FormInputCurrency name="rentalFee" label={t('event.race.rentalFee')} required />
                <Box>
                    {namedParticipantFields.map((field, index) => (
                        <Box key={'remove' + field.fieldId} sx={{maxWidth: 550, m: 'auto'}}>
                            <Grid2 container flexDirection="row" spacing={2} sx={{mb: 4}}>
                                <Grid2 size="grow" sx={{minWidth: 250}}>
                                    <AutocompleteElement
                                        name={'namedParticipants[' + index + '].namedParticipant'}
                                        options={namedPsList}
                                        label={t('event.race.namedParticipant.role')}
                                        autocompleteProps={{
                                            noOptionsText: t('common.form.autocomplete.noOptions'),
                                        }}
                                    />
                                </Grid2>
                                <Box>
                                    <SwitchElement
                                        name={'namedParticipants[' + index + '].required'}
                                        label={t('event.race.namedParticipant.required')}
                                    />
                                </Box>
                            </Grid2>
                            <Grid2 container flexDirection="row" spacing={2} sx={{mb: 4}}>
                                <FormInputNumber
                                    name={'namedParticipants[' + index + '].countMales'}
                                    label={t('event.race.count.males')}
                                    min={0}
                                    max={5}
                                    required
                                />
                                <FormInputNumber
                                    name={'namedParticipants[' + index + '].countFemales'}
                                    label={t('event.race.count.females')}
                                    min={0}
                                    required
                                />
                                <FormInputNumber
                                    name={'namedParticipants[' + index + '].countNonBinary'}
                                    label={t('event.race.count.nonBinary')}
                                    min={0}
                                    required
                                />
                                <FormInputNumber
                                    name={'namedParticipants[' + index + '].countMixed'}
                                    label={t('event.race.count.mixed')}
                                    min={0}
                                    required
                                />
                            </Grid2>
                        </Box>
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
                            {t('common.form.autocomplete.add', {
                                entity: t('event.race.namedParticipant.namedParticipant'),
                            })}
                        </Button>
                    </Box>
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
