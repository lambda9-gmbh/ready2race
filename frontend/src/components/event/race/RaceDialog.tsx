import {addRace, RaceDto, RaceRequest, updateRace} from '../../../api'
import {
    AutocompleteList,
    AutocompleteListElement,
    BaseEntityDialogProps,
} from '../../../utils/types.ts'
import EntityDialog from '../../EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '../../form/input/FormInputText.tsx'
import FormInputDateTime from '../../form/input/FormInputDateTime.tsx'
import {useTranslation} from 'react-i18next'
import {eventIndexRoute} from '../../../routes.tsx'
import {AutocompleteElement} from 'react-hook-form-mui'
import FormInputNumber from "../../form/input/FormInputNumber.tsx";
import {FormInputCurrency} from "../../form/input/FormInputCurrency.tsx";


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

    const values: RaceForm = props.entity ? mapDtoToForm(props.entity) : defaultValues

    const entityNameKey = {entity: t('event.event')}

    const templatesList: AutocompleteList = [
        {id: 'testId', label: 'Test Template'},
        {id: 'testId2', label: 'Test2 Template'},
    ]

    return (
        <EntityDialog
            {...props}
            values={values}
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
                <FormInputText name='identifier' label={t('event.race.identifier')} required />
                <FormInputText name='name' label={t('event.race.name')} required />
                <FormInputText name='shortName' label={t('event.race.shortName')} />
                <FormInputText name='description' label={t('event.race.description')} />
                <FormInputNumber name='countMales' label={t('event.race.count.males')} min={0} required /> // todo: only ints
                <FormInputNumber name='countFemales' label={t('event.race.count.females')} min={0} required/>
                <FormInputNumber name='countNonBinary' label={t('event.race.count.nonBinary')} min={0} required/>
                <FormInputNumber name='countMixed' label={t('event.race.count.mixed')} min={0} required/>
                <FormInputCurrency name='participationFee' label={t('event.race.participationFee')} required/>
                <FormInputCurrency name='rentalFee' label={t('event.race.rentalFee')} required/>
            </Stack>
        </EntityDialog>
    )
}

function mapFormToRequest(formData: RaceForm): RaceRequest {
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
            participationFee: Number(formData.participationFee!!.replace(',', '.')),
            rentalFee: Number(formData.rentalFee!!.replace(',', '.')),
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

function mapDtoToForm(dto: RaceDto): RaceForm {
    return {
        identifier: dto.properties.identifier,
        name: dto.properties.name,
        shortName: dto.properties.shortName,
        description: dto.properties.description,
        countMales: dto.properties.countMales.toString(),
        countFemales: dto.properties.countFemales.toString(),
        countNonBinary: dto.properties.countNonBinary.toString(),
        countMixed: dto.properties.countMixed.toString(),
        participationFee: dto.properties.participationFee.toString(),
        rentalFee: dto.properties.rentalFee.toString(),
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
        template: dto.template ? {id: dto.template, label: dto.properties.name} : undefined, // todo: this is not so clean - takes the propertiesName because race is identical to template if template is referenced
    }
}

export default RaceDialog