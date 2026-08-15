import {Trans, useTranslation} from 'react-i18next'
import {GridActionsCellItem, GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {clubIndexRoute} from '@routes'
import {deleteClubParticipant, getClubParticipants, ParticipantDto} from '../../api'
import {BaseEntityTableProps} from '@utils/types.ts'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import EntityTable from '../EntityTable.tsx'
import {Check, Groups, Upload} from '@mui/icons-material'
import {Button} from '@mui/material'
import ParticipantImportDialog from '@components/participant/ParticipantImportDialog.tsx'
import ParticipantClubsDialog from '@components/participant/ParticipantClubsDialog.tsx'
import {useState} from 'react'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'firstname', sort: 'asc'}]

const ParticipantTable = (props: BaseEntityTableProps<ParticipantDto>) => {
    const {t} = useTranslation()
    const [showImportDialog, setShowImportDialog] = useState(false)
    const [clubsDialogFor, setClubsDialogFor] = useState<ParticipantDto | null>(null)

    const {clubId} = clubIndexRoute.useParams()

    /**
     * Gehört die Person diesem Verein selbst — oder ist sie nur Gast?
     *
     * Seit der Mehrfach-Zugehörigkeit (Migration V202608142000) stehen in dieser Liste auch
     * Personen, deren Stammverein ein anderer ist. Melden darf man sie, ändern nicht. Das
     * Backend weist entsprechende Versuche ohnehin ab; hier werden die Schaltflächen erst gar
     * nicht angeboten, damit niemand gegen eine unsichtbare Wand läuft.
     */
    const isHomeClub = (p: ParticipantDto) => p.clubId === clubId

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getClubParticipants({
            signal,
            path: {clubId},
            query: {...paginationParameters},
        })
    }

    const deleteRequest = (dto: ParticipantDto) => {
        return deleteClubParticipant({path: {clubId, participantId: dto.id}})
    }

    const columns: GridColDef<ParticipantDto>[] = [
        {
            field: 'firstname',
            headerName: t('entity.firstname'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'lastname',
            headerName: t('entity.lastname'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'gender',
            headerName: t('entity.gender'),
            minWidth: 100,
            flex: 1,
        },
        {
            field: 'year',
            headerName: t('club.participant.year'),
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
            // Stammverein zuerst, dann die weiteren Vereine. In der eigenen Liste steht damit
            // ohne weiteres Klicken, wer die Person sonst noch melden darf; in der Liste eines
            // Zweitvereins steht vorne, wem sie gehört.
            field: 'clubs',
            headerName: t('club.participant.clubs'),
            minWidth: 200,
            flex: 1,
            sortable: false,
            valueGetter: (_, row) =>
                [row.clubName, ...row.additionalClubs.map(c => c.name)].join(', '),
        },
        {
            field: 'usedInRegistration',
            headerName: t('club.participant.usedInRegistration'),
            renderCell: ({value}) => (value ? <Check /> : <></>),
        },
    ]

    return (
        <>
            <EntityTable
                {...props}
                deletableIf={p => !p.usedInRegistration && isHomeClub(p)}
                editableIf={isHomeClub}
                customEntityActions={entity =>
                    isHomeClub(entity)
                        ? [
                              <GridActionsCellItem
                                  icon={<Groups />}
                                  label={t('club.participant.additionalClubs.manage')}
                                  onClick={() => setClubsDialogFor(entity)}
                                  showInMenu={true}
                              />,
                          ]
                        : []
                }
                parentResource={'CLUB'}
                initialPagination={initialPagination}
                pageSizeOptions={pageSizeOptions}
                initialSort={initialSort}
                columns={columns}
                dataRequest={dataRequest}
                entityName={t('club.participant.title')}
                deleteRequest={deleteRequest}
                customTableActions={
                    <Button
                        variant={'outlined'}
                        startIcon={<Upload />}
                        onClick={() => setShowImportDialog(true)}>
                        <Trans i18nKey={'club.participant.import'} />
                    </Button>
                }
            />
            <ParticipantImportDialog
                open={showImportDialog}
                onClose={() => setShowImportDialog(false)}
                reloadParticipants={props.reloadData}
            />
            <ParticipantClubsDialog
                open={clubsDialogFor !== null}
                onClose={() => setClubsDialogFor(null)}
                clubId={clubId}
                participant={clubsDialogFor}
                reload={() => {
                    setClubsDialogFor(null)
                    props.reloadData()
                }}
            />
        </>
    )
}

export default ParticipantTable
