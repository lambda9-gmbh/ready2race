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
    ToggleButton,
    ToggleButtonGroup,
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
import CompetitionScheduleRail from '@components/event/competition/CompetitionScheduleRail.tsx'
import {useUser} from '@contexts/user/UserContext.ts'
import {readEventGlobal} from '@authorization/privileges.ts'
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

/** Womit die Leiste gefüllt ist: der Rennliste oder dem Zeitplan. Am Regattatag springt man
 * zwischen beidem hin und her, deshalb bleibt die zuletzt gewählte Seite stehen. */
export type NavMode = 'competitions' | 'schedule'

const NAV_MODE_STORAGE_KEY = 'competition_nav_mode'

const storedNavMode = (): NavMode =>
    localStorage.getItem(NAV_MODE_STORAGE_KEY) === 'schedule' ? 'schedule' : 'competitions'

const LIST_WIDTH = 240
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
    const [mode, setMode] = useState<NavMode>(storedNavMode)

    const switchMode = (next: NavMode) => {
        setMode(next)
        localStorage.setItem(NAV_MODE_STORAGE_KEY, next)
    }
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
    // Der Zeitplan-Endpunkt verlangt dasselbe Recht wie der Zeitplan-Tab. Ohne diese Prüfung
    // böte die Leiste einen Umschalter an, der nur eine Fehlermeldung und eine leere Liste
    // bringt - und wer den Modus einmal gewählt hat, behielte ihn über den Speicher bei.
    const maySeeSchedule = user.checkPrivilege(readEventGlobal)
    const effectiveMode: NavMode = mode === 'schedule' && !maySeeSchedule ? 'competitions' : mode

    // Angemeldeten vorbehalten: abgemeldete Besucher sehen die öffentliche Wettkampfseite ohne
    // Leiste, und der Zeitplan stünde ihnen ohnehin nicht offen.
    const showList =
        user.loggedIn && (competitions.length > 1 || effectiveMode === 'schedule')

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

    // Der Umschalter steht über beiden Füllungen, damit er beim Wechsel nicht springt. Die
    // Zeitplan-Leiste wird nur eingehängt, wenn sie auch gewählt ist - so lädt der Zeitplan
    // nicht bei jedem Aufruf einer Wettkampfseite mit.
    const railContent = (
        <>
            <Stack
                direction={'row'}
                spacing={1}
                sx={{alignItems: 'center', px: 1.5, pt: 1.5, pb: 1}}>
                <ToggleButtonGroup
                    size={'small'}
                    exclusive
                    fullWidth
                    value={effectiveMode}
                    onChange={(_, next: NavMode | null) => next && switchMode(next)}>
                    <ToggleButton value={'competitions'}>
                        {t('event.competition.navigation.modeCompetitions')}
                    </ToggleButton>
                    {maySeeSchedule && (
                        <ToggleButton value={'schedule'}>
                            {t('event.competition.navigation.modeSchedule')}
                        </ToggleButton>
                    )}
                </ToggleButtonGroup>
                {isNarrow && (
                    <IconButton
                        size={'small'}
                        onClick={() => setDrawerOpen(false)}
                        aria-label={t('common.close')}
                        sx={{flex: 'none'}}>
                        <Close fontSize={'small'} />
                    </IconButton>
                )}
            </Stack>
            <Divider />
            {effectiveMode === 'competitions' ? (
                listContent
            ) : (
                <CompetitionScheduleRail
                    eventId={eventId}
                    competitionId={competitionId}
                    activeTab={activeTab}
                    onNavigate={() => setDrawerOpen(false)}
                />
            )}
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
                            width: LIST_WIDTH,
                            flex: 'none',
                            position: 'sticky',
                            top: `${STICKY_TOP}px`,
                            maxHeight: `calc(100vh - ${railOffset + STICKY_TOP}px)`,
                            display: 'flex',
                            flexDirection: 'column',
                        }}>
                        {railContent}
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
                            width: LIST_WIDTH,
                            display: 'flex',
                            flexDirection: 'column',
                        },
                    }}>
                    {railContent}
                </Drawer>
            )}
        </Stack>
    )
}

export default CompetitionNavigation
