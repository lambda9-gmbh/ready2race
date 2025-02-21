import {Box, Typography} from '@mui/material'
import {useEntityAdministration, useFeedback, useFetch} from '@utils/hooks.ts'
import {eventRoute} from '@routes'
import {useTranslation} from 'react-i18next'
import RaceTable from '@components/event/race/RaceTable.tsx'
import RaceDialog from '@components/event/race/RaceDialog.tsx'
import Throbber from '@components/Throbber.tsx'
import EventDayDialog from '@components/event/eventDay/EventDayDialog.tsx'
import EventDayTable from '@components/event/eventDay/EventDayTable.tsx'
import {getEvent} from '@api/sdk.gen.ts'
import {EventDayDto, RaceDto} from '@api/types.gen.ts'

const EventPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()

    const {data, pending} = useFetch(signal => getEvent({signal, path: {eventId: eventId}}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(t('common.load.error.single', {entity: t('event.event')}))
                console.log(error)
            }
        },
        deps: [eventId],
    })

    const raceAdministrationProps = useEntityAdministration<RaceDto>(t('event.race.race'))
    const eventDayAdministrationProps = useEntityAdministration<EventDayDto>(
        t('event.eventDay.eventDay'),
    )

    return (
        <Box sx={{display: 'flex', flexDirection: 'column'}}>
            {(data && (
                <>
                    <Typography variant="h1">{data.name}</Typography>
                    <Box sx={{mt: 4}}>
                        <RaceTable
                            {...raceAdministrationProps.table}
                            title={t('event.race.races')}
                        />
                        <RaceDialog {...raceAdministrationProps.dialog} />
                    </Box>
                    <Box sx={{mt: 4}}>
                        <EventDayTable
                            {...eventDayAdministrationProps.table}
                            title={t('event.eventDay.eventDays')}
                        />
                        <EventDayDialog {...eventDayAdministrationProps.dialog} />
                    </Box>
                </>
            )) ||
                (pending && <Throbber />)}
        </Box>
    )
}

export default EventPage
