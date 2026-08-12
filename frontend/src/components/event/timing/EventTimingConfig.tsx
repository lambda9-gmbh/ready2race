import {Alert, Box, Button, Divider, IconButton, Stack, Typography} from '@mui/material'
import {Add, Delete, Edit} from '@mui/icons-material'
import {FormContainer, useForm, useWatch} from 'react-hook-form-mui'
import {Trans, useTranslation} from 'react-i18next'
import {useState} from 'react'
import {eventRoute} from '@routes'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {
    deleteRaceClockerRace,
    getEventTimingConfig,
    getMatchResultImportConfigs,
    getRaceClockerRaces,
    getStartListConfigs,
    updateEventTimingConfig,
} from '@api/sdk.gen.ts'
import {CompetitionTimingDeviationDto, RaceClockerRaceDto} from '@api/types.gen.ts'
import RaceClockerRaceDialog from './RaceClockerRaceDialog.tsx'
import RaceClockerRaceAssignments from './RaceClockerRaceAssignments.tsx'
import InlineLink from '@components/InlineLink.tsx'
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
 * System, hat aber ein eigenes Startlisten-Format") die am leichtesten zu übersehende Abweichung
 * ist. Die RaceClocker-Rennen stehen bewusst nicht mehr hier: sie werden pro Wettkampf zugewiesen
 * (RaceClockerRaceAssignments) und haben keine Veranstaltungs-Voreinstellung, von der man abweichen
 * könnte.
 *
 * Die `as never`-Casts sind der Preis dafür, dass die Schlüssel hier zusammengesetzt und nicht als
 * Literale stehen, die der i18n-Typ prüfen könnte.
 */
const describeDeviation = (
    deviation: CompetitionTimingDeviationDto,
    t: (key: never, options?: object) => string,
) =>
    [
        deviation.timingSystem ? t('event.timing.deviations.system' as never) : null,
        deviation.startlistConfig ? t('event.timing.deviations.startlist' as never) : null,
        deviation.resultImportConfig ? t('event.timing.deviations.resultImport' as never) : null,
    ].filter(text => text !== null)

