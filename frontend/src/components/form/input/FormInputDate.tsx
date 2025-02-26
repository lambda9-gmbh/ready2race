import {RefAttributes} from 'react'
import {useTranslation} from 'react-i18next'
import {DatePickerElement, DatePickerElementProps} from 'react-hook-form-mui/date-pickers'
import {formatISO} from 'date-fns'
import FormInputLabel from './FormInputLabel.tsx'

type FormInputDateProps = DatePickerElementProps & RefAttributes<HTMLDivElement>

const FormInputDate = (props: FormInputDateProps) => {
    const {t} = useTranslation()

    return (
        <FormInputLabel
            label={props.label}
            required={props.required === true || props.rules?.required !== undefined}>
            <DatePickerElement
                {...props}
                rules={{
                    ...props.rules,
                    ...(props.required &&
                        !props.rules?.required && {required: t('common.form.required')}),
                }}
                transform={{
                    output: value =>
                        value === null ? undefined : formatISO(value, {representation: 'date'}),
                }}
                label={null}
                sx={{width: 1}}
            />
        </FormInputLabel>
    )
}
export default FormInputDate
