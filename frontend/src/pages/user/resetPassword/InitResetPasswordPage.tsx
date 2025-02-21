import {FormContainer, useForm} from 'react-hook-form-mui'
import {Divider, Stack, Typography} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {useTranslation} from 'react-i18next'
import {useState} from 'react'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {useFeedback, useFormWithCaptcha} from '@utils/hooks.ts'
import {initPasswordReset} from '@api/sdk.gen.ts'
import SimpleFormLayout from '@components/SimpleFormLayout.tsx'
import ConfirmationMailSent from '@components/user/ConfirmationMailSent.tsx'
import FormInputCaptcha, {CaptchaFormPart} from '@components/form/input/FormInputCaptcha.tsx'

type Form = CaptchaFormPart & {
    email: string
}

const InitResetPasswordPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)
    const [requested, setRequested] = useState(false)

    const formContext = useForm<Form>()

    const setCaptchaStart = (captchaStart: number) => {
        formContext.setValue('captcha.input', captchaStart)
    }

    const {captchaProps, onSubmitResult} = useFormWithCaptcha({onCaptchaCreated: setCaptchaStart})

    const handleSubmit = async (formData: Form) => {
        setSubmitting(true)
        const result = await initPasswordReset({
            query: {
                challenge: captchaProps.data?.id ?? '',
                input: formData.captcha.input ?? 0, // todo: When the Circle spawns on the same position as the slider the user would expect that it works without moving the slider. It doesn't though (solution is 12 and input is passed as 0)
            },
            body: {
                email: formData.email,
                language: 'de', // todo language
                callbackUrl: location.origin + location.pathname + '/',
            },
        })
        setSubmitting(false)
        onSubmitResult()
        formContext.resetField('captcha.input')
        if (result.error) {
            if (result.error.status.value === 404) {
                feedback.error(t('user.resetPassword.captcha.error.notFound'))
            } else if (result.error.status.value === 409) {
                feedback.error(t('user.resetPassword.captcha.error.submit'))
            } else if (result.error.status.value === 429) {
                feedback.error(t('user.resetPassword.captcha.error.tooManyRequests'))
            } else {

                feedback.error(t('common.error.unexpected'))
            }
            console.log(result.error)
        } else {
            setRequested(true)
        }
    }

    return (
        <SimpleFormLayout maxWidth={450}>
            {(!requested && (
                <>
                    <Typography variant="h2" textAlign="center">
                        {t('user.resetPassword.forgotPassword')}
                    </Typography>
                    <Divider sx={{my: 4}} />
                    <Typography sx={{mb: 4}}>{t('user.resetPassword.init.instruction')}</Typography>
                    <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                        <Stack spacing={2}>
                            <FormInputText name={'email'} label={t('user.email')} required />
                            <FormInputCaptcha
                                captchaProps={captchaProps}
                                formContext={formContext}
                            />
                            <SubmitButton
                                label={t('user.resetPassword.init.submit')}
                                submitting={submitting}
                            />
                        </Stack>
                    </FormContainer>
                </>
            )) || (
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
