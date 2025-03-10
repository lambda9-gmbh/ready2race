import {useTranslation} from 'react-i18next'
import SimpleFormLayout from '@components/SimpleFormLayout.tsx'
import {useState} from 'react'
import {useFeedback} from '@utils/hooks.ts'
import {Box, Stack, Typography} from '@mui/material'
import {FormContainer, useForm} from 'react-hook-form-mui'
import {NewPassword, PasswortFormPart} from '@components/form/NewPassword.tsx'
import {resetPassword} from '@api/sdk.gen.ts'
import {resetPasswordTokenRoute} from '@routes'
import RequestStatusResponse from '@components/user/RequestStatusResponse.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'

type Form = PasswortFormPart

const ResetPasswordPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {passwordResetToken} = resetPasswordTokenRoute.useParams()

    const [submitting, setSubmitting] = useState(false)
    const [requestSuccessful, setRequestSuccessful] = useState<boolean | undefined>(undefined)

    const defaultValues: Form = {
        password: '',
        confirmPassword: '',
    }

    const formContext = useForm<Form>({values: defaultValues})

    const handleSubmit = async (formData: Form) => {
        setSubmitting(true)
        const {error} = await resetPassword({
            path: {passwordResetToken: passwordResetToken},
            body: {
                password: formData.password,
            },
        })
        setSubmitting(false)
        if (error) {
            setRequestSuccessful(false)
            feedback.error(t('user.resetPassword.error.header'))
            console.error(error)
        } else {
            setRequestSuccessful(true)
        }
    }

    return (
        <SimpleFormLayout maxWidth={450}>
            {(requestSuccessful === undefined && (
                <>
                    <Box sx={{mb: 4}}>
                        <Typography variant="h1" textAlign="center">
                            {t('user.resetPassword.resetPassword')}
                        </Typography>
                    </Box>
                    <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                        <Stack spacing={4}>
                            <NewPassword
                                formContext={formContext}
                                passwordFieldLabel={t('user.resetPassword.newPassword')}
                            />
                            <SubmitButton
                                label={t('user.resetPassword.resetPassword')}
                                submitting={submitting}
                            />
                        </Stack>
                    </FormContainer>
                </>
            )) ||
                (requestSuccessful === true && (
                    <RequestStatusResponse
                        success={true}
                        header={t('user.resetPassword.success.header')}
                        showLoginNavigation>
                        <Typography textAlign="center">
                            {t('user.resetPassword.success.message')}
                        </Typography>
                    </RequestStatusResponse>
                )) ||
                (requestSuccessful === false && (
                    <RequestStatusResponse
                        success={false}
                        header={t('user.resetPassword.error.header')}>
                        <>
                            <Typography textAlign="center">
                                {t('user.resetPassword.error.message.part1')}
                            </Typography>
                            <Typography textAlign="center">
                                {t('user.resetPassword.error.message.part2')}
                            </Typography>
                        </>
                    </RequestStatusResponse>
                ))}
        </SimpleFormLayout>
    )
}

export default ResetPasswordPage
