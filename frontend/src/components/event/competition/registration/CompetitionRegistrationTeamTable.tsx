import {useTranslation} from 'react-i18next'
import {GridColDef, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {competitionRoute, eventRoute} from '@routes'
import {BaseEntityTableProps} from '@utils/types.ts'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {useMemo, useRef, useState} from 'react'
import EntityTable from '@components/EntityTable.tsx'
import {
    Box,
    Chip,
    IconButton,
    Link,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    Typography,
} from '@mui/material'
import {Add, CheckCircle, Download, Info, Warning} from '@mui/icons-material'
import QrCodeIcon from '@mui/icons-material/QrCode'
import {format} from 'date-fns'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'
import Cancel from '@mui/icons-material/Cancel'
import {useUser} from '@contexts/user/UserContext.ts'
import ChallengeResultDialog from '@components/event/competition/registration/ChallengeResultDialog.tsx'
import {downloadMatchTeamResultDocument, getCompetitionRegistrationTeams} from '@api/sdk.gen'
import {CompetitionDto, CompetitionRegistrationTeamDto, EventDto} from '@api/types.gen.ts'
import {useFeedback} from '@utils/hooks.ts'
import SelectionMenu from '@components/SelectionMenu.tsx'
import DownloadIcon from '@mui/icons-material/Download'
import {currentlyInTimespan} from '@utils/helpers.ts'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'clubName', sort: 'asc'}]

type Props = BaseEntityTableProps<CompetitionRegistrationTeamDto> & {
    eventData: EventDto
    competitionData: CompetitionDto
}

