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
    const testEventDayId = "2ceb8a2c-368b-4876-b060-bee69e34b0a2"
    const testRaceId = "125c3be2-a140-42b1-ad2a-7ba8b0506109"

    async function onAddEvent() {
        const {data, error} = await addEvent({
            body: {
                properties: {
                    name: 'ABCD TestEvent',
                    description: 'This is a test event :)',
                    location: 'Flensburg',
                    registrationAvailableFrom: new Date('2025-01-16T12:00:00').toISOString(),
                    registrationAvailableTo: new Date('2025-01-31T23:59:59').toISOString(),
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

    const initialEventSort = '[{"field": "NAME", "direction": "ASC"}]'
    useFetch(
        signal =>
            getEvents({
                signal,
                query: {limit: 20, offset: 0, sort: initialEventSort},
            }),
        {
            onResponse: ({data, response}) => {
                if (response.status === 200 && data !== undefined) {
                    console.log('Event page retrieved: ')
                    console.log(data)
                }
            },
        },
    )

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

    async function onUpdateEvent() {
        const {data, error} = await updateEvent({
            path: {eventId: testEventId},
            body: {
                properties: {
                    name: 'TestEvent',
                    description: 'This is the event iteration B',
                    location: 'Flensburg',
                    registrationAvailableFrom: new Date('2025-01-16T12:00:00').toISOString(),
                    registrationAvailableTo: new Date('2025-01-31T23:59:59').toISOString(),
                    invoicePrefix: 'TEv',
                },
            },
        })

        if (error) {
            console.error(error)
            return
        }
        if (data === undefined) return

        console.log(`Event updated`)
    }

    async function onDeleteEvent() {
        const {error} = await deleteEvent({
            path: {eventId: testEventId},
        })

        if (error) {
            console.error(error)
            return
        }

        console.log(`Event deleted`)
    }



    // ------- Event Day --------

    async function onAddEventDay() {
        const {data, error} = await addEventDay({
            path: {eventId: testEventId},
            body: {
                properties: {
                    date: '2025-02-02',
                    name: 'Vorlauftag',
                    description: 'Hier laufen die Qualifikationen'
                },
            },
        })

        if (error) {
            console.error(error)
            return
        }
        if (data === undefined) return

        console.log(`Event Day created (${data})`)
    }

    const initialEventDaySort = '[{"field": "DATE", "direction": "ASC"}]'
    useFetch(
        signal =>
            getEventDays({
                signal,
                query: {limit: 20, offset: 0, sort: initialEventDaySort, raceId: testRaceId},
                path: {eventId: testEventId},
            }),
        {
            onResponse: ({data, response}) => {
                if (response.status === 200 && data !== undefined) {
                    console.log('Event Day page retrieved: ')
                    console.log(data)
                } else{
                    console.error("No Event Days found")
                }
            },
        },
    )

    useFetch(
        signal => getEventDay({signal, path: {eventId: testEventId, eventDayId: testEventDayId}}),
        {
            onResponse: ({data, response}) => {
                if (response.status === 200 && data !== undefined) {
                    console.log('Event Day retrieved: ')
                    console.log(data)
                }
            },
        },
    )

    async function onUpdateEventDay() {
        const {data, error} = await updateEventDay({
            path: {eventId: testEventId, eventDayId: testEventDayId},
            body: {
                properties: {
                    date: '2025-02-03',
                    name: 'Vorlauftag-update',
                },
            },
        })
        if (error) {
            console.error(error)
            return
        }
        if (data === undefined) return
        console.log(`Event Day updated`)
    }

    async function onDeleteEventDay() {
        const {error} = await deleteEventDay({
            path: {eventId: testEventId, eventDayId: testEventDayId},
        })
        if (error) {
            console.error(error)
            return
        }
        console.log(`Event Day deleted`)
    }





    // ------- Race --------

    async function onAddRace() {
        const {data, error} = await addRace({
            path: {eventId: testEventId},
            body: {

            },
        })
        if (error) {
            console.error(error)
            return
        }
        if (data === undefined) return
    }

    const initialRaceSort = '[{"field": "TODO", "direction": "ASC"}]'
    useFetch(
        signal =>
            getEventDays({
                signal,
                query: {limit: 20, offset: 0, sort: initialEventDaySort},
                path: {eventId: testEventId},
            }),
        {
            onResponse: ({data, response}) => {
                if (response.status === 200 && data !== undefined) {
                    console.log('Event Day page retrieved: ')
                    console.log(data)
                } else{
                    console.error("No Event Days found")
                }
            },
        },
    )

    useFetch(
        signal => getEventDay({signal, path: {eventId: testEventId, eventDayId: testEventDayId}}),
        {
            onResponse: ({data, response}) => {
                if (response.status === 200 && data !== undefined) {
                    console.log('Event Day retrieved: ')
                    console.log(data)
                }
            },
        },
    )

    async function onUpdateRace() {
        const {data, error} = await updateRace({
            path: {eventId: testEventId, eventDayId: testEventDayId},
            body: {
                properties: {
                    date: '2025-02-03',
                    name: 'Vorlauftag-update',
                },
            },
        })
        if (error) {
            console.error(error)
            return
        }
        if (data === undefined) return
        console.log(`Event Day updated`)
    }

    async function onDeleteRace() {
        const {error} = await deleteRace({
            path: {eventId: testEventId, eventDayId: testEventDayId},
        })
        if (error) {
            console.error(error)
            return
        }
        console.log(`Event Day deleted`)
    }




    return (
        <Box sx={{display: 'flex', flexDirection: 'column'}}>
            <Box>
                <button onClick={onAddEvent}>Add Event</button>
                <button onClick={onUpdateEvent}>Update Event</button>
                <button onClick={onDeleteEvent}>Delete Event</button>
            </Box>
            <Box>
                <button onClick={onAddEventDay}>Add Event Day</button>
                <button onClick={onUpdateEventDay}>Update Event Day</button>
                <button onClick={onDeleteEventDay}>Delete Event Day</button>
            </Box>
            <Box>
                <button onClick={onAddEvent}>Add Race</button>
                <button onClick={onUpdateEvent}>Update Race</button>
                <button onClick={onDeleteEvent}>Delete Race</button>
            </Box>
            <Box>
                <button onClick={onAddEvent}>Add Named Participant</button>
                <button onClick={onUpdateEvent}>Update Named Participant</button>
                <button onClick={onDeleteEvent}>Delete Named Participant</button>
            </Box>
            <Box>
                <button onClick={onAddEvent}>Add Race Category</button>
                <button onClick={onUpdateEvent}>Update Race Category</button>
                <button onClick={onDeleteEvent}>Delete Race Category</button>
            </Box>
        </Box>
    )
}

export default EventPage
