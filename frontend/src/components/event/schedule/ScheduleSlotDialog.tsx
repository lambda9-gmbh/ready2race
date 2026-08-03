import {useCallback, useMemo} from 'react'
import {useTranslation} from 'react-i18next'
import {useForm, useWatch} from 'react-hook-form-mui'
import {Stack, Typography} from '@mui/material'
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
import {slotLabel} from './common.ts'

type ScheduleSlotMode = 'MATCH' | 'FREE'

type ScheduleSlotForm = {
    mode: ScheduleSlotMode
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
    editingSlot?: EventScheduleSlotDto
    presetMatch?: UnplannedSetupMatchDto
}

const blankValues = (
    unplannedSetupMatches: UnplannedSetupMatchDto[],
    presetMatch?: UnplannedSetupMatchDto,
): ScheduleSlotForm => ({
    mode: presetMatch || unplannedSetupMatches.length > 0 ? 'MATCH' : 'FREE',
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
    editingSlot,
    presetMatch,
}: Props) => {
    const {t} = useTranslation()

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

    // Bearbeiten eines bestehenden Lauf-Slots: keine Kaskade, nur Zeit/Dauer änderbar.
    const editingMatchSlot = editingSlot !== undefined && editingSlot.state !== 'FREE'

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

    const addAction = (formData: ScheduleSlotForm) =>
        createScheduleSlot({path: {eventId}, body: toRequest(formData)})

    const editAction = (formData: ScheduleSlotForm, entity: EventScheduleSlotDto) =>
        updateScheduleSlot({path: {eventId, slotId: entity.id}, body: toRequest(formData)})

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
            editAction={editAction}>
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
                )}
                {((!editingSlot && mode === 'FREE') || (editingSlot && !editingMatchSlot)) && (
                    <FormInputText name={'name'} label={t('entity.name')} required />
                )}
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
            </Stack>
        </EntityDialog>
    )
}

export default ScheduleSlotDialog
