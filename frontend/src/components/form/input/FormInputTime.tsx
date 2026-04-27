import {RefAttributes, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {TimePickerElement, TimePickerElementProps} from 'react-hook-form-mui/date-pickers'
import FormInputLabel from './FormInputLabel.tsx'

type FormInputTimeProps = TimePickerElementProps & RefAttributes<HTMLDivElement>

const FormInputTime = ({sx, ...props}: FormInputTimeProps) => {
    const {t} = useTranslation()
    const [open, setOpen] = useState(false)

    // todo? "overwriteErrorMessages" to translate the error msg: "Time is invalid" if the Input is invalid
    return (
        <FormInputLabel
            label={props.label}
            required={props.required === true || props.rules?.required !== undefined}>
            <TimePickerElement
                {...props}
                ampm={false}
                rules={{
                    ...props.rules,
                    ...(props.required &&
                        !props.rules?.required && {
                            required: t('common.form.required'),
                        }),
                }}
                label={null}
                sx={{width: 1, ...sx}}
                open={open}
                onClose={() => setOpen(false)}
                inputProps={{
                    onClick: () => {
                        setOpen(true)
                    },
                }}
            />
        </FormInputLabel>
    )
}
export default FormInputTime
