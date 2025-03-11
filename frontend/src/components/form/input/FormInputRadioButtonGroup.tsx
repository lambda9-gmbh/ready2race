import {useTranslation} from 'react-i18next'
import {RadioButtonGroup, RadioButtonGroupProps} from 'react-hook-form-mui'
import FormInputLabel from './FormInputLabel.tsx'

export const FormInputRadioButtonGroup = (props: RadioButtonGroupProps) => {
    const {t} = useTranslation()

    return (
        <FormInputLabel
            label={props.label}
            required={props.required === true || props.rules?.required !== undefined}>
            <RadioButtonGroup
                {...props}
                rules={{
                    ...props.rules,
                    ...(props.required &&
                        !props.rules?.required && {required: t('common.form.required')}),
                }}
                label={undefined}
            />
        </FormInputLabel>
    )
}
