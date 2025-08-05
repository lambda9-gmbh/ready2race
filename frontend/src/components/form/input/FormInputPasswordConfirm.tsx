import {useTranslation} from 'react-i18next'
import {PasswordRepeatElement, PasswordRepeatElementProps} from 'react-hook-form-mui'
import {RefAttributes} from 'react'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'

type FormInputPasswordConfirmProps = Omit<
    PasswordRepeatElementProps & RefAttributes<HTMLDivElement>,
    'type'
>

const FormInputPasswordConfirm = (props: FormInputPasswordConfirmProps) => {
    const {t} = useTranslation()

    return (
        <FormInputLabel
            label={props.label}
            required={props.required === true || props.rules?.required !== undefined}>
            <PasswordRepeatElement
                {...props}
                customInvalidFieldMessage={
                    props.customInvalidFieldMessage ?? t('user.password.notMatching')
                }
                rules={{
                    ...props.rules,
                    ...(props.required &&
                        !props.rules?.required && {required: t('common.form.required')}),
                }}
                label={null}
                sx={{width: 1}}
            />
        </FormInputLabel>
    )
}

export default FormInputPasswordConfirm
