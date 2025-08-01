import {useEffect, useMemo, useState} from 'react'
import {useForm} from 'react-hook-form-mui'
import {
    addEventRegistration,
    CompetitionRegistrationSingleUpsertDto, CompetitionRegistrationTeamUpsertDto, CompetitionRegistrationUpsertDto,
    EventRegistrationParticipantUpsertDto,
    getEventRegistrationTemplate,
} from '../../api'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useTranslation} from 'react-i18next'
import {eventRegisterRoute} from '@routes'
import {Result} from '@components/Result.tsx'
import EventRegistrationForm from '../../components/eventRegistration/EventRegistrationForm.tsx'
import EventRegistrationProvider from "@contexts/eventRegistration/EventRegistrationProvider.tsx";

export type CompetitionRegistrationSingleFormData = CompetitionRegistrationSingleUpsertDto & {
    locked: boolean
}

export type EventRegistrationParticipantFormData = Omit<EventRegistrationParticipantUpsertDto, 'competitionsSingle'> & {
    competitionsSingle?: CompetitionRegistrationSingleFormData[]
}

export type CompetitionRegistrationTeamFormData = CompetitionRegistrationTeamUpsertDto & {
    locked: boolean
}

export type CompetitionRegistrationFormData = Omit<CompetitionRegistrationUpsertDto, 'teams'> & {
    teams: CompetitionRegistrationTeamFormData[]
}

export type EventRegistrationFormData = {
    participants: EventRegistrationParticipantFormData[]
    competitionRegistrations: CompetitionRegistrationFormData[]
    message?: string
}

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
            body: formContext.getValues(), // [todo] update
        }).then(({error}) => {
            if (error) {
                feedback.error(t('common.error.unexpected'))
            } else {
                setRegistrationWasSuccessful(true)
            }
        })
    }

    const registrationInfo = useMemo(() => data?.info ?? null, [data?.info])

    const formContext = useForm<EventRegistrationFormData>({
        defaultValues: {
            participants: [],
            competitionRegistrations: [],
        },
    })

    useEffect(() => {
        if (data) {
            const formData: EventRegistrationFormData = {
                participants:
                    data.upsertableRegistration.participants.map(
                        p => (
                            {
                                ...p,
                                competitionsSingle: [
                                    ...p.competitionsSingle?.map(
                                        s => ({...s, locked: false})
                                    ) ?? [],
                                    ...data.lockedRegistration.participants.find(lp => lp.id === p.id)
                                        ?.competitionsSingle.map(s => ({...s, locked: true})) ?? []
                                ]
                            }
                        )
                    ),
                competitionRegistrations:
                    data.upsertableRegistration.competitionRegistrations.map(
                        r => (
                            {
                                ...r,
                                teams: [
                                    ...r.teams?.map(
                                        t => ({...t, locked: false})
                                    ) ?? [],
                                    ...data.lockedRegistration.competitionRegistrations.find(cr => cr.competitionId === r.competitionId)
                                        ?.teams.map(t => ({...t, locked: true})) ?? []
                                ]
                            }
                        )
                    ),
                message: data.upsertableRegistration.message
            }
            formContext.reset(formData)
        }
    }, [data])

    return (
        <EventRegistrationProvider info={registrationInfo}>
            {
                registrationWasSuccessful ? (
                    <Result
                        status={'SUCCESS'}
                        title={t('event.registration.success.title')}
                        subtitle={t('event.registration.success.subtitle')}
                    />
                ) : (
                    <EventRegistrationForm
                        onSubmit={onSubmit}
                        formContext={formContext}
                    />
                )
            }
        </EventRegistrationProvider>
    )
}

export default EventRegistrationCreatePage
