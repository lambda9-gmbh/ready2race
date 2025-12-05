import {Box, Stack, Typography, useMediaQuery, useTheme} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {CheckboxButtonGroup, FormContainer, useForm} from 'react-hook-form-mui'
import {Controller} from 'react-hook-form'
import {
    getClubs,
    getCompetitions,
    getCreateClubOnRegistrationAllowed,
    getPublicEvents,
    getRatingCategoriesForEvent,
    getRegistrationDocuments,
    participantSelfRegister,
    registerUser,
} from 'api/sdk.gen.ts'
import {useEffect, useMemo, useState} from 'react'
import {useCaptcha, useFeedback, useFetch} from '@utils/hooks.ts'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import SimpleFormLayout from '@components/SimpleFormLayout.tsx'
import ConfirmationMailSent from '@components/user/ConfirmationMailSent.tsx'
import {NewPassword, PasswordFormPart} from '@components/form/NewPassword.tsx'
import {
    AppUserRegisterRequest,
    CaptchaDto,
    EventPublicDto,
    Gender,
    ParticipantRegisterRequest,
    type ParticipantSelfRegisterError,
    RegisterUserError,
} from '@api/types.gen.ts'
import {i18nLanguage, languageMapping} from '@utils/helpers.ts'
import FormInputEmail from '@components/form/input/FormInputEmail.tsx'
import FormInputCaptcha from '@components/form/input/FormInputCaptcha.tsx'
import {AutocompleteClub} from '@components/club/AutocompleteClub.tsx'
import {FormInputCheckbox} from '@components/form/input/FormInputCheckbox.tsx'
import FormInputAutocomplete from '@components/form/input/FormInputAutocomplete.tsx'
import {FormInputRadioButtonGroup} from '@components/form/input/FormInputRadioButtonGroup.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {FormInputSelect} from '@components/form/input/FormInputSelect.tsx'
import {AutocompleteOption} from '@utils/types.ts'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {EventRegistrationConfirmDocumentsForm} from '@components/eventRegistration/EventRegistrationConfirmDocumentsForm.tsx'

type CompetitionRegistration = {
    checked: boolean
    competitionId: string
    optionalFees: string[]
    ratingCategory?: string
}

type Form = {
    clubname: string
    clubId?: string
    firstname: string
    lastname: string
    isParticipant: boolean
    isChallengeManager: boolean
    event: AutocompleteOption
    competitions: CompetitionRegistration[]
    birthYear?: number
    gender?: Gender
    emailRequired: string
    emailOptional: string
    captcha: number
} & PasswordFormPart

const RegistrationPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const theme = useTheme()

    const isMobile = useMediaQuery(theme.breakpoints.down('sm'))

    const [submitting, setSubmitting] = useState(false)
    const [requested, setRequested] = useState(false)

    const defaultValues: Form = {
        clubname: '',
        clubId: undefined,
        firstname: '',
        lastname: '',
        isParticipant: false,
        isChallengeManager: false,
        event: null,
        competitions: [],
        birthYear: undefined,
        gender: undefined,
        emailRequired: '',
        emailOptional: '',
        password: '',
        confirmPassword: '',
        captcha: 0,
    }

    const formContext = useForm<Form>({values: defaultValues})

    const watchIsParticipant = formContext.watch('isParticipant')
    const watchIsChallengeManager = formContext.watch('isChallengeManager')
    const watchEvent = formContext.watch('event')
    const watchClubname = formContext.watch('clubname')
    const watchCompetitions = formContext.watch('competitions')

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

    // TODO: New endpoint that fetches only competitions that fit the defined data (gender and age fit)
    const {data: competitionsData} = useFetch(
        signal =>
            getCompetitions({
                path: {eventId: watchEvent!.id},
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
                    const initialCompetitions: CompetitionRegistration[] = data.data.map(
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
            preCondition: () => watchEvent !== null,
            deps: [watchEvent],
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

    const ratingCategoryOptions = (ratingCategoryRequired: boolean) =>
        (ratingCategories?.length ?? 0) > 0
            ? [
                  ...(ratingCategoryRequired
                      ? []
                      : [
                            {
                                id: 'none',
                                label: t('common.form.select.none'),
                            },
                        ]),
                  ...(ratingCategories?.map(dto => ({
                      id: dto.ratingCategory.id,
                      label: dto.ratingCategory.name,
                  })) ?? []),
              ]
            : []

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
        (event: EventPublicDto) => event.allowParticipantSelfRegistration,
    )

    const handleSubmit = async (formData: Form) => {
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
            // Use registerUser when creating an account (challenge manager)

            const result = await registerUser({
                query: {
                    challenge: captcha.data!.id,
                    input: formData.captcha,
                },
                body: mapFormToAppUserRegisterRequest(formData),
            })
            error = result.error
        } else {
            // Use participantSelfRegister when only registering as participant

            if (!formData.event) return

            const result = await participantSelfRegister({
                path: {eventId: formData.event.id},
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
                } else if (error.errorCode === 'CAPTCHA_WRONG') {
                    feedback.error(t('captcha.error.incorrect'))
                } else if (error.errorCode === 'CLUB_NAME_ALREADY_EXISTS') {
                    formContext.setError('clubname', {
                        type: 'validate',
                        message: t('club.error.nameAlreadyExists'),
                    })
                }
            } else {
                feedback.error(t('user.registration.error.generic'))
            }
        } else {
            setRequested(true)
        }
    }

    const currentYear = useMemo(() => new Date().getFullYear(), [])

    return (
        <SimpleFormLayout maxWidth={500}>
            {!requested ? (
                <>
                    <Box sx={{mb: 4}}>
                        <Typography variant="h1" textAlign="center">
                            {t('user.registration.register')}
                        </Typography>
                    </Box>
                    <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                        <Stack spacing={4}>
                            <Box>
                                <Typography variant="body2" sx={{mb: 2}}>
                                    {t('user.registration.registrationType')}
                                </Typography>
                                <Stack
                                    spacing={2}
                                    direction={{xs: 'column', sm: 'row'}}
                                    sx={{justifyContent: 'space-between'}}>
                                    <FormInputCheckbox
                                        name="isChallengeManager"
                                        label={t('user.registration.asChallengeManager')}
                                        horizontal
                                        reverse
                                    />
                                    <FormInputCheckbox
                                        name="isParticipant"
                                        label={t('user.registration.asParticipant')}
                                        horizontal
                                        reverse
                                    />
                                </Stack>
                            </Box>

                            <AutocompleteClub
                                name="clubname"
                                label={t('club.club')}
                                required
                                freeSolo={
                                    createClubOnRegistrationAllowed === true &&
                                    watchIsChallengeManager
                                }
                            />

                            <FormInputText
                                name="firstname"
                                label={t('user.firstname')}
                                required
                                sx={{flex: 1}}
                            />

                            <FormInputText
                                name="lastname"
                                label={t('user.lastname')}
                                required
                                sx={{flex: 1}}
                            />

                            {watchIsParticipant &&
                                availableEvents &&
                                availableEvents.length > 0 && (
                                    <FormInputAutocomplete
                                        name="event"
                                        label={t('event.event')}
                                        required
                                        options={availableEvents.map(event => ({
                                            id: event.id,
                                            label: event.name,
                                        }))}
                                    />
                                )}

                            {watchIsParticipant && (
                                <>
                                    <FormInputRadioButtonGroup
                                        name="gender"
                                        label={t('entity.gender')}
                                        required
                                        row
                                        options={[
                                            {label: 'M', id: 'M'},
                                            {label: 'F', id: 'F'},
                                            {label: 'D', id: 'D'},
                                        ]}
                                    />
                                    <FormInputNumber
                                        required
                                        name={'birthYear'}
                                        label={t('user.birthYear')}
                                        integer
                                        min={currentYear - 120}
                                        max={currentYear}
                                    />
                                    {watchEvent &&
                                        competitionsData &&
                                        competitionsData.data.length > 0 && (
                                            <Box>
                                                <Typography variant="body2" sx={{mb: 2}}>
                                                    {t('event.competition.competitions')}
                                                </Typography>
                                                <Stack spacing={3}>
                                                    {competitionsData.data.map((competition, index) => {
                                                        const competitionReg = watchCompetitions?.[index]
                                                        const isChecked = competitionReg?.checked ?? false
                                                        const optionalFees =
                                                            competition.properties.fees?.filter(
                                                                f => !f.required,
                                                            ) ?? []

                                                        return (
                                                            <Box key={competition.id}>
                                                                <Controller
                                                                    name={`competitions.${index}.checked`}
                                                                    control={formContext.control}
                                                                    render={({field}) => (
                                                                        <FormInputCheckbox
                                                                            name={field.name}
                                                                            label={
                                                                                competition.properties
                                                                                    .name
                                                                            }
                                                                            checked={field.value}
                                                                            onChange={field.onChange}
                                                                        />
                                                                    )}
                                                                />
                                                                {isChecked && (
                                                                    <Box sx={{ml: 4, mt: 2}}>
                                                                        <Stack spacing={2}>
                                                                            {(ratingCategories?.length ??
                                                                                0) > 0 && (
                                                                                <FormInputSelect
                                                                                    name={`competitions.${index}.ratingCategory`}
                                                                                    label={t(
                                                                                        'event.competition.registration.ratingCategory',
                                                                                    )}
                                                                                    options={ratingCategoryOptions(
                                                                                        competition
                                                                                            .properties
                                                                                            .ratingCategoryRequired,
                                                                                    )}
                                                                                    required={
                                                                                        competition
                                                                                            .properties
                                                                                            .ratingCategoryRequired
                                                                                    }
                                                                                />
                                                                            )}
                                                                            {optionalFees.length > 0 && (
                                                                                <CheckboxButtonGroup
                                                                                    label={t(
                                                                                        'event.registration.optionalFee',
                                                                                    )}
                                                                                    name={`competitions.${index}.optionalFees`}
                                                                                    labelKey={'name'}
                                                                                    options={
                                                                                        optionalFees
                                                                                    }
                                                                                    row
                                                                                />
                                                                            )}
                                                                        </Stack>
                                                                    </Box>
                                                                )}
                                                            </Box>
                                                        )
                                                    })}
                                                </Stack>
                                            </Box>
                                        )}
                                    {watchEvent &&
                                        registrationDocuments &&
                                        registrationDocuments.length > 0 && (
                                            <EventRegistrationConfirmDocumentsForm
                                                eventId={watchEvent.id}
                                                documentTypes={registrationDocuments}
                                            />
                                        )}
                                </>
                            )}
                            {watchIsChallengeManager ? (
                                <FormInputEmail
                                    name="emailRequired"
                                    label={t('user.email.email')}
                                    required
                                />
                            ) : (
                                <FormInputEmail
                                    name="emailOptional"
                                    label={t('user.email.email')}
                                />
                            )}

                            {watchIsChallengeManager && (
                                <NewPassword formContext={formContext} horizontal={!isMobile} />
                            )}
                            <FormInputCaptcha captchaProps={captcha} />

                            <SubmitButton submitting={submitting}>
                                {t('user.registration.register')}
                            </SubmitButton>
                        </Stack>
                    </FormContainer>
                </>
            ) : (
                <ConfirmationMailSent header={t('user.registration.email.emailSent.header')}>
                    <Typography textAlign="center">
                        {t('user.registration.email.emailSent.message.part1')}
                    </Typography>
                    <Typography textAlign="center">
                        {t('user.registration.email.emailSent.message.part2')}
                    </Typography>
                </ConfirmationMailSent>
            )}
        </SimpleFormLayout>
    )
}

export default RegistrationPage

function mapFormToAppUserRegisterRequest(formData: Form): AppUserRegisterRequest {
    return {
        email: formData.emailRequired!,
        password: formData.password,
        firstname: formData.firstname,
        lastname: formData.lastname,
        clubId: formData.clubId,
        clubname: formData.clubId ? undefined : formData.clubname,
        language: languageMapping[i18nLanguage()],
        callbackUrl: location.origin + location.pathname + '/',
        registerToSingleCompetitions: formData.competitions
            .filter(competition => competition.checked)
            .map(competition => ({
                competitionId: competition.competitionId,
                optionalFees: competition.optionalFees,
                ratingCategory:
                    competition.ratingCategory !== 'none' ? competition.ratingCategory : undefined,
            })),
        birthYear: formData.birthYear,
        gender: formData.gender,
    }
}

function mapFormToParticipantRegisterRequest(formData: Form): ParticipantRegisterRequest {
    return {
        firstname: formData.firstname,
        lastname: formData.lastname,
        gender: formData.gender!,
        birthYear: formData.birthYear!,
        email: takeIfNotEmpty(formData.emailOptional),
        clubId: formData.clubId!,
        language: languageMapping[i18nLanguage()],
        registerToSingleCompetitions: formData.competitions
            .filter(competition => competition.checked)
            .map(competition => ({
                competitionId: competition.competitionId,
                optionalFees: competition.optionalFees,
                ratingCategory:
                    competition.ratingCategory !== 'none' ? competition.ratingCategory : undefined,
            })),
    }
}
