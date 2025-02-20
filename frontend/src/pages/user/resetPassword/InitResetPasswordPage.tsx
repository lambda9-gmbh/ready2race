import {FormContainer, useForm} from 'react-hook-form-mui'
import {Divider, Stack, Typography} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {useTranslation} from 'react-i18next'
import {useState} from 'react'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {useFeedback} from '@utils/hooks.ts'
import {initPasswordReset} from '@api/sdk.gen.ts'
import SimpleFormLayout from '@components/SimpleFormLayout.tsx'
import ConfirmationMailSent from '@components/user/ConfirmationMailSent.tsx'

type Form = {
    email: string
}

const InitResetPasswordPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)

    const [requested, setRequested] = useState(false)

    const formContext = useForm<Form>()

    const handleSubmit = async (formData: Form) => {
        setSubmitting(true)
        //todo captcha
        const {error} = await initPasswordReset({
            body: {
                email: formData.email,
                language: 'de', // todo language
                callbackUrl: location.origin + location.pathname + '/',
            },
        })

        setSubmitting(false)
        if (error) {
            feedback.error(t('common.error.unexpected'))
            console.log(error)
        }
        setRequested(true)
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
