import {Box, Card, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useEntityAdministration, useFeedback, useFetch} from '@utils/hooks.ts'
import {eventDayRoute, eventRoute} from '@routes'
import Throbber from '@components/Throbber.tsx'
import CompetitionAndDayAssignment from '@components/event/CompetitionAndDayAssignment.tsx'
import {AutocompleteOption} from '@utils/types.ts'
import {competitionLabelName} from '@components/event/competition/common.ts'
import {useState} from 'react'
import {getEventDay, getCompetitions} from '@api/sdk.gen.ts'
import {TimeslotPage} from '@components/event/eventDay/timeslots/TimeslotPage.tsx'
import TimeslotTable from '@components/event/eventDay/timeslots/TimeslotTable.tsx'
import {EventDto, TimeslotDto} from '@api/types.gen.ts'

const EventDayPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()
    const {eventDayId} = eventDayRoute.useParams()

    const [reloadData, setReloadData] = useState(false)

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
            deps: [eventId, eventDayId, reloadData],
        },
    )
    const assignedCompetitions = assignedCompetitionsData?.data.map(value => value.id) ?? []

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
            deps: [eventId, reloadData],
        },
    )

    const selection: AutocompleteOption[] =
        competitionsData?.data.map(value => ({
            id: value.id,
            label: competitionLabelName(value.properties.identifier, value.properties.name),
        })) ?? []

    const administrationProps = useEntityAdministration<TimeslotDto>(t('event.event'))

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
                                reloadData={() => setReloadData(!reloadData)}
                            />
                        )) ||
                            ((competitionsPending || assignedCompetitionsPending) && <Throbber />)}
                    </Card>
                    <Card sx={{p: 2}}>
                        <Typography variant="h6">Wambo</Typography>
                        <TimeslotPage eventId={eventId} eventDayId={eventDayId} />
                        {/*Durch entityTable ersetzen*/}
                        {/*<TimeslotTable {...administrationProps.table} title={'wambo'} />*/}
                    </Card>
                </Box>
            )) ||
                (eventDayPending && <Throbber />)}
        </Box>
    )
}

export default EventDayPage
