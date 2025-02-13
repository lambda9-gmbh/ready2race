import {Box, Divider, Grid2, Stack, Typography} from "@mui/material";
import {useTranslation} from "react-i18next";
import {useFeedback, useFetch} from "../../utils/hooks.ts";
import {eventRoute, raceRoute} from "../../routes.tsx";
import {getEventDays, getRace} from "../../api";
import {eventDayName} from "../../components/event/common.ts";
import {AutocompleteOption} from "../../utils/types.ts";
import Throbber from "../../components/Throbber.tsx";
import RaceAndDayAssignment from "../../components/event/raceAndDayAssignment/RaceAndDayAssignment.tsx";

const RacePage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()
    const {raceId} = raceRoute.useParams()

    const {data: raceData} = useFetch(
        signal => getRace({signal, path: {eventId: eventId, raceId: raceId}}),
        {
            onResponse: result => {
                if (result.error) {
                    feedback.error(
                        t('common.load.error.single', {entity: t('event.race.race')}),
                    )
                    console.log(result.error)
                }
            },
            deps: [eventId, raceId],
        },
    )

    const {data: assignedEventDaysData} = useFetch(
        signal => getEventDays({signal, path: {eventId: eventId}, query: {raceId: raceId}}),
        {
            onResponse: result => {
                if (result.error) {
                    feedback.error(t('common.load.error.multiple', {entity: t('event.eventDay.eventDays')}))
                    console.log(result.error)
                }
            },
            deps: [eventId, raceId],
        },
    )
    const assignedEventDays =
        assignedEventDaysData?.data.map(value => ({
            id: value.id,
            label: eventDayName(value.date, value.name),
        })) ?? []

    const {data: eventDaysData} = useFetch(
        signal => getEventDays({signal, path: {eventId: eventId}}),
        {
            onResponse: result => {
                if (result.error) {
                    feedback.error(t('common.load.error.multiple', {entity: t('event.eventDay.eventDays')}))
                    console.log(result.error)
                }
            },
            deps: [eventId],
        },
    )

    const selection: AutocompleteOption[] =
        eventDaysData?.data.map(value => ({
            id: value.id,
            label: eventDayName(value.date, value.name),
        })) ?? []



    return (
        <Grid2 container justifyContent="space-between" direction="row" spacing={2}>
            <Box sx={{flex: 1, maxWidth: 400}}>
                {(raceData && (
                    <Stack spacing={1}>
                        <Typography variant='h3'>WIP</Typography>
                        <Typography variant="h4">{raceData.properties.name}</Typography>
                        <Divider orientation="horizontal" />
                        <Typography variant="h5">{raceData.properties.identifier}</Typography>
                        <Divider orientation="horizontal" />
                        <Typography variant="body1">{raceData.properties.description}</Typography>
                    </Stack>
                )) || <Throbber />}
            </Box>
            <Divider orientation="vertical" />
            <Box sx={{flex: 1, maxWidth: 400}}>
                {(eventDaysData && assignedEventDaysData && (
                    <RaceAndDayAssignment
                        entityPathId={raceId}
                        options={selection}
                        assignedEntities={assignedEventDays}
                        assignEntityLabel={t('event.eventDay.eventDay')}
                        racesToDay={false}
                    />
                )) || <Throbber />}
            </Box>
        </Grid2>
    )
}

export default RacePage