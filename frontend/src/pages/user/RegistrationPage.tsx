import {Box, Divider, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {FormContainer, PasswordElement, useForm} from 'react-hook-form-mui'
import {registerUser} from '../../api'
import {useState} from 'react'
import {useFeedback} from '../../utils/hooks.ts'
import {FormInputText} from '../../components/form/input/FormInputText.tsx'
import {SubmitButton} from '../../components/form/SubmitButton.tsx'
import {EmailOutlined} from '@mui/icons-material'
import FormInputPassword from '../../components/form/input/FormInputPassword.tsx'
import SimpleFormLayout from '../../components/SimpleFormLayout.tsx'
import {Link} from '@tanstack/react-router'

type Form = {
    email: string
    password: string
    confirmPassword: string
    firstname: string
    lastname: string
}

const RegistrationPage = () => {
    const minPasswordLength = 10

    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)

    const [mailSent, setMailSent] = useState(false)

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
        } else {
            setMailSent(true)
        }
    }

    return (
        <SimpleFormLayout maxWidth={500}>
            {(!mailSent && (
                <>
                    <Box sx={{mb: 4}}>
                        <Typography variant="h1" textAlign='center'>{t('user.registration.register')}</Typography>
                    </Box>
                    <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                        <Stack spacing={4}>
                            <FormInputText name={'email'} label={t('user.email')} required />
                            <Stack direction="row" spacing={2}>
                                <FormInputPassword
                                    name={'password'}
                                    label={t('user.password')}
                                    required
                                    helperText={t('user.registration.password.minLength', {
                                        min: minPasswordLength,
                                    })}
                                    rules={{
                                        minLength: {
                                            value: minPasswordLength,
                                            message: t('user.registration.password.tooShort', {
                                                min: minPasswordLength,
                                            }),
                                        },
                                        validate: (val, vals) => {
                                            if (val !== vals['confirmPassword']) {
                                                formContext.setError('confirmPassword', {
                                                    type: 'validate',
                                                    message: t(
                                                        'user.registration.password.notMatching',
                                                    ),
                                                })
                                                return t('user.registration.password.notMatching')
                                            } else {
                                                formContext.clearErrors('confirmPassword')
                                            }
                                        },
                                    }}
                                    sx={{flex: 1}}
                                />
                                <PasswordElement
                                    name="confirmPassword"
                                    label={t('user.registration.password.confirm')}
                                    required
                                    type="password"
                                    rules={{
                                        required: t('common.form.required'),
                                        validate: (val, vals) => {
                                            if (val !== vals['password']) {
                                                formContext.setError('password', {
                                                    type: 'validate',
                                                    message: t(
                                                        'user.registration.password.notMatching',
                                                    ),
                                                })
                                                return t('user.registration.password.notMatching')
                                            } else {
                                                formContext.clearErrors('password')
                                            }
                                        },
                                    }}
                                    sx={{flex: 1}}
                                />
                            </Stack>
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
                <Stack spacing={2}>
                    <Box sx={{display: 'flex'}}>
                        <EmailOutlined sx={{height: 100, width: 100, margin: 'auto'}} />
                    </Box>
                    <Typography variant="h2" textAlign="center">
                        {t('user.registration.email.emailSent.header')}
                    </Typography>
                    <Divider />
                    <Typography textAlign="center">
                        {t('user.registration.email.emailSent.message.part1')}
                    </Typography>
                    <Typography textAlign="center">
                        {t('user.registration.email.emailSent.message.part2')}
                    </Typography>
                </Stack>
            )}
        </SimpleFormLayout>
    )
}

export default RegistrationPage
