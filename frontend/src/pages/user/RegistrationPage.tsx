import {Box, Button, Card, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {FormContainer, TextFieldElement, useForm} from 'react-hook-form-mui'
import {registerUser} from '../../api'
import {useState} from 'react'
import {useFeedback} from '../../utils/hooks.ts'
import {FormInputText} from '../../components/form/input/FormInputText.tsx'
import {SubmitButton} from '../../components/form/SubmitButton.tsx'
import {EmailOutlined} from '@mui/icons-material'

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
        const result = await registerUser({
            body: {
                email: formData.email,
                password: formData.password,
                firstname: formData.firstname,
                lastname: formData.lastname,
                language: 'de', // todo, read from i18n
                callbackUrl: location.origin + location.pathname,
            },
        })
        setSubmitting(false)

        if (result) {
            if (result.error) {
                if (result.error.status.value === 409) {
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
                console.log(result.error)
            } else {
                setMailSent(true)
            }
        }
    }

    return (
        <Box sx={{display: 'flex'}}>
            <Card
                sx={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignSelf: 'center',
                    margin: 'auto',
                    maxWidth: 600,
                    padding: 4,
                }}>
                {(!mailSent && (
                    <>
                        <Box sx={{mb: 4}}>
                            <Typography variant="h1">
                                {t('user.registration.registration')}
                            </Typography>
                        </Box>
                        <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                            <Stack spacing={4}>
                                <FormInputText name={'email'} label={t('user.email')} required />
                                <Stack direction="row" spacing={2}>
                                    <TextFieldElement
                                        name={'password'}
                                        label={t('user.password')}
                                        required
                                        type="password"
                                        helperText={t('user.registration.password.minLength', {
                                            min: minPasswordLength,
                                        })}
                                        rules={{
                                            required: t('common.form.required'),
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
                                                    return t(
                                                        'user.registration.password.notMatching',
                                                    )
                                                } else {
                                                    formContext.clearErrors('confirmPassword')
                                                }
                                            },
                                        }}
                                    />
                                    <TextFieldElement
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
                                                    return t(
                                                        'user.registration.password.notMatching',
                                                    )
                                                } else {
                                                    formContext.clearErrors('password')
                                                }
                                            },
                                        }}
                                        sx={{maxWidth: 250}}
                                    />
                                </Stack>
                                <Stack spacing={2} direction="row">
                                    <FormInputText
                                        name={'firstname'}
                                        label={t('user.firstname')}
                                        required
                                    />
                                    <FormInputText
                                        name={'lastname'}
                                        label={t('user.lastname')}
                                        required
                                    />
                                </Stack>
                                <SubmitButton
                                    label={t('user.registration.register')}
                                    submitting={submitting}
                                />
                            </Stack>
                        </FormContainer>
                        <Button onClick={() => setMailSent(true)}>Delete this later</Button>
                    </>
                )) || (
                    <>
                        <Box sx={{display: 'flex'}}>
                            <EmailOutlined sx={{height: 100, width: 100, margin: 'auto'}} />
                        </Box>
                        <Typography textAlign='center' variant='h2'>{t('user.registration.email.emailSent.header')}</Typography>
                        <Typography variant='body1'>{t('user.registration.email.emailSent.message')}</Typography>
                    </>
                )}
            </Card>
        </Box>
    )
}

export default RegistrationPage
