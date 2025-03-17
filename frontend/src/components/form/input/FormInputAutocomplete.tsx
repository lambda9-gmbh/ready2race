import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {AutocompleteElement, AutocompleteElementProps} from 'react-hook-form-mui'
import {RefAttributes} from 'react'
import {useTranslation} from 'react-i18next'

type FormInputAutocompleteProps = AutocompleteElementProps & RefAttributes<HTMLDivElement>

const FormInputAutocomplete = (props: FormInputAutocompleteProps) => {
    const {t} = useTranslation()

    return (
        <FormInputLabel
            label={props.label}
            required={props.required === true || props.rules?.required !== undefined}>
            <AutocompleteElement
                {...props}
                rules={{
                    ...props.rules,
                    ...(props.required &&
                        !props.rules?.required && {required: t('common.form.required')}),
                }}
                label={null}
            />
        </FormInputLabel>
    )
}

export default FormInputAutocomplete
