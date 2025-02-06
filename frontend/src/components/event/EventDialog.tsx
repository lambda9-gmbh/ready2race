import {addEvent, EventDto, EventProperties, updateEvent} from '../../api'
import {BaseEntityDialogProps} from '../../utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityDialog from '../EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '../form/input/FormInputText.tsx'
import FormInputDateTime from '../form/input/FormInputDateTime.tsx'
import {useForm} from "react-hook-form-mui";

type EventForm = EventProperties


const addAction = (formData: EventForm) => {
    return addEvent({
        body: {
            properties: formData,
        },
    })
}

const editAction = (formData: EventForm, entity: EventDto) => {
    return updateEvent({
        path: {eventId: entity.id},
        body: {
            properties: formData,
        },
    })
}

const EventDialog = (props: BaseEntityDialogProps<EventDto>) => {
    const {t} = useTranslation()



    const defaultValues: EventForm = {name: ''}

    const values: EventForm | undefined = props.entity ? {...props.entity.properties} : undefined

    const formContext = useForm<EventForm>({
        defaultValues: defaultValues,
        values: values
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
                <FormInputText name={'name'} label={t('event.name')} required />
                <FormInputText name={'description'} label={t('event.description')} />
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

export default EventDialog
