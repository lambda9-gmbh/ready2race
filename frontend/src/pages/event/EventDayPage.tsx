import {Box, Divider, Grid2, Stack} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {eventDayRoute, eventRoute} from '@routes'
import Throbber from '@components/Throbber.tsx'
import CompetitionAndDayAssignment from '@components/event/competitionAndDayAssignment/CompetitionAndDayAssignment.tsx'
import {AutocompleteOption} from '@utils/types.ts'
import {competitionLabelName} from '@components/event/competition/common.ts'
import {useState} from 'react'
import EntityDetailsEntry from '@components/EntityDetailsEntry.tsx'
import {getEventDay, getCompetitions} from '@api/sdk.gen.ts'

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
        signal => getCompetitions({signal, path: {eventId: eventId}, query: {eventDayId: eventDayId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(t('common.load.error.multiple.short', {entity: t('event.competition.competitions')}))
                }
            },
            deps: [eventId, eventDayId, reloadData],
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
                    feedback.error(t('common.load.error.multiple.short', {entity: t('event.competition.competitions')}))
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

    return (
        <Grid2 container justifyContent="space-between" direction="row" spacing={2}>
            <Box sx={{flex: 1, maxWidth: 600}}>
                {(eventDayData && (
                    <Stack spacing={2}>
                        <EntityDetailsEntry
                            content={
                                eventDayData.date +
                                (eventDayData.name ? ' | ' + eventDayData.name : '')
                            }
                            variant="h1"
                        />
                        <EntityDetailsEntry content={eventDayData.description} />
                    </Stack>
                )) ||
                    (eventDayPending && <Throbber />)}
            </Box>
            <Divider orientation="vertical" />
            <Box sx={{flex: 1, maxWidth: 400}}>
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
            </Box>
        </Grid2>
    )
}

export default EventDayPage
