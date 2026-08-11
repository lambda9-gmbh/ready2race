import {
    Alert,
    AlertTitle,
    Box,
    Divider,
    FormControlLabel,
    Stack,
    Switch,
    Typography,
} from '@mui/material'
import {FormContainer, useForm, useWatch} from 'react-hook-form-mui'
import {Trans, useTranslation} from 'react-i18next'
import {useState} from 'react'
import {competitionRoute, eventRoute} from '@routes'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {
    getMatchResultImportConfigs,
    getRaceClockerRaces,
    getStartListConfigs,
    getTimingConfig,
    updateTimingConfig,
} from '@api/sdk.gen.ts'
import {FormInputRadioButtonGroup} from '@components/form/input/FormInputRadioButtonGroup.tsx'
import FormInputAutocomplete from '@components/form/input/FormInputAutocomplete.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import InlineLink from '@components/InlineLink.tsx'
import {AutocompleteOption} from '@utils/types.ts'

/** Ein geladenes Format aus den Listen der Konfiguration — anders als [AutocompleteOption] nie null. */
type ConfigOption = {id: string; label: string}
import {
    effectiveTimingSystem,
    emptyTimingForm,
    mapDtoToTimingForm,
    mapTimingFormToRequest,
    overridesTiming,
    TimingForm,
    timingConfigWarnings,
} from './timingConfigForm.ts'

/** Ausgeschrieben statt zusammengesetzt, damit die i18n-Schlüssel typgeprüft bleiben. */
const systemLabelKeys = {
    NONE: 'event.competition.timing.systems.none',
    RACECLOCKER: 'event.competition.timing.systems.raceclocker',
    WEBSCORER: 'event.competition.timing.systems.webscorer',
} as const

/**
 * Die Zeitnahme-Einstellungen eines Wettkampfs: mit welchem Fremdsystem er arbeitet, unter welchen
 * Adressen dessen Ergebnisse liegen und mit welchen Spalten-Presets exportiert und importiert wird.
 *
 * Bewusst hier und nicht in „Wettkampf bearbeiten": jener Dialog pflegt competition_properties, die
 * laut Check-Constraint auch an einer Wettkampf-Vorlage hängen können. Diese Werte zeigen auf konkrete
 * Rennen einer konkreten Regatta und sind deshalb nie vorlagefähig.
 */
