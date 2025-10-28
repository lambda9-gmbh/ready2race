import {MultiSelectElement, MultiSelectElementProps} from 'react-hook-form-mui'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {RefAttributes} from 'react'
import {useTranslation} from 'react-i18next'

type FormInputMultiselectProps = MultiSelectElementProps &
    RefAttributes<HTMLDivElement> & {
        fullWidth?: boolean
    }

const FormInputMultiselect = ({fullWidth, sx, ...props}: FormInputMultiselectProps) => {
    const {t} = useTranslation()

    return (
        <FormInputLabel
            label={props.label}
            required={props.required === true || props.rules?.required !== undefined}>
            <MultiSelectElement
                {...props}
                rules={{
                    ...props.rules,
                    ...(props.required &&
                        !props.rules?.required && {required: t('common.form.required')}),
                }}
                label={undefined}
                formControlProps={{
                    ...props.formControlProps,
                    sx: {
                        ...props.formControlProps?.sx,
                        ...(fullWidth && {width: 1}),
                    },
                }}
                sx={{...sx}}
            />
        </FormInputLabel>
    )
}
export default FormInputMultiselect
