import {PropsWithChildren, useCallback, useEffect, useMemo, useRef, useState} from 'react'
import {
    Box,
    Button,
    Divider,
    Drawer,
    IconButton,
    InputAdornment,
    List,
    ListItem,
    ListItemButton,
    ListItemText,
    Paper,
    Stack,
    TextField,
    Typography,
    useMediaQuery,
    useTheme,
} from '@mui/material'
import {
    ArrowBack,
    ChevronLeft,
    ChevronRight,
    Close,
    FormatListBulleted,
    Search,
    ShortText,
    Subject,
} from '@mui/icons-material'
import {Link} from '@tanstack/react-router'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getCompetitions} from '@api/sdk.gen.ts'
import {competitionLabelName, CompetitionTab} from '@components/event/competition/common.ts'
import {useUser} from '@contexts/user/UserContext.ts'
import Throbber from '@components/Throbber.tsx'

/** Merkt sich pro Gerät, ob die Wettkampfliste zugeklappt ist - sonst steht sie bei jedem
 * Wettkampfwechsel wieder offen. Gleiches Muster wie live_dashboard_poll_interval. */
const NAV_COLLAPSED_STORAGE_KEY = 'competition_nav_collapsed'

const storedNavCollapsed = (): boolean =>
    localStorage.getItem(NAV_COLLAPSED_STORAGE_KEY) === 'true'

/** Ebenso gemerkt: ob die Liste die Rennen mit Kurznamen ("CM 4x+") statt mit ihrem langen Namen
 * führt. Wer die Kürzel liest, liest sie den ganzen Regattatag über. */
const NAV_SHORT_NAMES_STORAGE_KEY = 'competition_nav_short_names'

const storedNavShortNames = (): boolean =>
    localStorage.getItem(NAV_SHORT_NAMES_STORAGE_KEY) === 'true'

// Bis zum 12.08.2026 konnte die Leiste alternativ den Event-Zeitplan zeigen (NavMode
// 'schedule', gemerkt unter 'competition_nav_mode'). Mit dem Veranstaltungs-Modus des
// Zeitplan-Tabs (Zeitplan links, Durchführung rechts) war das doppelt und ist entfallen —
// ein noch gespeichertes 'schedule' wird schlicht nicht mehr gelesen, die Leiste zeigt
// immer die Rennliste.

/**
 * Untergrenze der Leistenbreite; auf breiten Schirmen wächst sie mit (siehe RAIL_WIDTH_SX).
 * Die festen 240px schnitten dort jeden Zeitplan-Eintrag ab, während rechts Platz brachlag
 * (beobachtet am 10.08.2026).
 */
const LIST_WIDTH = 240
/**
 * Die Schubladen-Breite in der Kurzform: „11 CF 4x+" braucht keine 240px — die bisherige
 * Breite ist seit dem 13.08.2026 das Maximum der Langform (Nutzer-Feedback: viel Leerraum
 * neben den Kürzeln).
 */
const LIST_WIDTH_SHORT = 190
/**
 * Die mitwachsende Breite der Leiste: ein Fünftel des Fensters, gedeckelt - die Inhalte rechts
 * behalten Vorfahrt, aber „Zeitfahren – Z…" bei freiem Platz daneben muss nicht sein.
 */
const RAIL_WIDTH_SX = {width: 'clamp(240px, 20vw, 380px)'}
/**
 * Die Kurzform kommt mit deutlich weniger aus (rund zwei Drittel der Langform): Rennnummer plus
 * Kürzel füllen keine 20vw, und der gewonnene Platz gehört dem Inhalt rechts. Einträge ohne
 * gepflegtes Kürzel zeigen weiterhin den langen Namen — der läuft wie bisher in die
 * Ellipse (noWrap), es clippt also nichts. Die Langform behält die bisherige Breite als Maximum.
 */
const RAIL_WIDTH_SHORT_SX = {width: 'clamp(190px, 13vw, 260px)'}
/** Abstand der mitlaufenden Leiste zum Fensterrand, in Pixeln. */
const STICKY_TOP = 16

type Props = PropsWithChildren<{
    eventId: string
    eventName: string
    competitionId: string
    /** Wird beim Sprung ins nächste Rennen mitgenommen, damit man im Arbeitstab bleibt. */
    activeTab: CompetitionTab
}>

