import AssignDocumentTemplate from '@components/documentTemplate/AssignDocumentTemplate.tsx'
import AssignBankAccount from '@components/bankAccount/AssignBankAccount.tsx'
import AssignContactInformation from '@components/contactInformation/AssignContactInformation.tsx'
import {Stack} from '@mui/material'

const GlobalConfigurationsTab = () => {
    return (
        <Stack spacing={2}>
            <AssignDocumentTemplate />
            <AssignBankAccount />
            <AssignContactInformation />
        </Stack>
    )
}

export default GlobalConfigurationsTab