/**
 * Die Zeitnahme-Voreinstellung einer Veranstaltung: Zeitnahme-System, Startlisten-Export und
 * Ergebnis-Import, gemeinsam für alle Wettkämpfe.
 *
 * Die RaceClocker-Rennen werden hier angelegt, aber nicht mehr voreingestellt — welcher Wettkampf
 * in welches Rennen exportiert, wird pro Rennen angehakt (RaceClockerRaceAssignments), weil die
 * umgekehrte Pflege bequemer ist als sich durch jeden Wettkampf zu klicken. Der Zeitnahme-Tab des
 * Wettkampfs bleibt als gezielter Override für System und Formate erhalten.
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

    // Die Rennen der Veranstaltung. Sie stehen außerhalb des Formulars, weil sie über eigene
    // Endpunkte gepflegt werden — ein Rennen anzulegen ist kein Teil des Speicherns dieser Seite.
    const [racesReloaded, setRacesReloaded] = useState(0)
    const [raceDialogOpen, setRaceDialogOpen] = useState(false)
    const [editedRace, setEditedRace] = useState<RaceClockerRaceDto | undefined>(undefined)

    const {data: races, pending: racesPending} = useFetch(
        signal => getRaceClockerRaces({signal, path: {eventId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(t('common.error.unexpected'))
                }
            },
            deps: [eventId, racesReloaded],
        },
    )

    const removeRace = async (race: RaceClockerRaceDto) => {
        if (!confirm(t('event.timing.races.deleteConfirm', {name: race.name}))) return

        const {error} = await deleteRaceClockerRace({
            path: {eventId, raceId: race.id},
        })
        if (error) {
            // Ein Rennen, das noch einem Wettkampf zugewiesen ist, lässt sich nicht löschen — die
            // Zuordnung muss erst am Rennen abgehakt werden (RaceClockerRaceAssignments unten).
            feedback.error(t('common.error.unexpected'))
        } else {
            feedback.success(t('event.timing.races.deleted'))
            setRacesReloaded(Date.now())
        }
    }

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
                            <Box>
                                <Typography variant={'subtitle2'} gutterBottom>
                                    <Trans i18nKey={'event.timing.races.title'} />
                                </Typography>
                                <Typography variant={'body2'} color={'text.secondary'}>
                                    <Trans i18nKey={'event.timing.races.hint'} />
                                </Typography>
                                <Stack spacing={1} sx={{mt: 2}}>
                                    {(races ?? []).length === 0 && !racesPending && (
                                        <Typography variant={'body2'} color={'text.secondary'}>
                                            <Trans i18nKey={'event.timing.races.none'} />
                                        </Typography>
                                    )}
                                    {(races ?? []).map(race => (
                                        <Stack
                                            key={race.id}
                                            direction={'row'}
                                            spacing={1}
                                            alignItems={'center'}>
                                            <Box sx={{flexGrow: 1, minWidth: 0}}>
                                                <Typography variant={'body2'}>
                                                    {race.name}
                                                    {race.capturesLaps &&
                                                        ` · ${t('event.timing.races.capturesLaps')}`}
                                                </Typography>
                                                <Typography
                                                    variant={'body2'}
                                                    color={'text.secondary'}
                                                    sx={{wordBreak: 'break-all'}}>
                                                    {race.resultsUrl}
                                                </Typography>
                                            </Box>
                                            <IconButton
                                                aria-label={t('event.timing.races.edit')}
                                                onClick={() => {
                                                    setEditedRace(race)
                                                    setRaceDialogOpen(true)
                                                }}>
                                                <Edit fontSize={'small'} />
                                            </IconButton>
                                            <IconButton
                                                aria-label={t('common.delete')}
                                                onClick={() => removeRace(race)}>
                                                <Delete fontSize={'small'} />
                                            </IconButton>
                                        </Stack>
                                    ))}
                                </Stack>
                                <Button
                                    startIcon={<Add />}
                                    sx={{mt: 1}}
                                    onClick={() => {
                                        setEditedRace(undefined)
                                        setRaceDialogOpen(true)
                                    }}>
                                    <Trans i18nKey={'event.timing.races.add'} />
                                </Button>
                            </Box>

                            {/* Umgedrehte Zuordnung: am Rennen die Wettkämpfe anhaken, statt sich
                                durch jeden Wettkampf zu klicken. Die Komponente lädt ihre Daten
                                selbst neu; die Abweichungsliste unten ist nur informativ und zieht
                                beim nächsten Speichern/Neuladen nach — bewusst kein Formular-Reset
                                hier, der ungespeicherte Eingaben verwerfen würde. */}
                            {(races ?? []).length > 0 && (
                                <RaceClockerRaceAssignments
                                    eventId={eventId}
                                    races={races ?? []}
                                />
                            )}

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
                            <FormInputAutocomplete
                                name={'startlistConfig'}
                                options={startListConfigs ?? []}
                                loading={startListConfigsPending}
                                label={t('event.timing.startlist')}
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

                    {/* Die Reichweite dieser Voreinstellung: welche Wettkämpfe ihr bei System oder
                        Dateiformat nicht folgen. Ohne diese Liste ändert man hier ein Format und
                        merkt erst am Renntag, dass drei Wettkämpfe ein eigenes gesetzt haben. */}
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
                                            {describeDeviation(deviation, t).join(', ')}
                                        </Typography>
                                    </Box>
                                ))}
                            </Stack>
                        )}
                    </Box>
                </Stack>
            </FormContainer>

            <RaceClockerRaceDialog
                eventId={eventId}
                entityName={t('event.timing.races.title')}
                dialogIsOpen={raceDialogOpen}
                closeDialog={() => setRaceDialogOpen(false)}
                reloadData={() => setRacesReloaded(Date.now())}
                entity={editedRace}
            />
        </Box>
    )
}

export default EventTimingConfig
