import {Box, Divider, Grid2, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {eventRoute, raceRoute} from '@routes'
import {eventDayName} from '@components/event/common.ts'
import {AutocompleteOption} from '@utils/types.ts'
import Throbber from '@components/Throbber.tsx'
import RaceAndDayAssignment from '@components/event/raceAndDayAssignment/RaceAndDayAssignment.tsx'
import {useState} from 'react'
import EntityDetailsEntry from "@components/EntityDetailsEntry.tsx";
import {getEventDays, getRace} from "@api/sdk.gen.ts";

const RacePage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()
    const {raceId} = raceRoute.useParams()

    const [reloadDataTrigger, setReloadDataTrigger] = useState(false)

    const {data: raceData} = useFetch(
        signal => getRace({signal, path: {eventId: eventId, raceId: raceId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(t('common.load.error.single', {entity: t('event.race.race')}))
                    console.log(error)
                }
            },
            deps: [eventId, raceId],
        },
    )

    const {data: assignedEventDaysData} = useFetch(
        signal => getEventDays({signal, path: {eventId: eventId}, query: {raceId: raceId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple', {entity: t('event.eventDay.eventDays')}),
                    )
                    console.log(error)
                }
            },
            deps: [eventId, raceId, reloadDataTrigger],
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
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple', {entity: t('event.eventDay.eventDays')}),
                    )
                    console.log(error)
                }
            },
            deps: [eventId, reloadDataTrigger],
        },
    )

    const selection: AutocompleteOption[] =
        eventDaysData?.data.map(value => ({
            id: value.id,
            label: eventDayName(value.date, value.name),
        })) ?? []

    return (
        <Grid2 container justifyContent="space-between" direction="row" spacing={2}>
            <Box sx={{flex: 1, maxWidth: 600}}>
                {(raceData && (
                    <Stack spacing={2}>
                        <EntityDetailsEntry content={raceData.properties.identifier + " | " + raceData.properties.name} variant="h1"/>
                        <EntityDetailsEntry content={raceData.properties.shortName}/>
                        <EntityDetailsEntry content={raceData.properties.description}/>
                        {raceData.properties.raceCategory && (
                            <Box sx={{py: 2}}>
                                <EntityDetailsEntry content={raceData.properties.raceCategory.name}/>
                                <EntityDetailsEntry content={raceData.properties.raceCategory.description}/>
                            </Box>
                        )}

                        <Divider orientation="horizontal" />
                        <EntityDetailsEntry label={t('event.race.participationFee')} content={raceData.properties.participationFee}/>
                        <EntityDetailsEntry label={t('event.race.rentalFee')} content={raceData.properties.rentalFee}/>

                        <Divider orientation="horizontal" />
                        <EntityDetailsEntry label={t('event.race.count.males')} content={raceData.properties.countMales}/>
                        <EntityDetailsEntry label={t('event.race.count.females')} content={raceData.properties.countFemales}/>
                        <EntityDetailsEntry label={t('event.race.count.nonBinary')} content={raceData.properties.countNonBinary}/>
                        <EntityDetailsEntry label={t('event.race.count.mixed')} content={raceData.properties.countMixed}/>

                        {raceData.properties.namedParticipants.map((np, index) => (
                            <>
                                <Divider orientation="horizontal" key={`divider${index}`}/>
                                <Box key={`box${index}`}>
                                    <Typography variant="subtitle1">{np.name}</Typography>
                                    <Typography>{np.description}</Typography>
                                    <Typography>
                                        {np.required
                                            ? t('event.race.namedParticipant.required.required')
                                            : t('event.race.namedParticipant.required.notRequired')}
                                    </Typography>
                                    <EntityDetailsEntry label={t('event.race.count.males')} content={np.countMales}/>
                                    <EntityDetailsEntry label={t('event.race.count.females')} content={np.countFemales}/>
                                    <EntityDetailsEntry label={t('event.race.count.nonBinary')} content={np.countNonBinary}/>
                                    <EntityDetailsEntry label={t('event.race.count.mixed')} content={np.countMixed}/>
                                </Box>
                            </>
                        ))}
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
                        onSuccess={() => setReloadDataTrigger(!reloadDataTrigger)}
                    />
                )) || <Throbber />}
            </Box>
        </Grid2>
    )
}

export default RacePage
