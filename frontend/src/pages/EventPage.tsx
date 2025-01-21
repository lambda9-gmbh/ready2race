import {addEvent, deleteEvent, getEvent, getEvents, updateEvent} from '../api'
import {Box} from '@mui/material'
import {useFetch} from '../utils/hooks.ts'

const EventPage = () => {
    async function onAddEvent() {
        const {data, error} = await addEvent({
            body: {
                properties: {
                    name: 'ABCD TestEvent',
                    description: 'This is a test event :)',
                    location: 'Flensburg',
                    registrationAvailableFrom: new Date('2025-01-16T12:00:00').toISOString(),
                    registrationAvailableTo: new Date('2025-01-31T23:59:59').toISOString(),
                    paymentDueDate: undefined,
                    invoicePrefix: 'TEv',
                },
            },
        })

        if (error) {
            console.error(error)
            return
        }
        if (data === undefined) return

        console.log(`Event created (${data})`)
    }

    const initialSort = '[{"field": "NAME", "direction": "ASC"}]'
    useFetch(signal => getEvents({signal, query: {limit: 20, offset: 0, sort: initialSort, search: 'ABCD ZZZ'}}), {
        onResponse: ({data, response}) => {
            if (response.status === 200 && data !== undefined) {
                console.log('Events retrieved: ')
                console.log(data)
            }
        },
    })

    useFetch(
        signal => getEvent({signal, path: {eventId: '979ee6ed-281f-411c-b3b9-883822f7ca64'}}),
        {
            onResponse: ({data, response}) => {
                if (response.status === 200 && data !== undefined) {
                    console.log('Event retrieved: ')
                    console.log(data)
                }
            },
        },
    )

    async function onUpdateEvent() {
        const {data, error} = await updateEvent({
            path: {eventId: '1040c7a6-059b-49f0-9b31-ae3ad2574266'},
            body: {
                properties: {
                    name: 'TestEvent',
                    description: 'This is the event iteration B',
                    location: 'Flensburg',
                    registrationAvailableFrom: new Date('2025-01-16T12:00:00').toISOString(),
                    registrationAvailableTo: new Date('2025-01-31T23:59:59').toISOString(),
                    paymentDueDate: undefined,
                    invoicePrefix: 'TEv',
                },
            },
        })

        if (error) {
            console.error(error)
            return
        }
        if (data === undefined) return

        console.log(`Event updated (${data})`)
    }

    async function onDeleteEvent() {
        const {error} = await deleteEvent({
            path: {eventId: '979ee6ed-281f-411c-b3b9-883822f7ca64'},
        })

        if (error) {
            console.error(error)
            return
        }

        console.log(`Event deleted`)
    }

    return (
        <Box>
            <button onClick={onAddEvent}>Add Event</button>
            <button onClick={onUpdateEvent}>Update Event</button>
            <button onClick={onDeleteEvent}>Delete Event</button>
        </Box>
    )
}

export default EventPage
