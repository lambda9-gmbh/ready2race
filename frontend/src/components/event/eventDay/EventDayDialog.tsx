import {useTranslation} from 'react-i18next'
import {eventIndexRoute} from '../../../routes.tsx'
import {
    addEventDay,
    EventDayDto,
    EventDayProperties,
    EventDayRequest,
    updateEventDay,
} from '../../../api'
import {BaseEntityDialogProps} from '../../../utils/types.ts'
import {useForm} from 'react-hook-form-mui'
import EntityDialog from '../../EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '../../form/input/FormInputText.tsx'
import FormInputDate from '../../form/input/FormInputDate.tsx'
import {takeIfNotEmpty} from '../../../utils/ApiUtils.ts'

type EventDayForm = {
    date: string
    name: string
    description: string
}

const EventDayDialog = (props: BaseEntityDialogProps<EventDayDto>) => {
    const {t} = useTranslation()

    const {eventId} = eventIndexRoute.useParams()

    const addAction = (formData: EventDayForm) => {
        return addEventDay({
            path: {eventId: eventId},
            body: mapFormToRequest(formData),
        })
    }

    const editAction = (formData: EventDayForm, entity: EventDayDto) => {
        return updateEventDay({
            path: {eventId: entity.event, raceId: entity.id},
            body: mapFormToRequest(formData),
        })
    }

    const defaultValues: EventDayForm = {
        date: '',
        name: '',
        description: '',
    }

    const values: EventDayForm | undefined = props.entity
        ? mapDtoToForm(props.entity.properties)
        : undefined

    const formContext = useForm<EventDayForm>({
        values: values,
    })

    const entityKeyName = {entity: t('event.eventDay.eventDay')}

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            title={action =>
                action === 'add' ? t('entity.add.action', entityKeyName) : t('entity.edit.action')
            }
            addAction={addAction}
            editAction={editAction}
            onSuccess={() => {}}
            defaultValues={defaultValues}>
            <Stack spacing={2} pt={2}>
                <FormInputDate name="date" label={t('event.eventDay.date')} required />
                <FormInputText name="name" label={t('entity.name')} />
                <FormInputText name='description' label={t('entity.description')} />
            </Stack>
        </EntityDialog>
    )
}

function mapFormToRequest(formData: EventDayForm): EventDayRequest {
    return {
        properties: {
            date: formData.date,
            name: takeIfNotEmpty(formData.name),
            description: takeIfNotEmpty(formData.description),
        },
    }
}

function mapDtoToForm(dto: EventDayProperties): EventDayForm {
    return {
        date: dto.date,
        name: dto.name ?? '',
        description: dto.description ?? '',
    }
}

export default EventDayDialog
