import {useTranslation} from 'react-i18next'
import FormInputPassword from '@components/form/input/FormInputPassword.tsx'
import {FieldValues, UseFormReturn} from 'react-hook-form-mui'
import {Grid2} from '@mui/material'
import FormInputPasswordConfirm from '@components/form/input/FormInputPasswordConfirm.tsx'

export type PasswordFormPart = {
    password: string
    confirmPassword: string
}
type Form<F extends FieldValues> = PasswordFormPart & F

type Props<F extends FieldValues> = {
    formContext: UseFormReturn<Form<F>>
    horizontal?: boolean
    passwordFieldLabel?: string
}

export const NewPassword = <F extends FieldValues>({formContext, ...props}: Props<F>) => {
    const minPasswordLength = 10

    const {t} = useTranslation()

    return (
        <Grid2 container spacing={props.horizontal ? 2 : 4}>
            <Grid2 size={props.horizontal ? 6 : 12}>
                <FormInputPassword
                    name={'password'}
                    label={props.passwordFieldLabel ?? t('user.password.password')}
                    required
                    helperText={t('user.password.minLength', {
                        min: minPasswordLength,
                    })}
                    rules={{
                        minLength: {
                            value: minPasswordLength,
                            message: t('user.password.tooShort', {
                                min: minPasswordLength,
                            }),
                        },
                    }}
                />
            </Grid2>
            <Grid2 size={props.horizontal ? 6 : 12}>
                <FormInputPasswordConfirm
                    name="confirmPassword"
                    passwordFieldName="password"
                    label={t('user.password.confirm')}
                    required
                />
            </Grid2>
        </Grid2>
    )
}
