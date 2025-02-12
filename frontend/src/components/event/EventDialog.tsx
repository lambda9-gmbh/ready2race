import {addEvent, EventDto, EventRequest, updateEvent} from '../../api'
import {BaseEntityDialogProps} from '../../utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityDialog from '../EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '../form/input/FormInputText.tsx'
import FormInputDateTime from '../form/input/FormInputDateTime.tsx'
import {useForm} from 'react-hook-form-mui'
import {takeIfNotEmpty} from "../../utils/ApiUtils.ts";
import {useCallback} from "react";

type EventForm = {
    name: string
    description: string
    location: string
    registrationAvailableFrom: string
    registrationAvailableTo: string
    invoicePrefix: string
}

const addAction = (formData: EventForm) => {
    return addEvent({
        body: mapFormToRequest(formData),
    })
}

const editAction = (formData: EventForm, entity: EventDto) => {
    return updateEvent({
        path: {eventId: entity.id},
        body: mapFormToRequest(formData),
    })
}

const EventDialog = (props: BaseEntityDialogProps<EventDto>) => {
    const {t} = useTranslation()

    const defaultValues: EventForm = {
        name: '',
        description: '',
        location: '',
        registrationAvailableFrom: '',
        registrationAvailableTo: '',
        invoicePrefix: '',
    }

    const formContext = useForm<EventForm>()

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
    }, [props.entity])


    const entityNameKey = {entity: t('event.event')}

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            title={action =>
                action === 'add'
                    ? t('entity.add.action', entityNameKey)
                    : t('entity.edit.action', entityNameKey)
            } // could be shortened but then the translation key can not be found by search
            addAction={addAction}
            editAction={editAction}
            onSuccess={() => {}}>
            <Stack spacing={2}>
                <FormInputText name={'name'} label={t('entity.name')} required />
                <FormInputText name={'description'} label={t('entity.description')} />
                <FormInputText name={'location'} label={t('event.location')} />
                <FormInputDateTime
                    name={'registrationAvailableFrom'}
                    label={t('event.registrationAvailable.from')}
                />
                <FormInputDateTime
                    name={'registrationAvailableTo'}
                    label={t('event.registrationAvailable.to')}
                />
                <FormInputText name={'invoicePrefix'} label={t('event.invoice.prefix')} />
            </Stack>
        </EntityDialog>
    )
}

function mapFormToRequest(formData: EventForm): EventRequest {
    return {
        name: formData.name,
        description: takeIfNotEmpty(formData.description),
        location: takeIfNotEmpty(formData.location),
        registrationAvailableFrom: takeIfNotEmpty(formData.registrationAvailableFrom),
        registrationAvailableTo: takeIfNotEmpty(formData.registrationAvailableTo),
        invoicePrefix: takeIfNotEmpty(formData.invoicePrefix)
    }
}

function mapDtoToForm(dto: EventDto): EventForm {
    return {
        name: dto.name,
        description: dto.description ?? '',
        location: dto.location ?? '',
        registrationAvailableFrom: dto.registrationAvailableFrom ?? '',
        registrationAvailableTo: dto.registrationAvailableTo ?? '',
        invoicePrefix: dto.invoicePrefix ?? '',
    }
}

export default EventDialog
