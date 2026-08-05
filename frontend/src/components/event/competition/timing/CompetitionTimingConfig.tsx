import {Alert, AlertTitle, Box, Card, Stack, Typography} from '@mui/material'
import {FormContainer, useForm, useWatch} from 'react-hook-form-mui'
import {Trans, useTranslation} from 'react-i18next'
import {useState} from 'react'
import {competitionRoute, eventRoute} from '@routes'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {
    getMatchResultImportConfigs,
    getStartListConfigs,
    getTimingConfig,
    updateTimingConfig,
} from '@api/sdk.gen.ts'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {FormInputRadioButtonGroup} from '@components/form/input/FormInputRadioButtonGroup.tsx'
import FormInputAutocomplete from '@components/form/input/FormInputAutocomplete.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import InlineLink from '@components/InlineLink.tsx'
import {
    emptyTimingForm,
    mapDtoToTimingForm,
    mapTimingFormToRequest,
    TimingForm,
    timingConfigWarnings,
} from './timingConfigForm.ts'

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

    useFetch(signal => getTimingConfig({signal, path: {eventId, competitionId}}), {
        onResponse: ({data, error}) => {
            if (error) {
                feedback.error(t('common.error.unexpected'))
            } else if (data) {
                formContext.reset(mapDtoToTimingForm(data))
            }
        },
        deps: [eventId, competitionId],
    })

    const timingSystem = useWatch({control: formContext.control, name: 'timingSystem'})
    const warnings = timingConfigWarnings(formContext.watch())

    return (
        <Card sx={{p: 3, maxWidth: 720}}>
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
                    <FormInputRadioButtonGroup
                        name={'timingSystem'}
                        label={t('event.competition.timing.system')}
                        row
                        options={[
                            {id: 'NONE', label: t('event.competition.timing.systems.none')},
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

                    {timingSystem === 'RACECLOCKER' && (
                        <Stack spacing={4}>
                            <Alert variant={'outlined'} severity={'info'}>
                                <Trans i18nKey={'event.competition.timing.raceclockerHint'} />
                            </Alert>
                            <FormInputText
                                name={'timeTrialResultsUrl'}
                                label={t('event.competition.timing.timeTrialUrl')}
                            />
                            <FormInputText
                                name={'heatsResultsUrl'}
                                label={t('event.competition.timing.heatsUrl')}
                            />
                        </Stack>
                    )}

                    {timingSystem !== 'NONE' && (
                        <Stack spacing={4}>
                            {timingSystem === 'RACECLOCKER' && (
                                <FormInputAutocomplete
                                    name={'startlistConfigQualification'}
                                    options={startListConfigs ?? []}
                                    loading={startListConfigsPending}
                                    label={t('event.competition.timing.startlistQualification')}
                                />
                            )}
                            <FormInputAutocomplete
                                name={'startlistConfigRounds'}
                                options={startListConfigs ?? []}
                                loading={startListConfigsPending}
                                label={t(
                                    timingSystem === 'RACECLOCKER'
                                        ? 'event.competition.timing.startlistRounds'
                                        : 'event.competition.timing.startlist',
                                )}
                            />
                            {/* Die RaceClocker-Presets exportieren ohne Kopfzeile, weil RaceClocker
                                eine solche Zeile als Teilnehmer importiert. Der Spaltenmapper zeigt
                                dann Positionen statt Namen, was leicht unbemerkt schiefgeht. */}
                            <Alert variant={'outlined'} severity={'info'}>
                                <Trans i18nKey={'event.competition.timing.importHint'} />
                            </Alert>
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
        </Card>
    )
}

export default CompetitionTimingConfig
