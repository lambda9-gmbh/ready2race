import {EventDayDto, getEvent, RaceDto} from '../../api'
import {Box, Typography} from '@mui/material'
import {useEntityAdministration, useFeedback, useFetch} from '../../utils/hooks.ts'
import {eventRoute} from '../../routes.tsx'
import {useTranslation} from 'react-i18next'
import RaceTable from '../../components/event/race/RaceTable.tsx'
import RaceDialog from '../../components/event/race/RaceDialog.tsx'
import Throbber from '../../components/Throbber.tsx'
import EventDayDialog from '../../components/event/eventDay/EventDayDialog.tsx'
import EventDayTable from '../../components/event/eventDay/EventDayTable.tsx'

const EventPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()

    const {data} = useFetch(
        signal => getEvent({signal, path: {eventId: eventId}}),
        {
            onResponse: result => {
                if (result.error) {
                    feedback.error(t('common.load.error.single', {entity: t('event.event')}))
                    console.log(result.error)
                }
            },
        },
        [eventId],
    )

    const raceAdministrationProps = useEntityAdministration<RaceDto>()
    const eventDayAdministrationProps = useEntityAdministration<EventDayDto>()

    return (
        <Box sx={{display: 'flex', flexDirection: 'column'}}>
            {(data && (
                <Box>
                    <Typography variant="h4">{data.name}</Typography>
                    <Box>
                        <RaceTable {...raceAdministrationProps} />
                        <RaceDialog {...raceAdministrationProps} />
                    </Box>
                    <Box>
                        <EventDayTable {...eventDayAdministrationProps} />
                        <EventDayDialog {...eventDayAdministrationProps} />
                    </Box>
                </Box>
            )) || <Throbber />}
        </Box>
    )
}

export default EventPage
