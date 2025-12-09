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
    type ParticipantSelfRegisterError,
    RegisterUserError,
} from '@api/types.gen.ts'
import {Step1RegistrationType} from '@components/user/registration/steps/Step1RegistrationType.tsx'
import {Step2BasicInformation} from '@components/user/registration/steps/Step2BasicInformation.tsx'
import {Step3Competitions} from '@components/user/registration/steps/Step3Competitions.tsx'
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

const stepIcons: {[index: string]: JSX.Element} = {
    1: <HowToRegIcon />,
    2: <InfoIcon />,
    3: <EmojiEventsIcon />,
    4: <CheckCircleIcon />,
}

function CustomStepIcon(props: StepIconProps) {
    const {active, completed, icon} = props
    return (
        <Box
            sx={{
                color: completed ? 'primary.main' : active ? 'primary.main' : 'text.disabled',
                display: 'flex',
                alignItems: 'center',
            }}>
            {stepIcons[String(icon)]}
        </Box>
    )
}

const RegistrationPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)
    const [requested, setRequested] = useState<false | 'CONFIRMATION_MAIL' | 'PARTICIPATING'>(false)
    const [activeStep, setActiveStep] = useState(0)
    const [reloadCompetitions, setReloadCompetitions] = useState(0)

    const defaultValues: RegistrationForm = {
        clubname: '',
        clubId: undefined,
        firstname: '',
        lastname: '',
        isParticipant: false,
        isChallengeManager: false,
        event: null,
        competitions: [],
        birthYear: '',
        gender: undefined,
        emailRequired: '',
        emailOptional: '',
        password: '',
        confirmPassword: '',
        captcha: 0,
    }

    const formContext = useForm<RegistrationForm>({values: defaultValues})

    const watchIsParticipant = formContext.watch('isParticipant')
    const watchIsChallengeManager = formContext.watch('isChallengeManager')
    const watchEvent = formContext.watch('event')
    const watchClubname = formContext.watch('clubname')
    const watchBirthYear = formContext.watch('birthYear')
    const watchGender = formContext.watch('gender')

    const setCaptchaStart = ({start}: CaptchaDto) => {
        formContext.setValue('captcha', start)
    }

    const {captcha, onSubmitResult} = useCaptcha(setCaptchaStart)

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

    const {data: clubsData} = useFetch(
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
                    if (foundClub) {
                        formContext.setValue('clubId', foundClub.id)
                    } else {
                        formContext.setValue('clubId', undefined)
                    }
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

    const {data: competitionsData} = useFetch(
        signal =>
            getCompetitionsForRegistration({
                path: {eventId: watchEvent!.id},
                query: {
                    birthYear: Number(watchBirthYear)!,
                    gender: watchGender!,
                },
                signal,
            }),
        {
            onResponse: ({error, data}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('event.competition.competitions'),
                        }),
                    )
                } else if (data) {
                    const initialCompetitions: CompetitionRegistration[] = data.competitions.map(
                        competition => ({
                            checked: false,
                            competitionId: competition.id,
                            optionalFees: [],
                            ratingCategory: competition.properties.ratingCategoryRequired
                                ? ''
                                : 'none',
                        }),
                    )
                    formContext.setValue('competitions', initialCompetitions)
                }
            },
            preCondition: () =>
                watchEvent !== null && watchBirthYear !== '' && watchGender !== undefined,
            deps: [reloadCompetitions],
        },
    )

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
            preCondition: () => watchEvent !== null && watchIsParticipant,
            deps: [watchEvent, watchIsParticipant],
        },
    )

    // Update clubId when a club name is selected from the list
    useEffect(() => {
        if (clubsData && watchClubname) {
            const foundClub = clubsData.data.find(club => club.name === watchClubname)
            if (foundClub) {
                formContext.setValue('clubId', foundClub.id)
            } else {
                formContext.setValue('clubId', undefined)
            }
        }
    }, [watchClubname, clubsData, formContext])

    // Filter events that allow participant self-registration
    const availableEvents = eventsData?.data.filter(
        (event: EventPublicDto) =>
            event.allowParticipantSelfRegistration &&
            getRegistrationState({
                registrationAvailableFrom: event.registrationAvailableFrom,
                registrationAvailableTo: event.registrationAvailableTo,
                lateRegistrationAvailableTo: event.lateRegistrationAvailableTo,
            }) !== 'CLOSED',
    )

    const handleSubmit = async (formData: RegistrationForm) => {
        // Validate at least one registration type is selected
        if (!formData.isParticipant && !formData.isChallengeManager) {
            feedback.error(t('user.registration.error.selectAtLeastOneType'))
            return
        }

        // Validate that participant-only registrations have an existing club selected
        if (formData.isParticipant && !formData.isChallengeManager && !formData.clubId) {
            formContext.setError('clubname', {
                type: 'validate',
                message: t('club.error.mustSelectExistingClub'),
            })
            return
        }

        setSubmitting(true)

        let error: RegisterUserError | ParticipantSelfRegisterError | undefined
        if (formData.isChallengeManager) {
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
        onSubmitResult()
        formContext.resetField('captcha')

        if (error) {
            if (error.status.value === 404) {
                feedback.error(t('captcha.error.notFound'))
            } else if (error.status.value === 409) {
                if (error.errorCode === 'EMAIL_IN_USE') {
                    formContext.setError(
                        watchIsChallengeManager ? 'emailRequired' : 'emailOptional',
                        {
                            type: 'validate',
                            message:
                                t('user.email.inUse.statement') +
                                ' ' +
                                t('user.email.inUse.callToAction.registration'),
                        },
                    )
                    setActiveStep(1)
                } else if (error.errorCode === 'CAPTCHA_WRONG') {
                    feedback.error(t('captcha.error.incorrect'))
                } else if (error.errorCode === 'CLUB_NAME_ALREADY_EXISTS') {
                    formContext.setError('clubname', {
                        type: 'validate',
                        message: t('club.error.nameAlreadyExists'),
                    })
                    setActiveStep(1)
                }
            } else {
                feedback.error(t('user.registration.error.generic'))
            }
        } else {
            setRequested(formData.isChallengeManager ? 'CONFIRMATION_MAIL' : 'PARTICIPATING')
            setRegisteredForEvent(
                eventsData?.data?.find(val => val.id === formData.event?.id) || null,
            )
        }
    }

    const validateStep = async (step: RegistrationStep): Promise<boolean> => {
        let fieldsToValidate: (keyof RegistrationForm)[] = []

        switch (step) {
            case RegistrationStep.REGISTRATION_TYPE: {
                if (!watchIsParticipant && !watchIsChallengeManager) {
                    feedback.error(t('user.registration.error.selectAtLeastOneType'))
                    return false
                }
                return await formContext.trigger(['event'])
            }

            case RegistrationStep.BASIC_INFORMATION: {
                fieldsToValidate = ['clubname', 'firstname', 'lastname']

                if (watchIsChallengeManager) {
                    fieldsToValidate.push('emailRequired', 'password', 'confirmPassword')
                } else {
                    fieldsToValidate.push('emailOptional')
                }

                if (watchIsParticipant) {
                    fieldsToValidate.push('event', 'gender', 'birthYear')
                }

                const isValid = await formContext.trigger(fieldsToValidate)

                if (
                    isValid &&
                    watchIsParticipant &&
                    !watchIsChallengeManager &&
                    !formContext.getValues('clubId')
                ) {
                    formContext.setError('clubname', {
                        type: 'validate',
                        message: t('club.error.mustSelectExistingClub'),
                    })
                    return false
                }

                return isValid
            }

            case RegistrationStep.COMPETITIONS:
                if (watchIsParticipant) {
                    if (!formContext.getValues('competitions').some(val => val.checked)) {
                        feedback.error(t('user.registration.error.selectAtLeastOneCompetition'))
                        return false
                    }
                }
                return true

            case RegistrationStep.CONFIRMATION:
                return true

            default:
                return true
        }
    }

    const handleNext = async () => {
        const isValid = await validateStep(activeStep)

        if (activeStep === RegistrationStep.BASIC_INFORMATION) {
            setReloadCompetitions(prev => prev + 1)
        }

        if (isValid) {
            setActiveStep(prev => prev + 1)
        }
    }

    const handleBack = () => {
        setActiveStep(prev => prev - 1)
    }

    const getStepContent = (step: RegistrationStep) => {
        switch (step) {
            case RegistrationStep.REGISTRATION_TYPE:
                return <Step1RegistrationType availableEvents={availableEvents} />

            case RegistrationStep.BASIC_INFORMATION:
                return (
                    <Step2BasicInformation
                        createClubOnRegistrationAllowed={createClubOnRegistrationAllowed ?? false}
                        selectedEvent={availableEvents?.find(val => val.id === watchEvent?.id)}
                    />
                )

            case RegistrationStep.COMPETITIONS:
                if (!watchIsParticipant) {
                    return (
                        <Box sx={{textAlign: 'center', py: 4}}>
                            <Typography variant="body1" color="text.secondary">
                                {t('user.registration.step.competitionsNotApplicable')}
                            </Typography>
                        </Box>
                    )
                }

                return (
                    watchEvent && (
                        <Step3Competitions
                            competitionsData={competitionsData ?? undefined}
                            ratingCategories={ratingCategories ?? undefined}
                            watchBirthYear={watchBirthYear}
                        />
                    )
                )

            case RegistrationStep.CONFIRMATION:
                return (
                    <Step4Confirmation
                        watchIsParticipant={watchIsParticipant}
                        watchEvent={watchEvent}
                        registrationDocuments={registrationDocuments ?? undefined}
                        captcha={captcha}
                    />
                )

            default:
                return null
        }
    }

    const [registeredForEvent, setRegisteredForEvent] = useState<EventPublicDto | null>(null)

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
                        {[1, 2, 3, 4].map(step => (
                            <Step key={step}>
                                <StepLabel slots={{stepIcon: CustomStepIcon}} />
                            </Step>
                        ))}
                    </Stepper>

                    <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                        <Stack spacing={4}>
                            {getStepContent(activeStep)}

                            <Box sx={{display: 'flex', justifyContent: 'space-between', mt: 3}}>
                                <Button
                                    disabled={activeStep === RegistrationStep.REGISTRATION_TYPE}
                                    onClick={handleBack}
                                    variant="outlined"
                                    sx={{cursor: 'pointer'}}>
                                    {t('common.back')}
                                </Button>

                                {activeStep < 3 ? (
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
                        {t('user.registration.requested.participating.message.part1')}
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
