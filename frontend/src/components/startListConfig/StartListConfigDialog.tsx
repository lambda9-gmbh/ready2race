import {BaseEntityDialogProps} from '@utils/types.ts'
import {
    AddStartListConfigError,
    StartListConfigDto,
    StartListConfigRequest,
    UpdateStartListConfigError,
} from '@api/types.gen.ts'
import EntityDialog from '@components/EntityDialog.tsx'
import {useForm} from 'react-hook-form-mui'
import {useCallback, useState} from 'react'
import {Stack, Tab} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {FormInputCheckbox} from '@components/form/input/FormInputCheckbox.tsx'
import {addStartListConfig, updateStartListConfig} from '@api/sdk.gen.ts'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {useFeedback} from '@utils/hooks.ts'
import {useTranslation} from 'react-i18next'
import TabSelectionContainer from '@components/tab/TabSelectionContainer.tsx'
import {a11yProps} from '@utils/helpers.ts'
import TabPanel from '@components/tab/TabPanel.tsx'

type Form = {
    name: string
    colParticipantFirstname: string
    colParticipantLastname: string
    colParticipantFullname: string
    colParticipantGender: string
    colParticipantRole: string
    colParticipantYear: string
    colParticipantClub: string
    colClubName: string
    colTeamName: string
    colTeamStartNumber: string
    colTeamRegistrationId: string
    colTeamMatchId: string
    colTeamRatingCategory: string
    colTeamClub: string
    colTeamDeregistered: string
    valueTeamDeregistered: string
    colMatchName: string
    colMatchStartTime: string
    colRoundName: string
    colCompetitionIdentifier: string
    colCompetitionName: string
    colCompetitionShortName: string
    colCompetitionCategory: string
    noHeader: boolean
    appendRatingToShortName: boolean
}

const defaultValues: Form = {
    name: '',
    colParticipantFirstname: '',
    colParticipantLastname: '',
    colParticipantFullname: '',
    colParticipantGender: '',
    colParticipantRole: '',
    colParticipantYear: '',
    colParticipantClub: '',
    colClubName: '',
    colTeamName: '',
    colTeamStartNumber: '',
    colTeamRegistrationId: '',
    colTeamMatchId: '',
    colTeamRatingCategory: '',
    colTeamClub: '',
    colTeamDeregistered: '',
    valueTeamDeregistered: '',
    colMatchName: '',
    colMatchStartTime: '',
    colRoundName: '',
    colCompetitionIdentifier: '',
    colCompetitionName: '',
    colCompetitionShortName: '',
    colCompetitionCategory: '',
    noHeader: false,
    appendRatingToShortName: false,
}

const addAction = (formData: Form) =>
    addStartListConfig({
        body: mapFormToRequest(formData),
    })

const editAction = (formData: Form, entity: StartListConfigDto) =>
    updateStartListConfig({
        path: {startListConfigId: entity.id},
        body: mapFormToRequest(formData),
    })

const FORM_TABS = ['participant', 'club', 'team', 'match', 'round', 'competition'] as const
type FormTab = (typeof FORM_TABS)[number]

const tabProps = (tab: FormTab) => a11yProps('startlistDialog', tab)

