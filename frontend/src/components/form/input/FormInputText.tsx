import {useTranslation} from 'react-i18next'
import {TextFieldElement, TextFieldElementProps} from 'react-hook-form-mui'

export type BaseFormInputProps = TextFieldElementProps

export const FormInputText = (props: BaseFormInputProps) => {
    const {t} = useTranslation()

    return (
        <TextFieldElement
            {...props}
            type={'text'}
            rules={{
                ...props.rules,
                ...(props.required && !props.rules?.required && {required: t('common.form.required')}),
            }}
        />
    )
}
