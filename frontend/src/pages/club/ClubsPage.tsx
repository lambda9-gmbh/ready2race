import {ClubDto, getCreateClubOnRegistrationAllowed, updateGlobalConfigurations} from '../../api'
import {Box, Card, CardContent, CardHeader, Stack, Tab, Typography} from '@mui/material'
import {useNavigate} from '@tanstack/react-router'
import {useEntityAdministration, useFeedback, useFetch} from '@utils/hooks.ts'
import {useTranslation} from 'react-i18next'
import ClubTable from '@components/club/ClubTable.tsx'
import ClubDialog from '@components/club/ClubDialog.tsx'
import ClubShortNamePanel from '@components/club/shortName/ClubShortNamePanel.tsx'
import ClubNameRulePanel from '@components/club/shortName/ClubNameRulePanel.tsx'
import TabSelectionContainer from '@components/tab/TabSelectionContainer.tsx'
import TabPanel from '@components/tab/TabPanel.tsx'
import {a11yProps} from '@utils/helpers.ts'
import {FormContainer, useForm} from 'react-hook-form-mui'
import FormInputSwitch from '@components/form/input/FormInputSwitch.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {useState} from 'react'
import {useUser} from '@contexts/user/UserContext.ts'
import {readClubGlobal, updateAdministrationConfigGlobal} from '@authorization/privileges.ts'
import {clubsIndexRoute} from '../../routes.tsx'

type GlobalConfigForm = {
    allowClubCreationOnRegistration: boolean
}

export const CLUB_TABS = ['clubs', 'short-names', 'settings'] as const
export type ClubTab = (typeof CLUB_TABS)[number]

/**
 * Vereine mit Reiterleiste, wie die Veranstaltung und die Konfiguration: die Kurzformen und die
 * Vereinseinstellungen sind Unterfunktionen von "Vereine" und gehören deshalb hierher statt als
 * eigener Eintrag ins Seitenmenü.
 *
 * Die Kürzungsregeln stehen unter "Einstellungen", die Namensliste unter "Kurzformen" — anders als
 * zuvor also nicht mehr untereinander. Wer eine Regel ändert, sieht ihre Wirkung folglich erst
 * nach dem Wechsel auf "Kurzformen"; dafür lädt der Reiter seine Liste beim Betreten ohnehin neu.
 */
const ClubsPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const [submitting, setSubmitting] = useState(false)
    const user = useUser()

    const {tab} = clubsIndexRoute.useSearch()
    const activeTab = tab ?? 'clubs'

    const navigate = useNavigate()
    const switchTab = (tab: ClubTab) => {
        navigate({from: clubsIndexRoute.fullPath, search: {tab}}).then()
    }

    const tabProps = (tab: ClubTab) => a11yProps('club', tab)

    const administrationProps = useEntityAdministration<ClubDto>(t('club.club'), {
        entityCreate: false,
    })

    const formContext = useForm<GlobalConfigForm>({
        defaultValues: {
            allowClubCreationOnRegistration: false,
        },
    })

    useFetch(signal => getCreateClubOnRegistrationAllowed({signal}), {
        onResponse: ({error, data}) => {
            if (error) {
                feedback.error(t('common.error.unexpected'))
            } else if (data) {
                formContext.reset({
                    allowClubCreationOnRegistration: data,
                })
            }
        },
        deps: [],
    })

    const onSubmit = async (data: GlobalConfigForm) => {
        setSubmitting(true)
        const {error} = await updateGlobalConfigurations({
            body: {
                allowClubCreationOnRegistration: data.allowClubCreationOnRegistration,
            },
        })
        setSubmitting(false)
        if (error) {
            feedback.error(t('common.error.unexpected'))
        } else {
            feedback.success(t('club.settings.saved'))
        }
    }

    const mayReadShortNames = user.checkPrivilege(readClubGlobal)
    const mayEditGlobalSettings = user.checkPrivilege(updateAdministrationConfigGlobal)

    return (
        <Box>
            <Typography variant="h1">{t('club.clubs')}</Typography>
            <TabSelectionContainer activeTab={activeTab} setActiveTab={switchTab}>
                <Tab label={t('club.tabs.clubs')} {...tabProps('clubs')} />
                {mayReadShortNames && (
                    <Tab label={t('club.tabs.shortNames')} {...tabProps('short-names')} />
                )}
                {(mayReadShortNames || mayEditGlobalSettings) && (
                    <Tab label={t('club.tabs.settings')} {...tabProps('settings')} />
                )}
            </TabSelectionContainer>
            <TabPanel index={'clubs'} activeTab={activeTab}>
                {/* Ohne Titel: Überschrift und Reiter sagen bereits "Vereine", ein dritter
                    identischer Titel darüber wäre nur Rauschen. */}
                <ClubTable {...administrationProps.table} />
                <ClubDialog {...administrationProps.dialog} />
            </TabPanel>
            {mayReadShortNames && (
                <TabPanel index={'short-names'} activeTab={activeTab}>
                    <ClubShortNamePanel />
                </TabPanel>
            )}
            {(mayReadShortNames || mayEditGlobalSettings) && (
                <TabPanel index={'settings'} activeTab={activeTab}>
                    <Stack spacing={3}>
                        {mayEditGlobalSettings && (
                            <Card>
                                <CardHeader title={t('club.settings.title')} />
                                <CardContent>
                                    <FormContainer formContext={formContext} onSuccess={onSubmit}>
                                        <Stack spacing={2}>
                                            <FormInputSwitch
                                                name="allowClubCreationOnRegistration"
                                                label={t(
                                                    'club.settings.allowClubCreationOnRegistration',
                                                )}
                                                reverse
                                                horizontal
                                            />
                                            <Box>
                                                <SubmitButton submitting={submitting}>
                                                    {t('club.settings.save')}
                                                </SubmitButton>
                                            </Box>
                                        </Stack>
                                    </FormContainer>
                                </CardContent>
                            </Card>
                        )}
                        {mayReadShortNames && <ClubNameRulePanel />}
                    </Stack>
                </TabPanel>
            )}
        </Box>
    )
}

export default ClubsPage
