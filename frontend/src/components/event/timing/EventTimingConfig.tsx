import {Alert, Box, Divider, Stack, Typography} from '@mui/material'
import {FormContainer, useForm, useWatch} from 'react-hook-form-mui'
import {Trans, useTranslation} from 'react-i18next'
import {useState} from 'react'
import {eventRoute} from '@routes'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {
    getEventTimingConfig,
    getMatchResultImportConfigs,
    getStartListConfigs,
    updateEventTimingConfig,
} from '@api/sdk.gen.ts'
import {CompetitionTimingDeviationDto} from '@api/types.gen.ts'
import InlineLink from '@components/InlineLink.tsx'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {FormInputRadioButtonGroup} from '@components/form/input/FormInputRadioButtonGroup.tsx'
import FormInputAutocomplete from '@components/form/input/FormInputAutocomplete.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import FormInputSwitch from '@components/form/input/FormInputSwitch.tsx'
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
        deviation.startlistConfigQualification
            ? ('event.timing.deviations.startlistQualification' as const)
            : null,
        deviation.startlistConfigRounds
            ? ('event.timing.deviations.startlistRounds' as const)
            : null,
        deviation.resultImportConfig ? ('event.timing.deviations.resultImport' as const) : null,
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

    const {data: startListConfigs, pending: startListConfigsPending} = useFetch(
        signal => getStartListConfigs({signal}),
        {
            mapData: data => data.data.map(dto => ({id: dto.id, label: dto.name})),
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(t('common.error.unexpected'))
                }
            },
        },
    )

    const {data: importConfigs, pending: importConfigsPending} = useFetch(
        signal => getMatchResultImportConfigs({signal}),
        {
            mapData: data => data.data.map(dto => ({id: dto.id, label: dto.name})),
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(t('common.error.unexpected'))
                }
            },
        },
    )

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
    const autoPull = useWatch({control: formContext.control, name: 'autoPull'})

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
                            <Divider />
                            <FormInputSwitch
                                name={'autoPull'}
                                label={t('event.timing.autoPull.enabled')}
                                horizontal
                            />
                            <Typography variant={'body2'} color={'text.secondary'}>
                                <Trans i18nKey={'event.timing.autoPull.hint'} />
                            </Typography>
                            {autoPull && (
                                /* Alle vier sind Pflicht: `transform.output` macht aus einem
                                   geleerten Feld null, und die vier Spalten sind im Backend nicht
                                   nullable. Ohne diese Regel schickt ein geleertes Feld ein null
                                   los, und der Bediener bekommt „Unerwarteter Fehler" zu sehen
                                   statt eines Hinweises am Feld. */
                                <Stack spacing={4}>
                                    <FormInputNumber
                                        name={'intervalActiveSeconds'}
                                        label={t('event.timing.autoPull.intervalActive')}
                                        min={2}
                                        integer
                                        rules={{required: t('common.form.required')}}
                                        transform={{
                                            output: value =>
                                                value.target.value !== ''
                                                    ? Number(value.target.value)
                                                    : null,
                                        }}
                                    />
                                    <FormInputNumber
                                        name={'intervalUpcomingSeconds'}
                                        label={t('event.timing.autoPull.intervalUpcoming')}
                                        min={2}
                                        integer
                                        rules={{required: t('common.form.required')}}
                                        transform={{
                                            output: value =>
                                                value.target.value !== ''
                                                    ? Number(value.target.value)
                                                    : null,
                                        }}
                                    />
                                    <FormInputNumber
                                        name={'watchBeforeMinutes'}
                                        label={t('event.timing.autoPull.watchBefore')}
                                        min={0}
                                        integer
                                        rules={{required: t('common.form.required')}}
                                        transform={{
                                            output: value =>
                                                value.target.value !== ''
                                                    ? Number(value.target.value)
                                                    : null,
                                        }}
                                    />
                                    <FormInputNumber
                                        name={'watchAfterMinutes'}
                                        label={t('event.timing.autoPull.watchAfter')}
                                        min={0}
                                        integer
                                        rules={{required: t('common.form.required')}}
                                        transform={{
                                            output: value =>
                                                value.target.value !== ''
                                                    ? Number(value.target.value)
                                                    : null,
                                        }}
                                    />
                                </Stack>
                            )}
                        </Stack>
                    )}

                    {/* Die beiden Dateiformate: welche Spalten exportiert und importiert werden.
                        Auch sie gelten für die ganze Veranstaltung, weil alle Wettkämpfe in dieselben
                        Rennen im Fremdsystem laufen und dort dieselbe Spaltenzuordnung brauchen. */}
                    {timingSystem !== 'NONE' && (
                        <Stack spacing={4}>
                            {timingSystem === 'RACECLOCKER' && (
                                <FormInputAutocomplete
                                    name={'startlistConfigQualification'}
                                    options={startListConfigs ?? []}
                                    loading={startListConfigsPending}
                                    label={t('event.timing.startlistQualification')}
                                />
                            )}
                            <FormInputAutocomplete
                                name={'startlistConfigRounds'}
                                options={startListConfigs ?? []}
                                loading={startListConfigsPending}
                                label={t(
                                    timingSystem === 'RACECLOCKER'
                                        ? 'event.timing.startlistRounds'
                                        : 'event.timing.startlist',
                                )}
                            />
                            <FormInputAutocomplete
                                name={'resultImportConfig'}
                                options={importConfigs ?? []}
                                loading={importConfigsPending}
                                label={t('event.timing.resultImport')}
                            />
                            <Typography variant={'body2'} color={'text.secondary'}>
                                <Trans i18nKey={'event.timing.formatsHint.1'} />
                                <InlineLink
                                    to={'/config'}
                                    search={{tab: 'competition-elements'}}
                                    hash={'startlists'}>
                                    <Trans i18nKey={'event.timing.formatsHint.2'} />
                                </InlineLink>
                                <Trans i18nKey={'event.timing.formatsHint.3'} />
                            </Typography>
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
