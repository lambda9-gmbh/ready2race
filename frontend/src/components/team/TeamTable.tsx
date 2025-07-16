import {PaginationParameters} from '@utils/ApiUtils.ts'
import {useTranslation} from 'react-i18next'
import {GridActionsCellItem, GridPaginationModel, GridRenderCellParams, GridSortModel} from '@mui/x-data-grid'
import EntityTable, {ExtendedGridColDef} from '@components/EntityTable.tsx'
import {BaseEntityTableProps} from '@utils/types.ts'
import {eventIndexRoute} from '@routes'
import {TeamStatusWithParticipantsDto} from '@api/types.gen.ts'
import {checkInTeam, checkOutTeam, getEventTeams} from '@api/sdk.gen.ts'
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Box,
    Chip,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    Typography
} from '@mui/material'
import {ExpandMore, Login, Logout} from '@mui/icons-material'
import {useState} from 'react'
import {useFeedback} from '@utils/hooks.ts'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | { value: number; label: string })[] = [10, 25, 50]
const initialSort: GridSortModel = [{ field: 'teamName', sort: 'asc' }]

const TeamTable = (props: BaseEntityTableProps<TeamStatusWithParticipantsDto>) => {
    const { t } = useTranslation()
    const { eventId } = eventIndexRoute.useParams()
    const feedback = useFeedback()
    const [loadingTeams, setLoadingTeams] = useState<Set<string>>(new Set())

    const dataRequest = async (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return await getEventTeams({
            signal,
            path: {eventId},
            query: {
                limit: paginationParameters.limit,
                offset: paginationParameters.offset,
                sort: paginationParameters.sort,
                search: paginationParameters.search
            }
        })
    }

    const handleCheckIn = async (team: TeamStatusWithParticipantsDto) => {
        setLoadingTeams(prev => new Set(prev).add(team.competitionRegistrationId))
        try {
            const result = await checkInTeam({
                path: { teamId: team.competitionRegistrationId },
                body: { eventId }
            })
            
            if (result.data) {
                feedback.success(t('team.checkIn.success'))
                props.reloadData()
            }
        } catch (error) {
            feedback.error(t('team.checkIn.error'))
        } finally {
            setLoadingTeams(prev => {
                const newSet = new Set(prev)
                newSet.delete(team.competitionRegistrationId)
                return newSet
            })
        }
    }

    const handleCheckOut = async (team: TeamStatusWithParticipantsDto) => {
        setLoadingTeams(prev => new Set(prev).add(team.competitionRegistrationId))
        try {
            const result = await checkOutTeam({
                path: { teamId: team.competitionRegistrationId },
                body: { eventId }
            })
            
            if (result.data) {
                feedback.success(t('team.checkOut.success'))
                props.reloadData()
            }
        } catch (error) {
            feedback.error(t('team.checkOut.error'))
        } finally {
            setLoadingTeams(prev => {
                const newSet = new Set(prev)
                newSet.delete(team.competitionRegistrationId)
                return newSet
            })
        }
    }

    const customEntityActions = (team: TeamStatusWithParticipantsDto) => {
        const isLoading = loadingTeams.has(team.competitionRegistrationId)
        
        if (team.currentStatus === 'ENTRY') {
            return [
                <GridActionsCellItem
                    key="checkout"
                    icon={<Logout />}
                    label={t('team.checkOutText')}
                    onClick={() => handleCheckOut(team)}
                    disabled={isLoading}
                />
            ]
        } else {
            return [
                <GridActionsCellItem
                    key="checkin"
                    icon={<Login />}
                    label={t('team.checkInText')}
                    onClick={() => handleCheckIn(team)}
                    disabled={isLoading}
                />
            ]
        }
    }

    const columns: ExtendedGridColDef<TeamStatusWithParticipantsDto>[] = [
        {
            field: 'teamName',
            headerName: t('team.name'),
            minWidth: 200,
            flex: 1,
            valueGetter: (_, team) => team.teamName,
        },
        {
            field: 'clubName',
            headerName: t('club.club'),
            minWidth: 150,
            valueGetter: (_, team) => team.clubName,
        },
        {
            field: 'currentStatus',
            headerName: t('team.statusText'),
            minWidth: 120,
            renderCell: (params: GridRenderCellParams<TeamStatusWithParticipantsDto>) => {
                const isIn = params.row.currentStatus === 'ENTRY'
                return (
                    <Chip
                        label={isIn ? (t('team.status.in')) : (t('team.status.out'))}
                        color={isIn ? 'success' : 'default'}
                        size="small"
                    />
                )
            },
        },
        {
            field: 'lastScanAt',
            headerName: t('team.lastScan'),
            minWidth: 180,
            valueGetter: (_, team) => team.lastScanAt ? new Date(team.lastScanAt).toLocaleString() : '-',
        },
        {
            field: 'participants',
            headerName: t('team.participants'),
            minWidth: 300,
            flex: 2,
            renderCell: (params: GridRenderCellParams<TeamStatusWithParticipantsDto>) => {
                const team = params.row
                return (
                    <Box sx={{ width: '100%', py: 1 }}>
                        <Accordion elevation={0}>
                            <AccordionSummary
                                expandIcon={<ExpandMore />}
                                sx={{ minHeight: 0, '& .MuiAccordionSummary-content': { margin: 0 } }}
                            >
                                <Typography variant="body2">
                                    {t('team.participantCount', { count: team.participants.length })}
                                </Typography>
                            </AccordionSummary>
                            <AccordionDetails>
                                <Table size="small">
                                    <TableHead>
                                        <TableRow>
                                            <TableCell>{t('entity.name')}</TableCell>
                                            <TableCell>{t('club.participant.year')}</TableCell>
                                            <TableCell>{t('entity.gender')}</TableCell>
                                            <TableCell>{t('club.participant.role')}</TableCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {team.participants.map((participant) => (
                                            <TableRow key={participant.participantId}>
                                                <TableCell>{`${participant.firstname} ${participant.lastname}`}</TableCell>
                                                <TableCell>{participant.year}</TableCell>
                                                <TableCell>{participant.gender}</TableCell>
                                                <TableCell>{participant.role || '-'}</TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            </AccordionDetails>
                        </Accordion>
                    </Box>
                )
            },
        },
    ]

    return (
        <EntityTable
            {...props}
            parentResource={'EVENT'}
            initialPagination={initialPagination}
            pageSizeOptions={pageSizeOptions}
            initialSort={initialSort}
            columns={columns}
            dataRequest={dataRequest}
            customEntityActions={customEntityActions}
            withSearch={true}
        />
    )
}

export default TeamTable