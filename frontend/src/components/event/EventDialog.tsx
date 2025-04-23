import {BaseEntityDialogProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityDialog from '@components/EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputDateTime from '@components/form/input/FormInputDateTime.tsx'
import {useForm} from 'react-hook-form-mui'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {useCallback} from 'react'
import {EventDto, EventRequest} from '@api/types.gen.ts'
import {addEvent, updateEvent} from '@api/sdk.gen.ts'
import {FormInputCheckbox} from '@components/form/input/FormInputCheckbox.tsx'

type EventForm = {
    name: string
    description: string
    location: string
    registrationAvailableFrom: string
    registrationAvailableTo: string
    invoicePrefix: string
    published: boolean
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
        published: false,
    }

    const formContext = useForm<EventForm>()

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
    }, [props.entity])

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            editAction={editAction}>
            <Stack spacing={4}>
                <FormInputText name={'name'} label={t('event.name')} required />
                <FormInputText name={'description'} label={t('event.description')} />
                <FormInputText name={'location'} label={t('event.location')} />
                <FormInputCheckbox name={'published'} label={t('event.published')} />
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
        invoicePrefix: takeIfNotEmpty(formData.invoicePrefix),
        published: formData.published,
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
        published: dto.published ?? false,
    }
}

export default EventDialog
