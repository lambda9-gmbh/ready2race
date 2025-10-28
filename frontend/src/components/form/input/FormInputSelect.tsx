import {SelectElement, SelectElementProps} from 'react-hook-form-mui'
import {useTranslation} from 'react-i18next'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'

export const FormInputSelect = ({sx, ...props}: SelectElementProps) => {
    const {t} = useTranslation()

    return (
        <FormInputLabel
            label={props.label}
            required={props.required === true || props.rules?.required !== undefined}>
            <SelectElement
                {...props}
                rules={{
                    ...props.rules,
                    ...(props.required &&
                        !props.rules?.required && {required: t('common.form.required')}),
                }}
                label={null}
                sx={{width: 1, ...sx}}
            />
        </FormInputLabel>
    )
}
