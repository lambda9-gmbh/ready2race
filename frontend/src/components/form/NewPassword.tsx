import {useTranslation} from 'react-i18next'
import FormInputPassword from '@components/form/input/FormInputPassword.tsx'
import {FieldValues, PasswordElement, Path, UseFormReturn} from 'react-hook-form-mui'
import {Stack} from '@mui/material'
import {useEffect} from 'react'

export type PasswortFormPart = {
    password: string
    confirmPassword: string
}
type Form<F extends FieldValues> = PasswortFormPart & F

type Props<F extends FieldValues> = {
    formContext: UseFormReturn<Form<F>>
    horizontal?: boolean
    resetPasswort?: boolean
}

export const NewPassword = <F extends FieldValues>({formContext, ...props}: Props<F>) => {
    const minPasswordLength = 10

    const {t} = useTranslation()

    const passwordsWatch = formContext.watch([
        'password' as Path<Form<F>>,
        'confirmPassword' as Path<Form<F>>,
    ])

    useEffect(() => {
        if (formContext.formState.isSubmitted) {
            if (passwordsWatch[0] !== passwordsWatch[1]) {
                formContext.setError('root.password', {
                    type: 'validate',
                    message: t('user.registration.password.notMatching'),
                })
                formContext.setError('root.confirmPassword', {
                    type: 'validate',
                    message: t('user.registration.password.notMatching'),
                })
            } else {
                formContext.clearErrors('root.password')
                formContext.clearErrors('root.confirmPassword')
            }

            formContext
                .trigger(['password' as Path<Form<F>>]) // todo: Better way to do this than cast?
                .catch(error => console.error(error))
            formContext
                .trigger(['confirmPassword' as Path<Form<F>>])
                .catch(error => console.error(error))
        }
    }, [passwordsWatch])

    return (
        <Stack direction={props.horizontal ? "row" : "column"} spacing={props.horizontal ? 2 : 4}>
            <FormInputPassword
                name={'password'}
                label={props.resetPasswort ? t('user.resetPassword.newPassword') : t('user.password')}
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
                            return t('user.registration.password.notMatching')
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
                            return t('user.registration.password.notMatching')
                        }
                    },
                }}
                sx={{flex: 1}}
            />
        </Stack>
    )
}
