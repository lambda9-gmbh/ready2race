import {useTranslation} from 'react-i18next'
import {BaseEntityDialogProps} from '@utils/types.ts'
import {useForm} from 'react-hook-form-mui'
import {Stack} from '@mui/material'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {useCallback} from 'react'
import EntityDialog from '@components/EntityDialog.tsx'
import FormInputDate from '@components/form/input/FormInputDate.tsx'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {eventIndexRoute} from '@routes'
import {addEventDay, updateEventDay} from '@api/sdk.gen.ts'
import {EventDayDto, EventDayRequest} from '@api/types.gen.ts'

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
            path: {eventId: entity.event, eventDayId: entity.id},
            body: mapFormToRequest(formData),
        })
    }

    const defaultValues: EventDayForm = {
        date: '',
        name: '',
        description: '',
    }

    const formContext = useForm<EventDayForm>()

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
                <FormInputDate name="date" label={t('event.eventDay.date')} required />
                <FormInputText name="name" label={t('entity.name')} />
                <FormInputText name="description" label={t('entity.description')} />
            </Stack>
        </EntityDialog>
    )
}

function mapFormToRequest(formData: EventDayForm): EventDayRequest {
    return {
        date: formData.date,
        name: takeIfNotEmpty(formData.name),
        description: takeIfNotEmpty(formData.description),
    }
}

function mapDtoToForm(dto: EventDayDto): EventDayForm {
    return {
        date: dto.date,
        name: dto.name ?? '',
        description: dto.description ?? '',
    }
}

export default EventDayDialog
