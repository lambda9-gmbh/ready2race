import {Add, ArrowDownward, ArrowUpward, Delete} from '@mui/icons-material'
import {
    Box,
    FormControlLabel,
    IconButton,
    Paper,
    Stack,
    Switch,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TextField,
    Typography,
} from '@mui/material'
import {useEffect, useRef, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {
    addClubNameRule,
    deleteClubNameRule,
    getClubNameRules,
    reorderClubNameRules,
    updateClubNameRule,
} from '@api/sdk.gen.ts'
import {ClubNameRuleDto, ClubNameRuleKind} from '@api/types.gen.ts'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import Throbber from '@components/Throbber.tsx'
import {useUser} from '@contexts/user/UserContext.ts'
import {updateClubGlobal} from '@authorization/privileges.ts'
import {MoveDirection, reorderedRuleIds, rulesOfKind, switchRule} from './clubNameRules.ts'

type Draft = {
    term: string
    replacement: string
}

type Props = {
    /**
     * Nur nötig, wenn eine Namensliste auf derselben Seite steht — die zeigt die Wirkung der Regeln
     * und muss dann mitgehen. Unter dem Einstellungen-Reiter gibt es sie nicht: dort lädt der
     * Reiter "Kurzformen" seine Liste beim Betreten ohnehin neu.
     */
    onRulesChanged?: () => void
}

/**
 * Die Kürzungsregeln - der obere Abschnitt der Pflegeseite.
 *
 * Bewusst ohne reguläre Ausdrücke: Wortpaare und eine Streichliste literaler Bestandteile als
 * Listen, die beiden strukturellen Regeln als Schalter. Ein Tippfehler in einem Muster würde die
 * Anzeige aller Vereine zerlegen, und die Seite bedient jemand, der eine Regatta organisiert.
 */
const ClubNameRulePanel = ({onRulesChanged}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const user = useUser()

    const mayUpdate = user.checkPrivilege(updateClubGlobal)

    const [lastRequested, setLastRequested] = useState(Date.now())
    const [drafts, setDrafts] = useState<Record<string, Draft>>({})
    const [newPair, setNewPair] = useState<Draft>({term: '', replacement: ''})
    const [newTerm, setNewTerm] = useState('')
    const justWritten = useRef<string | null>(null)

    const {data: rules, pending} = useFetch(signal => getClubNameRules({signal}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(
                    t('common.load.error.multiple.short', {entity: t('club.shortName.rules.rules')}),
                )
            }
        },
        deps: [lastRequested],
    })

    // Wie bei den Vereinsnamen: Getipptes überlebt das Nachladen, nur die gerade geschriebene
    // Zeile übernimmt den Wert vom Server.
    useEffect(() => {
        if (!rules) return

        const written = justWritten.current
        justWritten.current = null

        setDrafts(prev =>
            Object.fromEntries(
                rules.map(rule => [
                    rule.id,
                    prev[rule.id] !== undefined && rule.id !== written
                        ? prev[rule.id]
                        : {term: rule.term ?? '', replacement: rule.replacement ?? ''},
                ]),
            ),
        )
    }, [rules])

    const reload = (writtenRuleId: string | null = null) => {
        justWritten.current = writtenRuleId
        setLastRequested(Date.now())
        onRulesChanged?.()
    }

    const reportError = (status: number) => {
        // 400 heißt hier immer dasselbe zweierlei: Bestandteil fehlt, oder es gibt ihn schon.
        feedback.error(
            status === 400 ? t('club.shortName.rules.error.rejected') : t('common.error.unexpected'),
        )
    }

    const add = async (kind: ClubNameRuleKind, draft: Draft) => {
        const {error} = await addClubNameRule({
            body: {
                kind,
                term: draft.term.trim() || undefined,
                replacement: draft.replacement.trim() || undefined,
            },
        })

        if (error) {
            reportError(error.status?.value ?? 0)
            return false
        }

        feedback.success(t('club.shortName.rules.saved'))
        reload()
        return true
    }

    const save = async (rule: ClubNameRuleDto) => {
        const draft = drafts[rule.id]
        if (!draft) return

        if (draft.term === (rule.term ?? '') && draft.replacement === (rule.replacement ?? '')) {
            return
        }

        const {error} = await updateClubNameRule({
            path: {ruleId: rule.id},
            body: {kind: rule.kind, term: draft.term, replacement: draft.replacement},
        })

        if (error) {
            reportError(error.status?.value ?? 0)
            return
        }

        feedback.success(t('club.shortName.rules.saved'))
        reload(rule.id)
    }

    const remove = async (ruleId: string) => {
        const {error} = await deleteClubNameRule({path: {ruleId}})
        if (error) {
            reportError(error.status?.value ?? 0)
            return
        }

        feedback.success(t('club.shortName.rules.removed'))
        reload()
    }

    const move = async (rule: ClubNameRuleDto, direction: MoveDirection) => {
        const ruleIds = reorderedRuleIds(rules ?? [], rule.id, direction)
        if (!ruleIds) return

        const {error} = await reorderClubNameRules({body: {ruleIds}})
        if (error) {
            reportError(error.status?.value ?? 0)
            return
        }

        reload()
    }

    const toggleSwitch = async (kind: ClubNameRuleKind, on: boolean) => {
        const existing = switchRule(rules ?? [], kind)
        if (on && !existing) {
            await add(kind, {term: '', replacement: ''})
        } else if (!on && existing) {
            await remove(existing.id)
        }
    }

    const ruleRows = (kind: ClubNameRuleKind, withReplacement: boolean) =>
        rulesOfKind(rules ?? [], kind).map(rule => (
            <TableRow key={rule.id}>
                <TableCell>
                    <TextField
                        size={'small'}
                        fullWidth
                        disabled={!mayUpdate}
                        value={drafts[rule.id]?.term ?? ''}
                        onChange={event =>
                            setDrafts(prev => ({
                                ...prev,
                                [rule.id]: {...prev[rule.id], term: event.target.value},
                            }))
                        }
                        onBlur={() => save(rule)}
                    />
                </TableCell>
                {withReplacement && (
                    <TableCell>
                        <TextField
                            size={'small'}
                            fullWidth
                            disabled={!mayUpdate}
                            value={drafts[rule.id]?.replacement ?? ''}
                            onChange={event =>
                                setDrafts(prev => ({
                                    ...prev,
                                    [rule.id]: {...prev[rule.id], replacement: event.target.value},
                                }))
                            }
                            onBlur={() => save(rule)}
                        />
                    </TableCell>
                )}
                <TableCell align={'right'} sx={{whiteSpace: 'nowrap'}}>
                    <IconButton
                        size={'small'}
                        disabled={!mayUpdate || !reorderedRuleIds(rules ?? [], rule.id, 'up')}
                        aria-label={t('club.shortName.rules.moveUp')}
                        onClick={() => move(rule, 'up')}>
                        <ArrowUpward fontSize={'inherit'} />
                    </IconButton>
                    <IconButton
                        size={'small'}
                        disabled={!mayUpdate || !reorderedRuleIds(rules ?? [], rule.id, 'down')}
                        aria-label={t('club.shortName.rules.moveDown')}
                        onClick={() => move(rule, 'down')}>
                        <ArrowDownward fontSize={'inherit'} />
                    </IconButton>
                    <IconButton
                        size={'small'}
                        disabled={!mayUpdate}
                        aria-label={t('common.delete')}
                        onClick={() => remove(rule.id)}>
                        <Delete fontSize={'inherit'} />
                    </IconButton>
                </TableCell>
            </TableRow>
        ))

    if (pending && !rules) {
        return <Throbber />
    }

    return (
        <Stack spacing={3}>
            {/* Überschriften bleiben bei h2/h3: das Theme stylt nur h1-h3, ein h4 fiele auf die
                MUI-Vorgabe von 2,125rem zurück und wäre damit größer als der Abschnitt, zu dem es
                gehört. */}
            <Stack spacing={0.5}>
                <Typography variant={'h2'}>{t('club.shortName.rules.rules')}</Typography>
                <Typography variant={'body2'} color={'text.secondary'}>
                    {t('club.shortName.rules.hint')}
                </Typography>
                <Typography variant={'body2'} color={'text.secondary'}>
                    {t('club.shortName.rules.orderHint')}
                </Typography>
            </Stack>

            <Box>
                <FormControlLabel
                    control={
                        <Switch
                            disabled={!mayUpdate}
                            checked={switchRule(rules ?? [], 'REMOVE_YEARS') !== undefined}
                            onChange={event => toggleSwitch('REMOVE_YEARS', event.target.checked)}
                        />
                    }
                    label={t('club.shortName.rules.removeYears')}
                />
                <FormControlLabel
                    control={
                        <Switch
                            disabled={!mayUpdate}
                            checked={switchRule(rules ?? [], 'REMOVE_BRACKETED') !== undefined}
                            onChange={event =>
                                toggleSwitch('REMOVE_BRACKETED', event.target.checked)
                            }
                        />
                    }
                    label={t('club.shortName.rules.removeBracketed')}
                />
            </Box>

            <Box>
                <Typography variant={'h3'}>{t('club.shortName.rules.wordPairs')}</Typography>
                <Typography variant={'body2'} color={'text.secondary'} sx={{mb: 1.5}}>
                    {t('club.shortName.rules.wordPairsHint')}
                </Typography>
                <TableContainer component={Paper}>
                    {/* Wie bei den Vereinsnamen: lieber die Tabelle rollen lassen als die
                        Eingabefelder am Telefon auf wenige Zeichen zusammenzudrücken. */}
                    <Table size={'small'} sx={{minWidth: 480}}>
                        <TableHead>
                            <TableRow>
                                <TableCell>{t('club.shortName.rules.term')}</TableCell>
                                <TableCell>{t('club.shortName.rules.replacement')}</TableCell>
                                <TableCell sx={{width: 150}} />
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {ruleRows('ABBREVIATION', true)}
                            {mayUpdate && (
                                <TableRow>
                                    <TableCell>
                                        <TextField
                                            size={'small'}
                                            fullWidth
                                            placeholder={t('club.shortName.rules.term')}
                                            value={newPair.term}
                                            onChange={event =>
                                                setNewPair(prev => ({
                                                    ...prev,
                                                    term: event.target.value,
                                                }))
                                            }
                                        />
                                    </TableCell>
                                    <TableCell>
                                        <TextField
                                            size={'small'}
                                            fullWidth
                                            placeholder={t('club.shortName.rules.replacement')}
                                            value={newPair.replacement}
                                            onChange={event =>
                                                setNewPair(prev => ({
                                                    ...prev,
                                                    replacement: event.target.value,
                                                }))
                                            }
                                        />
                                    </TableCell>
                                    <TableCell align={'right'}>
                                        <IconButton
                                            size={'small'}
                                            aria-label={t('club.shortName.rules.add')}
                                            disabled={
                                                newPair.term.trim() === '' ||
                                                newPair.replacement.trim() === ''
                                            }
                                            onClick={async () => {
                                                if (await add('ABBREVIATION', newPair)) {
                                                    setNewPair({term: '', replacement: ''})
                                                }
                                            }}>
                                            <Add fontSize={'inherit'} />
                                        </IconButton>
                                    </TableCell>
                                </TableRow>
                            )}
                        </TableBody>
                    </Table>
                </TableContainer>
            </Box>

            <Box>
                <Typography variant={'h3'}>{t('club.shortName.rules.removedTerms')}</Typography>
                <Typography variant={'body2'} color={'text.secondary'} sx={{mb: 1.5}}>
                    {t('club.shortName.rules.removedTermsHint')}
                </Typography>
                <TableContainer component={Paper}>
                    <Table size={'small'} sx={{minWidth: 360}}>
                        <TableHead>
                            <TableRow>
                                <TableCell>{t('club.shortName.rules.term')}</TableCell>
                                <TableCell sx={{width: 150}} />
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {ruleRows('REMOVE_TERM', false)}
                            {mayUpdate && (
                                <TableRow>
                                    <TableCell>
                                        <TextField
                                            size={'small'}
                                            fullWidth
                                            placeholder={t('club.shortName.rules.term')}
                                            value={newTerm}
                                            onChange={event => setNewTerm(event.target.value)}
                                        />
                                    </TableCell>
                                    <TableCell align={'right'}>
                                        <IconButton
                                            size={'small'}
                                            aria-label={t('club.shortName.rules.add')}
                                            disabled={newTerm.trim() === ''}
                                            onClick={async () => {
                                                const added = await add('REMOVE_TERM', {
                                                    term: newTerm,
                                                    replacement: '',
                                                })
                                                if (added) {
                                                    setNewTerm('')
                                                }
                                            }}>
                                            <Add fontSize={'inherit'} />
                                        </IconButton>
                                    </TableCell>
                                </TableRow>
                            )}
                        </TableBody>
                    </Table>
                </TableContainer>
            </Box>
        </Stack>
    )
}

export default ClubNameRulePanel