const CompetitionTimingConfig = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const [submitting, setSubmitting] = useState(false)

    const formContext = useForm<TimingForm>({defaultValues: emptyTimingForm})

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

    // Die Rennen der Veranstaltung: Der Wettkampf wählt sein eigenes Rennen daraus aus. Ein
    // Veranstaltungs-Default gibt es nicht mehr; die Zuordnung ist entweder hier oder am Rennen.
    const {data: raceOptions, pending: racesPending} = useFetch(
        signal => getRaceClockerRaces({signal, path: {eventId}}),
        {
            mapData: data => data.map(dto => ({id: dto.id, label: dto.name})),
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(t('common.error.unexpected'))
                }
            },
            deps: [eventId],
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

    // Der Schalter steht bewusst neben dem Formular statt darin: er ist keine zu speichernde
    // Einstellung, sondern die Frage „eigene Werte oder die der Veranstaltung?". Ausgeschaltet
    // bedeutet: die drei Felder sind leer, und leer heißt im Backend geerbt.
    const [override, setOverride] = useState(false)

    useFetch(signal => getTimingConfig({signal, path: {eventId, competitionId}}), {
        onResponse: ({data, error}) => {
            if (error) {
                feedback.error(t('common.error.unexpected'))
            } else if (data) {
                const form = mapDtoToTimingForm(data)
                formContext.reset(form)
                setOverride(overridesTiming(form))
            }
        },
        deps: [eventId, competitionId],
    })

    // useWatch() without `name` types its result as DeepPartialSkipArrayKey<TimingForm>; every
    // field is in fact populated because defaultValues (emptyTimingForm) already covers all of them.
    const formValues = useWatch<TimingForm>({control: formContext.control}) as TimingForm
    const warnings = timingConfigWarnings(formValues)

    // Was tatsächlich gilt: der eigene Wert, sonst die Voreinstellung der Veranstaltung. Die
    // Presets unten richten sich danach, sonst verschwände die halbe Seite, obwohl der Wettkampf
    // sehr wohl mit RaceClocker fährt.
    const effectiveSystem = effectiveTimingSystem(formValues)
    const eventSystem = formValues.eventTimingSystem

    /**
     * Die Veranstaltung liefert ihre Formate als blanke ID; den Namen dazu kennen erst die geladenen
     * Listen. Solange die noch laufen, bleibt das Feld leer statt eine UUID zu zeigen.
     */
    const configOption = (options: ConfigOption[] | null, id: string): AutocompleteOption =>
        (id && options?.find(option => option.id === id)) || null

    const configName = (options: ConfigOption[] | null, id: string) =>
        configOption(options, id)?.label || t('event.competition.timing.eventDefaults.unset')

    /**
     * Einschalten füllt alle Felder mit dem, was gerade gilt — man weicht von einem Stand ab, statt
     * vor leeren Feldern zu stehen. Ausschalten leert sie wieder; das ist die einzige Art, das Erben
     * zurückzubekommen, und der Schalter macht sie sichtbar statt sie zu verstecken.
     *
     * Die Rennen-Anwahl fasst der Schalter NICHT an — in keine Richtung. Sie ist kein Override,
     * sondern die Zuordnung dieses Wettkampfs zu einem RaceClocker-Rennen, und dieselbe Zuordnung
     * wird auch am Rennen selbst vergeben (RaceClockerRaceAssignments). Würde das Ausschalten sie
     * leeren, löschte ein Klick hier die Arbeit aus der anderen Ansicht: genau das ist am Abend des
     * 11.08.2026 passiert, und danach meldete der Tab „keine Rennen angewählt".
     */
    const toggleOverride = (checked: boolean) => {
        setOverride(checked)
        if (checked) {
            formContext.setValue(
                'timingSystem',
                eventSystem !== 'NONE' ? eventSystem : 'RACECLOCKER',
            )
            // Rennen erben nicht von der Veranstaltung; sie werden hier (oder am Rennen) gezielt
            // zugewiesen und starten deshalb leer.
            formContext.setValue(
                'startlistConfig',
                configOption(startListConfigs, formValues.eventStartlistConfig),
            )
            formContext.setValue(
                'resultImportConfig',
                configOption(importConfigs, formValues.eventResultImportConfig),
            )
        } else {
            formContext.setValue('timingSystem', 'NONE')
            formContext.setValue('startlistConfig', null)
            formContext.setValue('resultImportConfig', null)
        }
    }

    return (
        // Ohne Karte wie die Nachbar-Tabs (Durchführung, Wettbewerbsablauf): dort steht der Inhalt
        // blank auf der Seite, eine Karte nur hier sähe nach einem fremden Bildschirm aus.
        <Box sx={{maxWidth: 720}}>
            <FormContainer
                formContext={formContext}
                onSuccess={async (data: TimingForm) => {
                    setSubmitting(true)
                    const {error} = await updateTimingConfig({
                        path: {eventId, competitionId},
                        body: mapTimingFormToRequest(data),
                    })
                    setSubmitting(false)

                    if (error) {
                        feedback.error(
                            error.status.value === 422
                                ? t('event.competition.timing.invalid')
                                : t('common.error.unexpected'),
                        )
                    } else {
                        feedback.success(t('event.competition.timing.saved'))
                    }
                }}>
                <Stack spacing={4}>
                    {/* Was die Veranstaltung vorgibt — lesbar, nicht als Eingabefeld getarnt. Ein
                        editierbares Feld, dessen Inhalt in Wahrheit woanders gepflegt wird, ist
                        genau die Verwirrung, die dieser Block auflöst. */}
                    <Box>
                        <Typography variant={'subtitle2'} gutterBottom>
                            <Trans i18nKey={'event.competition.timing.eventDefaults.title'} />
                        </Typography>
                        <Typography variant={'body2'} color={'text.secondary'}>
                            {t('event.competition.timing.system')}:{' '}
                            {t(systemLabelKeys[eventSystem])}
                        </Typography>
                        {eventSystem !== 'NONE' && (
                            <>
                                <Typography variant={'body2'} color={'text.secondary'}>
                                    {t('event.competition.timing.startlist')}:{' '}
                                    {configName(startListConfigs, formValues.eventStartlistConfig)}
                                </Typography>
                                <Typography variant={'body2'} color={'text.secondary'}>
                                    {t('event.competition.timing.resultImport')}:{' '}
                                    {configName(importConfigs, formValues.eventResultImportConfig)}
                                </Typography>
                            </>
                        )}
                        <Typography variant={'body2'} sx={{mt: 1}}>
                            <InlineLink
                                to={'/event/$eventId'}
                                params={{eventId}}
                                search={{tab: 'settings'}}>
                                <Trans i18nKey={'event.competition.timing.eventDefaults.link'} />
                            </InlineLink>
                        </Typography>
                    </Box>

                    {/* Die Rennen-Anwahl steht ÜBER dem Überschreiben-Schalter und außerhalb seiner
                        Reichweite: Sie ist keine Abweichung von der Veranstaltung, sondern die
                        Zuordnung dieses Wettkampfs zu einem RaceClocker-Rennen. Dieselbe Zuordnung
                        lässt sich umgekehrt am Rennen anhaken (RaceClockerRaceAssignments) — beide
                        Wege schreiben dieselbe Spalte, deshalb darf der Schalter sie nicht
                        anfassen (Fehler vom 11.08.2026). */}
                    {effectiveSystem === 'RACECLOCKER' && (
                        <Stack spacing={4}>
                            <Box>
                                <Typography variant={'subtitle2'}>
                                    <Trans i18nKey={'event.competition.timing.raceSection.title'} />
                                </Typography>
                                <Typography variant={'body2'} color={'text.secondary'}>
                                    <Trans i18nKey={'event.competition.timing.raceclockerHint'} />
                                </Typography>
                            </Box>
                            <FormInputAutocomplete
                                name={'race'}
                                options={raceOptions ?? []}
                                loading={racesPending}
                                label={t('event.competition.timing.race')}
                            />
                            <Divider />
                        </Stack>
                    )}

                    <Box>
                        <FormControlLabel
                            control={
                                <Switch
                                    checked={override}
                                    onChange={(_, checked) => toggleOverride(checked)}
                                />
                            }
                            label={t('event.competition.timing.override.label')}
                        />
                        <Typography variant={'body2'} color={'text.secondary'}>
                            <Trans i18nKey={'event.competition.timing.override.hint'} />
                        </Typography>
                    </Box>

                    {warnings.length > 0 && (
                        <Alert variant={'outlined'} severity={'warning'}>
                            <AlertTitle>
                                <Trans i18nKey={'event.competition.timing.incomplete.title'} />
                            </AlertTitle>
                            {warnings.map(warning => (
                                <Typography key={warning}>
                                    {t(`event.competition.timing.incomplete.${warning}`)}
                                </Typography>
                            ))}
                        </Alert>
                    )}

                    {override && (
                        <FormInputRadioButtonGroup
                            name={'timingSystem'}
                            label={t('event.competition.timing.system')}
                            row
                            // Kein „nicht gesetzt": leer heißt erben, und dafür ist der Schalter da.
                            options={[
                                {
                                    id: 'RACECLOCKER',
                                    label: t('event.competition.timing.systems.raceclocker'),
                                },
                                {
                                    id: 'WEBSCORER',
                                    label: t('event.competition.timing.systems.webscorer'),
                                },
                            ]}
                        />
                    )}

                    {override && effectiveSystem !== 'NONE' && (
                        <Stack spacing={4}>
                            <Divider />
                            {/* Die Presets sind kein Override: sie hängen an den Spalten dieser
                                Startliste (Bootsklasse, Crew-Größe) und gelten immer nur für diesen
                                Wettkampf. Deshalb unterhalb des Trenners und mit eigener Überschrift,
                                damit der Schalter darüber nicht auf sie zu zielen scheint. */}
                            <Box>
                                <Typography variant={'subtitle2'}>
                                    <Trans i18nKey={'event.competition.timing.presets.title'} />
                                </Typography>
                                <Typography variant={'body2'} color={'text.secondary'}>
                                    <Trans i18nKey={'event.competition.timing.presets.hint'} />
                                </Typography>
                            </Box>
                            <FormInputAutocomplete
                                name={'startlistConfig'}
                                options={startListConfigs ?? []}
                                loading={startListConfigsPending}
                                label={t('event.competition.timing.startlist')}
                            />
                            {/* Die RaceClocker-Presets exportieren ohne Kopfzeile, weil RaceClocker
                                eine solche Zeile als Teilnehmer importiert. Der Spaltenmapper zeigt
                                dann Positionen statt Namen, was leicht unbemerkt schiefgeht.

                                Eigener Alert und nicht in raceclockerHint aufgenommen: jener erklärt
                                die beiden Ergebnis-Adressen darüber, dieser die Presets darunter.
                                Nur bei RaceClocker, weil Webscorer weder „Extra info" noch einen
                                Spaltenmapper kennt — dort zeigte der Hinweis ins Leere. */}
                            {effectiveSystem === 'RACECLOCKER' && (
                                <Alert variant={'outlined'} severity={'info'}>
                                    <Trans i18nKey={'event.competition.timing.importHint'} />
                                </Alert>
                            )}
                            <FormInputAutocomplete
                                name={'resultImportConfig'}
                                options={importConfigs ?? []}
                                loading={importConfigsPending}
                                label={t('event.competition.timing.resultImport')}
                            />
                            <Typography variant={'body2'} color={'text.secondary'}>
                                <Trans i18nKey={'event.competition.timing.presetsHint.1'} />
                                <InlineLink
                                    to={'/config'}
                                    search={{tab: 'competition-elements'}}
                                    hash={'startlists'}>
                                    <Trans i18nKey={'event.competition.timing.presetsHint.2'} />
                                </InlineLink>
                                <Trans i18nKey={'event.competition.timing.presetsHint.3'} />
                            </Typography>
                        </Stack>
                    )}

                    <Box>
                        <SubmitButton submitting={submitting}>
                            <Trans i18nKey={'common.save'} />
                        </SubmitButton>
                    </Box>
                </Stack>
            </FormContainer>
        </Box>
    )
}

export default CompetitionTimingConfig
