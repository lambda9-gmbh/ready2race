import {useTranslation} from 'react-i18next'
import {addEvent} from '../api'

const EventPage = () => {
    const {t} = useTranslation()

    async function onAddEvent() {
        const {data, error} = await addEvent({
            body: {
                properties: {
                    name: 'TestEvent',
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
        if (data === undefined) {
            return
        }
        const eventId = data
        console.log(`Event created (${eventId})`)
    }

    return <button onClick={onAddEvent}>Add Event</button>
}

export default EventPage
