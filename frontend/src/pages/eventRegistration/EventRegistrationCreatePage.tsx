import {useEffect, useMemo, useState} from 'react'
import {useForm} from 'react-hook-form-mui'
import {
    addEventRegistration,
    EventRegistrationUpsertDto,
    getEventRegistrationTemplate,
} from '../../api'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useTranslation} from 'react-i18next'
import {eventRegisterRoute} from '@routes'
import {Result} from '@components/Result.tsx'
import EventRegistrationForm from '../../components/eventRegistration/EventRegistrationForm.tsx'

const EventRegistrationCreatePage = () => {
    const {t} = useTranslation()
    // const user = useUser() // TODO use later for explicit clubId
    const feedback = useFeedback()
    const [registrationWasSuccessful, setRegistrationWasSuccessful] = useState<boolean>(false)
    const {eventId} = eventRegisterRoute.useParams()

    const {data} = useFetch(
        signal => getEventRegistrationTemplate({signal, path: {eventId: eventId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(t('common.load.error.single', {entity: t('event.event')}))
                }
            },
            deps: [eventId],
        },
    )

    const onSubmit = () => {
        addEventRegistration({
            path: {eventId: eventId},
            body: formContext.getValues(),
        }).then(({error}) => {
            if (error) {
                feedback.error(t('common.error.unexpected'))
            } else {
                setRegistrationWasSuccessful(true)
            }
        })
    }

    const registrationInfo = useMemo(() => data?.info ?? null, [data?.info])

    const formContext = useForm<EventRegistrationUpsertDto>({
        defaultValues: {
            participants: [],
            competitionRegistrations: [],
        },
    })

    useEffect(() => {
        if (data) {
            formContext.reset(data.upsertableRegistration)
        }
    }, [data])

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
            info={registrationInfo}
        />
    )
}

export default EventRegistrationCreatePage
