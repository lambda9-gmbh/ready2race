import {useTranslation} from 'react-i18next'
import FormInputLabel from './FormInputLabel.tsx'
import {ToggleButtonGroupElement, ToggleButtonGroupElementProps} from 'react-hook-form-mui'

export const FormInputToggleButtonGroup = (props: ToggleButtonGroupElementProps) => {
    const {t} = useTranslation()

    return (
        <FormInputLabel
            label={props.label}
            required={props.required === true || props.rules?.required !== undefined}>
            <ToggleButtonGroupElement
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