const CompetitionNavigation = ({
    eventId,
    eventName,
    competitionId,
    activeTab,
    children,
}: Props) => {
    const {t} = useTranslation()
    const user = useUser()
    const feedback = useFeedback()
    const theme = useTheme()

    // Unterhalb von lg ist das App-Menü selbst schon eine Schublade - zwei feste Leisten
    // nebeneinander lassen für den Inhalt nichts übrig, also legt sich die Liste hier darüber.
    const isNarrow = useMediaQuery(theme.breakpoints.down('lg'))

    const [collapsed, setCollapsed] = useState(storedNavCollapsed)
    const [shortNames, setShortNames] = useState(storedNavShortNames)
    const [drawerOpen, setDrawerOpen] = useState(false)
    const [filter, setFilter] = useState('')

    const toggleShortNames = () =>
        setShortNames(prev => {
            localStorage.setItem(NAV_SHORT_NAMES_STORAGE_KEY, String(!prev))
            return !prev
        })

    const listRef = useRef<HTMLUListElement | null>(null)

    const {data, pending} = useFetch(
        signal =>
            getCompetitions({
                signal,
                path: {eventId},
                query: {sort: JSON.stringify([{field: 'IDENTIFIER', direction: 'ASC'}])},
            }),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('event.competition.competitions'),
                        }),
                    )
                }
            },
            deps: [eventId],
        },
    )

    const competitions = useMemo(() => data?.data ?? [], [data])

    const visible = useMemo(() => {
        const needle = filter.trim().toLowerCase()
        if (!needle) {
            return competitions
        }
        return competitions.filter(c =>
            [c.properties.identifier, c.properties.name, c.properties.shortName]
                .filter(v => v)
                .some(v => v!.toLowerCase().includes(needle)),
        )
    }, [competitions, filter])

    // Bei 30 Rennen liegt das aktuelle sonst irgendwo außerhalb des sichtbaren Ausschnitts.
    // scrollIntoView scheidet aus: es verschiebt zusätzlich das Fenster, und man landet auf der
    // Wettkampfseite bereits vorbeigescrollt. Also nur der Container selbst.
    const scrollActiveIntoView = useCallback(() => {
        const list = listRef.current
        const item = list?.querySelector<HTMLElement>(`[data-competition-id="${competitionId}"]`)
        if (!list || !item) {
            return
        }
        const itemBottom = item.offsetTop + item.offsetHeight
        const outOfView =
            item.offsetTop < list.scrollTop || itemBottom > list.scrollTop + list.clientHeight
        if (outOfView) {
            list.scrollTop = item.offsetTop - (list.clientHeight - item.offsetHeight) / 2
        }
    }, [competitionId])

    // Am Ref statt an einer Abhängigkeitsliste: die Liste hängt je nach Breite und Klappzustand
    // mal im Seitenfluss, mal in der Schublade, mal gar nicht im DOM. Der Ref-Rückruf feuert bei
    // jedem dieser Wechsel von selbst, der Effekt deckt das spätere Eintreffen der Daten ab.
    const setListRef = useCallback(
        (node: HTMLUListElement | null) => {
            listRef.current = node
            scrollActiveIntoView()
        },
        [scrollActiveIntoView],
    )

    // Solange die Seite oben steht, beginnt die Leiste nicht am Fensterrand, sondern unter der
    // Kopfzeile. Eine Höhe von 100vh ragt dann unten heraus und die letzten Rennen sind
    // unerreichbar, wenn der Inhalt rechts zu kurz zum Scrollen ist. Also den eigenen Abstand
    // messen statt ihn zu schätzen - er hängt an Titelumbruch, Schriftgröße und Kopfleiste.
    const railRef = useRef<HTMLDivElement | null>(null)
    const [railOffset, setRailOffset] = useState(STICKY_TOP)

    const listOpen = isNarrow ? drawerOpen : !collapsed

    const toggleList = () => {
        if (isNarrow) {
            setDrawerOpen(prev => !prev)
        } else {
            setCollapsed(prev => {
                localStorage.setItem(NAV_COLLAPSED_STORAGE_KEY, String(!prev))
                return !prev
            })
        }
    }

    // Ein einzelner Wettkampf ist keine Liste - dann bleibt nur der Weg zurück.
    // Angemeldeten vorbehalten: abgemeldete Besucher sehen die öffentliche Wettkampfseite
    // ohne Leiste.
    const showList = user.loggedIn && competitions.length > 1

    useEffect(() => {
        const measure = () => {
            const node = railRef.current
            if (node) {
                setRailOffset(node.getBoundingClientRect().top + window.scrollY)
            }
        }
        measure()
        window.addEventListener('resize', measure)
        return () => window.removeEventListener('resize', measure)
    }, [showList, listOpen, isNarrow])

    // railOffset gehört in die Abhängigkeiten: das Messen ändert die Höhe der Liste, und der
    // Ausschnitt wäre sonst noch mit der Höhe von davor berechnet.
    useEffect(scrollActiveIntoView, [scrollActiveIntoView, visible.length, railOffset])

    const toggleIcon = isNarrow ? (
        <FormatListBulleted />
    ) : listOpen ? (
        <ChevronLeft />
    ) : (
        <ChevronRight />
    )

    const listContent = (
        <>
            <Stack
                direction={'row'}
                spacing={1}
                sx={{alignItems: 'center', justifyContent: 'space-between', px: 2, pt: 1.5}}>
                <Typography variant={'overline'} color={'text.secondary'} noWrap>
                    {t('event.competition.competitions')} (
                    {visible.length === competitions.length
                        ? competitions.length
                        : `${visible.length}/${competitions.length}`}
                    )
                </Typography>
                <Stack direction={'row'} spacing={0.5} sx={{alignItems: 'center', flex: 'none'}}>
                    {/* Am Regattatag sucht man das Rennen am Kurznamen ("CM 4x+"), nicht am
                        ausgeschriebenen Namen - der ist in der schmalen Leiste ohnehin
                        abgeschnitten. Wer ihn braucht, schaltet zurück oder fährt den Eintrag an,
                        dessen Titel weiterhin den vollen Namen trägt. */}
                    <IconButton
                        size={'small'}
                        onClick={toggleShortNames}
                        color={shortNames ? 'primary' : 'default'}
                        aria-pressed={shortNames}
                        title={t(
                            shortNames
                                ? 'event.competition.navigation.showLongNames'
                                : 'event.competition.navigation.showShortNames',
                        )}>
                        {shortNames ? (
                            <Subject fontSize={'small'} />
                        ) : (
                            <ShortText fontSize={'small'} />
                        )}
                    </IconButton>
                    {/* In der Schublade (schmal) braucht die Liste ihren eigenen
                        Schließen-Knopf — bis zum 12.08.2026 saß er in der Kopfzeile des
                        entfallenen Rennen/Zeitplan-Umschalters. */}
                    {isNarrow && (
                        <IconButton
                            size={'small'}
                            onClick={() => setDrawerOpen(false)}
                            aria-label={t('common.close')}>
                            <Close fontSize={'small'} />
                        </IconButton>
                    )}
                </Stack>
            </Stack>
            <Box sx={{px: 1.5, pb: 1.5}}>
                <TextField
                    size={'small'}
                    fullWidth
                    value={filter}
                    onChange={e => setFilter(e.target.value)}
                    placeholder={t('event.competition.navigation.filter')}
                    slotProps={{
                        input: {
                            startAdornment: (
                                <InputAdornment position={'start'}>
                                    <Search fontSize={'small'} />
                                </InputAdornment>
                            ),
                        },
                    }}
                />
            </Box>
            <Divider />
            <List dense ref={setListRef} sx={{flex: 1, overflowY: 'auto', py: 0}}>
                {visible.map(c => {
                    const isActive = c.id === competitionId
                    return (
                        <Link
                            key={c.id}
                            to={'/event/$eventId/competition/$competitionId'}
                            params={{eventId, competitionId: c.id}}
                            search={{tab: activeTab === 'general' ? undefined : activeTab}}
                            onClick={() => setDrawerOpen(false)}
                            // Lange Namen sind in der schmalen Leiste abgeschnitten; der Titel
                            // zeigt sie beim Überfahren und benennt den Link für Vorleseprogramme.
                            title={competitionLabelName(c.properties.identifier, c.properties.name)}>
                            <ListItem disablePadding data-competition-id={c.id}>
                                <ListItemButton
                                    selected={isActive}
                                    sx={{
                                        borderLeft: 3,
                                        borderColor: isActive ? 'primary.main' : 'transparent',
                                    }}>
                                    {/* Kurznamen sind kurz genug, um neben der Rennnummer zu
                                        stehen - eine Zeile statt zwei, damit mehr Rennen ohne
                                        Scrollen sichtbar sind. Der lange Name braucht die zweite
                                        Zeile weiterhin für sich. */}
                                    <ListItemText
                                        primary={
                                            shortNames ? (
                                                <Stack
                                                    component={'span'}
                                                    direction={'row'}
                                                    spacing={1}
                                                    sx={{alignItems: 'baseline', minWidth: 0}}>
                                                    <Box component={'span'} sx={{flex: 'none'}}>
                                                        {c.properties.identifier}
                                                    </Box>
                                                    {/* Ohne gepflegten Kurznamen steht hier der
                                                        lange Name - besser abgeschnitten als
                                                        namenlos. */}
                                                    <Box
                                                        component={'span'}
                                                        sx={{
                                                            color: 'text.secondary',
                                                            fontWeight: 'normal',
                                                            overflow: 'hidden',
                                                            textOverflow: 'ellipsis',
                                                        }}>
                                                        {c.properties.shortName ??
                                                            c.properties.name}
                                                    </Box>
                                                </Stack>
                                            ) : (
                                                c.properties.identifier
                                            )
                                        }
                                        secondary={shortNames ? undefined : c.properties.name}
                                        slotProps={{
                                            primary: {
                                                fontWeight: isActive ? 'bold' : 'medium',
                                                noWrap: true,
                                            },
                                            secondary: {noWrap: true, variant: 'body2'},
                                        }}
                                    />
                                </ListItemButton>
                            </ListItem>
                        </Link>
                    )
                })}
                {visible.length === 0 && (
                    <ListItem>
                        <ListItemText
                            secondary={pending ? <Throbber /> : t('common.noResults')}
                        />
                    </ListItem>
                )}
            </List>
        </>
    )

    return (
        <Stack spacing={2}>
            <Stack direction={'row'} spacing={1} sx={{alignItems: 'center', flexWrap: 'wrap'}}>
                {showList && (
                    <Button size={'small'} startIcon={toggleIcon} onClick={toggleList}>
                        {t('event.competition.competitions')}
                    </Button>
                )}
                <Link to={'/event/$eventId'} params={{eventId}} search={{tab: 'competitions'}}>
                    <Button
                        size={'small'}
                        startIcon={<ArrowBack />}
                        title={t('event.competition.navigation.backToEvent')}>
                        {eventName}
                    </Button>
                </Link>
            </Stack>
            <Box sx={{display: 'flex', gap: 2, alignItems: 'flex-start'}}>
                {showList && !isNarrow && listOpen && (
                    <Paper
                        ref={railRef}
                        variant={'outlined'}
                        sx={{
                            // Die Breite folgt der Kurzform-Wahl (kein eigener Schalter):
                            // Kürzel brauchen die volle Leiste nicht.
                            ...(shortNames ? RAIL_WIDTH_SHORT_SX : RAIL_WIDTH_SX),
                            transition: 'width 0.2s ease',
                            flex: 'none',
                            position: 'sticky',
                            top: `${STICKY_TOP}px`,
                            maxHeight: `calc(100vh - ${railOffset + STICKY_TOP}px)`,
                            display: 'flex',
                            flexDirection: 'column',
                        }}>
                        {listContent}
                    </Paper>
                )}
                <Box sx={{flex: 1, minWidth: 0}}>{children}</Box>
            </Box>
            {showList && isNarrow && (
                <Drawer
                    variant={'temporary'}
                    open={drawerOpen}
                    onClose={() => setDrawerOpen(false)}
                    className={'ready2race'}
                    sx={{
                        '& .MuiDrawer-paper': {
                            boxSizing: 'border-box',
                            // Auch die Schublade folgt der Kurzform-Wahl.
                            width: shortNames ? LIST_WIDTH_SHORT : LIST_WIDTH,
                            transition: 'width 0.2s ease',
                            display: 'flex',
                            flexDirection: 'column',
                        },
                    }}>
                    {listContent}
                </Drawer>
            )}
        </Stack>
    )
}

export default CompetitionNavigation
