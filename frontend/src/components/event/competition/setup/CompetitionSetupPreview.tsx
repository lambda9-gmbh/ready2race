import {useLayoutEffect, useRef, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    ButtonBase,
    Chip,
    Dialog,
    DialogContent,
    DialogTitle,
    IconButton,
    Stack,
    Tooltip,
    Typography,
    useMediaQuery,
    useTheme,
} from '@mui/material'
import {Close, Fullscreen, FullscreenExit, AccountTree, East} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'
import type {FormSetupRound} from './common'
import {
    buildSetupPreview,
    formatPlaceRanges,
    type PreviewEdge,
    type PreviewMatch,
    type PreviewSource,
} from './setupPreviewModel'

const key = 'event.competition.setup.preview' as const

type Props = {rounds: FormSetupRound[]; onClose: () => void}

export default function CompetitionSetupPreview({rounds, onClose}: Props) {
    const {t} = useTranslation()
    const theme = useTheme()
    const small = useMediaQuery(theme.breakpoints.down('sm'))
    const [fullscreen, setFullscreen] = useState(false)
    const [selectedId, setSelectedId] = useState<string>()
    const [selectedRoundId, setSelectedRoundId] = useState<string>()
    // Rebuild from watched form values, including edits made while the dialog is open.
    const model = buildSetupPreview(rounds)
    const nodes = new Map(model.rounds.flatMap(round => round.matches).map(m => [m.id, m]))
    const selected = selectedId ? nodes.get(selectedId) : undefined
    const relatedEdges = model.edges.filter(e => e.from === selected?.id || e.to === selected?.id)
    const relatedIds = new Set(relatedEdges.flatMap(e => [e.from, e.to]))
    const [graphElement, setGraphElement] = useState<HTMLDivElement | null>(null)
    const cardRefs = useRef(new Map<string, HTMLButtonElement>())
    const [paths, setPaths] = useState<Record<string, string>>({})
    const geometryKey = JSON.stringify(model)

    useLayoutEffect(() => {
        // Opening the detail panel reduces the graph height, especially on phones.
        // Keep the selected card in view after that layout change.
        if (selectedId)
            cardRefs.current.get(selectedId)?.scrollIntoView({block: 'nearest', inline: 'nearest'})
    }, [selectedId])

    useLayoutEffect(() => {
        const graph = graphElement
        if (!graph) return
        const measure = () => {
            const bounds = graph.getBoundingClientRect()
            const next: Record<string, string> = {}
            model.edges.forEach(edge => {
                const from = cardRefs.current.get(edge.from)?.getBoundingClientRect()
                const to = cardRefs.current.get(edge.to)?.getBoundingClientRect()
                if (!from || !to) return
                const x1 = from.right - bounds.left
                const y1 = from.top + from.height / 2 - bounds.top
                const x2 = to.left - bounds.left
                const y2 = to.top + to.height / 2 - bounds.top
                const middle = (x1 + x2) / 2
                next[`${edge.from}:${edge.to}`] =
                    `M ${x1} ${y1} C ${middle} ${y1}, ${middle} ${y2}, ${x2} ${y2}`
            })
            setPaths(next)
        }
        const observer = new ResizeObserver(measure)
        observer.observe(graph)
        cardRefs.current.forEach(card => observer.observe(card))
        measure()
        return () => observer.disconnect()
        // geometryKey includes the complete read-only model; translation can change card heights.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [graphElement, geometryKey, fullscreen, small, t])

    const matchName = (match: PreviewMatch) =>
        match.name?.trim() || t(`${key}.matchName`, {number: match.weighting})
    const roundName = (index: number) =>
        model.rounds[index].name?.trim() || t(`${key}.roundName`, {number: index + 1})
    const placeLabel = (match: PreviewMatch, places: number[]) => {
        if (match.capacity.kind === 'fixed' && match.capacity.count === 2 && places.length === 1) {
            return t(`${key}.${places[0] === 1 ? 'winner' : 'loser'}`)
        }
        return t(`${key}.places`, {places: formatPlaceRanges(places)})
    }
    const conditionalLabel = (label: string, conditional?: boolean) =>
        conditional ? t(`${key}.conditionalSource`, {source: label}) : label
    const sourceLabel = (source: PreviewSource) => {
        if (source.kind === 'seed') return t(`${key}.seed`, {number: source.seed})
        if (source.kind === 'result') {
            const from = nodes.get(source.matchId)!
            return conditionalLabel(
                t(`${key}.from`, {
                    place: placeLabel(from, [source.place]),
                    match: matchName(from),
                }),
                source.conditional,
            )
        }
        const reason = t(`${key}.open.${source.reason}`)
        return source.seed === undefined
            ? reason
            : t(`${key}.openOutcome`, {number: source.seed, reason})
    }
    const groupedSources = (match: PreviewMatch) => {
        const seen = new Set<string>()
        return match.sources.flatMap(source => {
            if (source.kind !== 'result') return [sourceLabel(source)]
            if (seen.has(source.matchId)) return []
            seen.add(source.matchId)
            const from = nodes.get(source.matchId)!
            const places = match.sources.flatMap(s =>
                s.kind === 'result' && s.matchId === source.matchId ? [s.place] : [],
            )
            const conditional = match.sources.some(
                s => s.kind === 'result' && s.matchId === source.matchId && s.conditional,
            )
            return [
                conditionalLabel(
                    t(`${key}.from`, {place: placeLabel(from, places), match: matchName(from)}),
                    conditional,
                ),
            ]
        })
    }
    const jumpTo = (id: string) => {
        setSelectedId(id)
        setSelectedRoundId(model.rounds[nodes.get(id)!.roundIndex].id)
        const card = cardRefs.current.get(id)
        card?.scrollIntoView({block: 'nearest', inline: 'center'})
        card?.focus({preventScroll: true})
    }
    const lossEdge = (edge: PreviewEdge) => {
        const from = nodes.get(edge.from)
        return (
            from?.capacity.kind === 'fixed' &&
            from.capacity.count === 2 &&
            edge.places.length === 1 &&
            edge.places[0] === 2
        )
    }
    // Order neighbours by their actual incoming relations to reduce crossings. This changes only
    // presentation; weighting, execution order and winner/loser membership are never inferred.
    const positions = new Map<string, number>()
    const orderedRounds = model.rounds.map(round => {
        const score = (m: PreviewMatch) => {
            const incoming = model.edges.filter(e => e.to === m.id)
            return incoming.length
                ? incoming.reduce((sum, e) => sum + (positions.get(e.from) ?? 0), 0) /
                      incoming.length
                : m.weighting - 1
        }
        const matches = [...round.matches].sort(
            (a, b) => score(a) - score(b) || a.weighting - b.weighting,
        )
        matches.forEach((match, index) => positions.set(match.id, index))
        return {...round, matches}
    })

    return (
        <Dialog
            open
            onClose={onClose}
            fullScreen={small || fullscreen}
            maxWidth="xl"
            fullWidth
            aria-labelledby="setup-preview-title"
            PaperProps={{
                sx: {
                    height: small || fullscreen ? '100%' : '88dvh',
                    maxHeight: small || fullscreen ? '100%' : undefined,
                },
            }}>
            <DialogTitle
                component="div"
                sx={{display: 'flex', alignItems: 'center', gap: 1, py: 1.5}}>
                <AccountTree color="primary" />
                <Box sx={{flex: 1, minWidth: 0}}>
                    <Typography id="setup-preview-title" component="h2" variant="h6">
                        {t(`${key}.title`)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                        {t(`${key}.subtitle`)}
                    </Typography>
                </Box>
                {!small && (
                    <Tooltip title={t(`${key}.${fullscreen ? 'windowed' : 'fullscreen'}`)}>
                        <IconButton
                            type="button"
                            onClick={() => setFullscreen(!fullscreen)}
                            aria-label={t(`${key}.${fullscreen ? 'windowed' : 'fullscreen'}`)}>
                            {fullscreen ? <FullscreenExit /> : <Fullscreen />}
                        </IconButton>
                    </Tooltip>
                )}
                <IconButton type="button" aria-label={t('common.close')} onClick={onClose}>
                    <Close />
                </IconButton>
            </DialogTitle>
            <DialogContent
                dividers
                sx={{p: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden'}}>
                <Box sx={{px: 2, py: 1, borderBottom: 1, borderColor: 'divider'}}>
                    <Typography variant="body2" color="text.secondary">
                        {t(`${key}.planned`)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                        {t(`${key}.navigation`)}
                    </Typography>
                    {model.edges.some(edge => edge.conditional) && (
                        <Typography variant="caption" color="text.secondary" display="block">
                            {t(`${key}.conditionalLegend`)}
                        </Typography>
                    )}
                </Box>
                {model.rounds.length === 0 ? (
                    <Alert severity="info" sx={{m: 2}}>
                        {t(`${key}.empty`)}
                    </Alert>
                ) : (
                    <>
                        <Stack
                            direction="row"
                            spacing={1}
                            sx={{px: 2, py: 1, overflowX: 'auto', flexShrink: 0}}>
                            {model.rounds.map((round, index) => (
                                <Chip
                                    key={round.id}
                                    label={roundName(index)}
                                    component="button"
                                    type="button"
                                    clickable
                                    color={selectedRoundId === round.id ? 'primary' : 'default'}
                                    variant={selectedRoundId === round.id ? 'filled' : 'outlined'}
                                    aria-pressed={selectedRoundId === round.id}
                                    aria-controls={`setup-preview-round-${round.id}`}
                                    onClick={() => {
                                        setSelectedRoundId(round.id)
                                        setSelectedId(undefined)
                                        graphElement
                                            ?.querySelector(`#setup-preview-round-${round.id}`)
                                            ?.scrollIntoView({block: 'start', inline: 'start'})
                                    }}
                                />
                            ))}
                        </Stack>
                        <Box
                            tabIndex={0}
                            role="region"
                            aria-label={t(`${key}.title`)}
                            sx={{
                                overflow: 'auto',
                                flex: 1,
                                minHeight: 180,
                                p: 2,
                                bgcolor: 'action.hover',
                            }}>
                            <Box
                                ref={setGraphElement}
                                sx={{
                                    position: 'relative',
                                    display: 'flex',
                                    gap: '72px',
                                    width: 'max-content',
                                    minHeight: '100%',
                                    pr: 1,
                                }}>
                                <Box
                                    component="svg"
                                    aria-hidden="true"
                                    sx={{
                                        position: 'absolute',
                                        inset: 0,
                                        width: '100%',
                                        height: '100%',
                                        pointerEvents: 'none',
                                    }}>
                                    {[...model.edges]
                                        .sort(
                                            (a, b) =>
                                                Number(relatedEdges.includes(a)) -
                                                Number(relatedEdges.includes(b)),
                                        )
                                        .map(edge => {
                                            const active = relatedEdges.includes(edge)
                                            return (
                                                <path
                                                    key={`${edge.from}:${edge.to}`}
                                                    d={paths[`${edge.from}:${edge.to}`]}
                                                    fill="none"
                                                    stroke={
                                                        active
                                                            ? lossEdge(edge)
                                                                ? theme.palette.warning.main
                                                                : theme.palette.primary.main
                                                            : theme.palette.text.disabled
                                                    }
                                                    strokeWidth={active ? 3 : 1.5}
                                                    strokeDasharray={
                                                        edge.conditional
                                                            ? '2 4'
                                                            : lossEdge(edge)
                                                              ? '6 4'
                                                              : undefined
                                                    }
                                                    opacity={selected && !active ? 0.25 : 1}
                                                />
                                            )
                                        })}
                                </Box>
                                {orderedRounds.map((round, roundIndex) => (
                                    <Box
                                        key={round.id}
                                        data-round={round.id}
                                        sx={{
                                            width: {xs: '260px', sm: '280px'},
                                            display: 'flex',
                                            flexDirection: 'column',
                                            zIndex: 1,
                                        }}>
                                        <Box
                                            id={`setup-preview-round-${round.id}`}
                                            sx={{
                                                minHeight: 64,
                                                p: 1,
                                                mb: 1,
                                                borderBottom: 2,
                                                borderColor:
                                                    selectedRoundId === round.id
                                                        ? 'primary.main'
                                                        : 'transparent',
                                                bgcolor:
                                                    selectedRoundId === round.id
                                                        ? 'action.selected'
                                                        : undefined,
                                                borderRadius: 1,
                                            }}>
                                            <Typography variant="overline" color="text.secondary">
                                                {t(`${key}.roundName`, {number: roundIndex + 1})}
                                            </Typography>
                                            <Typography
                                                variant="subtitle1"
                                                sx={{overflowWrap: 'anywhere'}}>
                                                {roundName(roundIndex)}
                                            </Typography>
                                            {round.qualification && (
                                                <Chip
                                                    size="small"
                                                    label={t(`${key}.qualification`)}
                                                />
                                            )}
                                            {round.minimumParticipantsPerUnlimitedMatch !==
                                                undefined && (
                                                <Typography
                                                    variant="body2"
                                                    color="text.secondary"
                                                    sx={{mt: 1}}>
                                                    {t(`${key}.occupancyAssumption`, {
                                                        count: round.minimumParticipantsPerUnlimitedMatch,
                                                    })}
                                                </Typography>
                                            )}
                                        </Box>
                                        {round.group ? (
                                            <Alert severity="info">
                                                {t(`${key}.groupsExcluded`)}
                                            </Alert>
                                        ) : round.matches.length === 0 ? (
                                            <Alert severity="info">{t(`${key}.noMatches`)}</Alert>
                                        ) : (
                                            <Stack
                                                spacing={2}
                                                sx={{flex: 1, justifyContent: 'space-around'}}>
                                                {round.matches.map(match => {
                                                    const labels = groupedSources(match)
                                                    const active = selected?.id === match.id
                                                    const related = relatedIds.has(match.id)
                                                    const hasOpen =
                                                        match.issues.length > 0 ||
                                                        match.sources.some(s => s.kind === 'open')
                                                    // Only a valid, optional single-entry match is a
                                                    // planned bye. Names such as "Wartet" are not rules.
                                                    const autoAdvance =
                                                        match.capacity.kind === 'fixed' &&
                                                        match.capacity.count === 1 &&
                                                        !match.required &&
                                                        !hasOpen &&
                                                        match.sources.length === 1
                                                    return (
                                                        <ButtonBase
                                                            key={match.id}
                                                            type="button"
                                                            ref={element => {
                                                                if (element)
                                                                    cardRefs.current.set(
                                                                        match.id,
                                                                        element,
                                                                    )
                                                                else
                                                                    cardRefs.current.delete(
                                                                        match.id,
                                                                    )
                                                            }}
                                                            aria-pressed={active}
                                                            onClick={() => {
                                                                setSelectedRoundId(round.id)
                                                                setSelectedId(
                                                                    active ? undefined : match.id,
                                                                )
                                                            }}
                                                            sx={{
                                                                display: 'block',
                                                                textAlign: 'left',
                                                                width: autoAdvance
                                                                    ? 'calc(100% - 16px)'
                                                                    : '100%',
                                                                mx: autoAdvance ? 1 : 0,
                                                                border: autoAdvance
                                                                    ? '1.5px dashed'
                                                                    : '1px solid',
                                                                borderColor:
                                                                    active || related
                                                                        ? 'text.primary'
                                                                        : 'divider',
                                                                borderRadius: autoAdvance ? 3 : 1,
                                                                bgcolor: autoAdvance
                                                                    ? theme.palette.mode === 'dark'
                                                                        ? 'grey.800'
                                                                        : 'grey.200'
                                                                    : 'background.paper',
                                                                opacity:
                                                                    selected && !active && !related
                                                                        ? 0.5
                                                                        : 1,
                                                                outline: active
                                                                    ? `2px solid ${theme.palette.text.primary}`
                                                                    : undefined,
                                                                outlineOffset: 2,
                                                                '&.Mui-focusVisible': {
                                                                    outline: `3px solid ${theme.palette.text.primary}`,
                                                                },
                                                                overflowWrap: 'anywhere',
                                                            }}>
                                                            <Box
                                                                sx={{
                                                                    px: 1.5,
                                                                    py: 1,
                                                                    borderBottom: autoAdvance
                                                                        ? 0
                                                                        : 1,
                                                                    borderColor: 'divider',
                                                                    bgcolor:
                                                                        related || active
                                                                            ? 'action.selected'
                                                                            : autoAdvance
                                                                              ? 'transparent'
                                                                              : 'action.hover',
                                                                    borderRadius: 'inherit',
                                                                }}>
                                                                {autoAdvance && (
                                                                    <Stack
                                                                        direction="row"
                                                                        spacing={0.75}
                                                                        sx={{
                                                                            alignItems: 'center',
                                                                            mb: 0.5,
                                                                            color: 'text.secondary',
                                                                        }}>
                                                                        <East fontSize="small" />
                                                                        <Typography
                                                                            variant="caption"
                                                                            fontWeight={700}>
                                                                            {t(
                                                                                `${key}.autoAdvance`,
                                                                            )}
                                                                        </Typography>
                                                                    </Stack>
                                                                )}
                                                                <Typography variant="subtitle2">
                                                                    {matchName(match)}
                                                                </Typography>
                                                                <Typography
                                                                    variant="caption"
                                                                    color="text.secondary">
                                                                    {autoAdvance
                                                                        ? t(
                                                                              `${key}.autoAdvanceHint`,
                                                                          )
                                                                        : match.capacity.kind ===
                                                                            'unlimited'
                                                                          ? t(`${key}.unlimited`)
                                                                          : match.capacity.kind ===
                                                                              'fixed'
                                                                            ? t(
                                                                                  `${key}.participants`,
                                                                                  {
                                                                                      count: match
                                                                                          .capacity
                                                                                          .count,
                                                                                  },
                                                                              )
                                                                            : t(
                                                                                  `${key}.invalidCapacity`,
                                                                              )}
                                                                </Typography>
                                                            </Box>
                                                            <Box
                                                                sx={{
                                                                    px: 1.5,
                                                                    pt: autoAdvance ? 0 : 1,
                                                                    pb: 1,
                                                                }}>
                                                                {labels
                                                                    .slice(0, 4)
                                                                    .map((label, index) => (
                                                                        <Typography
                                                                            key={index}
                                                                            variant={
                                                                                autoAdvance
                                                                                    ? 'caption'
                                                                                    : 'body2'
                                                                            }
                                                                            component="div"
                                                                            color={
                                                                                autoAdvance
                                                                                    ? 'text.secondary'
                                                                                    : 'text.primary'
                                                                            }
                                                                            sx={{
                                                                                py: autoAdvance
                                                                                    ? 0
                                                                                    : 0.25,
                                                                            }}>
                                                                            {label}
                                                                        </Typography>
                                                                    ))}
                                                                {labels.length > 4 && (
                                                                    <Typography
                                                                        variant="caption"
                                                                        color="text.secondary">
                                                                        {t(`${key}.more`, {
                                                                            count:
                                                                                labels.length - 4,
                                                                        })}
                                                                    </Typography>
                                                                )}
                                                                {match.capacity.kind ===
                                                                    'unlimited' && (
                                                                    <Typography
                                                                        variant="body2"
                                                                        color="text.secondary">
                                                                        {t(
                                                                            `${key}.${roundIndex === 0 ? 'dynamicFirst' : 'dynamicFollowing'}`,
                                                                        )}
                                                                    </Typography>
                                                                )}
                                                                {match.omittedSlots > 0 && (
                                                                    <Typography variant="caption">
                                                                        {t(`${key}.omitted`, {
                                                                            count: match.omittedSlots,
                                                                        })}
                                                                    </Typography>
                                                                )}
                                                                {hasOpen && (
                                                                    <Typography
                                                                        variant="caption"
                                                                        color="warning.main"
                                                                        display="block">
                                                                        {t(
                                                                            `${key}.checkAssignments`,
                                                                        )}
                                                                    </Typography>
                                                                )}
                                                            </Box>
                                                        </ButtonBase>
                                                    )
                                                })}
                                            </Stack>
                                        )}
                                    </Box>
                                ))}
                            </Box>
                        </Box>
                    </>
                )}
                <Box
                    aria-live="polite"
                    sx={{
                        borderTop: 1,
                        borderColor: 'divider',
                        px: 2,
                        py: 1.5,
                        maxHeight: '34%',
                        overflow: 'auto',
                        flexShrink: 0,
                    }}>
                    {!selected ? (
                        <Typography variant="body2" color="text.secondary">
                            {t(`${key}.selectMatch`)}
                        </Typography>
                    ) : (
                        <>
                            <Typography variant="subtitle1">
                                {matchName(selected)} · {roundName(selected.roundIndex)}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                                {t(`${key}.order`, {
                                    weighting: selected.weighting,
                                    order: selected.executionOrder,
                                })}
                            </Typography>
                            {selected.capacity.kind === 'fixed' &&
                                selected.capacity.count === 1 && (
                                    <Typography variant="body2">
                                        {t(
                                            `${key}.${selected.required ? 'singleRequired' : 'singleOptional'}`,
                                        )}
                                    </Typography>
                                )}
                            {model.rounds[selected.roundIndex].customNaming && (
                                <Typography variant="body2" color="text.secondary">
                                    {t(`${key}.customNaming`)}
                                </Typography>
                            )}
                            {selected.issues.map(issue => (
                                <Typography key={issue} variant="body2" color="warning.main">
                                    {t(`${key}.issues.${issue}`)}
                                </Typography>
                            ))}
                            {relatedEdges.some(edge => edge.conditional) && (
                                <Typography variant="body2" color="text.secondary" sx={{mt: 1}}>
                                    {t(`${key}.conditionalNotice`)}
                                </Typography>
                            )}
                            <Typography variant="subtitle2" sx={{mt: 1}}>
                                {t(`${key}.sources`)}
                            </Typography>
                            {groupedSources(selected).map((label, index) => (
                                <Typography key={index} variant="body2">
                                    {label}
                                </Typography>
                            ))}
                            {selected.capacity.kind === 'unlimited' && (
                                <Typography variant="body2">
                                    {t(
                                        `${key}.${selected.roundIndex === 0 ? 'dynamicFirst' : 'dynamicFollowing'}`,
                                    )}
                                </Typography>
                            )}
                            {selected.omittedSlots > 0 && (
                                <Typography variant="body2">
                                    {t(`${key}.omitted`, {count: selected.omittedSlots})}
                                </Typography>
                            )}
                            <Stack direction="row" useFlexGap flexWrap="wrap" gap={1} sx={{mt: 1}}>
                                {relatedEdges
                                    .filter(e => e.to === selected.id)
                                    .map(edge => (
                                        <Button
                                            type="button"
                                            size="small"
                                            variant="outlined"
                                            key={edge.from}
                                            onClick={() => jumpTo(edge.from)}>
                                            {t(`${key}.goToSource`, {
                                                match: matchName(nodes.get(edge.from)!),
                                            })}
                                        </Button>
                                    ))}
                            </Stack>
                            <Typography variant="subtitle2" sx={{mt: 1}}>
                                {t(`${key}.destinations`)}
                            </Typography>
                            <Stack direction="row" useFlexGap flexWrap="wrap" gap={1}>
                                {relatedEdges
                                    .filter(e => e.from === selected.id)
                                    .map(edge => (
                                        <Button
                                            type="button"
                                            size="small"
                                            variant="outlined"
                                            key={edge.to}
                                            onClick={() => jumpTo(edge.to)}>
                                            {conditionalLabel(
                                                `${placeLabel(selected, edge.places)} → ${matchName(nodes.get(edge.to)!)}`,
                                                edge.conditional,
                                            )}
                                        </Button>
                                    ))}
                                {!relatedEdges.some(e => e.from === selected.id) && (
                                    <Typography variant="body2" color="text.secondary">
                                        {t(`${key}.noDestination`)}
                                    </Typography>
                                )}
                            </Stack>
                        </>
                    )}
                </Box>
            </DialogContent>
        </Dialog>
    )
}
