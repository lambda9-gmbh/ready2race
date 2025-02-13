import {RefAttributes} from 'react'
import {useTranslation} from 'react-i18next'
import {DateTimePickerElement, DateTimePickerElementProps} from 'react-hook-form-mui/date-pickers'

type FormInputDateTimeProps = DateTimePickerElementProps & RefAttributes<HTMLDivElement>

const FormInputDateTime = (props: FormInputDateTimeProps) => {
    const {t} = useTranslation()

    //todo: check if needed with date-fns
    /*const formContext = useFormContext()

    useEffect(() => {
        const value = formContext.getValues(props.name)
        if (value) {
            formContext.setValue(props.name, dayjs(value))
        }
    }, [])*/

    // todo? "overwriteErrorMessages" to translate the error msg: "Date is invalid" if the Input is invalid
    return (
        <DateTimePickerElement
            {...props}
            ampm={false}
            rules={{
                ...props.rules,
                ...(props.required &&
                    !props.rules?.required && {
                        required: t('common.form.required'),
                    }),
            }}
        />
    )
}
export default FormInputDateTime
