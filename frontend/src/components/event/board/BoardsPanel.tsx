import {useState} from 'react'
import {
    Box,
    Button,
    Card,
    CardActions,
    CardContent,
    IconButton,
    Stack,
    Tooltip,
    Typography,
} from '@mui/material'
import {
    Add as AddIcon,
    ContentCopy as ContentCopyIcon,
    Delete as DeleteIcon,
    Edit as EditIcon,
    OpenInNew as OpenInNewIcon,
} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext'
import {useUser} from '@contexts/user/UserContext.ts'
import {updateBoardGlobal, updateEventGlobal} from '@authorization/privileges.ts'
import BaseDialog from '@components/BaseDialog'
import {createBoard, deleteBoard, getBoards, updateBoard} from '@api/sdk.gen'
import {BoardDto, BoardRequest} from '@api/types.gen'
import BoardEditor from './BoardEditor'

interface BoardsPanelProps {
    eventId: string
}

/**
 * Die Verwaltung der Boards eines Events: Liste, Anlegen, Bearbeiten, Löschen — und je
 * Board die öffentliche URL, die ein montierter Bildschirm lädt.
 */
const BoardsPanel = ({eventId}: BoardsPanelProps) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const {confirmAction} = useConfirmation()
    const user = useUser()

    // Lesen darf, wer die Seite erreicht (READ BOARD oder READ EVENT); schreiben nur, wer eines
    // der beiden Änderungsrechte hat. Ohne diese Prüfung stünden die Knöpfe auch für eine reine
    // Leserolle da und liefen beim Klick in den 403 des Servers.
    const canEdit =
        user.checkPrivilege(updateBoardGlobal) || user.checkPrivilege(updateEventGlobal)

    const [reloadKey, setReloadKey] = useState(0)
    const [editorOpen, setEditorOpen] = useState(false)
    const [editingBoard, setEditingBoard] = useState<BoardDto | null>(null)

    const {data: boards} = useFetch(signal => getBoards({signal, path: {eventId}}), {
        deps: [eventId, reloadKey],
    })

    const publicUrl = (board: BoardDto) =>
        `${window.location.origin}/board/${eventId}/${board.id}`

    const layoutLabel = (board: BoardDto) =>
        t('event.boards.columnCount', {count: board.config.columns ?? 3})

    // Den 422-Grund des Servers zeigen, wenn vorhanden ("layout … expects N tiles",
    // "TEXT needs text"). Der generierte Fehlertyp kennt `details` nicht, deshalb die
    // Prüfung über unknown.
    const showSaveError = (error: unknown) => {
        const details = (error as {details?: unknown}).details
        const reason =
            details !== null &&
            typeof details === 'object' &&
            'reason' in details &&
            typeof (details as {reason?: unknown}).reason === 'string'
                ? (details as {reason: string}).reason
                : undefined
        feedback.error(reason ?? t('common.error.unexpected'))
    }

    /**
     * Zwei Speichern-Wege: [stay] = false schließt wie bisher; true lässt den Editor
     * offen — für den Blick auf den zweiten Bildschirm, der die Änderung mit seinem
     * Poll binnen Sekunden nachzieht, während hier weiter justiert wird.
     *
     * Der Fetch-Client wirft NICHT bei 4xx/5xx, er gibt {error} zurück. Ein try/catch
     * fing hier deshalb nie etwas, und jeder abgelehnte Speichervorgang (z. B. eine
     * 422-Validierung wie „TEXT braucht Text") meldete trotzdem „gespeichert" — daher
     * die explizite Fehlerprüfung je Zweig.
     */
    const handleSubmit = async (request: BoardRequest, stay: boolean) => {
        if (editingBoard) {
            const {error} = await updateBoard({
                path: {eventId, boardId: editingBoard.id},
                body: request,
            })
            if (error) {
                showSaveError(error)
                return
            }
        } else {
            const {data, error} = await createBoard({path: {eventId}, body: request})
            if (error || !data) {
                showSaveError(error)
                return
            }
            // Beim Bleiben nach dem ERSTEN Speichern eines neuen Boards übernimmt der
            // Editor das angelegte Board vom Server: ab jetzt gilt der Bearbeiten-Weg
            // (update statt create) — sonst legte das zweite „Speichern" ein Duplikat
            // an. Der key-Wechsel ('new' → id) remountet den Editor mit dem
            // Server-Stand; der ist identisch mit dem gerade Gespeicherten, es geht
            // also nichts verloren.
            if (stay) setEditingBoard(data)
        }

        feedback.success(t('event.boards.saved'))
        setReloadKey(prev => prev + 1)
        if (!stay) {
            setEditorOpen(false)
            setEditingBoard(null)
        }
    }

    const handleDelete = (board: BoardDto) => {
        confirmAction(
            async () => {
                const {error} = await deleteBoard({path: {eventId, boardId: board.id}})
                if (error) {
                    feedback.error(t('common.error.unexpected'))
                    return
                }
                feedback.success(t('event.boards.deleted'))
                setReloadKey(prev => prev + 1)
            },
            {
                title: t('common.confirmDelete'),
                content: t('event.boards.confirmDelete', {name: board.name}),
                okText: t('common.delete'),
            },
        )
    }

    return (
        <Box>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{mb: 2}}>
                <Typography variant="h5">{t('event.boards.title')}</Typography>
                {canEdit && (
                    <Button
                        variant="contained"
                        startIcon={<AddIcon />}
                        onClick={() => {
                            setEditingBoard(null)
                            setEditorOpen(true)
                        }}>
                        {t('event.boards.create')}
                    </Button>
                )}
            </Stack>

            {boards && boards.length === 0 && (
                <Typography color="text.secondary" sx={{py: 4, textAlign: 'center'}}>
                    {t('event.boards.none')}
                </Typography>
            )}

            <Box
                sx={{
                    display: 'grid',
                    gap: 2,
                    gridTemplateColumns: {xs: '1fr', md: 'repeat(2, 1fr)', lg: 'repeat(3, 1fr)'},
                }}>
                {boards?.map(board => (
                    <Card key={board.id} variant="outlined">
                        <CardContent>
                            <Typography variant="h6">{board.name}</Typography>
                            <Typography variant="body2" color="text.secondary">
                                {layoutLabel(board)} ·{' '}
                                {t('event.boards.tileCount', {count: board.config.tiles.length})}
                            </Typography>
                        </CardContent>
                        <CardActions>
                            <Tooltip title={t('event.boards.open')}>
                                <IconButton
                                    size="small"
                                    component="a"
                                    href={publicUrl(board)}
                                    target="_blank"
                                    rel="noopener">
                                    <OpenInNewIcon fontSize="small" />
                                </IconButton>
                            </Tooltip>
                            <Tooltip title={t('event.boards.copyUrl')}>
                                <IconButton
                                    size="small"
                                    onClick={() => {
                                        void navigator.clipboard.writeText(publicUrl(board))
                                        feedback.success(t('event.boards.urlCopied'))
                                    }}>
                                    <ContentCopyIcon fontSize="small" />
                                </IconButton>
                            </Tooltip>
                            <Box sx={{flex: 1}} />
                            {canEdit && (
                                <>
                                    <IconButton
                                        size="small"
                                        onClick={() => {
                                            setEditingBoard(board)
                                            setEditorOpen(true)
                                        }}>
                                        <EditIcon fontSize="small" />
                                    </IconButton>
                                    <IconButton size="small" onClick={() => handleDelete(board)}>
                                        <DeleteIcon fontSize="small" />
                                    </IconButton>
                                </>
                            )}
                        </CardActions>
                    </Card>
                ))}
            </Box>

            <BaseDialog
                open={editorOpen}
                onClose={() => {
                    setEditorOpen(false)
                    setEditingBoard(null)
                }}
                maxWidth="lg">
                {/* key erzwingt frischen Editor-State je Board — sonst behielte ein
                    zweites Öffnen die Konfiguration des ersten. */}
                <BoardEditor
                    key={editingBoard?.id ?? 'new'}
                    eventId={eventId}
                    board={editingBoard}
                    onSubmit={handleSubmit}
                    onCancel={() => {
                        setEditorOpen(false)
                        setEditingBoard(null)
                    }}
                />
            </BaseDialog>
        </Box>
    )
}

export default BoardsPanel
