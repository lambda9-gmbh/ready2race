import {useEffect, useMemo, useState} from 'react'
import {useForm} from 'react-hook-form-mui'
import {EventRegistrationUpsertDto, getClubParticipants, getEventRegistrationTemplate} from '../../api'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useTranslation} from 'react-i18next'
import {eventRegisterRoute} from '@routes'
import {Result} from '@components/Result.tsx'
import EventRegistrationForm from '../../components/eventRegistration/EventRegistrationForm.tsx'
import {useUser} from '@contexts/user/UserContext.ts'

const EventRegistrationCreatePage = () => {
    const {t} = useTranslation()
    const user = useUser()
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

    const {data: clubParticipants} = useFetch(signal => getClubParticipants({
        signal,
        path: {clubId: 'ee4dc6e1-cfa5-4aa7-a1aa-4904e1e3e5be' /*user.clubId*/},
        query: {limit: 1000, sort: JSON.stringify([{field: 'FIRSTNAME', direction: 'ASC'}])},
    }), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(t('common.load.error.single', {entity: t('club.participant.title')}))
                console.log(error)
            }
        },
        deps: [user],
    })

    const onSubmit = () => {
        console.log(formContext.getValues())
        // setRegistrationWasSuccessful(true)
    }

    const registrationTemplate = useMemo(() => data, [data])

    const formContext = useForm<EventRegistrationUpsertDto>({
        defaultValues: {
            participants: [],
            competitionRegistrations: [],
        },
    })

    useEffect(() => {
        if (data) {
            formContext.setValue('competitionRegistrations', data.competitionsTeam.map(c => ({
                competitionId: c.id,
                teams: [],
            })))
        }
    }, [data])

    useEffect(() => {
        if (data) {
            formContext.setValue('participants', clubParticipants?.data?.map(p => {
                return ({...p, isNew: false})
            }) ?? [])
        }
    }, [clubParticipants])

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
