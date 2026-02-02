import {BaseEntityDialogProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityDialog from '@components/EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {useForm} from 'react-hook-form-mui'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {useCallback} from 'react'
import {TimeslotDto, TimeslotRequest} from '@api/types.gen.ts'
import {addTimeslot, updateTimeslot} from '@api/sdk.gen.ts'
import FormInputTime from '@components/form/input/FormInputTime.tsx'
import {eventDayRoute, eventRoute} from '@routes'

type TimeslotForm = {
    name: string
    description: string
    startTime: Date
    endTime: Date
}

const addAction = (formData: TimeslotForm, eventId: string, eventDayId: string) => {
    return addTimeslot({
        path: {eventId: eventId, eventDayId: eventDayId},
        body: mapFormToCreateRequest(formData, eventDayId),
    })
}

const editAction = (
    formData: TimeslotForm,
    entity: TimeslotDto,
    eventId: string,
    eventDayId: string,
) => {
    return updateTimeslot({
        path: {eventId: eventId, eventDayId: eventDayId, timeslotId: entity.id},
        body: mapFormToUpdateRequest(formData, eventDayId),
    })
}

const TimeslotDialog = (props: BaseEntityDialogProps<TimeslotDto>) => {
    const {t} = useTranslation()

    const {eventId} = eventRoute.useParams()
    const {eventDayId} = eventDayRoute.useParams()

    const defaultValues: TimeslotForm = {
        name: '',
        description: '',
        startTime: new Date(),
        endTime: new Date(),
    }

    const formContext = useForm<TimeslotForm>()

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
    }, [props.entity])

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={formdata => addAction(formdata, eventId, eventDayId)}
            editAction={formdata => editAction(formdata, props.entity!, eventId, eventDayId)}>
            <Stack spacing={4}>
                <FormInputText name={'name'} label={t('event.eventDay.name')} required />
                <FormInputText name={'description'} label={t('event.eventDay.description')} />
                <FormInputTime
                    name={'startTime'}
                    label={t('event.eventDay.startTime')}
                    timeSteps={{minutes: 1}}
                    required
                />
                <FormInputTime
                    name={'endTime'}
                    label={t('event.eventDay.endTime')}
                    timeSteps={{minutes: 1}}
                    required
                />
            </Stack>
        </EntityDialog>
    )
}

function mapFormToCreateRequest(formData: TimeslotForm, eventDayId: string): TimeslotRequest {
    return {
        eventDay: eventDayId,
        name: formData.name,
        description: takeIfNotEmpty(formData.description),
        startTime: formData.startTime.toTimeString().slice(0, 8),
        endTime: formData.endTime.toTimeString().slice(0, 8),
    }
}

function mapFormToUpdateRequest(formData: TimeslotForm, eventDayId: string): TimeslotRequest {
    return {
        eventDay: eventDayId,
        name: formData.name,
        description: takeIfNotEmpty(formData.description),
        startTime: formData.startTime.toTimeString().slice(0, 8),
        endTime: formData.endTime.toTimeString().slice(0, 8),
    }
}

function mapDtoToForm(dto: TimeslotDto): TimeslotForm {
    return {
        name: dto.name,
        description: dto.description ?? '',
        startTime: new Date('1970-01-01T' + dto.startTime),
        endTime: new Date('1970-01-01T' + dto.endTime),
    }
}

export default TimeslotDialog
