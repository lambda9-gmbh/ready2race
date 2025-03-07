import DocumentTypeTable from '@components/event/document/type/DocumentTypeTable.tsx'
import DocumentTypeDialog from '@components/event/document/type/DocumentTypeDialog.tsx'
import {useEntityAdministration} from '@utils/hooks.ts'
import {EventDocumentTypeDto} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'

const ConfigurationPage = () => {
    const {t} = useTranslation()
    const documentTypeAdministrationProps = useEntityAdministration<EventDocumentTypeDto>(
        t('event.document.type.documentType'),
    )

    return (
        <>
            <DocumentTypeTable {...documentTypeAdministrationProps.table} title={t('event.document.type.documentTypes')} />
            <DocumentTypeDialog {...documentTypeAdministrationProps.dialog} />
        </>
    )
}

export default ConfigurationPage
