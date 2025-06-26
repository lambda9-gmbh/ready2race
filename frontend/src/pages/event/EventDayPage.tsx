import {Box, Card, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {eventDayRoute, eventRoute} from '@routes'
import Throbber from '@components/Throbber.tsx'
import CompetitionAndDayAssignment from '@components/event/competitionAndDayAssignment/CompetitionAndDayAssignment.tsx'
import {AutocompleteOption} from '@utils/types.ts'
import {competitionLabelName} from '@components/event/competition/common.ts'
import {useState} from 'react'
import {getEventDay, getCompetitions} from '@api/sdk.gen.ts'

const EventDayPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()
    const {eventDayId} = eventDayRoute.useParams()

    const [reloadDataTrigger, setReloadDataTrigger] = useState(false)

    const {data: eventDayData, pending: eventDayPending} = useFetch(
        signal => getEventDay({signal, path: {eventId: eventId, eventDayId: eventDayId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.single', {entity: t('event.eventDay.eventDay')}),
                    )
                }
            },
            deps: [eventId, eventDayId],
        },
    )

    const {data: assignedCompetitionsData, pending: assignedCompetitionsPending} = useFetch(
        signal =>
            getCompetitions({signal, path: {eventId: eventId}, query: {eventDayId: eventDayId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('event.competition.competitions'),
                        }),
                    )
                }
            },
            deps: [eventId, eventDayId, reloadDataTrigger],
        },
    )
    const assignedCompetitions =
        assignedCompetitionsData?.data.map(value => ({
            id: value.id,
            label: competitionLabelName(value.properties.identifier, value.properties.name),
        })) ?? []

    const {data: competitionsData, pending: competitionsPending} = useFetch(
        signal => getCompetitions({signal, path: {eventId: eventId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('event.competition.competitions'),
                        }),
                    )
                }
            },
            deps: [eventId, reloadDataTrigger],
        },
    )

    const selection: AutocompleteOption[] =
        competitionsData?.data.map(value => ({
            id: value.id,
            label: competitionLabelName(value.properties.identifier, value.properties.name),
        })) ?? []

    return (
        <Box>
            {(eventDayData && (
                <Box sx={{display: 'flex', flexDirection: 'column', gap: 4}}>
                    <Typography variant={'h1'}>
                        {eventDayData.date + (eventDayData.name ? ' | ' + eventDayData.name : '')}
                    </Typography>
                    {eventDayData.description && (
                        <Typography>{eventDayData.description}</Typography>
                    )}
                    <Card sx={{p: 2}}>
                        {(competitionsData && assignedCompetitionsData && (
                            <CompetitionAndDayAssignment
                                entityPathId={eventDayId}
                                options={selection}
                                assignedEntities={assignedCompetitions}
                                assignEntityLabel={t('event.competition.competition')}
                                competitionsToDay={true}
                                onSuccess={() => setReloadDataTrigger(!reloadDataTrigger)}
                            />
                        )) ||
                            ((competitionsPending || assignedCompetitionsPending) && <Throbber />)}
                    </Card>
                </Box>
            )) ||
                (eventDayPending && <Throbber />)}
        </Box>
    )
}

export default EventDayPage
