import {useTranslation} from 'react-i18next'
import {TextFieldElement, TextFieldElementProps} from 'react-hook-form-mui'
import FormInputLabel from './FormInputLabel.tsx'

export type BaseFormInputProps = TextFieldElementProps

export const FormInputText = (props: BaseFormInputProps) => {
    const {t} = useTranslation()

    return (
        <TextFieldElement
            {...props}
            type={'text'}
            rules={{
                ...props.rules,
                ...(props.required &&
                    !props.rules?.required && {required: t('common.form.required')}),
            }}
            label={
                <FormInputLabel
                    label={props.label}
                    required={props.required === true || props.rules?.required !== undefined}
                    optional={t('common.form.optional')}
                />
            }
        />
    )
}
