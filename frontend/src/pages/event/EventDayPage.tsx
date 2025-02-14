import {Box, Divider, Grid2, Stack} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '../../utils/hooks.ts'
import {eventDayRoute, eventRoute} from '../../routes.tsx'
import {getEventDay, getRaces} from '../../api'
import Throbber from '../../components/Throbber.tsx'
import RaceAndDayAssignment from '../../components/event/raceAndDayAssignment/RaceAndDayAssignment.tsx'
import {AutocompleteOption} from '../../utils/types.ts'
import {raceLabelName} from '../../components/event/race/common.ts'
import {useState} from "react";
import EntityDetailsEntry from "../../components/EntityDetailsEntry.tsx";

const EventDayPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()
    const {eventDayId} = eventDayRoute.useParams()

    const [reloadDataTrigger, setReloadDataTrigger] = useState(false)

    const {data: eventDayData} = useFetch(
        signal => getEventDay({signal, path: {eventId: eventId, eventDayId: eventDayId}}),
        {
            onResponse: result => {
                if (result.error) {
                    feedback.error(
                        t('common.load.error.single', {entity: t('event.eventDay.eventDay')}),
                    )
                    console.log(result.error)
                }
            },
            deps: [eventId, eventDayId],
        },
    )

    const {data: assignedRacesData} = useFetch(
        signal => getRaces({signal, path: {eventId: eventId}, query: {eventDayId: eventDayId}}),
        {
            onResponse: result => {
                if (result.error) {
                    feedback.error(t('common.load.error.multiple', {entity: t('event.race.races')}))
                    console.log(result.error)
                }
            },
            deps: [eventId, eventDayId, reloadDataTrigger],
        },
    )
    const assignedRaces =
        assignedRacesData?.data.map(value => ({
            id: value.id,
            label: raceLabelName(value.properties.identifier, value.properties.name),
        })) ?? []

    const {data: racesData} = useFetch(signal => getRaces({signal, path: {eventId: eventId}}), {
        onResponse: result => {
            if (result.error) {
                feedback.error(t('common.load.error.multiple', {entity: t('event.race.races')}))
                console.log(result.error)
            }
        },
        deps: [eventId, reloadDataTrigger],
    })

    const selection: AutocompleteOption[] =
        racesData?.data.map(value => ({
            id: value.id,
            label: raceLabelName(value.properties.identifier, value.properties.name),
        })) ?? []

    return (
        <Grid2 container justifyContent="space-between" direction="row" spacing={2}>
            <Box sx={{flex: 1, maxWidth: 400}}>
                {(eventDayData && (
                    <Stack spacing={2}>
                        <EntityDetailsEntry content={eventDayData.date + ( eventDayData.name ? " | " + eventDayData.name : "")} variant="h4"/>
                        <EntityDetailsEntry content={eventDayData.description}/>
                    </Stack>
                )) || <Throbber />}
            </Box>
            <Divider orientation="vertical" />
            <Box sx={{flex: 1, maxWidth: 400}}>
                {(racesData && assignedRacesData && (
                    <RaceAndDayAssignment
                        entityPathId={eventDayId}
                        options={selection}
                        assignedEntities={assignedRaces}
                        assignEntityLabel={t('event.race.race')}
                        racesToDay={true}
                        onSuccess={() => setReloadDataTrigger(!reloadDataTrigger)}
                    />
                )) || <Throbber />}
            </Box>
        </Grid2>
    )
}

export default EventDayPage
