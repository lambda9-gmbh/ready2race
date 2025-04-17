import {Box, Button} from "@mui/material";
import {FormSetupRound} from "@components/event/competition/setup/common.ts";

type Props = {
    index: number;
    insertRound: (index: number, values: FormSetupRound) => void
}
const AddRoundButton = ({index, insertRound}: Props) => {
    return (
        <Box sx={{maxWidth: 200}}>
            <Button
                variant="outlined"
                onClick={() => {
                    insertRound(index, {
                        name: '',
                        required: false,
                        matches: [],
                        groups: [],
                        useDefaultSeeding: true,
                        places: [],
                        isGroupRound: false,
                        useStartTimeOffsets: false,
                    })
                }}
                sx={{width: 1}}>
                Add Round
            </Button>
        </Box>
    )
}
export default AddRoundButton