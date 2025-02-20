import {FormContainer, useForm} from 'react-hook-form-mui'
import {Stack} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {useTranslation} from 'react-i18next'
import {useState} from 'react'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {useFeedback} from '@utils/hooks.ts'
import {initPasswordReset} from "@api/sdk.gen.ts";

type Form = {
    email: string
}

const InitResetPasswordPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)

    const formContext = useForm<Form>()

    const handleSubmit = async (formData: Form) => {
        setSubmitting(true)
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
        }
    }

    return (
        <FormContainer formContext={formContext} onSuccess={handleSubmit}>
            <Stack spacing={2}>
                <FormInputText name={'email'} label={'TODO'} />
                <SubmitButton label={t('user.resetPassword.init.submit')} submitting={submitting} />
            </Stack>
        </FormContainer>
    )
}

export default InitResetPasswordPage