const CompetitionRegistrationTeamTable = ({eventData, competitionData, ...props}: Props) => {
    const {t} = useTranslation()
    const user = useUser()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const downloadRef = useRef<HTMLAnchorElement>(null)

    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
        return getCompetitionRegistrationTeams({
            signal,
            path: {eventId, competitionId},
            query: {...paginationParameters},
        })
    }

    const challengeResultTypeUnit = eventData.challengeResultType === 'DISTANCE' ? 'm' : ''

    const updateResultScope = user.getPrivilegeScope('UPDATE', 'RESULT')
    const resultSubmissionAllowed =
        updateResultScope === 'GLOBAL' ||
        (updateResultScope === 'OWN' &&
            eventData.allowSelfSubmission &&
            currentlyInTimespan(
                competitionData.properties.challengeConfig?.startAt,
                competitionData.properties.challengeConfig?.endAt,
            ))

    const columns: GridColDef<CompetitionRegistrationTeamDto>[] = useMemo(
        () => [
            {
                field: 'clubName',
                headerName: t('club.club'),
                minWidth: 200,
            },
            {
                field: 'name',
                headerName: t('entity.name'),
                valueGetter: value => value ?? '-',
            },
            {
                field: 'namedParticipants',
                headerName: t('event.registration.teamMembers'),
                flex: 2,
                minWidth: 550,
                sortable: false,
                renderCell: ({row}) => {
                    return (
                        <Table size="small">
                            <TableHead>
                                <TableRow>
                                    <TableCell sx={{width: '30%'}}>{t('entity.name')}</TableCell>
                                    <TableCell sx={{width: '25%'}}>
                                        {t('event.competition.namedParticipant.namedParticipant')}
                                    </TableCell>
                                    <TableCell sx={{width: '15%'}}>{t('qrCode.qrCode')}</TableCell>
                                    <TableCell sx={{width: '15%'}}>
                                        {t('club.participant.tracking.status')}
                                    </TableCell>
                                    <TableCell sx={{width: '15%'}}>
                                        {t('event.participantRequirement.approved')}
                                    </TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {row.namedParticipants.map(np =>
                                    np.participants.map(participant => (
                                        <TableRow key={participant.id}>
                                            <TableCell
                                                sx={{
                                                    width: '30%',
                                                }}>{`${participant.firstname} ${participant.lastname}`}</TableCell>
                                            <TableCell sx={{width: '25%'}}>
                                                {np.namedParticipantName}
                                            </TableCell>
                                            <TableCell sx={{width: '15%'}}>
                                                {participant.qrCodeId ? (
                                                    <HtmlTooltip
                                                        title={
                                                            <Box sx={{p: 1}}>
                                                                <Typography
                                                                    fontWeight={'bold'}
                                                                    gutterBottom>
                                                                    {t('qrCode.value')}:
                                                                </Typography>
                                                                <Typography>
                                                                    {participant.qrCodeId}
                                                                </Typography>
                                                            </Box>
                                                        }>
                                                        <QrCodeIcon />
                                                    </HtmlTooltip>
                                                ) : row.namedParticipants
                                                      .flatMap(np => np.participants)
                                                      .some(p => p.qrCodeId !== undefined) ? (
                                                    <></>
                                                ) : (
                                                    <HtmlTooltip
                                                        title={
                                                            <Typography>
                                                                {t('qrCode.noQrCodeAssigned')}
                                                            </Typography>
                                                        }>
                                                        <Warning color={'warning'} />
                                                    </HtmlTooltip>
                                                )}
                                            </TableCell>
                                            <TableCell sx={{width: '15%'}}>
                                                {participant.currentStatus !== undefined && (
                                                    <HtmlTooltip
                                                        title={
                                                            <>
                                                                {participant.lastScanAt && (
                                                                    <>
                                                                        <Typography variant={'h6'}>
                                                                            {t(
                                                                                'club.participant.tracking.lastScan.at',
                                                                            )}
                                                                        </Typography>
                                                                        <Typography>
                                                                            {format(
                                                                                new Date(
                                                                                    participant.lastScanAt,
                                                                                ),
                                                                                t(
                                                                                    'format.datetime',
                                                                                ),
                                                                            )}
                                                                        </Typography>
                                                                    </>
                                                                )}
                                                                {participant.lastScanBy && (
                                                                    <Typography>
                                                                        {t('common.by')}:{' '}
                                                                        {
                                                                            participant.lastScanBy
                                                                                .firstname
                                                                        }{' '}
                                                                        {
                                                                            participant.lastScanBy
                                                                                .lastname
                                                                        }
                                                                    </Typography>
                                                                )}
                                                            </>
                                                        }>
                                                        <Chip
                                                            label={
                                                                participant.currentStatus ===
                                                                'ENTRY'
                                                                    ? t(
                                                                          'club.participant.tracking.in',
                                                                      )
                                                                    : t(
                                                                          'club.participant.tracking.out',
                                                                      )
                                                            }
                                                            color={
                                                                participant.currentStatus ===
                                                                'ENTRY'
                                                                    ? 'success'
                                                                    : 'default'
                                                            }
                                                            size="small"
                                                        />
                                                    </HtmlTooltip>
                                                )}
                                            </TableCell>
                                            <TableCell sx={{width: '15%'}}>
                                                <Stack
                                                    direction={'row'}
                                                    spacing={1}
                                                    alignItems={'center'}>
                                                    <Typography>
                                                        {
                                                            participant
                                                                .participantRequirementsChecked
                                                                .length
                                                        }
                                                        /
                                                        {row.globalParticipantRequirements.length +
                                                            np.participantRequirements.length}{' '}
                                                    </Typography>
                                                    {row.globalParticipantRequirements.length +
                                                        np.participantRequirements.length >
                                                        0 && (
                                                        <HtmlTooltip
                                                            placement={'right'}
                                                            title={
                                                                <Stack spacing={1} p={1}>
                                                                    {[
                                                                        ...row.globalParticipantRequirements.map(
                                                                            gpr => ({
                                                                                ...gpr,
                                                                                qrCodeRequired:
                                                                                    false,
                                                                            }),
                                                                        ),
                                                                        ...np.participantRequirements,
                                                                    ].map(req => {
                                                                        const note =
                                                                            participant.participantRequirementsChecked.find(
                                                                                c =>
                                                                                    c.id === req.id,
                                                                            )?.note
                                                                        return (
                                                                            <Stack
                                                                                direction={'row'}
                                                                                spacing={1}
                                                                                key={req.id}>
                                                                                {participant.participantRequirementsChecked.some(
                                                                                    c =>
                                                                                        c.id ===
                                                                                        req.id,
                                                                                ) ? (
                                                                                    <CheckCircle
                                                                                        color={
                                                                                            'success'
                                                                                        }
                                                                                    />
                                                                                ) : (
                                                                                    <Cancel
                                                                                        color={
                                                                                            'error'
                                                                                        }
                                                                                    />
                                                                                )}
                                                                                <Typography>
                                                                                    {req.name}{' '}
                                                                                    {req.optional
                                                                                        ? ` (${t('entity.optional')})`
                                                                                        : ''}
                                                                                    {req.qrCodeRequired &&
                                                                                        ' (QR)'}
                                                                                    {note &&
                                                                                        ` [ ${note} ]`}
                                                                                </Typography>
                                                                            </Stack>
                                                                        )
                                                                    })}
                                                                </Stack>
                                                            }>
                                                            <Info
                                                                color={'info'}
                                                                fontSize={'small'}
                                                            />
                                                        </HtmlTooltip>
                                                    )}
                                                </Stack>
                                            </TableCell>
                                        </TableRow>
                                    )),
                                )}
                            </TableBody>
                        </Table>
                    )
                },
            },
            ...(eventData.challengeEvent
                ? [
                      {
                          field: 'challengeResultValue',
                          headerName: t(
                              'event.competition.execution.results.challenge.challengeResults',
                          ),
                          minWidth: 150,
                          sortable: false,
                          renderCell: ({row}: {row: CompetitionRegistrationTeamDto}) => {
                              const challengeResultDocuments = eventData.challengeEvent
                                  ? Object.entries(row.challengeResultDocuments ?? {}).map(
                                        ([key, value]) => {
                                            return {id: key, fileName: value}
                                        },
                                    )
                                  : []
                              return (
                                  <Box
                                      sx={{
                                          display: 'flex',
                                          flexDirection: 'column',
                                          width: 1,
                                          height: 1,
                                          justifyContent: 'center',
                                          alignItems: 'center',
                                      }}>
                                      {row.challengeResultValue ? (
                                          <>
                                              <Typography variant={'body2'} color={'textSecondary'}>
                                                  {row.ratingCategory?.name ?? ''}
                                              </Typography>
                                              <Typography>
                                                  {row.challengeResultValue}{' '}
                                                  {challengeResultTypeUnit}
                                              </Typography>
                                              {challengeResultDocuments.length > 0 && (
                                                  <SelectionMenu
                                                      keyLabel={'challenge-team-result-doc'}
                                                      buttonContent={<Download />}
                                                      onSelectItem={async (docId: string) => {
                                                          const docName =
                                                              row.challengeResultDocuments?.[docId]
                                                          if (!docName) return
                                                          void handleDownloadResultDocument(
                                                              docId,
                                                              docName,
                                                          )
                                                      }}
                                                      items={challengeResultDocuments.map(doc => ({
                                                          id: doc.id,
                                                          label: doc.fileName,
                                                      }))}
                                                      itemIcon={<DownloadIcon color={'primary'} />}
                                                      anchor={{
                                                          button: {
                                                              vertical: 'top',
                                                              horizontal: 'right',
                                                          },
                                                          menu: {
                                                              vertical: 'top',
                                                              horizontal: 'right',
                                                          },
                                                      }}
                                                  />
                                              )}
                                          </>
                                      ) : resultSubmissionAllowed ? (
                                          <>
                                              <HtmlTooltip
                                                  title={
                                                      <Typography>
                                                          {t(
                                                              'event.competition.execution.results.challenge.submitResults',
                                                          )}
                                                      </Typography>
                                                  }>
                                                  <IconButton
                                                      onClick={() => openResultsDialog(row)}>
                                                      <Add />
                                                  </IconButton>
                                              </HtmlTooltip>
                                          </>
                                      ) : (
                                          '-'
                                      )}
                                  </Box>
                              )
                          },
                      },
                  ]
                : []),
        ],
        [],
    )

    const handleDownloadResultDocument = async (docId: string, docName: string) => {
        const {data, error} = await downloadMatchTeamResultDocument({
            path: {
                eventId,
                competitionId,
                resultDocumentId: docId,
            },
        })
        const anchor = downloadRef.current

        if (error) {
            feedback.error(t('event.competition.execution.results.document.download.error'))
        } else if (data !== undefined && anchor) {
            anchor.href = URL.createObjectURL(data)
            anchor.download = docName
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }
    }

    const [resultsDialogOpen, setResultsDialogOpen] = useState(false)
    const openResultsDialog = (entity: CompetitionRegistrationTeamDto) => {
        setResultsDialogOpen(true)
        setSelectedTeam(entity)
    }
    const closeResultsDialog = () => {
        setResultsDialogOpen(false)
        setSelectedTeam(null)
    }
    const [selectedTeam, setSelectedTeam] = useState<CompetitionRegistrationTeamDto | null>(null)

    return (
        <>
            <Link ref={downloadRef} display={'none'}></Link>
            <EntityTable
                {...props}
                parentResource={'REGISTRATION'}
                initialPagination={initialPagination}
                pageSizeOptions={pageSizeOptions}
                initialSort={initialSort}
                columns={columns}
                dataRequest={dataRequest}
                entityName={t('event.registration.teams')}
                hideEntityActions
            />
            <ChallengeResultDialog
                dialogOpen={resultsDialogOpen}
                teamDto={selectedTeam}
                closeDialog={closeResultsDialog}
                reloadTeams={props.reloadData}
                resultConfirmationImageRequired={
                    competitionData.properties.challengeConfig?.resultConfirmationImageRequired ??
                    false
                }
                resultType={eventData.challengeResultType}
                outsideOfChallengeTimespan={
                    !currentlyInTimespan(
                        competitionData.properties.challengeConfig?.startAt,
                        competitionData.properties.challengeConfig?.endAt,
                    )
                }
            />
        </>
    )
}

export default CompetitionRegistrationTeamTable