const StartListConfigDialog = (props: BaseEntityDialogProps<StartListConfigDto>) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const formContext = useForm<Form>()

    const [activeTab, setActiveTab] = useState<FormTab>('participant')

    const onOpen = useCallback(() => {
        setActiveTab('participant')
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
    }, [props.entity])

    const onError = (error: AddStartListConfigError | UpdateStartListConfigError): boolean => {
        if (error.status.value === 422) {
            feedback.error(t('configuration.export.startlist.error.tooFewCols'))
            return true
        } else {
            return false
        }
    }

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            onAddError={onError}
            onEditError={onError}
            editAction={editAction}
            maxWidth={'md'}
            sx={{
                '& .MuiDialog-paper': {height: 1},
            }}>
            <Stack spacing={4}>
                <FormInputText
                    name={'name'}
                    label={t('configuration.export.startlist.name')}
                    required
                />
                <FormInputCheckbox
                    name={'noHeader'}
                    label={t('configuration.export.startlist.noHeader')}
                    horizontal
                />
                <TabSelectionContainer activeTab={activeTab} setActiveTab={setActiveTab}>
                    {FORM_TABS.map(tab => (
                        <Tab
                            key={tab}
                            label={t(`configuration.export.startlist.col.${tab}.heading`)}
                            {...tabProps(tab)}
                        />
                    ))}
                </TabSelectionContainer>
                <TabPanel index={'participant'} activeTab={activeTab}>
                    <Stack spacing={4}>
                        <FormInputText
                            name={'colParticipantFirstname'}
                            label={t('configuration.export.startlist.col.participant.firstname')}
                        />
                        <FormInputText
                            name={'colParticipantLastname'}
                            label={t('configuration.export.startlist.col.participant.lastname')}
                        />
                        <FormInputText
                            name={'colParticipantFullname'}
                            label={t('configuration.export.startlist.col.participant.fullname')}
                        />
                        <FormInputText
                            name={'colParticipantGender'}
                            label={t('configuration.export.startlist.col.participant.gender')}
                        />
                        <FormInputText
                            name={'colParticipantRole'}
                            label={t('configuration.export.startlist.col.participant.role')}
                        />
                        <FormInputText
                            name={'colParticipantYear'}
                            label={t('configuration.export.startlist.col.participant.year')}
                        />
                        <FormInputText
                            name={'colParticipantClub'}
                            label={t('configuration.export.startlist.col.participant.club')}
                        />
                    </Stack>
                </TabPanel>
                <TabPanel index={'club'} activeTab={activeTab}>
                    <Stack spacing={4}>
                        <FormInputText
                            name={'colClubName'}
                            label={t('configuration.export.startlist.col.club.name')}
                        />
                    </Stack>
                </TabPanel>
                <TabPanel index={'team'} activeTab={activeTab}>
                    <Stack spacing={4}>
                        <FormInputText
                            name={'colTeamName'}
                            label={t('configuration.export.startlist.col.team.name')}
                        />
                        <FormInputText
                            name={'colTeamStartNumber'}
                            label={t('configuration.export.startlist.col.team.startNumber')}
                        />
                        <FormInputText
                            name={'colTeamRegistrationId'}
                            label={t('configuration.export.startlist.col.team.registrationId')}
                        />
                        <FormInputText
                            name={'colTeamMatchId'}
                            label={t('configuration.export.startlist.col.team.matchId')}
                        />
                        <FormInputText
                            name={'colTeamRatingCategory'}
                            label={t('configuration.export.startlist.col.team.ratingCategory')}
                        />
                        <FormInputText
                            name={'colTeamClub'}
                            label={t('configuration.export.startlist.col.team.club')}
                        />
                        <FormInputText
                            name={'colTeamDeregistered'}
                            label={t('configuration.export.startlist.col.team.deregistered')}
                        />
                        <FormInputText
                            name={'valueTeamDeregistered'}
                            label={t('configuration.export.startlist.col.team.deregisteredValue')}
                        />
                    </Stack>
                </TabPanel>
                <TabPanel index={'match'} activeTab={activeTab}>
                    <Stack spacing={4}>
                        <FormInputText
                            name={'colMatchName'}
                            label={t('configuration.export.startlist.col.match.name')}
                        />
                        <FormInputText
                            name={'colMatchStartTime'}
                            label={t('configuration.export.startlist.col.match.startTime')}
                        />
                    </Stack>
                </TabPanel>
                <TabPanel index={'round'} activeTab={activeTab}>
                    <Stack spacing={4}>
                        <FormInputText
                            name={'colRoundName'}
                            label={t('configuration.export.startlist.col.round.name')}
                        />
                    </Stack>
                </TabPanel>
                <TabPanel index={'competition'} activeTab={activeTab}>
                    <Stack spacing={4}>
                        <FormInputText
                            name={'colCompetitionIdentifier'}
                            label={t('configuration.export.startlist.col.competition.identifier')}
                        />
                        <FormInputText
                            name={'colCompetitionName'}
                            label={t('configuration.export.startlist.col.competition.name')}
                        />
                        <FormInputText
                            name={'colCompetitionShortName'}
                            label={t('configuration.export.startlist.col.competition.shortName')}
                        />
                        <FormInputCheckbox
                            name={'appendRatingToShortName'}
                            label={t(
                                'configuration.export.startlist.col.competition.appendRatingToShortName',
                            )}
                            horizontal
                        />
                        <FormInputText
                            name={'colCompetitionCategory'}
                            label={t('configuration.export.startlist.col.competition.category')}
                        />
                    </Stack>
                </TabPanel>
            </Stack>
        </EntityDialog>
    )
}

