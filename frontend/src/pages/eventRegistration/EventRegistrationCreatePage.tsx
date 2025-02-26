import {useMemo, useState} from 'react'
import {useForm} from 'react-hook-form-mui'
import {EventRegistrationUpsertDto, getEventRegistrationTemplate} from '../../api'
import {useFeedback, useFetch} from '../../utils/hooks.ts'
import {useTranslation} from 'react-i18next'
import {eventRegisterRoute} from '../../routes.tsx'
import {Result} from '../../components/Result.tsx'
import EventRegistrationForm from '../../components/eventRegistration/EventRegistrationForm.tsx'

const EventRegistrationCreatePage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const [registrationWasSuccessful, setRegistrationWasSuccessful] = useState<boolean>(false)
    const {eventId} = eventRegisterRoute.useParams()

    const {data} = useFetch(signal => getEventRegistrationTemplate({signal, path: {eventId: eventId}}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(t('common.load.error.single', {entity: t('event.event')}))
                console.log(error)
            }
        },
        deps: [eventId],
    })

    const onSubmit = () => {
        setRegistrationWasSuccessful(true)
    }

    const registrationTemplate = useMemo(() => data, [data])

    const formContext = useForm<EventRegistrationUpsertDto>({
        defaultValues: {
            participants: [],
            raceRegistrations: [],
        },
    })

    return registrationWasSuccessful ? (
        <Result
            status={'SUCCESS'}
            title={t('event.registration.success.title')}
            subtitle={t('event.registration.success.subtitle')}
        />
    ) : (
        <EventRegistrationForm
            onSubmit={onSubmit}
            formContext={formContext}
            template={registrationTemplate}
        />
    )
}

export default EventRegistrationCreatePage
