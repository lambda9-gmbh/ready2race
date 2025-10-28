import {useEffect, useMemo, useState} from 'react'
import {useForm} from 'react-hook-form-mui'
import {
    addEventRegistration,
    CompetitionRegistrationSingleUpsertDto,
    CompetitionRegistrationTeamUpsertDto,
    CompetitionRegistrationUpsertDto,
    EventRegistrationParticipantUpsertDto,
    EventRegistrationUpsertDto,
    getEventRegistrationTemplate,
} from '../../api'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useTranslation} from 'react-i18next'
import {eventRegisterRoute} from '@routes'
import {Result} from '@components/Result.tsx'
import EventRegistrationForm from '../../components/eventRegistration/EventRegistrationForm.tsx'
import EventRegistrationProvider from '@contexts/eventRegistration/EventRegistrationProvider.tsx'

export type CompetitionRegistrationSingleFormData = CompetitionRegistrationSingleUpsertDto & {
    locked: boolean
    isLate: boolean
}

export type EventRegistrationParticipantFormData = Omit<
    EventRegistrationParticipantUpsertDto,
    'competitionsSingle'
> & {
    competitionsSingle?: CompetitionRegistrationSingleFormData[]
}

export type CompetitionRegistrationTeamFormData = CompetitionRegistrationTeamUpsertDto & {
    locked: boolean
    isLate: boolean
}

export type CompetitionRegistrationFormData = Omit<CompetitionRegistrationUpsertDto, 'teams'> & {
    teams: CompetitionRegistrationTeamFormData[]
}

export type EventRegistrationFormData = {
    participants: EventRegistrationParticipantFormData[]
    competitionRegistrations: CompetitionRegistrationFormData[]
    message?: string
}

const formDataToRequest = (formData: EventRegistrationFormData): EventRegistrationUpsertDto => ({
    participants: formData.participants.map(p => ({
        ...p,
        competitionsSingle: p.competitionsSingle
            ?.filter(s => !s.locked)
            .map(s => {
                const single: CompetitionRegistrationSingleUpsertDto = {
                    competitionId: s.competitionId,
                    optionalFees: s.optionalFees,
                    ratingCategory: s.ratingCategory !== 'none' ? s.ratingCategory : undefined,
                }
                return single
            }),
    })),
    competitionRegistrations: formData.competitionRegistrations.map(r => ({
        ...r,
        teams: r.teams
            .filter(t => !t.locked)
            .map(t => {
                const team: CompetitionRegistrationTeamUpsertDto = {
                    id: t.id,
                    clubId: t.clubId,
                    optionalFees: t.optionalFees,
                    namedParticipants: t.namedParticipants,
                    ratingCategory: t.ratingCategory !== 'none' ? t.ratingCategory : undefined,
                }
                return team
            }),
    })),
    message: formData.message,
})

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
            body: formDataToRequest(formContext.getValues()),
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
                participants: data.upsertableRegistration.participants.map(p => ({
                    ...p,
                    competitionsSingle: [
                        ...(p.competitionsSingle?.map(s => ({
                            ...s,
                            locked: false,
                            isLate: registrationInfo?.state === 'LATE',
                            ratingCategory:
                                s.ratingCategory ??
                                (registrationInfo?.competitionsSingle.find(
                                    foo => foo.id === s.competitionId,
                                )?.ratingCategoryRequired
                                    ? ''
                                    : 'none'),
                        })) ?? []),
                        ...(data.lockedRegistration.participants
                            .find(lp => lp.id === p.id)
                            ?.competitionsSingle.map(s => ({
                                ...s,
                                locked: true,
                                ratingCategory:
                                    s.ratingCategory ??
                                    (registrationInfo?.competitionsSingle.find(
                                        foo => foo.id === s.competitionId,
                                    )?.ratingCategoryRequired
                                        ? ''
                                        : 'none'),
                            })) ?? []),
                    ],
                })),
                competitionRegistrations: data.upsertableRegistration.competitionRegistrations.map(
                    r => ({
                        ...r,
                        teams: [
                            ...(r.teams?.map(t => ({
                                ...t,
                                locked: false,
                                isLate: registrationInfo?.state === 'LATE',
                                ratingCategory:
                                    t.ratingCategory ??
                                    (registrationInfo?.competitionsSingle.find(
                                        foo => foo.id === r.competitionId,
                                    )?.ratingCategoryRequired
                                        ? ''
                                        : 'none'),
                            })) ?? []),
                            ...(data.lockedRegistration.competitionRegistrations
                                .find(cr => cr.competitionId === r.competitionId)
                                ?.teams.map(t => ({
                                    ...t,
                                    locked: true,
                                    ratingCategory:
                                        t.ratingCategory ??
                                        (registrationInfo?.competitionsSingle.find(
                                            foo => foo.id === r.competitionId,
                                        )?.ratingCategoryRequired
                                            ? ''
                                            : 'none'),
                                })) ?? []),
                        ],
                    }),
                ),
                message: data.upsertableRegistration.message,
            }
            formContext.reset(formData)
        }
    }, [data])

    return (
        <EventRegistrationProvider info={registrationInfo}>
            {registrationWasSuccessful ? (
                <Result
                    status={'SUCCESS'}
                    title={t('event.registration.success.title')}
                    subtitle={t('event.registration.success.subtitle')}
                />
            ) : (
                <EventRegistrationForm onSubmit={onSubmit} formContext={formContext} />
            )}
        </EventRegistrationProvider>
    )
}

export default EventRegistrationCreatePage
