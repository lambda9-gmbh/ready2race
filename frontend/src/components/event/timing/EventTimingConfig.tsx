import {Alert, Box, Card, Stack, Typography} from '@mui/material'
import {FormContainer, useForm, useWatch} from 'react-hook-form-mui'
import {Trans, useTranslation} from 'react-i18next'
import {useState} from 'react'
import {eventRoute} from '@routes'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getEventTimingConfig, updateEventTimingConfig} from '@api/sdk.gen.ts'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {FormInputRadioButtonGroup} from '@components/form/input/FormInputRadioButtonGroup.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {
    emptyEventTimingForm,
    EventTimingForm,
    mapDtoToEventTimingForm,
    mapEventTimingFormToRequest,
} from './eventTimingConfigForm.ts'

/**
 * Die Zeitnahme-Voreinstellung einer Veranstaltung: ein RaceClocker-Rennen für die Zeitfahren und
 * eines für die Läufe, gemeinsam für alle Wettkämpfe.
 *
 * Sie steht hier und nicht im Wettkampf, weil die Rennen im Fremdsystem pro Veranstaltung angelegt
 * werden — dieselben zwei Adressen in zwanzig Wettkämpfen zu pflegen hieße, zwanzig Gelegenheiten
 * für einen Tippfehler zu schaffen, der erst am Renntag auffällt. Der Zeitnahme-Tab des Wettkampfs
 * bleibt als gezielter Override erhalten.
 */
const EventTimingConfig = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()

    const [submitting, setSubmitting] = useState(false)

    const formContext = useForm<EventTimingForm>({defaultValues: emptyEventTimingForm})

    useFetch(signal => getEventTimingConfig({signal, path: {eventId}}), {
        onResponse: ({data, error}) => {
            if (error) {
                feedback.error(t('common.error.unexpected'))
            } else if (data) {
                formContext.reset(mapDtoToEventTimingForm(data))
            }
        },
        deps: [eventId],
    })

    const timingSystem = useWatch({control: formContext.control, name: 'timingSystem'})

    return (
        <Card sx={{p: 3, maxWidth: 720}}>
            <FormContainer
                formContext={formContext}
                onSuccess={async (data: EventTimingForm) => {
                    setSubmitting(true)
                    const {error} = await updateEventTimingConfig({
                        path: {eventId},
                        body: mapEventTimingFormToRequest(data),
                    })
                    setSubmitting(false)

                    if (error) {
                        feedback.error(
                            error.status.value === 422
                                ? t('event.timing.invalid')
                                : t('common.error.unexpected'),
                        )
                    } else {
                        feedback.success(t('event.timing.saved'))
                    }
                }}>
                <Stack spacing={4}>
                    <Box>
                        <Typography variant={'h6'}>{t('event.timing.title')}</Typography>
                        <Typography variant={'body2'} color={'text.secondary'}>
                            <Trans i18nKey={'event.timing.hint'} />
                        </Typography>
                    </Box>

                    <FormInputRadioButtonGroup
                        name={'timingSystem'}
                        label={t('event.timing.system')}
                        row
                        options={[
                            {id: 'NONE', label: t('event.timing.systems.none')},
                            {id: 'RACECLOCKER', label: t('event.timing.systems.raceclocker')},
                            {id: 'WEBSCORER', label: t('event.timing.systems.webscorer')},
                        ]}
                    />

                    {timingSystem === 'RACECLOCKER' && (
                        <Stack spacing={4}>
                            <Alert variant={'outlined'} severity={'info'}>
                                <Trans i18nKey={'event.timing.raceclockerHint'} />
                            </Alert>
                            <FormInputText
                                name={'timeTrialResultsUrl'}
                                label={t('event.timing.timeTrialUrl')}
                            />
                            <FormInputText
                                name={'heatsResultsUrl'}
                                label={t('event.timing.heatsUrl')}
                            />
                        </Stack>
                    )}

                    <Box>
                        <SubmitButton submitting={submitting}>
                            <Trans i18nKey={'common.save'} />
                        </SubmitButton>
                    </Box>
                </Stack>
            </FormContainer>
        </Card>
    )
}

export default EventTimingConfig
