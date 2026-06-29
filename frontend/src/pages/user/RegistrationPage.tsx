import {Box, Button, Divider, Stack, Step, StepLabel, Stepper, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {FormContainer, useForm} from 'react-hook-form-mui'
import {
    getClubs,
    getCompetitionsForRegistration,
    getCreateClubOnRegistrationAllowed,
    getPublicEvents,
    getRatingCategoriesForEvent,
    getRegistrationDocuments,
    getSingleCompetitionSelfRegistrationsAvailable,
    participantSelfRegister,
    registerUser,
} from 'api/sdk.gen.ts'
import {useEffect, useState} from 'react'
import {useCaptcha, useFeedback, useFetch} from '@utils/hooks.ts'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import SimpleFormLayout from '@components/SimpleFormLayout.tsx'
import ConfirmationMailSent from '@components/user/ConfirmationMailSent.tsx'
import {
    CaptchaDto,
    EventPublicDto,
    GetCompetitionsForRegistrationResponse,
    type ParticipantSelfRegisterError,
    RegisterUserError,
} from '@api/types.gen.ts'
import {Step1RegistrationType} from '@components/user/registration/steps/Step1RegistrationType.tsx'
import {Step2BasicInformation} from '@components/user/registration/steps/Step2BasicInformation.tsx'
import {Step3EventCompetitions} from '@components/user/registration/steps/Step3EventCompetitions.tsx'
import {Step4Confirmation} from '@components/user/registration/steps/Step4Confirmation.tsx'
import {
    CompetitionRegistration,
    mapFormToAppUserRegisterRequest,
    mapFormToParticipantRegisterRequest,
    RegistrationForm,
    RegistrationStep,
} from '@components/user/registration/common.ts'
import HowToRegIcon from '@mui/icons-material/HowToReg'
import InfoIcon from '@mui/icons-material/Info'
import EmojiEventsIcon from '@mui/icons-material/EmojiEvents'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import {StepIconProps} from '@mui/material/StepIcon'
import {CheckCircleOutline} from '@mui/icons-material'
import {getRegistrationState} from '@utils/helpers.ts'

const stepIcons: Record<RegistrationStep, JSX.Element> = {
    [RegistrationStep.REGISTRATION_TYPE]: <HowToRegIcon />,
    [RegistrationStep.BASIC_INFORMATION]: <InfoIcon />,
    [RegistrationStep.COMPETITIONS]: <EmojiEventsIcon />,
    [RegistrationStep.CONFIRMATION]: <CheckCircleIcon />,
}

function CustomStepIcon(props: StepIconProps & {stepType: RegistrationStep}) {
    const {active, completed, stepType} = props
    return (
        <Box
            sx={{
                color: completed || active ? 'primary.main' : 'text.disabled',
                display: 'flex',
                alignItems: 'center',
            }}>
            {stepIcons[stepType]}
        </Box>
    )
}

const RegistrationPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)
    const [requested, setRequested] = useState<false | 'CONFIRMATION_MAIL' | 'PARTICIPATING'>(false)
    const [activeStep, setActiveStep] = useState(0)
    const [competitionsData, setCompetitionsData] =
        useState<GetCompetitionsForRegistrationResponse>()
    const [competitionsLoading, setCompetitionsLoading] = useState(false)
    const [registeredForEvent, setRegisteredForEvent] = useState<EventPublicDto | null>(null)

    const defaultValues: RegistrationForm = {
        clubname: '',
        clubId: undefined,
        firstname: '',
        lastname: '',
        registrationType: undefined,
        event: null,
        competitions: [],
        participateSelf: false,
        birthYear: '',
        gender: undefined,
        emailRequired: '',
        emailOptional: '',
        password: '',
        confirmPassword: '',
        captcha: 0,
    }

    const formContext = useForm<RegistrationForm>({values: defaultValues})

    const watchRegistrationType = formContext.watch('registrationType')
    const watchEvent = formContext.watch('event')
    const watchClubname = formContext.watch('clubname')
    const watchBirthYear = formContext.watch('birthYear')
    const watchGender = formContext.watch('gender')
    const watchParticipateSelf = formContext.watch('participateSelf')

    const isClub = watchRegistrationType === 'CLUB'
    const isParticipantOnly = watchRegistrationType === 'PARTICIPANT'

    // Whether the registrant ends up being created as a participant: always for the participant
    // flow, and for the club flow only when they opt in via the "participate self" checkbox.
    // This gates the birthYear/gender fields and the event/competition step.
    const participatesAsParticipant = isParticipantOnly || (isClub && watchParticipateSelf)

    // If there are ANY single competitions for which self registration is enabled.
    // If not, registering as a participant only is not possible and only the club flow remains.
    const {data: anySingleCompetitionsAvailable} = useFetch(
        signal => getSingleCompetitionSelfRegistrationsAvailable({signal}),
        {
            onResponse: ({error, data}) => {
                if (error) {
                    feedback.error(t('common.error.unexpected'))
                } else if (data === false) {
                    // No participant-only option exists - preselect the club flow.
                    formContext.setValue('registrationType', 'CLUB')
                }
            },
            deps: [],
        },
    )

    // The event/competition step is only shown when the registrant participates and single
    // competitions are available. For the participant flow it is mandatory; for the club flow
    // it only appears once they opt in to participate themselves.
    const showCompetitionStep =
        participatesAsParticipant && anySingleCompetitionsAvailable === true

    const steps: RegistrationStep[] = [
        RegistrationStep.REGISTRATION_TYPE,
        RegistrationStep.BASIC_INFORMATION,
        ...(showCompetitionStep ? [RegistrationStep.COMPETITIONS] : []),
        RegistrationStep.CONFIRMATION,
    ]
    const currentStep = steps[activeStep] ?? RegistrationStep.CONFIRMATION
    const isLastStep = activeStep >= steps.length - 1

    // Clear flow-specific fields when switching the registration type
    useEffect(() => {
        if (isParticipantOnly) {
            formContext.setValue('emailRequired', '')
            formContext.setValue('password', '')
            formContext.setValue('confirmPassword', '')
        } else if (isClub) {
            formContext.setValue('emailOptional', '')
        }
    }, [watchRegistrationType, isClub, isParticipantOnly, formContext])

    const setCaptchaStart = ({start}: CaptchaDto) => {
        formContext.setValue('captcha', start)
    }

    const {captcha, onReloadCaptcha} = useCaptcha(setCaptchaStart, {
        preCondition: () => currentStep === RegistrationStep.CONFIRMATION,
    })

    // The captcha is only fetched when its precondition (being on the confirmation step) holds.
    // useFetch re-evaluates that precondition only when its deps change, so trigger a (re)load
    // whenever the confirmation step is entered.
    useEffect(() => {
        if (currentStep === RegistrationStep.CONFIRMATION) {
            onReloadCaptcha()
        }
    }, [currentStep, onReloadCaptcha])

    const {data: createClubOnRegistrationAllowed} = useFetch(
        signal => getCreateClubOnRegistrationAllowed({signal}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(t('common.error.unexpected'))
                }
            },
            deps: [],
        },
    )

    useFetch(
        signal =>
            getClubs({
                signal,
                query: {
                    search: watchClubname,
                },
            }),
        {
            onResponse: ({error, data}) => {
                if (error) {
                    feedback.error(t('common.error.unexpected'))
                } else if (data) {
                    const foundClub = data.data.find(club => club.name === watchClubname)
                    formContext.setValue('clubId', foundClub?.id)
                }
            },
            deps: [watchClubname],
        },
    )

    const {data: eventsData} = useFetch(signal => getPublicEvents({signal}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(t('common.error.unexpected'))
            }
        },
        deps: [],
    })

    // Load the available competitions for the currently selected event/birthYear/gender and
    // initialize the competition form entries. Runs whenever any of those inputs change.
    useEffect(() => {
        let cancelled = false

        const loadCompetitions = async () => {
            if (!watchEvent || watchBirthYear === '' || watchGender === undefined) {
                setCompetitionsData(undefined)
                setCompetitionsLoading(false)
                return
            }

            setCompetitionsLoading(true)

            const {data, error} = await getCompetitionsForRegistration({
                path: {eventId: watchEvent.id},
                query: {
                    birthYear: Number(watchBirthYear),
                    gender: watchGender,
                },
            })

            if (cancelled) return

            setCompetitionsLoading(false)

            if (error || !data) {
                feedback.error(
                    t('common.load.error.multiple.short', {
                        entity: t('event.competition.competitions'),
                    }),
                )
                setCompetitionsData(undefined)
                return
            }

            setCompetitionsData(data)
            const initialCompetitions: CompetitionRegistration[] = data.competitions.map(
                competition => ({
                    checked: false,
                    competitionId: competition.id,
                    optionalFees: [],
                    ratingCategory: competition.properties.ratingCategoryRequired ? '' : 'none',
                }),
            )
            formContext.setValue('competitions', initialCompetitions)
        }

        void loadCompetitions()

        return () => {
            cancelled = true
        }
    }, [watchEvent, watchBirthYear, watchGender])

    const {data: ratingCategories} = useFetch(
        signal =>
            getRatingCategoriesForEvent({
                signal,
                path: {eventId: watchEvent!.id},
            }),
        {
            onResponse: ({error}) =>
                error &&
                feedback.error(
                    t('common.load.error.multiple.short', {
                        entity: t('configuration.ratingCategory.ratingCategories'),
                    }),
                ),
            preCondition: () => watchEvent !== null,
            deps: [watchEvent],
        },
    )

    const {data: registrationDocuments} = useFetch(
        signal =>
            getRegistrationDocuments({
                signal,
                path: {eventId: watchEvent!.id},
            }),
        {
            onResponse: ({error}) =>
                error &&
                feedback.error(
                    t('common.load.error.multiple.short', {
                        entity: t('event.document.documents'),
                    }),
                ),
            preCondition: () => watchEvent !== null,
            deps: [watchEvent],
        },
    )

    // Filter events that allow participant self-registration and are still open
    const availableEvents = eventsData?.data.filter(
        (event: EventPublicDto) =>
            event.allowParticipantSelfRegistration &&
            getRegistrationState({
                registrationAvailableFrom: event.registrationAvailableFrom,
                registrationAvailableTo: event.registrationAvailableTo,
                lateRegistrationAvailableTo: event.lateRegistrationAvailableTo,
            }) !== 'CLOSED',
    )

    // For the participant-only flow an event must be chosen, and in most cases there is only one.
    // Preselect the first available event so the user does not have to pick it manually.
    const firstAvailableEventId = availableEvents?.[0]?.id
    useEffect(() => {
        if (isParticipantOnly && availableEvents?.[0] && !formContext.getValues('event')) {
            const event = availableEvents[0]
            formContext.setValue('event', {id: event.id, label: event.name})
        }
    }, [isParticipantOnly, firstAvailableEventId])

    const handleSubmit = async (formData: RegistrationForm) => {
        if (!formData.registrationType) {
            feedback.error(t('user.registration.error.selectType'))
            return
        }

        // Participant-only registrations must reference an existing club
        if (formData.registrationType === 'PARTICIPANT' && !formData.clubId) {
            formContext.setError('clubname', {
                type: 'validate',
                message: t('club.error.mustSelectExistingClub'),
            })
            return
        }

        setSubmitting(true)

        let error: RegisterUserError | ParticipantSelfRegisterError | undefined
        if (formData.registrationType === 'CLUB') {
            const result = await registerUser({
                query: {
                    challenge: captcha.data!.id,
                    input: formData.captcha,
                },
                body: mapFormToAppUserRegisterRequest(formData),
            })
            error = result.error
        } else {
            if (!formData.event) return

            const result = await participantSelfRegister({
                path: {eventId: formData.event.id},
                query: {
                    challenge: captcha.data!.id,
                    input: formData.captcha,
                },
                body: mapFormToParticipantRegisterRequest(formData),
            })
            error = result.error
        }

        setSubmitting(false)
        onReloadCaptcha()
        formContext.resetField('captcha')

        if (error) {
            if (error.status.value === 404) {
                feedback.error(t('captcha.error.notFound'))
            } else if (error.status.value === 409) {
                if (error.errorCode === 'EMAIL_IN_USE') {
                    formContext.setError(isClub ? 'emailRequired' : 'emailOptional', {
                        type: 'validate',
                        message:
                            t('user.email.inUse.statement') +
                            ' ' +
                            t('user.email.inUse.callToAction.registration'),
                    })
                    setActiveStep(RegistrationStep.BASIC_INFORMATION)
                } else if (error.errorCode === 'CAPTCHA_WRONG') {
                    feedback.error(t('captcha.error.incorrect'))
                } else if (error.errorCode === 'CLUB_NAME_ALREADY_EXISTS') {
                    formContext.setError('clubname', {
                        type: 'validate',
                        message: t('club.error.nameAlreadyExists'),
                    })
                    setActiveStep(RegistrationStep.BASIC_INFORMATION)
                }
            } else {
                feedback.error(t('user.registration.error.generic'))
            }
        } else {
            setRequested(isClub ? 'CONFIRMATION_MAIL' : 'PARTICIPATING')
            setRegisteredForEvent(
                eventsData?.data?.find(val => val.id === formData.event?.id) || null,
            )
        }
    }

    const validateStep = async (step: RegistrationStep): Promise<boolean> => {
        switch (step) {
            case RegistrationStep.REGISTRATION_TYPE: {
                if (!watchRegistrationType) {
                    feedback.error(t('user.registration.error.selectType'))
                    return false
                }
                return true
            }

            case RegistrationStep.BASIC_INFORMATION: {
                const fieldsToValidate: (keyof RegistrationForm)[] = [
                    'clubname',
                    'firstname',
                    'lastname',
                ]

                // birthYear/gender are only required when the registrant actually participates
                if (participatesAsParticipant) {
                    fieldsToValidate.push('gender', 'birthYear')
                }

                if (isClub) {
                    fieldsToValidate.push('emailRequired', 'password', 'confirmPassword')
                } else {
                    fieldsToValidate.push('emailOptional')
                }

                const isValid = await formContext.trigger(fieldsToValidate)

                // Participant-only registrations must reference an existing club
                if (isValid && isParticipantOnly && !formContext.getValues('clubId')) {
                    formContext.setError('clubname', {
                        type: 'validate',
                        message: t('club.error.mustSelectExistingClub'),
                    })
                    return false
                }

                return isValid
            }

            case RegistrationStep.COMPETITIONS: {
                if (isParticipantOnly) {
                    if (!(await formContext.trigger(['event']))) {
                        return false
                    }
                    if (!formContext.getValues('competitions').some(val => val.checked)) {
                        feedback.error(t('user.registration.error.selectAtLeastOneCompetition'))
                        return false
                    }
                }
                return true
            }

            case RegistrationStep.CONFIRMATION:
                return true

            default:
                return true
        }
    }

    const handleNext = async () => {
        const isValid = await validateStep(currentStep)
        if (!isValid) return

        setActiveStep(prev => prev + 1)
    }

    const handleBack = () => {
        setActiveStep(prev => prev - 1)
    }

    const getStepContent = (step: RegistrationStep) => {
        switch (step) {
            case RegistrationStep.REGISTRATION_TYPE:
                return (
                    <Step1RegistrationType
                        anySingleCompetitionsAvailable={anySingleCompetitionsAvailable}
                        onSelect={() => setActiveStep(prev => prev + 1)}
                    />
                )

            case RegistrationStep.BASIC_INFORMATION:
                return (
                    <Step2BasicInformation
                        createClubOnRegistrationAllowed={createClubOnRegistrationAllowed ?? false}
                    />
                )

            case RegistrationStep.COMPETITIONS:
                return (
                    <Step3EventCompetitions
                        availableEvents={availableEvents}
                        competitionsData={competitionsData}
                        competitionsLoading={competitionsLoading}
                        ratingCategories={ratingCategories ?? undefined}
                        watchBirthYear={watchBirthYear}
                        isParticipant={isParticipantOnly}
                    />
                )

            case RegistrationStep.CONFIRMATION:
                return (
                    <Step4Confirmation
                        watchEvent={watchEvent}
                        registrationDocuments={registrationDocuments ?? undefined}
                        captcha={captcha}
                    />
                )

            default:
                return null
        }
    }

    return (
        <SimpleFormLayout maxWidth={600}>
            {!requested ? (
                <>
                    <Box sx={{mb: 4}}>
                        <Typography variant="h1" textAlign="center">
                            {t('user.registration.register')}
                        </Typography>
                    </Box>

                    <Stepper activeStep={activeStep} alternativeLabel sx={{mb: 4}}>
                        {steps.map(step => (
                            <Step key={step}>
                                <StepLabel
                                    slots={{
                                        stepIcon: iconProps => (
                                            <CustomStepIcon {...iconProps} stepType={step} />
                                        ),
                                    }}
                                />
                            </Step>
                        ))}
                    </Stepper>

                    <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                        <Stack spacing={4}>
                            {getStepContent(currentStep)}

                            {currentStep !== RegistrationStep.REGISTRATION_TYPE && (
                                <Box
                                    sx={{
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        mt: 3,
                                    }}>
                                    <Button
                                        onClick={handleBack}
                                        variant="outlined"
                                        sx={{cursor: 'pointer'}}>
                                        {t('common.back')}
                                    </Button>

                                    {!isLastStep ? (
                                        <Button
                                            onClick={handleNext}
                                            variant="contained"
                                            sx={{cursor: 'pointer'}}>
                                            {t('common.next')}
                                        </Button>
                                    ) : (
                                        <SubmitButton submitting={submitting}>
                                            {t('user.registration.register')}
                                        </SubmitButton>
                                    )}
                                </Box>
                            )}
                        </Stack>
                    </FormContainer>
                </>
            ) : requested === 'CONFIRMATION_MAIL' ? (
                <ConfirmationMailSent header={t('user.registration.requested.emailSent.header')}>
                    <Typography textAlign="center">
                        {t('user.registration.requested.emailSent.message.part1')}
                    </Typography>
                    <Typography textAlign="center">
                        {t('user.registration.requested.emailSent.message.part2')}
                    </Typography>
                </ConfirmationMailSent>
            ) : (
                <Stack spacing={2}>
                    <Box sx={{display: 'flex'}}>
                        <CheckCircleOutline sx={{height: 100, width: 100, margin: 'auto'}} />
                    </Box>
                    <Typography variant="h2" textAlign="center">
                        {t('user.registration.requested.participating.header')}
                    </Typography>
                    <Divider />
                    <Typography textAlign="center">
                        {t('user.registration.requested.participating.message.part1', {
                            eventName: registeredForEvent?.name,
                        })}
                    </Typography>
                    {registeredForEvent?.challengeEvent &&
                        registeredForEvent.allowSelfSubmission && (
                            <>
                                <Typography textAlign="center">
                                    {t(
                                        'user.registration.requested.participating.message.part2Email',
                                    )}
                                </Typography>
                                <Typography textAlign="center">
                                    {t(
                                        'user.registration.requested.participating.message.part3ClubRep',
                                    )}
                                </Typography>
                            </>
                        )}
                </Stack>
            )}
        </SimpleFormLayout>
    )
}

export default RegistrationPage
