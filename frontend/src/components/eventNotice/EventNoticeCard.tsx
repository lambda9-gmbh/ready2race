import {useEffect, useState} from 'react'
import {
    Box,
    Button,
    Card,
    Stack,
    TextField,
    ToggleButton,
    ToggleButtonGroup,
    Typography,
} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {updateEventNotice} from '@api/sdk.gen.ts'
import {EventNoticeDto, EventNoticeSeverity, UpdateEventNoticeRequest} from '@api/types.gen.ts'
import {useFeedback} from '@utils/hooks.ts'
import EventNoticeBanner from './EventNoticeBanner.tsx'

type Props = {
    eventId: string
    /** Der aktuell gespeicherte Hinweis aus dem EventDto; null/undefined = keiner. */
    notice: EventNoticeDto | null | undefined
    /** Nach erfolgreichem Speichern/Entfernen — der Aufrufer lädt sein EventDto neu. */
    onChanged: () => void
}

/**
 * Pflege des veranstaltungsweiten Hinweisbanners auf der Event-Hauptseite: Text, Stufe
 * (Rot/Gelb/Grün), Speichern und Entfernen — der schmale Handgriff am Renntag, bewusst ohne
 * das große Veranstaltungsformular (eigener Endpoint PUT /event/{eventId}/notice).
 *
 * Die Vorschau über dem Formular ist derselbe Banner, den die Anzeigen rendern — was hier
 * steht, steht so auch am Ufer. Auf den Geräten erscheint eine Änderung erst nach deren
 * Poll-Takt plus kurzer Server-Zwischenspeicher (einige Sekunden bis eine Viertelminute).
 */
const EventNoticeCard = ({eventId, notice, onChanged}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [text, setText] = useState(notice?.text ?? '')
    const [severity, setSeverity] = useState<EventNoticeSeverity | null>(notice?.severity ?? null)
    const [submitting, setSubmitting] = useState(false)

    // Der gespeicherte Stand kommt asynchron (und nach jedem Reload) — das Formular zieht nach,
    // statt auf dem Stand des ersten Renderns sitzen zu bleiben.
    useEffect(() => {
        setText(notice?.text ?? '')
        setSeverity(notice?.severity ?? null)
    }, [notice])

    // Setzen schickt beide Felder, Entfernen einen leeren Rumpf — fehlende Felder sind für
    // den Server null, und beide null heißt "Banner weg" (UpdateEventNoticeRequest).
    const submit = async (body: UpdateEventNoticeRequest) => {
        setSubmitting(true)
        const {error} = await updateEventNotice({
            path: {eventId},
            body,
        })
        setSubmitting(false)
        if (error) {
            feedback.error(t('event.notice.saveError'))
        } else {
            feedback.success(
                body.text === undefined ? t('event.notice.removed') : t('event.notice.saved'),
            )
            onChanged()
        }
    }

    const canSave = text.trim().length > 0 && severity !== null

    // Die Reihenfolge Rot/Gelb/Grün ist absichtlich die der Dringlichkeit — wer in Eile
    // klickt, findet die Warnstufen vorn.
    const severities: EventNoticeSeverity[] = ['CRITICAL', 'WARNING', 'INFO']
    const severityColor = {CRITICAL: 'error', WARNING: 'warning', INFO: 'success'} as const

    return (
        <Card sx={{p: 2}}>
            <Typography variant="h6" sx={{mb: 1}}>
                {t('event.notice.title')}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{mb: 2}}>
                {t('event.notice.description')}
            </Typography>
            <Stack spacing={2}>
                {/* Dieselbe Komponente wie auf den Anzeigen — die Vorschau lügt nicht. */}
                {text.trim() && severity && (
                    <EventNoticeBanner notice={{text: text.trim(), severity}} />
                )}
                <TextField
                    label={t('event.notice.text')}
                    value={text}
                    onChange={e => setText(e.target.value)}
                    multiline
                    minRows={2}
                    fullWidth
                />
                <Box>
                    <Typography variant="body2" color="text.secondary" sx={{mb: 0.5}}>
                        {t('event.notice.severity.label')}
                    </Typography>
                    <ToggleButtonGroup
                        exclusive
                        value={severity}
                        onChange={(_, value: EventNoticeSeverity | null) => {
                            // Erneuter Klick auf die aktive Stufe liefert null — die Auswahl
                            // bleibt stehen, abgewählt wird hier nichts.
                            if (value !== null) setSeverity(value)
                        }}>
                        {severities.map(level => (
                            <ToggleButton
                                key={level}
                                value={level}
                                color={severityColor[level]}
                                size="small">
                                {t(`event.notice.severity.${level}` as never)}
                            </ToggleButton>
                        ))}
                    </ToggleButtonGroup>
                </Box>
                <Stack direction="row" spacing={2}>
                    <Button
                        variant="contained"
                        disabled={!canSave || submitting}
                        onClick={() => severity && submit({text: text.trim(), severity})}>
                        {t('common.save')}
                    </Button>
                    {/* Entfernen gibt es nur, solange serverseitig ein Hinweis steht. */}
                    {notice && (
                        <Button
                            variant="outlined"
                            color="error"
                            disabled={submitting}
                            onClick={() => submit({})}>
                            {t('event.notice.remove')}
                        </Button>
                    )}
                </Stack>
            </Stack>
        </Card>
    )
}

export default EventNoticeCard
