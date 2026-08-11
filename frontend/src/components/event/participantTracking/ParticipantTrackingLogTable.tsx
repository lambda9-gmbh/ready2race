import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {BaseEntityTableProps} from '@utils/types.ts'
import {ParticipantTrackingDto} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {eventIndexRoute} from '@routes'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {getParticipantTrackings} from '@api/sdk.gen.ts'
import {useMemo, useState} from 'react'
import {format} from 'date-fns'
import EntityTable from '@components/EntityTable.tsx'
import {Box, Chip, MenuItem, Select, Switch} from '@mui/material'
import {GridActionsCellItem} from '@mui/x-data-grid'
import {History} from '@mui/icons-material'
import {useUser} from '@contexts/user/UserContext.ts'
import {updateEventGlobal, updateLiveDashboardGlobal} from '@authorization/privileges.ts'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import ParticipantTrackingDialog from './ParticipantTrackingDialog.tsx'

// 'ALL' steht nur für die Auswahl "kein Filter" - der Request bekommt dann kein scanType.
type ScanTypeFilter = 'ALL' | 'ENTRY' | 'EXIT'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'scannedAt', sort: 'desc'}]

const ParticipantTrackingLogTable = (props: BaseEntityTableProps<ParticipantTrackingDto>) => {
    const {t} = useTranslation()
    const {eventId} = eventIndexRoute.useParams()
    const user = useUser()
    // Dieselben zwei Rechte wie im Backend (siehe participantForEvent.kt).
    const mayEditTracking =
        user.checkPrivilege(updateLiveDashboardGlobal) || user.checkPrivilege(updateEventGlobal)
    const [tracked, setTracked] = useState<ParticipantTrackingDto | null>(null)

    // Die beiden Filter über der Tabelle. Ihre Kombination ist der Sicherheits-Anwendungsfall
    // "wer hat sich aufs Wasser abgemeldet und ist noch nicht zurück?": nur letzter Status +
    // Abgemeldet. Das Backend filtert den Status NACH der Reduktion auf das jüngste Ereignis je
    // Person - wer nach dem Abmelden wieder angemeldet wurde, taucht dann nicht mehr auf.
    //
    // EntityTable lädt nur auf seine eigenen deps (Pagination, Sortierung, Suche, lastRequested)
    // neu - ein geänderter Filter allein löst also nichts aus. Deshalb ruft jede Filteränderung
    // zusätzlich props.reloadData() auf (stößt lastRequested an), wie beim onlyUnverified-Filter
    // in CompetitionRegistrationTeamTable. dataRequest liest beim Neuladen den frischen State.
    const [onlyLatest, setOnlyLatest] = useState(false)
    const [scanTypeFilter, setScanTypeFilter] = useState<ScanTypeFilter>('ALL')

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getParticipantTrackings({
            signal,
            path: {eventId},
            query: {
                ...paginationParameters,
                onlyLatest,
                scanType: scanTypeFilter === 'ALL' ? undefined : scanTypeFilter,
            },
        })
    }

    const columns: GridColDef<ParticipantTrackingDto>[] = useMemo(
        () => [
            {
                field: 'clubName',
                headerName: t('club.club'),
                flex: 1,
                minWidth: 100,
            },
            {
                field: 'firstName',
                headerName: t('entity.firstname'),
                maxWidth: 180,
                minWidth: 100,
                flex: 1,
            },
            {
                field: 'lastName',
                headerName: t('entity.lastname'),
                maxWidth: 180,
                minWidth: 100,
                flex: 1,
            },
            {
                field: 'externalClubName',
                headerName: t('club.participant.externalClub'),
                minWidth: 150,
                flex: 1,
            },
            {
                field: 'scanType',
                headerName: t('club.participant.tracking.status'),
                minWidth: 150,
                flex: 1,
                renderCell: ({row}) => (
                    <Chip
                        label={
                            row.scanType === 'ENTRY'
                                ? t('club.participant.tracking.in')
                                : t('club.participant.tracking.out')
                        }
                        color={row.scanType === 'ENTRY' ? 'success' : 'default'}
                        size="small"
                    />
                ),
            },
            {
                // Der Unterschied, auf den es bei diesem Protokoll ankommt: ein von Hand
                // nachgetragener oder berichtigter Eintrag darf nie wie ein Scan aussehen.
                field: 'source',
                headerName: t('club.participant.tracking.manual.source'),
                minWidth: 130,
                flex: 1,
                sortable: false,
                renderCell: ({row}) =>
                    row.source === 'MANUAL' ? (
                        <Chip
                            size="small"
                            color="warning"
                            label={t('club.participant.tracking.manual.sourceManual')}
                        />
                    ) : row.editCount > 0 ? (
                        <Chip
                            size="small"
                            color="warning"
                            variant="outlined"
                            label={t('club.participant.tracking.manual.sourceQrCorrected')}
                        />
                    ) : (
                        <Chip size="small" label={t('club.participant.tracking.manual.sourceQr')} />
                    ),
            },
            {
                field: 'scannedAt',
                headerName: t('club.participant.tracking.lastScan.at'),
                minWidth: 100,
                maxWidth: 170,
                flex: 1,
                valueGetter: (v: string) => (v ? format(new Date(v), t('format.datetime')) : null),
            },
            {
                field: 'lastScanBy',
                headerName: t('club.participant.tracking.lastScan.by'),
                minWidth: 170,
                flex: 1,
                sortable: false,
                renderCell: ({row}) =>
                    row.lastScanBy ? row.lastScanBy.firstname + ' ' + row.lastScanBy.lastname : '-',
            },
        ],
        [t],
    )

    return (
        <>
            {tracked !== null && (
                <ParticipantTrackingDialog
                    open
                    onClose={() => setTracked(null)}
                    eventId={eventId}
                    participantId={tracked.participantId}
                    participantName={`${tracked.firstName} ${tracked.lastName}`}
                    onChanged={props.reloadData}
                />
            )}
            <EntityTable
                {...props}
                parentResource={'EVENT'}
                initialPagination={initialPagination}
                pageSizeOptions={pageSizeOptions}
                initialSort={initialSort}
                columns={columns}
                dataRequest={dataRequest}
                entityName={t('event.registration.registration')}
                mobileBreakpoint={'lg'}
                customTableActions={
                    <Box
                        display={'flex'}
                        justifyContent={'end'}
                        alignItems={'center'}
                        flexWrap={'wrap'}
                        gap={2}>
                        <FormInputLabel
                            label={t('club.participant.tracking.filter.onlyLatest')}
                            required={true}
                            horizontal
                            reverse>
                            <Switch
                                checked={onlyLatest}
                                onChange={(_, checked) => {
                                    setOnlyLatest(checked)
                                    props.reloadData()
                                }}
                            />
                        </FormInputLabel>
                        <FormInputLabel
                            label={t('club.participant.tracking.status')}
                            required={true}
                            horizontal>
                            <Select
                                size={'small'}
                                value={scanTypeFilter}
                                onChange={e => {
                                    setScanTypeFilter(e.target.value as ScanTypeFilter)
                                    props.reloadData()
                                }}>
                                <MenuItem value={'ALL'}>
                                    {t('club.participant.tracking.filter.status.all')}
                                </MenuItem>
                                <MenuItem value={'ENTRY'}>
                                    {t('club.participant.tracking.filter.status.entry')}
                                </MenuItem>
                                <MenuItem value={'EXIT'}>
                                    {t('club.participant.tracking.filter.status.exit')}
                                </MenuItem>
                            </Select>
                        </FormInputLabel>
                    </Box>
                }
                customEntityActions={entity =>
                    mayEditTracking
                        ? [
                              <GridActionsCellItem
                                  icon={<History />}
                                  label={t('club.participant.tracking.manual.open')}
                                  onClick={() => setTracked(entity)}
                                  showInMenu
                              />,
                          ]
                        : []
                }
            />
        </>
    )
}
export default ParticipantTrackingLogTable
