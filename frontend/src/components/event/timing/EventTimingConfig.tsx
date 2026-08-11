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
 * System, hat aber ein eigenes Läufe-Rennen") die häufigste und die am leichtesten zu übersehende
 * Abweichung ist.
 *
 * Liefert fertige Texte statt Schlüssel, weil zwei davon den Rennennamen einsetzen müssen. Die
 * `as never`-Casts sind der Preis dafür: Die Schlüssel stehen hier nicht mehr als Literale, die der
 * i18n-Typ prüfen könnte.
 */
const describeDeviation = (
    deviation: CompetitionTimingDeviationDto,
    t: (key: never, options?: object) => string,
) =>
    [
        deviation.timingSystem ? t('event.timing.deviations.system' as never) : null,
        // Beim Namen genannt statt nur „hat ein eigenes Rennen": Wer hier nachsieht, will wissen,
        // WOHIN der Wettkampf zeigt.
        deviation.raceQualificationName
            ? t('event.timing.deviations.raceQualification' as never, {
                  name: deviation.raceQualificationName,
              })
            : null,
        deviation.raceRoundsName
            ? t('event.timing.deviations.raceRounds' as never, {name: deviation.raceRoundsName})
            : null,
        deviation.startlistConfigQualification
            ? t('event.timing.deviations.startlistQualification' as never)
            : null,
        deviation.startlistConfigRounds
            ? t('event.timing.deviations.startlistRounds' as never)
            : null,
        deviation.resultImportConfig ? t('event.timing.deviations.resultImport' as never) : null,
    ].filter(text => text !== null)

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

    const raceOptions = (races ?? []).map(race => ({id: race.id, label: race.name}))

    const removeRace = async (race: RaceClockerRaceDto) => {
        // Wer darauf zeigt, wird beim Namen genannt. „Wettkämpfe erben danach wieder" ist wahr,
        // aber unbrauchbar, solange man nicht weiß, welche.
        const affected = deviations
            .filter(
                d =>
                    d.raceQualificationName === race.name || d.raceRoundsName === race.name,
            )
            .map(d => `${d.identifier} ${d.name}`)

        const question = [
            t('event.timing.races.deleteConfirm', {name: race.name}),
            affected.length > 0 ? affected.join(', ') : null,
        ]
            .filter(line => line !== null)
            .join('\n\n')

        if (!confirm(question)) return

        const {error} = await deleteRaceClockerRace({
            path: {eventId, raceId: race.id},
        })
        if (error) {
            feedback.error(t('common.error.unexpected'))
        } else {
            feedback.success(t('event.timing.races.deleted'))
            setRacesReloaded(Date.now())
            // Die Anwahl im Formular zeigt sonst auf ein Rennen, das es nicht mehr gibt. Gezielt
            // geleert statt das ganze Formular neu zu laden: Ein Neuladen würde jede nicht
            // gespeicherte Eingabe daneben stillschweigend verwerfen.
            if (formContext.getValues('raceQualification')?.id === race.id) {
                formContext.setValue('raceQualification', null)
            }
            if (formContext.getValues('raceRounds')?.id === race.id) {
                formContext.setValue('raceRounds', null)
            }
            // Nur die Rennen-Anwahl aus dem Eintrag nehmen, nicht den ganzen Eintrag: Ein
            // Wettkampf mit zusätzlich eigenem System oder Preset weicht weiterhin ab und darf
            // nicht aus der Liste verschwinden.
            setDeviations(current =>
                current
                    .map(d => ({
                        ...d,
                        raceQualificationName:
                            d.raceQualificationName === race.name
                                ? undefined
                                : d.raceQualificationName,
                        raceRoundsName:
                            d.raceRoundsName === race.name ? undefined : d.raceRoundsName,
                    }))
                    .filter(d => describeDeviation(d, t).length > 0),
            )
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
                                                    {t(
                                                        `event.timing.races.startModes.${race.startMode}`,
                                                    )}{' '}
                                                    · {race.resultsUrl}
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

                            <FormInputAutocomplete
                                name={'raceQualification'}
                                options={raceOptions}
                                loading={racesPending}
                                label={t('event.timing.raceQualification')}
                            />
                            <FormInputAutocomplete
                                name={'raceRounds'}
                                options={raceOptions}
                                loading={racesPending}
                                label={t('event.timing.raceRounds')}
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
