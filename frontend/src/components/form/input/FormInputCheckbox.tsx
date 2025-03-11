import {useTranslation} from 'react-i18next'
import {CheckboxElement, CheckboxElementProps} from 'react-hook-form-mui'
import FormInputLabel from './FormInputLabel.tsx'
import {RefAttributes} from 'react'

export type BaseFormInputProps = CheckboxElementProps & RefAttributes<HTMLDivElement>

export const FormInputCheckbox = (props: BaseFormInputProps) => {
    const {t} = useTranslation()

    return (
        <FormInputLabel
            label={props.label}
            required={props.required === true || props.rules?.required !== undefined}>
            <CheckboxElement
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
