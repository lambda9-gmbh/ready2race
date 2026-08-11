import {useCallback} from 'react'
import {useForm} from 'react-hook-form-mui'
import {useTranslation} from 'react-i18next'
import {Stack} from '@mui/material'
import {BaseEntityDialogProps} from '@utils/types.ts'
import EntityDialog from '@components/EntityDialog.tsx'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputSwitch from '@components/form/input/FormInputSwitch.tsx'
import {useFeedback} from '@utils/hooks.ts'
import {addRaceClockerRace, updateRaceClockerRace} from '@api/sdk.gen.ts'
import {ApiError, RaceClockerRaceDto} from '@api/types.gen.ts'

type Form = {
    name: string
    resultsUrl: string
    capturesLaps: boolean
}

const defaultValues: Form = {
    name: '',
    resultsUrl: '',
    capturesLaps: false,
}

const mapFormToRequest = (form: Form) => ({
    name: form.name.trim(),
    resultsUrl: form.resultsUrl.trim(),
    capturesLaps: form.capturesLaps,
})

const mapDtoToForm = (dto: RaceClockerRaceDto): Form => ({
    name: dto.name,
    resultsUrl: dto.resultsUrl,
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
    const feedback = useFeedback()
    const formContext = useForm<Form>()

    /**
     * Die drei Fehler, die beim Anlegen wirklich vorkommen, beim Namen nennen.
     *
     * Ohne das liefe jeder Fehlschlag in die Sammelmeldung von [EntityDialog] — und am Renntag ist
     * der wahrscheinlichste Fehlgriff eine eingefügte Adresse, die gar nicht zu RaceClocker gehört.
     * „Konnte nicht angelegt werden" hilft dann niemandem weiter.
     *
     * Rückgabewert `true` heißt: behandelt, die Sammelmeldung unterbleibt.
     */
    const showKnownError = (error: ApiError): boolean => {
        switch (error.errorCode) {
            case 'RACECLOCKER_RACE_NAME_TAKEN':
                feedback.error(t('event.timing.races.nameTaken'))
                return true
            case 'RACECLOCKER_RACE_URL_TAKEN':
                feedback.error(t('event.timing.races.urlTaken'))
                return true
            case 'RACECLOCKER_URL_INVALID':
                feedback.error(t('event.timing.races.invalidUrl'))
                return true
            default:
                return false
        }
    }

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
    }, [props.entity])

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            onAddError={showKnownError}
            onEditError={showKnownError}
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
