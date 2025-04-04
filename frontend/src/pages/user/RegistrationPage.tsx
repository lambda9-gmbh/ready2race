import {Box, Divider, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {FormContainer, useForm} from 'react-hook-form-mui'
import {registerUser} from 'api/sdk.gen.ts'
import {useState} from 'react'
import {useCaptcha, useFeedback} from '@utils/hooks.ts'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import SimpleFormLayout from '@components/SimpleFormLayout.tsx'
import {Link} from '@tanstack/react-router'
import ConfirmationMailSent from '@components/user/ConfirmationMailSent.tsx'
import {NewPassword, PasswordFormPart} from '@components/form/NewPassword.tsx'
import {CaptchaDto, RegisterRequest} from '@api/types.gen.ts'
import {i18nLanguage, languageMapping} from '@utils/helpers.ts'
import FormInputEmail from '@components/form/input/FormInputEmail.tsx'
import FormInputCaptcha from '@components/form/input/FormInputCaptcha.tsx'

type Form = {
    email: string
    firstname: string
    lastname: string
    clubname: string
    captcha: number
} & PasswordFormPart

const RegistrationPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)

    const [requested, setRequested] = useState(false)

    const defaultValues: Form = {
        email: '',
        firstname: '',
        lastname: '',
        clubname: '',
        password: '',
        confirmPassword: '',
        captcha: 0,
    }

    const formContext = useForm<Form>({values: defaultValues})

    const setCaptchaStart = ({start}: CaptchaDto) => {
        formContext.setValue('captcha', start)
    }

    const {captcha, onSubmitResult} = useCaptcha(setCaptchaStart)

    const handleSubmit = async (formData: Form) => {
        setSubmitting(true)

        const {error} = await registerUser({
            query: {
                challenge: captcha.data!.id,
                input: formData.captcha,
            },
            body: mapFormToRequest(formData),
        })

        setSubmitting(false)
        onSubmitResult()
        formContext.resetField('captcha')

        if (error) {
            if (error.status.value === 404) {
                feedback.error(t('captcha.error.notFound'))
            } else if (error.status.value === 409) {
                if (error.errorCode === 'EMAIL_IN_USE') {
                    formContext.setError('email', {
                        type: 'validate',
                        message:
                            t('user.email.inUse.statement') +
                            ' ' +
                            t('user.email.inUse.callToAction.registration'),
                    })
                } else if (error.errorCode === 'CAPTCHA_WRONG') {
                    feedback.error(t('captcha.error.incorrect'))
                }
            } else {
                feedback.error(t('user.registration.error'))
            }
        } else {
            setRequested(true)
        }
    }

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
                            <FormInputEmail name={'email'} label={t('user.email.email')} required />
                            <NewPassword formContext={formContext} horizontal />
                            <FormInputText
                                name={'firstname'}
                                label={t('user.firstname')}
                                required
                                sx={{flex: 1}}
                            />
                            <FormInputText
                                name={'lastname'}
                                label={t('user.lastname')}
                                required
                                sx={{flex: 1}}
                            />
                            <FormInputText
                                name={'clubname'}
                                label={t('club.club')}
                                required
                                sx={{flex: 1}}
                            />
                            <FormInputCaptcha captchaProps={captcha} />
                            <SubmitButton
                                label={t('user.registration.register')}
                                submitting={submitting}
                            />
                            <Divider />
                        </Stack>
                    </FormContainer>
                    <Stack direction="row" spacing="5px" justifyContent="center" sx={{mt: 4}}>
                        <Typography variant="body1" sx={{fontWeight: 'light'}}>
                            {t('user.registration.login.message')}
                        </Typography>
                        <Link to="/login">
                            <Typography>{t('user.registration.login.link')}</Typography>
                        </Link>
                    </Stack>
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

function mapFormToRequest(formData: Form): RegisterRequest {
    return {
        email: formData.email,
        password: formData.password,
        firstname: formData.firstname,
        lastname: formData.lastname,
        clubname: formData.clubname,
        language: languageMapping[i18nLanguage()],
        callbackUrl: location.origin + location.pathname + '/',
    }
}
