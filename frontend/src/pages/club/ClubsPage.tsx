import {ClubDto} from '../../api'
import {Box} from '@mui/material'
import {useEntityAdministration} from '@utils/hooks.ts'
import {useTranslation} from 'react-i18next'
import ClubTable from '../../components/club/ClubTable.tsx'
import ClubDialog from '../../components/club/ClubDialog.tsx'

const ClubsPage = () => {
    const {t} = useTranslation()

    const administrationProps = useEntityAdministration<ClubDto>(t('club.club'), {
        entityCreate: false,
    })

    return (
        <Box>
            <ClubTable {...administrationProps.table} title={t('club.clubs')} />
            <ClubDialog {...administrationProps.dialog} />
        </Box>
    )
}

export default ClubsPage