const mapFormToRequest = (formData: Form): StartListConfigRequest => ({
    name: formData.name,
    colParticipantFirstname: takeIfNotEmpty(formData.colParticipantFirstname),
    colParticipantLastname: takeIfNotEmpty(formData.colParticipantLastname),
    colParticipantFullname: takeIfNotEmpty(formData.colParticipantFullname),
    colParticipantGender: takeIfNotEmpty(formData.colParticipantGender),
    colParticipantRole: takeIfNotEmpty(formData.colParticipantRole),
    colParticipantYear: takeIfNotEmpty(formData.colParticipantYear),
    colParticipantClub: takeIfNotEmpty(formData.colParticipantClub),
    colClubName: takeIfNotEmpty(formData.colClubName),
    colTeamName: takeIfNotEmpty(formData.colTeamName),
    colTeamStartNumber: takeIfNotEmpty(formData.colTeamStartNumber),
    colTeamRegistrationId: takeIfNotEmpty(formData.colTeamRegistrationId),
    colTeamMatchId: takeIfNotEmpty(formData.colTeamMatchId),
    colTeamRatingCategory: takeIfNotEmpty(formData.colTeamRatingCategory),
    colTeamClub: takeIfNotEmpty(formData.colTeamClub),
    colTeamDeregistered: takeIfNotEmpty(formData.colTeamDeregistered),
    valueTeamDeregistered: takeIfNotEmpty(formData.valueTeamDeregistered),
    colMatchName: takeIfNotEmpty(formData.colMatchName),
    colMatchStartTime: takeIfNotEmpty(formData.colMatchStartTime),
    colRoundName: takeIfNotEmpty(formData.colRoundName),
    colCompetitionIdentifier: takeIfNotEmpty(formData.colCompetitionIdentifier),
    colCompetitionName: takeIfNotEmpty(formData.colCompetitionName),
    colCompetitionShortName: takeIfNotEmpty(formData.colCompetitionShortName),
    colCompetitionCategory: takeIfNotEmpty(formData.colCompetitionCategory),
    noHeader: formData.noHeader,
    appendRatingToShortName: formData.appendRatingToShortName,
})

const mapDtoToForm = (dto: StartListConfigDto): Form => ({
    name: dto.name,
    colParticipantFirstname: dto.colParticipantFirstname ?? '',
    colParticipantLastname: dto.colParticipantLastname ?? '',
    colParticipantFullname: dto.colParticipantFullname ?? '',
    colParticipantGender: dto.colParticipantGender ?? '',
    colParticipantRole: dto.colParticipantRole ?? '',
    colParticipantYear: dto.colParticipantYear ?? '',
    colParticipantClub: dto.colParticipantClub ?? '',
    colClubName: dto.colClubName ?? '',
    colTeamName: dto.colTeamName ?? '',
    colTeamStartNumber: dto.colTeamStartNumber ?? '',
    colTeamRegistrationId: dto.colTeamRegistrationId ?? '',
    colTeamMatchId: dto.colTeamMatchId ?? '',
    colTeamRatingCategory: dto.colTeamRatingCategory ?? '',
    colTeamClub: dto.colTeamClub ?? '',
    colTeamDeregistered: dto.colTeamDeregistered ?? '',
    valueTeamDeregistered: dto.valueTeamDeregistered ?? '',
    colMatchName: dto.colMatchName ?? '',
    colMatchStartTime: dto.colMatchStartTime ?? '',
    colRoundName: dto.colRoundName ?? '',
    colCompetitionIdentifier: dto.colCompetitionIdentifier ?? '',
    colCompetitionName: dto.colCompetitionName ?? '',
    colCompetitionShortName: dto.colCompetitionShortName ?? '',
    colCompetitionCategory: dto.colCompetitionCategory ?? '',
    noHeader: dto.noHeader,
    appendRatingToShortName: dto.appendRatingToShortName,
})

export default StartListConfigDialog
