import {useState} from 'react'
import {useTranslation} from 'react-i18next'
import {Add, ArrowDownward, ArrowUpward, Delete, ListAlt, PictureAsPdf} from '@mui/icons-material'
import {
    Box,
    IconButton,
    MenuItem,
    Paper,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableRow,
    TextField,
    Typography,
} from '@mui/material'
import {
    addExportBundleDocument,
    getDocuments,
    getExportBundle,
    removeExportBundleItem,
    reorderExportBundle,
} from '@api/sdk.gen.ts'
import {EventExportBundleItemDto} from '@api/types.gen.ts'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useUser} from '@contexts/user/UserContext.ts'
import {updateEventGlobal} from '@authorization/privileges.ts'
import {isPdfName, MoveDirection, reorderedItemIds} from './exportBundle.ts'

type Props = {
    eventId: string
    /**
     * Die Dokumentliste derselben Seite lädt nach Uploads/Löschungen neu - dieser Zähler nimmt
     * die Mappe mit: Ein gelöschtes Dokument verschwindet serverseitig auch aus der Mappe
     * (on delete cascade), das soll ohne Neuladen der Seite sichtbar sein.
     */
    lastRequested?: number
}

/** Der leere Auswahlwert des "Dokument hinzufügen"-Selects - MUI mag kein null. */
const NONE = ''

/**
 * Die Export-Mappe der Veranstaltung - die Reihenfolge, in der der PDF-Sammelexport am
 * Zeitplan-Tab Dokumente und generierte Startlisten zu EINER Datei zusammenhängt (die
 * „Regatta-Mappe", wie das handgebaute Meldeergebnis-Dokument der Vorjahre).
 *
 * Der Platzhalter „Gesammelte Startlisten" ist immer vorhanden (der Server legt ihn beim ersten
 * Laden an, Default ganz hinten) und lässt sich nur verschieben, nie löschen - abwählbar ist er
 * je Export im Export-Dialog. Angeboten werden nur PDF-Dateien: Die Dokumenttabelle kennt keinen
 * Content-Type, entschieden wird über den Dateinamen (exportBundle.ts, isPdfName); der Server
 * überspringt Nicht-PDFs beim Zusammenbau ohnehin tolerant.
 */
