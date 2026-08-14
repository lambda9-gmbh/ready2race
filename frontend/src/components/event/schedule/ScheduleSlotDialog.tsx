import {useCallback, useMemo} from 'react'
import {useTranslation} from 'react-i18next'
import {useForm, useWatch} from 'react-hook-form-mui'
import {Stack, Typography} from '@mui/material'
import {format} from 'date-fns'
import EntityDialog from '@components/EntityDialog.tsx'
import {FormInputRadioButtonGroup} from '@components/form/input/FormInputRadioButtonGroup.tsx'
import {FormInputSelect} from '@components/form/input/FormInputSelect.tsx'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputDateTime from '@components/form/input/FormInputDateTime.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {createScheduleSlot, updateScheduleSlot} from '@api/sdk.gen.ts'
import {
    EventScheduleSlotDto,
    UnplannedSetupMatchDto,
    UpsertScheduleSlotRequest,
} from '@api/types.gen.ts'
import {freeSlotOptionLabel, plannableFreeSlots, slotLabel} from './common.ts'
import {ScheduleApiError, slotActionErrorText, slotActionUnexpectedKey} from './scheduleError.ts'
import {useFeedback} from '@utils/hooks.ts'

type ScheduleSlotMode = 'MATCH' | 'FREE'

// Wohin der Lauf beim "Einplanen" gesetzt wird: in einen neu angelegten Slot (NEW, der bisherige
// Weg) oder in einen schon vorhandenen freien Slot (EXISTING). Nur beim Einplanen relevant, beim
// Anlegen und Bearbeiten über den Zeitplan-Kopf gibt es die Frage nicht.
type PlanTarget = 'NEW' | 'EXISTING'

type ScheduleSlotForm = {
    mode: ScheduleSlotMode
    planTarget: PlanTarget
    targetSlotId: string
    competitionId: string
    roundName: string
    setupMatchId: string
    name: string
    startTime: string
    durationMinutes: number | null
}

type Props = {
    eventId: string
    open: boolean
    onClose: () => void
    reloadData: () => void
    unplannedSetupMatches: UnplannedSetupMatchDto[]
    /** Alle Slots des Events - Quelle der freien Slots, auf die ein Lauf gelegt werden kann. */
    slots: EventScheduleSlotDto[]
    editingSlot?: EventScheduleSlotDto
    presetMatch?: UnplannedSetupMatchDto
}

const blankValues = (
    unplannedSetupMatches: UnplannedSetupMatchDto[],
    presetMatch?: UnplannedSetupMatchDto,
): ScheduleSlotForm => ({
    mode: presetMatch || unplannedSetupMatches.length > 0 ? 'MATCH' : 'FREE',
    // Der neue Slot bleibt die Vorbelegung: Wer aus der Liste heraus einplant, hat meistens noch
    // gar keinen Zeitplan - die freien Slots aus dem Excel-Import sind der Sonderfall, nicht die
    // Regel. Ein Umschalten kostet einen Klick, ein versehentlich überschriebener Slot mehr.
    planTarget: 'NEW',
    targetSlotId: '',
    competitionId: presetMatch?.competitionId ?? '',
    roundName: presetMatch?.roundName ?? '',
    setupMatchId: presetMatch?.setupMatchId ?? '',
    name: '',
    startTime: new Date().toLocaleString(),
    durationMinutes: null,
})

// setupMatchId (nicht matchId!) trägt die Verknüpfung für den PUT-Body - matchId ist bei
// WAITING-Slots bewusst null (siehe EventScheduleService.getSchedule), setupMatchId dagegen für
// jeden Match-Slot befüllt, unabhängig von der Materialisierung.
const mapSlotToForm = (slot: EventScheduleSlotDto): ScheduleSlotForm => ({
    mode: slot.state === 'FREE' ? 'FREE' : 'MATCH',
    planTarget: 'NEW',
    targetSlotId: '',
    competitionId: slot.competitionId ?? '',
    roundName: slot.roundName ?? '',
    setupMatchId: slot.setupMatchId ?? '',
    name: slot.name ?? '',
    startTime: slot.startTime,
    durationMinutes: slot.durationMinutes ?? null,
})

const toRequest = (formData: ScheduleSlotForm): UpsertScheduleSlotRequest =>
    formData.mode === 'FREE'
        ? {
              startTime: formData.startTime,
              name: formData.name,
              durationMinutes: formData.durationMinutes,
          }
        : {
              startTime: formData.startTime,
              competitionSetupMatch: formData.setupMatchId,
              durationMinutes: formData.durationMinutes,
          }

