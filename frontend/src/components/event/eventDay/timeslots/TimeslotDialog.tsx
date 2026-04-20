import {BaseEntityDialogProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityDialog from '@components/EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {useForm} from 'react-hook-form-mui'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {useCallback, useEffect, useMemo} from 'react'
import {EventDayScheduleCompetitionDataDto, TimeslotDto, TimeslotRequest} from '@api/types.gen.ts'
import {addTimeslot, getCompetitionMatchData, updateTimeslot} from '@api/sdk.gen.ts'
import FormInputTime from '@components/form/input/FormInputTime.tsx'
import {eventDayRoute, eventRoute} from '@routes'
import {useFetch} from '@utils/hooks.ts'
import {addMinutes} from 'date-fns'
import {
    calculateMatchDurationWithOffsetMinutes,
    calculateTotalDurationMinutes,
} from '@components/event/eventDay/timeslots/timeslotDuration.ts'

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
    endTimeOverride?: Date,
) => {
    return updateTimeslot({
        path: {eventId: eventId, eventDayId: eventDayId, timeslotId: entity.id},
        body: mapFormToUpdateRequest(formData, eventDayId, endTimeOverride),
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
    const watchedStartTime = formContext.watch('startTime')

    const isEditingReferencedTimeslot =
        props.entity !== undefined &&
        Boolean(
            props.entity.competitionReference ||
                props.entity.roundReference ||
                props.entity.matchReference,
        )

    const {data: competitionMatchData} = useFetch(
        signal =>
            getCompetitionMatchData({
                signal,
                path: {eventId, eventDayId},
            }),
        {
            preCondition: () => isEditingReferencedTimeslot && props.dialogIsOpen,
            deps: [eventId, eventDayId, props.dialogIsOpen, props.entity?.id],
        },
    )

    const referencedTimeslotDuration = useMemo(() => {
        if (!isEditingReferencedTimeslot || !props.entity || !competitionMatchData) {
            return null
        }
        return calculateReferencedTimeslotDuration(props.entity, competitionMatchData)
    }, [isEditingReferencedTimeslot, props.entity, competitionMatchData])

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
    }, [props.entity])

    useEffect(() => {
        if (
            !isEditingReferencedTimeslot ||
            referencedTimeslotDuration == null ||
            watchedStartTime == null
        ) {
            return
        }
        const calculatedEndTime = addMinutes(watchedStartTime, referencedTimeslotDuration)
        formContext.setValue('endTime', calculatedEndTime, {shouldDirty: true})
    }, [formContext, isEditingReferencedTimeslot, referencedTimeslotDuration, watchedStartTime])

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={formdata => addAction(formdata, eventId, eventDayId)}
            editAction={formdata => {
                const calculatedEndTime =
                    isEditingReferencedTimeslot && referencedTimeslotDuration != null
                        ? addMinutes(formdata.startTime, referencedTimeslotDuration)
                        : undefined
                const fallbackEndTime =
                    formdata.endTime ?? formContext.getValues('endTime') ?? formdata.startTime
                return editAction(
                    formdata,
                    props.entity!,
                    eventId,
                    eventDayId,
                    calculatedEndTime ?? fallbackEndTime,
                )
            }}>
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
                    disabled={isEditingReferencedTimeslot}
                />
            </Stack>
        </EntityDialog>
    )
}

function calculateReferencedTimeslotDuration(
    entity: TimeslotDto,
    competitionMatchData: EventDayScheduleCompetitionDataDto[],
): number {
    const competition = competitionMatchData.find(
        comp => comp.competitionId === entity.competitionReference,
    )
    if (!competition?.matchDuration || !competition?.matchGapsDuration) {
        return 0
    }

    const matches = entity.matchReference
        ? competition.rounds
              .flatMap(round => round.matches)
              .filter(match => match.matchId === entity.matchReference)
        : entity.roundReference
          ? (competition.rounds.find(round => round.roundId === entity.roundReference)?.matches ?? [])
          : competition.rounds.flatMap(round => round.matches)

    if (entity.matchReference) {
        const selectedMatch = matches[0]
        return selectedMatch
            ? calculateMatchDurationWithOffsetMinutes(selectedMatch, competition.matchDuration)
            : 0
    }

    return calculateTotalDurationMinutes(
        matches,
        competition.matchDuration,
        competition.matchGapsDuration,
    )
}

function mapFormToCreateRequest(formData: TimeslotForm, eventDayId: string): TimeslotRequest {
    const safeEndTime = formData.endTime ?? formData.startTime
    return {
        eventDay: eventDayId,
        name: formData.name,
        description: takeIfNotEmpty(formData.description),
        startTime: formData.startTime.toTimeString().slice(0, 8),
        endTime: safeEndTime.toTimeString().slice(0, 8),
    }
}

function mapFormToUpdateRequest(
    formData: TimeslotForm,
    eventDayId: string,
    endTimeOverride?: Date,
): TimeslotRequest {
    const safeEndTime = endTimeOverride ?? formData.endTime ?? formData.startTime
    return {
        eventDay: eventDayId,
        name: formData.name,
        description: takeIfNotEmpty(formData.description),
        startTime: formData.startTime.toTimeString().slice(0, 8),
        endTime: safeEndTime.toTimeString().slice(0, 8),
    }
}

function mapDtoToForm(dto: TimeslotDto): TimeslotForm {
    return {
        name: dto.name,
        description: dto.descriptionManual ?? '',
        startTime: new Date('1970-01-01T' + dto.startTime),
        endTime: new Date('1970-01-01T' + dto.endTime),
    }
}

export default TimeslotDialog
