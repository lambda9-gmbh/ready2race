import {
    Box,
    Chip,
    MenuItem,
    Paper,
    Select,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TextField,
    Typography,
    useTheme,
} from '@mui/material'
import {useEffect, useRef, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {
    deleteClubShortName,
    getClubShortNames,
    getEvents,
    updateClubShortName,
} from '@api/sdk.gen.ts'
import {ClubShortNameDto} from '@api/types.gen.ts'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import Throbber from '@components/Throbber.tsx'
import {useUser} from '@contexts/user/UserContext.ts'
import {updateClubGlobal} from '@authorization/privileges.ts'
import {clubShortNameAction, mergedSpellings, primaryName} from './clubShortNames.ts'

const ALL_EVENTS = 'all'

const ClubShortNamePanel = () => {
    const {t} = useTranslation()
    const theme = useTheme()
    const feedback = useFeedback()
    const user = useUser()

    const mayUpdate = user.checkPrivilege(updateClubGlobal)

    const [eventId, setEventId] = useState<string>(ALL_EVENTS)
    const [lastRequested, setLastRequested] = useState(Date.now())
    const [drafts, setDrafts] = useState<Record<string, string>>({})
    const justWritten = useRef<string | null>(null)

    const {data: events} = useFetch(signal => getEvents({signal}), {deps: []})

    const {data: rows, pending} = useFetch(
        signal =>
            getClubShortNames({
                signal,
                query: eventId === ALL_EVENTS ? {} : {eventId},
            }),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {entity: t('club.shortName.shortNames')}),
                    )
                }
            },
            deps: [eventId, lastRequested],
        },
    )

    // Die Felder werden aus der Antwort vorbelegt - mit der gepflegten Kurzform, sonst mit der
    // automatischen. Ein leeres Feld hieße "löschen", und das darf das Laden nicht auslösen.
    //
    // Bereits Getipptes überlebt das Nachladen: nach dem Speichern einer Zeile wird die Liste neu
    // geholt, und wer währenddessen schon in der nächsten Zeile schreibt, verlöre sonst seine
    // Eingabe. Nur die gerade geschriebene Zeile übernimmt den Wert vom Server - bei ihr ist er
    // die Antwort auf die eigene Änderung, beim Löschen die zurückgekehrte Heuristik.
    useEffect(() => {
        if (!rows) return

        const written = justWritten.current
        justWritten.current = null

        setDrafts(prev =>
            Object.fromEntries(
                rows.map(row => [
                    row.nameKey,
                    prev[row.nameKey] !== undefined && row.nameKey !== written
                        ? prev[row.nameKey]
                        : row.shortName,
                ]),
            ),
        )
    }, [rows])

    const save = async (row: ClubShortNameDto) => {
        const draft = drafts[row.nameKey] ?? row.shortName

        switch (clubShortNameAction(row, draft)) {
            case 'none':
                return
            case 'save': {
                const {error} = await updateClubShortName({
                    path: {nameKey: row.nameKey},
                    body: {shortName: draft.trim(), sampleName: primaryName(row)},
                })
                if (error) {
                    feedback.error(t('common.error.unexpected'))
                } else {
                    feedback.success(t('club.shortName.saved'))
                }
                break
            }
            case 'delete': {
                const {error} = await deleteClubShortName({path: {nameKey: row.nameKey}})
                if (error) {
                    feedback.error(t('common.error.unexpected'))
                } else {
                    feedback.success(t('club.shortName.removed'))
                }
                break
            }
        }

        justWritten.current = row.nameKey
        setLastRequested(Date.now())
    }

    return (
        <Box>
            <Typography variant={'h2'}>{t('club.shortName.shortNames')}</Typography>
            <Box sx={{color: theme.palette.text.secondary}}>{t('club.shortName.tableHint')}</Box>
            <Box sx={{color: theme.palette.text.secondary}}>{t('club.shortName.emptyHint')}</Box>

            <Box sx={{display: 'flex', justifyContent: 'flex-end', pt: 1, mb: 1}}>
                <Select
                    size={'small'}
                    value={eventId}
                    onChange={event => {
                        // Ein anderer Ausschnitt heißt andere Zeilen - halb Getipptes von vorher
                        // gehört nicht in eine Liste, in der die Zeile womöglich gar nicht steht.
                        setDrafts({})
                        setEventId(event.target.value as string)
                    }}>
                    <MenuItem value={ALL_EVENTS}>{t('club.shortName.filter.all')}</MenuItem>
                    {events?.data.map(event => (
                        <MenuItem key={event.id} value={event.id}>
                            {t('club.shortName.filter.event', {name: event.name})}
                        </MenuItem>
                    ))}
                </Select>
            </Box>

            {pending && !rows ? (
                <Throbber />
            ) : rows && rows.length > 0 ? (
                <TableContainer component={Paper}>
                    <Table size={'small'}>
                        <TableHead>
                            <TableRow>
                                <TableCell>{t('club.shortName.name')}</TableCell>
                                <TableCell sx={{width: 260}}>
                                    {t('club.shortName.shortName')}
                                </TableCell>
                                <TableCell sx={{width: 140}}>
                                    {t('club.shortName.origin')}
                                </TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {rows.map(row => (
                                <TableRow key={row.nameKey}>
                                    <TableCell>
                                        <Typography>{primaryName(row)}</Typography>
                                        {mergedSpellings(row).map(name => (
                                            <Typography
                                                key={name}
                                                variant={'body2'}
                                                sx={{color: theme.palette.text.secondary}}>
                                                {t('club.shortName.also', {name})}
                                            </Typography>
                                        ))}
                                    </TableCell>
                                    <TableCell>
                                        <TextField
                                            size={'small'}
                                            fullWidth
                                            disabled={!mayUpdate}
                                            value={drafts[row.nameKey] ?? ''}
                                            onChange={event =>
                                                setDrafts(prev => ({
                                                    ...prev,
                                                    [row.nameKey]: event.target.value,
                                                }))
                                            }
                                            onBlur={() => save(row)}
                                        />
                                    </TableCell>
                                    <TableCell>
                                        <Chip
                                            size={'small'}
                                            variant={row.maintained ? 'filled' : 'outlined'}
                                            label={t(
                                                row.maintained
                                                    ? 'club.shortName.maintained'
                                                    : 'club.shortName.automatic',
                                            )}
                                        />
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </TableContainer>
            ) : (
                <Typography>{t('club.shortName.empty')}</Typography>
            )}
        </Box>
    )
}

export default ClubShortNamePanel