const ScheduleSlotDialog = ({
    eventId,
    open,
    onClose,
    reloadData,
    unplannedSetupMatches,
    slots,
    editingSlot,
    presetMatch,
}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const formContext = useForm<ScheduleSlotForm>()

    const onOpen = useCallback(() => {
        formContext.reset(
            editingSlot ? mapSlotToForm(editingSlot) : blankValues(unplannedSetupMatches, presetMatch),
        )
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [editingSlot, presetMatch, unplannedSetupMatches])

    const mode = useWatch({control: formContext.control, name: 'mode'})
    const competitionId = useWatch({control: formContext.control, name: 'competitionId'})
    const roundName = useWatch({control: formContext.control, name: 'roundName'})
    const planTarget = useWatch({control: formContext.control, name: 'planTarget'})

    // Bearbeiten eines bestehenden Lauf-Slots: keine Kaskade, nur Zeit/Dauer änderbar.
    const editingMatchSlot = editingSlot !== undefined && editingSlot.state !== 'FREE'

    const freeSlots = useMemo(() => plannableFreeSlots(slots), [slots])

    // Die Wahl "neuer Slot oder freier Slot" gibt es nur beim Einplanen aus der Liste der nicht
    // verplanten Läufe (presetMatch) und nur, solange es überhaupt einen freien Slot gibt -
    // sonst bliebe eine Auswahl mit genau einer Möglichkeit stehen. [mode] gehört mit in die
    // Bedingung: Wer im offenen Dialog auf "Freier Slot" umschaltet, will einen Programmpunkt
    // anlegen und keinen bestehenden überschreiben.
    const offerFreeSlotTarget =
        presetMatch !== undefined && mode === 'MATCH' && freeSlots.length > 0
    const placeOnFreeSlot = offerFreeSlotTarget && planTarget === 'EXISTING'

    const freeSlotOptions = useMemo(
        () =>
            freeSlots.map(s => ({
                id: s.id,
                label: freeSlotOptionLabel(s, iso => format(new Date(iso), t('format.datetime'))),
            })),
        [freeSlots, t],
    )

    const competitionOptions = useMemo(() => {
        const seen = new Map<string, string>()
        unplannedSetupMatches.forEach(m => seen.set(m.competitionId, m.competitionName))
        return [...seen.entries()].map(([id, label]) => ({id, label}))
    }, [unplannedSetupMatches])

    const roundOptions = useMemo(() => {
        const rounds = new Set(
            unplannedSetupMatches
                .filter(m => m.competitionId === competitionId)
                .map(m => m.roundName),
        )
        return [...rounds].map(r => ({id: r, label: r}))
    }, [unplannedSetupMatches, competitionId])

    const matchOptions = useMemo(
        () =>
            unplannedSetupMatches
                .filter(m => m.competitionId === competitionId && m.roundName === roundName)
                .map(m => ({id: m.setupMatchId, label: m.matchName ?? m.roundName})),
        [unplannedSetupMatches, competitionId, roundName],
    )

    // "Einplanen" legt normalerweise einen Slot an - auf einen freien Slot gelegt ist es dagegen
    // ein Update dieses Slots (der Server verwandelt ihn dabei in einen Lauf-Slot, siehe
    // EventScheduleService.updateSlot). Für EntityDialog bleibt es beides Mal die Add-Aktion:
    // der Dialog trägt kein [entity], und aus Sicht des Nutzers entsteht ein Eintrag im Zeitplan.
    //
    // Der Body ist derselbe wie beim Anlegen eines Lauf-Slots: Startzeit und Dauer stehen seit der
    // Slot-Auswahl im Formular (siehe onChange unten), [name] bleibt weg - die XOR-Regel des
    // Servers (UpsertScheduleSlotRequest.validate) lässt Lauf und Name nie zusammen zu, der Name
    // des freien Slots fällt also weg. Der Slot wird bewusst NICHT noch einmal aus [freeSlots]
    // gesucht: Ist er in der Zwischenzeit verschwunden (30-Sekunden-Abgleich, andere Sitzung),
    // soll der Server ablehnen und nicht stillschweigend ein neuer Slot entstehen.
    const addAction = (formData: ScheduleSlotForm) =>
        placeOnFreeSlot
            ? updateScheduleSlot({
                  path: {eventId, slotId: formData.targetSlotId},
                  body: toRequest(formData),
              })
            : createScheduleSlot({path: {eventId}, body: toRequest(formData)})

    const editAction = (formData: ScheduleSlotForm, entity: EventScheduleSlotDto) =>
        updateScheduleSlot({path: {eventId, slotId: entity.id}, body: toRequest(formData)})

    // "Lauf ist schon verplant" ist der einzige Grund, aus dem der Server hier regelmäßig ablehnt -
    // und der einzige, bei dem der Nutzer selbst etwas tun kann (Liste neu laden, anderen Lauf
    // wählen). Alles Übrige bleibt bei der Sammelmeldung von EntityDialog (Rückgabe false).
    const handleError = (error: ScheduleApiError): boolean => {
        const {key, values} = slotActionErrorText(error)
        if (key === slotActionUnexpectedKey) {
            return false
        }
        feedback.error(t(key, values))
        return true
    }

    return (
        <EntityDialog
            entityName={t('event.schedule.slot')}
            dialogIsOpen={open}
            closeDialog={onClose}
            reloadData={reloadData}
            entity={editingSlot}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            editAction={editAction}
            onAddError={handleError}
            onEditError={handleError}>
            <Stack spacing={3}>
                {editingMatchSlot ? (
                    <Typography>{slotLabel(editingSlot)}</Typography>
                ) : (
                    !editingSlot && (
                        <FormInputRadioButtonGroup
                            name={'mode'}
                            label={t('event.schedule.slotType')}
                            row
                            options={[
                                {id: 'MATCH', label: t('event.schedule.matchSlot')},
                                {id: 'FREE', label: t('event.schedule.freeSlot')},
                            ]}
                        />
                    )
                )}
                {!editingSlot && mode === 'MATCH' && (
                    unplannedSetupMatches.length === 0 ? (
                        <Typography color={'text.secondary'}>
                            {t('event.schedule.allPlanned')}
                        </Typography>
                    ) : (
                        <>
                            <FormInputSelect
                                name={'competitionId'}
                                label={t('event.schedule.competition')}
                                required
                                options={competitionOptions}
                                onChange={() => {
                                    formContext.setValue('roundName', '')
                                    formContext.setValue('setupMatchId', '')
                                }}
                            />
                            <FormInputSelect
                                name={'roundName'}
                                label={t('event.schedule.round')}
                                required
                                disabled={!competitionId}
                                options={roundOptions}
                                onChange={() => formContext.setValue('setupMatchId', '')}
                            />
                            <FormInputSelect
                                name={'setupMatchId'}
                                label={t('event.schedule.match')}
                                required
                                disabled={!roundName}
                                options={matchOptions}
                            />
                        </>
                    )
                )}
                {offerFreeSlotTarget && (
                    <FormInputRadioButtonGroup
                        name={'planTarget'}
                        label={t('event.schedule.planTarget')}
                        row
                        options={[
                            {id: 'NEW', label: t('event.schedule.planTargetNew')},
                            {id: 'EXISTING', label: t('event.schedule.planTargetExisting')},
                        ]}
                    />
                )}
                {placeOnFreeSlot && (
                    <>
                        <FormInputSelect
                            name={'targetSlotId'}
                            label={t('event.schedule.planTargetSlot')}
                            required
                            options={freeSlotOptions}
                            // Zeit und Dauer des gewählten Slots wandern sofort ins Formular,
                            // statt sie beim Absenden noch einmal nachzuschlagen: Damit ist der
                            // Request auch dann vollständig, wenn der Slot inzwischen aus der
                            // Liste gefallen ist - und der Server lehnt ihn dann sichtbar ab.
                            onChange={(slotId: string) => {
                                const target = freeSlots.find(s => s.id === slotId)
                                if (target) {
                                    formContext.setValue('startTime', target.startTime)
                                    formContext.setValue(
                                        'durationMinutes',
                                        target.durationMinutes ?? null,
                                    )
                                }
                            }}
                        />
                        <Typography variant={'body2'} color={'text.secondary'}>
                            {t('event.schedule.planTargetHint')}
                        </Typography>
                    </>
                )}
                {((!editingSlot && mode === 'FREE') || (editingSlot && !editingMatchSlot)) && (
                    <FormInputText name={'name'} label={t('entity.name')} required />
                )}
                {/* Auf einem freien Slot stehen Startzeit und Dauer schon fest - sie noch einmal
                    einzutippen wäre nicht nur überflüssig, sondern die Gelegenheit, den Slot
                    versehentlich zu verschieben. */}
                {!placeOnFreeSlot && (
                    <>
                        <FormInputDateTime
                            required
                            name={'startTime'}
                            label={t('event.schedule.startTime')}
                        />
                        <FormInputNumber
                            name={'durationMinutes'}
                            label={t('event.schedule.duration')}
                            min={0}
                            transform={{
                                output: value =>
                                    value.target.value !== '' ? Number(value.target.value) : null,
                            }}
                        />
                    </>
                )}
            </Stack>
        </EntityDialog>
    )
}

export default ScheduleSlotDialog
