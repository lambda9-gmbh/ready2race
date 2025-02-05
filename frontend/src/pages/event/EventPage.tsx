import {
    addEvent,
    addEventDay, addRace,
    deleteEvent, deleteEventDay, deleteRace,
    getEvent,
    getEventDay,
    getEventDays,
    getEvents,
    updateEvent,
    updateEventDay, updateRace
} from '../../api'
import {Box} from '@mui/material'
import {useFetch} from '../../utils/hooks.ts'

const EventPage = () => {

    const testEventId = "684fe2e4-3237-43e3-a2ec-bffd9ae67407"

    useFetch(
        signal => getEvent({signal, path: {eventId: testEventId}}),
        {
            onResponse: ({data, response}) => {
                if (response.status === 200 && data !== undefined) {
                    console.log('Event retrieved: ')
                    console.log(data)
                } else{
                    console.error("Event not found")
                }
            },
        },
    )




    return (
        <Box sx={{display: 'flex', flexDirection: 'column'}}>
        </Box>
    )
}

export default EventPage
