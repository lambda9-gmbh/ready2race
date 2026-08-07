import {Alert, Box, Divider, Stack, Typography} from '@mui/material'
import {FormContainer, useForm, useWatch} from 'react-hook-form-mui'
import {Trans, useTranslation} from 'react-i18next'
import {useState} from 'react'
import {eventRoute} from '@routes'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getEventTimingConfig, updateEventTimingConfig} from '@api/sdk.gen.ts'
import {CompetitionTimingDeviationDto} from '@api/types.gen.ts'
import InlineLink from '@components/InlineLink.tsx'
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
 * Was an einem Wettkampf abweicht — jedes gesetzte Feld einzeln, weil ein Teil-Override („erbt das
 * System, hat aber ein eigenes Läufe-Rennen") die häufigste und die am leichtesten zu übersehende
 * Abweichung ist. Ausgeschriebene Schlüssel, damit i18n typgeprüft bleibt.
 */
const describeDeviation = (deviation: CompetitionTimingDeviationDto) =>
    [
        deviation.timingSystem ? ('event.timing.deviations.system' as const) : null,
        deviation.timeTrialResultsUrl ? ('event.timing.deviations.timeTrialUrl' as const) : null,
        deviation.heatsResultsUrl ? ('event.timing.deviations.heatsUrl' as const) : null,
    ].filter(key => key !== null)

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

    // Die Abweichungen stehen bewusst außerhalb des Formulars: sie werden hier nicht bearbeitet,
    // sondern nur gezeigt. Nach dem Speichern neu geladen, weil ein Wettkampf durch eine geänderte
    // Voreinstellung zur Abweichung werden kann, ohne dass ihn jemand angefasst hat.
    const [deviations, setDeviations] = useState<CompetitionTimingDeviationDto[]>([])
    const [lastSaved, setLastSaved] = useState(0)

    useFetch(signal => getEventTimingConfig({signal, path: {eventId}}), {
        onResponse: ({data, error}) => {
            if (error) {
                feedback.error(t('common.error.unexpected'))
            } else if (data) {
                formContext.reset(mapDtoToEventTimingForm(data))
                setDeviations(data.deviatingCompetitions ?? [])
            }
        },
        deps: [eventId, lastSaved],
    })

    const timingSystem = useWatch({control: formContext.control, name: 'timingSystem'})

    return (
        // Kein Card-Rahmen: die Nachbarn im Einstellungen-Tab (Dokumente, Teilnahmebedingungen)
        // sind blanke Abschnitte mit h2-Überschrift und Hinweistext darunter — siehe EntityTable.
        // Eine Karte dazwischen sähe aus wie ein Fremdkörper aus einem anderen Bildschirm.
        <Box id={'timing'}>
            <Typography variant={'h2'}>{t('event.timing.title')}</Typography>
            <Box sx={{color: 'text.secondary'}}>
                <Trans i18nKey={'event.timing.hint'} />
            </Box>
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
                        setLastSaved(Date.now())
                    }
                }}>
                <Stack spacing={4} sx={{maxWidth: 720, pt: 2}}>
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

                    {/* Die Reichweite dieser Voreinstellung: welche Wettkämpfe ihr nicht folgen.
                        Ohne diese Liste ändert man hier eine Adresse und merkt erst am Renntag,
                        dass drei Wettkämpfe weiterhin ins alte Rennen zeigen. */}
                    <Divider />
                    <Box>
                        <Typography variant={'subtitle2'} gutterBottom>
                            <Trans i18nKey={'event.timing.deviations.title'} />
                        </Typography>
                        {deviations.length === 0 ? (
                            <Typography variant={'body2'} color={'text.secondary'}>
                                <Trans i18nKey={'event.timing.deviations.none'} />
                            </Typography>
                        ) : (
                            <Stack spacing={1}>
                                {deviations.map(deviation => (
                                    <Box key={deviation.competitionId}>
                                        <InlineLink
                                            to={'/event/$eventId/competition/$competitionId'}
                                            params={{
                                                eventId,
                                                competitionId: deviation.competitionId,
                                            }}
                                            search={{tab: 'timing'}}>
                                            {deviation.identifier} {deviation.name}
                                        </InlineLink>
                                        <Typography variant={'body2'} color={'text.secondary'}>
                                            {describeDeviation(deviation)
                                                .map(key => t(key))
                                                .join(', ')}
                                        </Typography>
                                    </Box>
                                ))}
                            </Stack>
                        )}
                    </Box>
                </Stack>
            </FormContainer>
        </Box>
    )
}

export default EventTimingConfig
