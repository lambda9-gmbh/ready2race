import {useCallback} from 'react'
import {useForm} from 'react-hook-form-mui'
import {useTranslation} from 'react-i18next'
import {Stack} from '@mui/material'
import {BaseEntityDialogProps} from '@utils/types.ts'
import EntityDialog from '@components/EntityDialog.tsx'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {FormInputRadioButtonGroup} from '@components/form/input/FormInputRadioButtonGroup.tsx'
import FormInputSwitch from '@components/form/input/FormInputSwitch.tsx'
import {addRaceClockerRace, updateRaceClockerRace} from '@api/sdk.gen.ts'
import {RaceClockerRaceDto, RaceClockerStartMode} from '@api/types.gen.ts'

type Form = {
    name: string
    resultsUrl: string
    startMode: RaceClockerStartMode
    capturesLaps: boolean
}

const defaultValues: Form = {
    name: '',
    resultsUrl: '',
    // Der häufigere Fall: Ein Zeitfahren-Rennen legt eine Regatta höchstens einmal an, Läufe-Rennen
    // dagegen für jede Strecke.
    startMode: 'WAVE',
    capturesLaps: false,
}

const mapFormToRequest = (form: Form) => ({
    name: form.name.trim(),
    resultsUrl: form.resultsUrl.trim(),
    startMode: form.startMode,
    capturesLaps: form.capturesLaps,
})

const mapDtoToForm = (dto: RaceClockerRaceDto): Form => ({
    name: dto.name,
    resultsUrl: dto.resultsUrl,
    startMode: dto.startMode,
    capturesLaps: dto.capturesLaps,
})

/**
 * Anlegen und Bearbeiten eines RaceClocker-Rennens.
 *
 * Die Adresse wird serverseitig normalisiert und gegen die Host-Allowlist geprüft; hier steht
 * bewusst keine zweite Prüfung, die auseinanderlaufen könnte.
 */
const RaceClockerRaceDialog = (
    props: BaseEntityDialogProps<RaceClockerRaceDto> & {eventId: string},
) => {
    const {t} = useTranslation()
    const formContext = useForm<Form>()

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
    }, [props.entity])

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={formData =>
                addRaceClockerRace({
                    path: {eventId: props.eventId},
                    body: mapFormToRequest(formData),
                })
            }
            editAction={(formData, entity) =>
                updateRaceClockerRace({
                    path: {eventId: props.eventId, raceId: entity.id},
                    body: mapFormToRequest(formData),
                })
            }>
            <Stack spacing={4}>
                <FormInputText name={'name'} label={t('event.timing.races.name')} required />
                <FormInputText name={'resultsUrl'} label={t('event.timing.races.url')} required />
                <FormInputRadioButtonGroup
                    name={'startMode'}
                    label={t('event.timing.races.startMode')}
                    options={[
                        {
                            id: 'INDIVIDUAL',
                            label: t('event.timing.races.startModes.INDIVIDUAL'),
                        },
                        {id: 'WAVE', label: t('event.timing.races.startModes.WAVE')},
                    ]}
                />
                <FormInputSwitch
                    name={'capturesLaps'}
                    label={t('event.timing.races.capturesLaps')}
                    horizontal
                />
            </Stack>
        </EntityDialog>
    )
}

export default RaceClockerRaceDialog
