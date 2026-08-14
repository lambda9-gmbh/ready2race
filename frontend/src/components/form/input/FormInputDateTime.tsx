import {RefAttributes, useMemo} from 'react'
import {useTranslation} from 'react-i18next'
import {DateTimePickerElement, DateTimePickerElementProps} from 'react-hook-form-mui/date-pickers'
import FormInputLabel from './FormInputLabel.tsx'
import {dateTimeToFormValue} from './dateTimeValue.ts'

type FormInputDateTimeProps = DateTimePickerElementProps & RefAttributes<HTMLDivElement>

// Das Feld war früher schreibgeschützt (textReadOnly) und öffnete den Picker über einen
// Klick-Umweg: ein onClick auf dem Textfeld suchte sich per querySelector('button') den
// Kalenderknopf und klickte ihn selbst an. Beides ist raus, weil es zwei Dinge kaputt machte:
//  - Tippen war unmöglich. Am Regattatag ist die Tastatur aber der schnellste Weg zu einer
//    Startzeit, und der Picker selbst rückte Minuten nur in 5er-Schritten heraus.
//  - Auf Geräten ohne feine Maus (Touchscreen, Tablet-Modus) rendert MUI die Mobil-Variante
//    des Pickers. Die hat gar keinen Kalenderknopf, der querySelector fand also nichts - und
//    weil unser onClick den Öffnen-Handler der Mobil-Variante überschrieb, passierte beim
//    Antippen des Feldes überhaupt nichts mehr. Genau so wurde es aus Edge gemeldet.
// Ohne den Umweg gilt wieder das normale Verhalten: in den Text tippen, Kalenderknopf (bzw.
// auf Touch das Feld selbst) öffnet den Picker.
const FormInputDateTime = ({sx, ...props}: FormInputDateTimeProps) => {
    const {t} = useTranslation()

    // Die Meldungen kommen aus react-hook-form-mui und sind englisch. Seit man tippen kann,
    // sind sie auch wirklich erreichbar - eine halb getippte Zeit ist ein ungültiges Datum.
    const errorMessages = useMemo(
        () => ({
            invalidDate: t('common.form.dateTime.invalid'),
            disableFuture: t('common.form.dateTime.error.disableFuture'),
            disablePast: t('common.form.dateTime.error.disablePast'),
            minDate: t('common.form.dateTime.error.minDate'),
            maxDate: t('common.form.dateTime.error.maxDate'),
            minTime: t('common.form.dateTime.error.minTime'),
            maxTime: t('common.form.dateTime.error.maxTime'),
            shouldDisableDate: t('common.form.dateTime.error.shouldDisableDate'),
            shouldDisableMonth: t('common.form.dateTime.error.shouldDisableMonth'),
            shouldDisableYear: t('common.form.dateTime.error.shouldDisableYear'),
            'shouldDisableTime-hours': t('common.form.dateTime.error.shouldDisableTimeHours'),
            'shouldDisableTime-minutes': t('common.form.dateTime.error.shouldDisableTimeMinutes'),
            'shouldDisableTime-seconds': t('common.form.dateTime.error.shouldDisableTimeSeconds'),
            minutesStep: t('common.form.dateTime.error.minutesStep'),
        }),
        [t],
    )

    return (
        <FormInputLabel
            label={props.label}
            required={props.required === true || props.rules?.required !== undefined}>
            <DateTimePickerElement
                // Die Uhr des Pickers bietet Minuten sonst nur in 5er-Schritten an. Steht
                // bewusst vor dem Spread, damit einzelne Felder gröber bleiben dürfen.
                timeSteps={{minutes: 1}}
                {...props}
                ampm={false}
                rules={{
                    ...props.rules,
                    ...(props.required &&
                        !props.rules?.required && {
                            required: t('common.form.required'),
                        }),
                }}
                overwriteErrorMessages={errorMessages}
                transform={{
                    output: dateTimeToFormValue,
                }}
                label={null}
                sx={{width: 1, ...sx}}
            />
        </FormInputLabel>
    )
}
export default FormInputDateTime
