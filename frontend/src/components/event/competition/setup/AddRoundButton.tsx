import {Box, Button} from '@mui/material'
import {FormSetupRound} from '@components/event/competition/setup/common.ts'
import {useTranslation} from 'react-i18next'
import Add from "@mui/icons-material/Add";

type Props = {
    index: number
    insertRound: (index: number, values: FormSetupRound) => void
}
const AddRoundButton = ({index, insertRound}: Props) => {
    const {t} = useTranslation()

    return (
        <Box sx={{alignSelf: 'start'}}>
            <Button
                variant="outlined"
                onClick={() => {
                    insertRound(index, {
                        name: '',
                        required: false,
                        matches: [],
                        groups: [],
                        useDefaultSeeding: true,
                        placesOption: 'EQUAL',
                        places: [],
                        isGroupRound: false,
                        useStartTimeOffsets: false,
                    })
                }}
                sx={{width: 1}}
                startIcon={<Add />}>
                {t('entity.add.action', {entity: t('event.competition.setup.round.round')})}
            </Button>
        </Box>
    )
}
export default AddRoundButton
