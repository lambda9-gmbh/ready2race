import {useTranslation} from 'react-i18next'
import {BaseFormInputProps, FormInputText} from '@components/form/input/FormInputText.tsx'

type FormInputEmailProps = Omit<BaseFormInputProps, 'type'>

const FormInputEmail = (props: FormInputEmailProps) => {
    const {t} = useTranslation()
    return (
        <FormInputText
            {...props}
            type={'email'}
            rules={{
                ...props.rules,
                ...(!props.rules?.pattern && {
                    pattern: {
                        value: /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/,
                        message: t('common.form.email.invalid'),
                    },
                }),
            }}
        />
    )
}

export default FormInputEmail
