import {FormContainer, useForm} from 'react-hook-form-mui'
import {Alert, Divider, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useEffect, useState} from 'react'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {useFeedback, useCaptcha} from '@utils/hooks.ts'
import {initPasswordReset} from '@api/sdk.gen.ts'
import SimpleFormLayout from '@components/SimpleFormLayout.tsx'
import ConfirmationMailSent from '@components/user/ConfirmationMailSent.tsx'
import FormInputCaptcha from '@components/form/input/FormInputCaptcha.tsx'
import {i18nLanguage, languageMapping} from '@utils/helpers.ts'
import {CaptchaDto, TooManyRequestsError} from '@api/types.gen.ts'
import FormInputEmail from "@components/form/input/FormInputEmail.tsx";

type Form = {
    email: string
    captcha: number
}

const InitResetPasswordPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)
    const [requested, setRequested] = useState(false)

    const [tooManyRequestsTimer, setTooManyRequestsTimer] = useState(0)

    useEffect(() => {
        const interval = setInterval(() => {
            tooManyRequestsTimer > 0 ? setTooManyRequestsTimer(prevTime => prevTime - 1) : null
        }, 1000)
        return () => clearInterval(interval)
    }, [tooManyRequestsTimer])

    const defaultValues: Form = {
        email: '',
        captcha: 0,
    }

    const formContext = useForm<Form>({values: defaultValues})

    const setCaptchaStart = ({start}: CaptchaDto) => {
        formContext.setValue('captcha', start)
    }

    const {captcha, onSubmitResult} = useCaptcha(setCaptchaStart)

    const handleSubmit = async (formData: Form) => {
        setSubmitting(true)

        const result = await initPasswordReset({
            query: {
                challenge: captcha.data!.id,
                input: formData.captcha,
            },
            body: {
                email: formData.email,
                language: languageMapping[i18nLanguage()],
                callbackUrl: location.origin + location.pathname + '/',
            },
        })

        setSubmitting(false)
        onSubmitResult()
        formContext.resetField('captcha')

        if (result.error) {
            if (result.error.status.value === 404) {
                feedback.error(t('captcha.error.notFound'))
            } else if (result.error.status.value === 409) {
                feedback.error(t('captcha.error.incorrect'))
            } else if (result.error.status.value === 422) {
                feedback.error(t('user.resetPassword.error.invalid'))
            } else if (result.error.status.value === 429) { // todo: It's possible to get RateLimits remaining from the response header before
                try {
                    const tooManyRequestsError = result.error as TooManyRequestsError // todo: better way than try catch?
                    feedback.error(
                        t('common.error.tooManyRequests', {
                            val: Math.ceil(tooManyRequestsError.details.retryAfter / 60),
                        }),
                    )
                    setTooManyRequestsTimer(tooManyRequestsError.details.retryAfter)
                } catch (e) {}
            } else {
                feedback.error(t('common.error.unexpected'))
            }
        } else {
            setRequested(true)
        }
    }

    return (
        <SimpleFormLayout maxWidth={450}>
            {!requested ? (
                <>
                    <Typography variant="h2" textAlign="center">
                        {t('user.resetPassword.forgotPassword')}
                    </Typography>
                    <Divider sx={{my: 4}} />
                    <Typography sx={{mb: 4}}>{t('user.resetPassword.init.instruction')}</Typography>
                    <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                        <Stack spacing={4}>
                            <FormInputEmail name={'email'} label={t('user.email.email')} required />
                            {tooManyRequestsTimer <= 0 ? (
                                <FormInputCaptcha captchaProps={captcha} />
                            ) : (
                                <Alert severity="error">
                                    {t('common.error.tooManyRequests', {
                                        val: Math.ceil(tooManyRequestsTimer / 60), // todo: under 1min show seconds
                                    })}
                                </Alert>
                            )}
                            <SubmitButton
                                label={t('user.resetPassword.init.submit')}
                                disabled={!captcha.data || tooManyRequestsTimer > 0}
                                submitting={submitting}
                            />
                        </Stack>
                    </FormContainer>
                </>
            ) : (
                <ConfirmationMailSent header={t('user.resetPassword.emailSent.header')}>
                    <Typography textAlign="center">
                        {t('user.resetPassword.emailSent.message.part1')}
                    </Typography>
                    <Typography textAlign="center">
                        {t('user.resetPassword.emailSent.message.part2')}
                    </Typography>
                </ConfirmationMailSent>
            )}
        </SimpleFormLayout>
    )
}

export default InitResetPasswordPage
