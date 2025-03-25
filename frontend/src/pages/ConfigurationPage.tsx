import DocumentTypeTable from '@components/event/document/type/DocumentTypeTable.tsx'
import DocumentTypeDialog from '@components/event/document/type/DocumentTypeDialog.tsx'
import {useEntityAdministration} from '@utils/hooks.ts'
import {EventDocumentTypeDto, ParticipantRequirementDto} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import ParticipantRequirementTable from '@components/event/participantRequirement/ParticipantRequirementTable.tsx'
import ParticipantRequirementDialog from '@components/event/participantRequirement/ParticipantRequirementDialog.tsx'
import {Stack} from '@mui/material'

const ConfigurationPage = () => {
    const {t} = useTranslation()
    const documentTypeAdministrationProps = useEntityAdministration<EventDocumentTypeDto>(
        t('event.document.type.documentType'),
    )

    const participantRequirementAdministrationProps =
        useEntityAdministration<ParticipantRequirementDto>(t('event.document.type.documentType'))

    return (
        <Stack spacing={4}>
            <DocumentTypeTable
                {...documentTypeAdministrationProps.table}
                title={t('event.document.type.documentTypes')}
            />
            <DocumentTypeDialog {...documentTypeAdministrationProps.dialog} />
            <ParticipantRequirementTable
                {...participantRequirementAdministrationProps.table}
                title={t('event.participantRequirements')}
            />
            <ParticipantRequirementDialog {...participantRequirementAdministrationProps.dialog} />
        </Stack>
    )
}

export default ConfigurationPage
