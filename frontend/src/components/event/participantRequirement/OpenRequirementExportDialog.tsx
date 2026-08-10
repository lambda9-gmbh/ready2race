import {useState} from 'react'
import {
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    MenuItem,
    Stack,
    TextField,
} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {exportOpenParticipantRequirements} from '@api/sdk.gen.ts'
import {ParticipantRequirementForEventDto} from '@api/types.gen.ts'
import {useFeedback} from '@utils/hooks.ts'
import {getFilename} from '@utils/helpers.ts'
import {eventIndexRoute} from '@routes'
import LoadingButton from '@components/form/LoadingButton.tsx'

/**
 * Auswahlwert für "alle aktiven Bedingungen". Bewusst ein eigenes Kürzel und kein leerer
 * String: MUI wertet '' als "nichts ausgewählt" und zeigt dann ein leeres Feld statt der
 * Voreinstellung. Beim Aufruf wird daraus schlicht kein Filterparameter.
 */
const ALL = 'ALL'

type Props = {
    open: boolean
    onClose: () => void
    /** Die an der Veranstaltung aktiven Bedingungen. */
    requirements: ParticipantRequirementForEventDto[]
    /** Anker für den Download, wird von der Tabelle gestellt. */
    downloadRef: React.RefObject<HTMLAnchorElement>
}

/**
 * Zieht die Liste der Gemeldeten, denen noch Bedingungen fehlen - Grundlage dafür, die
 * betroffenen Vereine anzuschreiben.
 */
const OpenRequirementExportDialog = ({open, onClose, requirements, downloadRef}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const {eventId} = eventIndexRoute.useParams()

    const [requirementId, setRequirementId] = useState<string>(ALL)
    const [pending, setPending] = useState(false)

    const handleExport = async () => {
        setPending(true)
        const {data, error, response} = await exportOpenParticipantRequirements({
            path: {eventId},
            query: requirementId === ALL ? {} : {requirementId},
        })
        setPending(false)

        const anchor = downloadRef.current

        if (error) {
            feedback.error(t('event.participantRequirement.openExport.error'))
            return
        }

        if (data !== undefined && anchor) {
            const url = URL.createObjectURL(new Blob([data]))
            anchor.href = url
            anchor.download = getFilename(response) ?? 'offene-Bedingungen.xlsx'
            anchor.click()
            anchor.href = ''
            anchor.download = ''
            URL.revokeObjectURL(url)
            onClose()
        }
    }

    return (
        <Dialog open={open} onClose={onClose} fullWidth maxWidth={'sm'}>
            <DialogTitle>{t('event.participantRequirement.openExport.title')}</DialogTitle>
            <DialogContent>
                <Stack spacing={2} sx={{pt: 1}}>
                    <DialogContentText>
                        {t('event.participantRequirement.openExport.description')}
                    </DialogContentText>
                    <TextField
                        select
                        fullWidth
                        label={t('event.participantRequirement.openExport.requirement')}
                        value={requirementId}
                        onChange={e => setRequirementId(e.target.value)}>
                        <MenuItem value={ALL}>
                            {t('event.participantRequirement.openExport.allRequirements')}
                        </MenuItem>
                        {requirements.map(r => (
                            <MenuItem key={r.id} value={r.id}>
                                {r.name}
                            </MenuItem>
                        ))}
                    </TextField>
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={pending}>
                    {t('common.cancel')}
                </Button>
                <LoadingButton variant={'contained'} pending={pending} onClick={handleExport}>
                    {t('event.participantRequirement.openExport.submit')}
                </LoadingButton>
            </DialogActions>
        </Dialog>
    )
}

export default OpenRequirementExportDialog
