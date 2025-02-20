import {Box, Divider, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {FormContainer, useForm} from 'react-hook-form-mui'
import {registerUser} from 'api/sdk.gen.ts'
import {useState} from 'react'
import {useFeedback} from '@utils/hooks.ts'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import SimpleFormLayout from '@components/SimpleFormLayout.tsx'
import {Link} from '@tanstack/react-router'
import ConfirmationMailSent from "@components/user/ConfirmationMailSent.tsx";
import {NewPassword, PasswortFormPart} from "@components/form/NewPassword.tsx";

type Form = {
    email: string
    firstname: string
    lastname: string
} & PasswortFormPart

const RegistrationPage = () => {

    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)

    const [requested, setRequested] = useState(false)

    const formContext = useForm<Form>()

    const handleSubmit = async (formData: Form) => {
        setSubmitting(true)
        const {error} = await registerUser({
            body: {
                email: formData.email,
                password: formData.password,
                firstname: formData.firstname,
                lastname: formData.lastname,
                language: 'de', // todo, read from i18n
                callbackUrl: location.origin + location.pathname + '/',
            },
        })
        setSubmitting(false)
        if (error) {
            if (error.status.value === 409) {
                formContext.setError('email', {
                    type: 'validate',
                    message:
                        t('user.registration.email.inUse.statement') +
                        ' ' +
                        t('user.registration.email.inUse.callToAction'),
                })
                feedback.error(t('user.registration.email.inUse.statement'))
            } else {
                feedback.error(t('user.registration.error'))
            }
            console.log(error)
        }
        setRequested(true)
    }

    return (
        <SimpleFormLayout maxWidth={500}>
            {(!requested && (
                <>
                    <Box sx={{mb: 4}}>
                        <Typography variant="h1" textAlign='center'>{t('user.registration.register')}</Typography>
                    </Box>
                    <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                        <Stack spacing={4}>
                            <FormInputText name={'email'} label={t('user.email')} required />
                            <NewPassword formContext={formContext} horizontal/>
                            <Stack spacing={2} direction="row">
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
                            </Stack>
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
            )) || (
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
