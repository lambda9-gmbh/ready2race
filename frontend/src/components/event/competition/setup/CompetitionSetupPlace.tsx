import {Controller, UseFormReturn, useWatch} from 'react-hook-form-mui'
import {CompetitionSetupForm} from '@components/event/competition/setup/CompetitionSetup.tsx'
import {TableCell, TableRow, TextField} from '@mui/material'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {useEffect} from "react";

type Props = {
    formContext: UseFormReturn<CompetitionSetupForm>
    roundIndex: number
    placeIndex: number
    id: string
}
const CompetitionSetupPlace = ({...props}: Props) => {
    const watchVal = props.formContext.watch(`rounds.${props.roundIndex}.places.${props.placeIndex}.roundOutcome`)

    const watchX = props.formContext.watch(`rounds.${props.roundIndex}`)

    useEffect(() => {

        console.log("Watch X Trigger", watchX)
    }, [watchX]);

    return (
        <Controller
            key={props.id}
            name={`rounds.${props.roundIndex}.places.${props.placeIndex}`}
            control={props.formContext.control}
            render={({
                field: {onChange: placeOnChange, value: placeValue = {roundOutcome: 0, place: 0}},
            }) => (
                <>
                    {watchVal && (
                        <TableRow>
                            <TableCell>{placeValue.roundOutcome}</TableCell>
                            <TableCell>
                                <TextField value={placeValue.place} />
                                {/* <FormInputNumber
                            name={`rounds[${round.index}].places[${placeIndex}].place`}
                            placeholder={`${teamCounts.nextRound + 1}`}
                            required
                            key={place.id}
                        />*/}
                            </TableCell>
                        </TableRow>
                    )}
                </>
            )}></Controller>
    )
}

export default CompetitionSetupPlace
