import {ClubDto} from '../../api'
import {Box} from '@mui/material'
import {useEntityAdministration} from '../../utils/hooks.ts'
import {useTranslation} from 'react-i18next'
import ClubTable from '../../components/club/ClubTable.tsx'
import ClubDialog from '../../components/club/ClubDialog.tsx'

const ClubsPage = () => {
    const {t} = useTranslation()

    const administrationProps = useEntityAdministration<ClubDto>(t('club.club'))

    return (
        <Box>
            <ClubTable {...administrationProps.table}/>
            <ClubDialog {...administrationProps.dialog}/>
        </Box>
    )
}

export default ClubsPage
