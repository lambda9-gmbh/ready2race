import {
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    IconButton,
    Typography,
} from '@mui/material'
import {Close} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'
import {useFetch} from '@utils/hooks.ts'
import {getEventMatches, updateMatchRunningState} from '@api/sdk.gen.ts'
import {MatchForRunningStatusDto} from '@api/types.gen.ts'
import {useEffect, useState} from 'react'
import DragDropMatchLists from './DragDropMatchLists.tsx'
import Throbber from '@components/Throbber.tsx'

interface ManageRunningMatchesDialogProps {
    open: boolean
    onClose: () => void
    eventId: string
}

const ManageRunningMatchesDialog = ({open, onClose, eventId}: ManageRunningMatchesDialogProps) => {
    const {t} = useTranslation()
    const [runningMatches, setRunningMatches] = useState<MatchForRunningStatusDto[]>([])
    const [availableMatches, setAvailableMatches] = useState<MatchForRunningStatusDto[]>([])
    const [saving, setSaving] = useState(false)

    const {data: matches = [], pending} = useFetch(
        signal =>
            getEventMatches({
                signal,
                path: {eventId},
                query: {withoutPlaces: true},
            }),
        {
            deps: [eventId, open],
        },
    )

    useEffect(() => {
        if (matches) {
            setRunningMatches(matches.filter(m => m.currentlyRunning))
            setAvailableMatches(matches.filter(m => !m.currentlyRunning))
        }
    }, [matches])

    const handleMatchMove = async (matchId: string, toRunning: boolean) => {
        setSaving(true)
        try {
            // Find the match and its competition ID
            const allMatches = [...runningMatches, ...availableMatches]
            const match = allMatches.find(m => m.id === matchId)

            if (!match) return

            // Update the match running state
            await updateMatchRunningState({
                path: {
                    eventId,
                    competitionId: match.competitionId,
                    competitionMatchId: matchId,
                },
                body: {
                    currentlyRunning: toRunning,
                },
            })

            // Update local state
            if (toRunning) {
                const matchToMove = availableMatches.find(m => m.id === matchId)
                if (matchToMove) {
                    setAvailableMatches(availableMatches.filter(m => m.id !== matchId))
                    setRunningMatches([...runningMatches, {...matchToMove, currentlyRunning: true}])
                }
            } else {
                const matchToMove = runningMatches.find(m => m.id === matchId)
                if (matchToMove) {
                    setRunningMatches(runningMatches.filter(m => m.id !== matchId))
                    setAvailableMatches([
                        ...availableMatches,
                        {...matchToMove, currentlyRunning: false},
                    ])
                }
            }
        } catch (error) {
            console.error('Failed to update match running state:', error)
        } finally {
            setSaving(false)
        }
    }

    return (
        <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
            <DialogTitle>
                <Box display="flex" alignItems="center" justifyContent="space-between">
                    <Typography variant="h6">
                        {t('event.competition.execution.match.manageRunning')}
                    </Typography>
                    <IconButton onClick={onClose} edge="end">
                        <Close />
                    </IconButton>
                </Box>
            </DialogTitle>
            <DialogContent dividers>
                {pending ? (
                    <Box display="flex" justifyContent="center" p={4}>
                        <Throbber />
                    </Box>
                ) : (
                    <DragDropMatchLists
                        runningMatches={runningMatches}
                        availableMatches={availableMatches}
                        onMatchMove={handleMatchMove}
                        disabled={saving}
                        eventId={eventId}
                    />
                )}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} variant="outlined">
                    {t('common.close')}
                </Button>
            </DialogActions>
        </Dialog>
    )
}

export default ManageRunningMatchesDialog
