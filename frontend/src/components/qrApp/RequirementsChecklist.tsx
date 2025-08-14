import {
    Stack,
    Typography,
    Button,
    DialogContent,
    DialogActions
} from '@mui/material'
import {Trans, useTranslation} from 'react-i18next'
import {CheckedParticipantRequirement, ParticipantRequirementForEventDto} from '@api/types.gen.ts'
import {Block, Check, EditNote} from "@mui/icons-material";
import {useEffect, useState} from "react";
import BaseDialog from "@components/BaseDialog.tsx";
import {FormInputText} from "@components/form/input/FormInputText.tsx";
import {FormContainer, useForm} from "react-hook-form-mui";
import {SubmitButton} from "@components/form/SubmitButton.tsx";

interface RequirementsChecklistProps {
    requirements: ParticipantRequirementForEventDto[]
    checkedRequirements: CheckedParticipantRequirement[]
    pending: boolean
    onRequirementChange: (
        requirementId: string,
        checked: boolean | string,
        namedParticipantId?: string,
    ) => void
    namedParticipantIds: string[]
}

type NoteForm = {
    note: string
}

const defaultNoteValues: NoteForm = {
    note: ''
}

export const RequirementsChecklist = ({
    requirements,
    checkedRequirements,
    pending,
    onRequirementChange,
    namedParticipantIds,
}: RequirementsChecklistProps) => {
    const {t} = useTranslation()

    const [reqForNoteDialog, setReqForNoteDialog] = useState<ParticipantRequirementForEventDto | null>(null)
    const showNoteDialog = reqForNoteDialog !== null
    const closeNoteDialog = () => setReqForNoteDialog(null)

    const checkedIds = checkedRequirements.map(r => r.id)

    const openNoteDialog = (req: ParticipantRequirementForEventDto) => {
        setReqForNoteDialog(req)
    }

    const formContext = useForm<NoteForm>()

    useEffect(() => {
        if (reqForNoteDialog !== null) {
            formContext.reset(defaultNoteValues)
        }
    }, [reqForNoteDialog])

    return (
        <Stack spacing={1} width={'100%'}>
            <Typography variant="h6">
                {t('participantRequirement.participantRequirements')}
            </Typography>

            {pending && <Typography>{t('qrParticipant.loading') as string}</Typography>}

            {requirements.length === 0 && !pending && (
                <Typography>{t('qrParticipant.noRequirements') as string}</Typography>
            )}

            {requirements.map(req => {
                const checked = checkedIds.includes(req.id)
                return (
                    <Stack direction={'row'} alignItems={'center'} spacing={3}>
                        <Stack direction={'row'} spacing={2}>
                            {checked ? (
                                <>
                                    <Button variant={'outlined'} sx={{visibility: 'hidden'}}>
                                        <EditNote />
                                    </Button>
                                    <Button variant={'outlined'} sx={{color: 'red'}} disabled={pending} onClick={() => onRequirementChange(req.id, false, req.requirements?.find(npReq => namedParticipantIds.some(np => np === npReq.id))?.id)}>
                                        <Block />
                                    </Button>
                                </>
                            ) : (
                                <>
                                    <Button variant={'outlined'} onClick={() => openNoteDialog(req)}>
                                        <EditNote />
                                    </Button>
                                    <Button variant={'outlined'} sx={{color: 'green'}} disabled={pending} onClick={() => onRequirementChange(req.id, true, req.requirements?.find(npReq => namedParticipantIds.some(np => np === npReq.id))?.id)}>
                                        <Check />
                                    </Button>
                                </>
                            )}
                        </Stack>
                        <Stack direction={'row'}>
                            <Typography>
                                {req.name}
                            </Typography>
                        </Stack>
                    </Stack>
                )
            })}

            <BaseDialog open={showNoteDialog} onClose={closeNoteDialog}>
                <FormContainer formContext={formContext} onSuccess={(formData) => {
                    closeNoteDialog()
                    onRequirementChange(reqForNoteDialog!.id, formData.note, reqForNoteDialog!.requirements?.find(npReq => namedParticipantIds.some(np => np === npReq.id))?.id)
                }}>
                    <DialogContent>
                        <FormInputText name={'note'} label={t('event.participantRequirement.checkedNote')}/>
                    </DialogContent>
                    <DialogActions>
                        <Button onClick={closeNoteDialog}>
                            <Trans i18nKey={'common.cancel'} />
                        </Button>
                        <SubmitButton submitting={false}>
                            <Trans i18nKey={'event.participantRequirement.approve'} />
                        </SubmitButton>
                    </DialogActions>
                </FormContainer>
            </BaseDialog>
        </Stack>
    )
}
