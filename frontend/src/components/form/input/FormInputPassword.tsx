import {PasswordElement, PasswordElementProps} from 'react-hook-form-mui'
import {RefAttributes} from 'react'
import {useTranslation} from 'react-i18next'
import FormInputLabel from './FormInputLabel.tsx'

type FormInputPasswordProps = Omit<PasswordElementProps & RefAttributes<HTMLDivElement>, 'type'>

const FormInputPassword = (props: FormInputPasswordProps) => {
    const {t} = useTranslation()

    return (
        <FormInputLabel
            label={props.label}
            required={props.required === true || props.rules?.required !== undefined}>
            <PasswordElement
                {...props}
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

export default FormInputPassword