const ExportBundleCard = ({eventId, lastRequested}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const user = useUser()

    const mayUpdate = user.checkPrivilege(updateEventGlobal)

    const [reloadCounter, setReloadCounter] = useState(0)
    const [documentToAdd, setDocumentToAdd] = useState(NONE)

    const {data: items} = useFetch(signal => getExportBundle({signal, path: {eventId}}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(t('common.error.unexpected'))
            }
        },
        deps: [eventId, reloadCounter, lastRequested],
    })

    // Die Kandidaten fürs Hinzufügen - großzügiges Limit statt Paginierung: Es sind die
    // Dokumente EINER Veranstaltung, und ein Select mit 200 Einträgen ist das kleinere Übel
    // gegenüber einem paginierten Auswahlfeld.
    const {data: documents} = useFetch(
        signal => getDocuments({signal, path: {eventId}, query: {limit: 200}}),
        {deps: [eventId, reloadCounter, lastRequested]},
    )

    const reload = () => setReloadCounter(prev => prev + 1)

    const addable = (documents?.data ?? []).filter(
        document =>
            isPdfName(document.name) &&
            !(items ?? []).some(item => item.document === document.id),
    )

    const add = async () => {
        if (documentToAdd === NONE) return
        const {error} = await addExportBundleDocument({
            path: {eventId},
            body: {document: documentToAdd},
        })
        if (error) {
            feedback.error(t('common.error.unexpected'))
            return
        }
        setDocumentToAdd(NONE)
        reload()
    }

    const remove = async (itemId: string) => {
        const {error} = await removeExportBundleItem({path: {eventId, itemId}})
        if (error) {
            feedback.error(t('common.error.unexpected'))
            return
        }
        reload()
    }

    const move = async (item: EventExportBundleItemDto, direction: MoveDirection) => {
        const itemIds = reorderedItemIds(items ?? [], item.id, direction)
        if (!itemIds) return

        const {error} = await reorderExportBundle({path: {eventId}, body: {itemIds}})
        if (error) {
            // 409 = veralteter Stand (zweiter Tab) - neu laden zeigt die echte Reihenfolge.
            feedback.error(t('common.error.unexpected'))
        }
        reload()
    }

    const itemLabel = (item: EventExportBundleItemDto): string =>
        item.kind === 'GENERATED_STARTLISTS'
            ? t('event.document.exportBundle.startlistsPlaceholder')
            : (item.documentName ?? '')

    return (
        <Stack spacing={1.5}>
            <Stack spacing={0.5}>
                <Typography variant={'h2'}>{t('event.document.exportBundle.title')}</Typography>
                <Typography variant={'body2'} color={'text.secondary'}>
                    {t('event.document.exportBundle.hint')}
                </Typography>
            </Stack>
            <TableContainer component={Paper}>
                <Table size={'small'} sx={{minWidth: 360}}>
                    <TableBody>
                        {(items ?? []).map(item => (
                            <TableRow key={item.id}>
                                <TableCell sx={{width: 40}}>
                                    {item.kind === 'GENERATED_STARTLISTS' ? (
                                        <ListAlt fontSize={'small'} color={'primary'} />
                                    ) : (
                                        <PictureAsPdf fontSize={'small'} color={'action'} />
                                    )}
                                </TableCell>
                                <TableCell>
                                    <Typography
                                        variant={'body2'}
                                        fontStyle={
                                            item.kind === 'GENERATED_STARTLISTS'
                                                ? 'italic'
                                                : undefined
                                        }>
                                        {itemLabel(item)}
                                    </Typography>
                                </TableCell>
                                <TableCell align={'right'} sx={{whiteSpace: 'nowrap', width: 150}}>
                                    <IconButton
                                        size={'small'}
                                        disabled={
                                            !mayUpdate ||
                                            !reorderedItemIds(items ?? [], item.id, 'up')
                                        }
                                        aria-label={t('event.document.exportBundle.moveUp')}
                                        onClick={() => move(item, 'up')}>
                                        <ArrowUpward fontSize={'inherit'} />
                                    </IconButton>
                                    <IconButton
                                        size={'small'}
                                        disabled={
                                            !mayUpdate ||
                                            !reorderedItemIds(items ?? [], item.id, 'down')
                                        }
                                        aria-label={t('event.document.exportBundle.moveDown')}
                                        onClick={() => move(item, 'down')}>
                                        <ArrowDownward fontSize={'inherit'} />
                                    </IconButton>
                                    {/* Der Platzhalter ist nicht löschbar - ohne ihn hätte der
                                        Export keine Stelle für die Startlisten. */}
                                    {item.kind === 'DOCUMENT' && (
                                        <IconButton
                                            size={'small'}
                                            disabled={!mayUpdate}
                                            aria-label={t('common.delete')}
                                            onClick={() => remove(item.id)}>
                                            <Delete fontSize={'inherit'} />
                                        </IconButton>
                                    )}
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </TableContainer>
            {mayUpdate && (
                <Box>
                    <Stack direction={'row'} spacing={1} alignItems={'center'}>
                        <TextField
                            select
                            size={'small'}
                            sx={{minWidth: 280}}
                            label={t('event.document.exportBundle.addDocument')}
                            value={documentToAdd}
                            onChange={e => setDocumentToAdd(e.target.value)}>
                            {addable.length === 0 && (
                                <MenuItem value={NONE} disabled>
                                    {t('event.document.exportBundle.noAddableDocuments')}
                                </MenuItem>
                            )}
                            {addable.map(document => (
                                <MenuItem key={document.id} value={document.id}>
                                    {document.name}
                                </MenuItem>
                            ))}
                        </TextField>
                        <IconButton
                            aria-label={t('event.document.exportBundle.add')}
                            disabled={documentToAdd === NONE}
                            onClick={add}>
                            <Add />
                        </IconButton>
                    </Stack>
                    <Typography variant={'caption'} color={'text.secondary'}>
                        {t('event.document.exportBundle.onlyPdfHint')}
                    </Typography>
                </Box>
            )}
        </Stack>
    )
}

export default ExportBundleCard
