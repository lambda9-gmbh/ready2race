import {useTranslation} from 'react-i18next'
import {GridActionsCellItem, GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {competitionRoute, eventRoute} from '@routes'
import {
    checkInCompetitionRegistration,
    checkOutCompetitionRegistration,
    CompetitionRegistrationTeamDto,
    deleteCompetitionRegistration,
    getCompetitionRegistrations,
} from '../../../../api'
import {BaseEntityTableProps} from '@utils/types.ts'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {Fragment, useMemo, useState} from 'react'
import EntityTable from '@components/EntityTable.tsx'
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Box,
    Chip,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    Typography
} from '@mui/material'
import {ExpandMore, Login, Logout} from '@mui/icons-material'
import {useFeedback} from '@utils/hooks.ts'
import {format} from 'date-fns'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'clubName', sort: 'asc'}]

const CompetitionRegistrationTable = (
    props: BaseEntityTableProps<CompetitionRegistrationTeamDto>,
) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const [loadingTeams, setLoadingTeams] = useState<Set<string>>(new Set())

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getCompetitionRegistrations({
            signal,
            path: {eventId, competitionId},
            query: {...paginationParameters},
        })
    }

    const deleteRequest = (dto: CompetitionRegistrationTeamDto) =>
        deleteCompetitionRegistration({
            path: {
                eventId: eventId,
                competitionId: competitionId,
                competitionRegistrationId: dto.id,
            },
        })

    const handleCheckIn = async (team: CompetitionRegistrationTeamDto) => {
        setLoadingTeams(prev => new Set(prev).add(team.id))
        try {
            const result = await checkInCompetitionRegistration({
                path: {
                    eventId,
                    competitionId,
                    competitionRegistrationId: team.id,
                },
                body: { eventId }
            })
            
            if (result.data?.success) {
                feedback.success(t('team.checkIn.success'))
                props.reloadData()
            } else {
                feedback.error(result.data?.message || t('team.checkIn.error'))
            }
        } catch (error) {
            feedback.error(t('team.checkIn.error'))
        } finally {
            setLoadingTeams(prev => {
                const newSet = new Set(prev)
                newSet.delete(team.id)
                return newSet
            })
        }
    }

    const handleCheckOut = async (team: CompetitionRegistrationTeamDto) => {
        setLoadingTeams(prev => new Set(prev).add(team.id))
        try {
            const result = await checkOutCompetitionRegistration({
                path: {
                    eventId,
                    competitionId,
                    competitionRegistrationId: team.id,
                },
                body: { eventId }
            })
            
            if (result.data?.success) {
                feedback.success(t('team.checkOut.success'))
                props.reloadData()
            } else {
                feedback.error(result.data?.message || t('team.checkOut.error'))
            }
        } catch (error) {
            feedback.error(t('team.checkOut.error'))
        } finally {
            setLoadingTeams(prev => {
                const newSet = new Set(prev)
                newSet.delete(team.id)
                return newSet
            })
        }
    }

    const customEntityActions = (team: CompetitionRegistrationTeamDto) => {
        const isLoading = loadingTeams.has(team.id)
        const isCheckedIn = team.currentStatus === 'ENTRY'
        
        return [
            <GridActionsCellItem
                key="check-in"
                icon={<Login />}
                label={t('team.checkInText')}
                onClick={() => handleCheckIn(team)}
                disabled={isLoading || isCheckedIn}
                showInMenu
            />,
            <GridActionsCellItem
                key="check-out"
                icon={<Logout />}
                label={t('team.checkOutText')}
                onClick={() => handleCheckOut(team)}
                disabled={isLoading || !isCheckedIn}
                showInMenu
            />
        ]
    }

    const columns: GridColDef<CompetitionRegistrationTeamDto>[] = useMemo(
        () => [
            {
                field: 'clubName',
                headerName: t('club.club') + ' / ' + t('entity.name'),
                minWidth: 250,
                renderCell: (params) => {
                    const teamName = params.row.name ? ` - ${params.row.name}` : ''
                    return `${params.row.clubName}${teamName}`
                },
            },
            {
                field: 'namedParticipants',
                headerName: t('club.participant.title'),
                flex: 2,
                minWidth: 300,
                sortable: false,
                renderCell: ({row}) => {
                    const totalParticipants = row.namedParticipants.reduce(
                        (acc, np) => acc + np.participants.length,
                        0
                    )
                    return (
                        <Box sx={{ width: '100%', py: 1 }}>
                            <Accordion elevation={0}>
                                <AccordionSummary
                                    expandIcon={<ExpandMore />}
                                    sx={{ minHeight: 0, '& .MuiAccordionSummary-content': { margin: 0 } }}
                                >
                                    <Typography variant="body2">
                                        {t('team.participantCount', { count: totalParticipants })}
                                    </Typography>
                                </AccordionSummary>
                                <AccordionDetails>
                                    <Table size="small">
                                        <TableHead>
                                            <TableRow>
                                                <TableCell>{t('entity.name')}</TableCell>
                                                <TableCell>{t('event.competition.namedParticipant.namedParticipant')}</TableCell>
                                            </TableRow>
                                        </TableHead>
                                        <TableBody>
                                            {row.namedParticipants.map(np => 
                                                np.participants.map((participant) => (
                                                    <TableRow key={participant.id}>
                                                        <TableCell>{`${participant.firstname} ${participant.lastname}`}</TableCell>
                                                        <TableCell>{np.namedParticipantName}</TableCell>
                                                    </TableRow>
                                                ))
                                            )}
                                        </TableBody>
                                    </Table>
                                </AccordionDetails>
                            </Accordion>
                        </Box>
                    )
                },
            },
            {
                field: 'statusAndScan',
                headerName: t('team.statusText'),
                minWidth: 220,
                renderCell: (params) => {
                    const row = params.row
                    if (!row.currentStatus) return '-'
                    const isIn = row.currentStatus === 'ENTRY'
                    const scanTime = row.lastScanAt ? new Date(row.lastScanAt).toLocaleString() : ''
                    return (
                        <Stack spacing={0.5}>
                            <Chip
                                label={isIn ? t('team.status.in') : t('team.status.out')}
                                color={isIn ? 'success' : 'default'}
                                size="small"
                            />
                            {scanTime && (
                                <Typography variant="caption" color="text.secondary">
                                    {scanTime}
                                </Typography>
                            )}
                        </Stack>
                    )
                },
            },
            {
                field: 'optionalFees',
                headerName: t('event.registration.optionalFee'),
                sortable: false,
                renderCell: ({row}) => (
                    <Stack>
                        {row.optionalFees.length >= 1
                            ? row.optionalFees.map(f => f.feeName).join(', ')
                            : '-'}
                    </Stack>
                ),
            },
            {
                field: 'updatedAt',
                headerName: t('entity.updatedAt'),
                minWidth: 100,
                maxWidth: 170,
                flex: 1,
                valueGetter: (v: string) => (v ? format(new Date(v), t('format.datetime')) : null),
            },
        ],
        [],
    )

    return (
        <Fragment>
            <EntityTable
                {...props}
                withSearch={false}
                parentResource={'REGISTRATION'}
                initialPagination={initialPagination}
                pageSizeOptions={pageSizeOptions}
                initialSort={initialSort}
                columns={columns}
                dataRequest={dataRequest}
                deleteRequest={deleteRequest}
                customEntityActions={customEntityActions}
                entityName={t('event.registration.registration')}
            />
        </Fragment>
    )
}

export default CompetitionRegistrationTable
