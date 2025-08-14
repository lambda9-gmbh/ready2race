import * as React from 'react'
import {ReactNode, useEffect, useState} from 'react'
import List from '@mui/material/List'
import Card from '@mui/material/Card'
import CardHeader from '@mui/material/CardHeader'
import ListItemText from '@mui/material/ListItemText'
import ListItemIcon from '@mui/material/ListItemIcon'
import Checkbox from '@mui/material/Checkbox'
import Button from '@mui/material/Button'
import Divider from '@mui/material/Divider'
import {Grid2, ListItem, Stack, TextField, Typography} from '@mui/material'
import {Control, FieldPath, FieldValues} from 'react-hook-form'
import {useController, useFormContext} from 'react-hook-form-mui'
import {Trans, useTranslation} from 'react-i18next'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {ParticipantRequirementApproveManuallyForEventForm} from '@components/event/participantRequirement/ParticipantRequirementApproveManuallyForEventDialog.tsx'

type RecordWithId = Record<string, any> & {id: string}

type TransferListProps<
    TFieldValues extends FieldValues = FieldValues,
    TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>,
    TValue extends RecordWithId = {id: string},
> = {
    name: TName
    control?: Control<TFieldValues>
    options: TValue[]
    loading?: boolean
    labelLeft: string
    labelRight: string
    renderValue: (value: TValue) => {primary: ReactNode; secondary: ReactNode}
}

export default function FormInputTransferList<
    TFieldValues extends FieldValues = FieldValues,
    TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>,
    TValue extends RecordWithId = {id: string},
>(props: TransferListProps<TFieldValues, TName, TValue>) {
    const {t} = useTranslation()
    const {name, control, labelRight, labelLeft, options, renderValue} = props
    const {field} = useController({
        name,
        control,
    })

    const formContext = useFormContext()

    const {value: right} = field

    const not = (a: TValue[], b: TValue[]): TValue[] => {
        return a.filter(value => !b.some(bValue => bValue.id === value.id))
    }

    const intersection = (a: TValue[], b: TValue[]) => {
        return a.filter(value => b.some(bValue => bValue.id === value.id))
    }

    const union = (a: TValue[], b: TValue[]) => {
        return [...a, ...not(b, a)]
    }

    const [checked, setChecked] = useState<TValue[]>([])
    const [left, setLeft] = useState<TValue[]>(not(options, right))

    useEffect(() => {
        setLeft(not(options, right))
    }, [options, right])

    const leftChecked = intersection(checked, left)
    const rightChecked = intersection(checked, right)

    const handleToggle = (value: TValue) => () => {
        const currentIndex = checked.indexOf(value)
        const newChecked = [...checked]

        if (currentIndex === -1) {
            newChecked.push(value)
        } else {
            newChecked.splice(currentIndex, 1)
        }

        setChecked(newChecked)
    }

    const numberOfChecked = (items: TValue[]) => intersection(checked, items).length

    const handleToggleAll = (items: TValue[]) => () => {
        if (numberOfChecked(items) === items.length) {
            setChecked(not(checked, items))
        } else {
            setChecked(union(checked, items))
        }
    }

    const handleCheckedRight = () => {
        const newRight = right.concat(leftChecked)
        setChecked(not(checked, leftChecked))
        field.onChange(newRight)
    }

    const handleCheckedLeft = () => {
        const newRight = not(right, rightChecked)
        setChecked(not(checked, rightChecked))
        field.onChange(newRight)
    }

    const customList = (title: React.ReactNode, items: TValue[], isRight: boolean) => (
        <Card elevation={2}>
            <Stack direction={'row'} justifyContent={'space-between'} alignItems={'end'}>
                <CardHeader
                    sx={{px: 2, py: 1}}
                    avatar={
                        <Checkbox
                            onClick={handleToggleAll(items)}
                            checked={numberOfChecked(items) === items.length && items.length !== 0}
                            indeterminate={
                                numberOfChecked(items) !== items.length &&
                                numberOfChecked(items) !== 0
                            }
                            disabled={items.length === 0}
                            inputProps={{
                                'aria-label': 'all items selected',
                            }}
                        />
                    }
                    title={title}
                    subheader={`${numberOfChecked(items)}/${items.length} ${t('common.selected')}`}
                />
                {isRight && (
                    <Typography sx={{px: 2, py: 1}}>
                        <Trans i18nKey={'event.participantRequirement.checkedNote'} />
                    </Typography>
                )}
            </Stack>

            <Divider />
            <List
                sx={{
                    width: '30vw',
                    height: '60vh',
                    bgcolor: 'background.paper',
                    overflow: 'auto',
                }}
                dense
                component="div"
                role="list">
                {items.map((value: TValue) => {
                    const labelId = `transfer-list-all-item-${value.id}-label`
                    const {primary, secondary} = renderValue(value)

                    return (
                        <ListItem
                            key={value.id}
                            role="listitem"
                            sx={{display: 'flex', justifyContent: 'space-between'}}>
                            <FormInputLabel
                                label={
                                    <ListItemText
                                        id={labelId}
                                        primary={primary}
                                        secondary={secondary}
                                    />
                                }
                                required
                                horizontal
                                reverse>
                                <ListItemIcon>
                                    <Checkbox
                                        onClick={handleToggle(value)}
                                        checked={checked.includes(value)}
                                        tabIndex={-1}
                                        disableRipple
                                        inputProps={{
                                            'aria-labelledby': labelId,
                                        }}
                                    />
                                </ListItemIcon>
                            </FormInputLabel>
                            {isRight && (
                                <TextField
                                    value={value.note}
                                    onChange={e => {
                                        const val = e.target.value
                                        const currentList: ParticipantRequirementApproveManuallyForEventForm['approvedParticipants'] =
                                            formContext.getValues('approvedParticipants')
                                        const currentIndex = currentList.findIndex(
                                            c => c.id === value.id,
                                        )!
                                        const newList = [...currentList]
                                        const p = newList[currentIndex]
                                        newList.splice(currentIndex, 1, {
                                            ...p,
                                            note: val,
                                        })
                                        formContext.setValue('approvedParticipants', newList)
                                    }}
                                />
                            )}
                        </ListItem>
                    )
                })}
            </List>
        </Card>
    )

    return (
        <Grid2 container spacing={2} sx={{justifyContent: 'center', alignItems: 'center'}}>
            <Grid2>{customList(labelLeft, left, false)}</Grid2>
            <Grid2>
                <Grid2 container direction="column" sx={{alignItems: 'center'}}>
                    <Button
                        sx={{my: 0.5}}
                        variant="outlined"
                        size="small"
                        onClick={handleCheckedRight}
                        disabled={leftChecked.length === 0}
                        aria-label="move selected right">
                        &gt;
                    </Button>
                    <Button
                        sx={{my: 0.5}}
                        variant="outlined"
                        size="small"
                        onClick={handleCheckedLeft}
                        disabled={rightChecked.length === 0}
                        aria-label="move selected left">
                        &lt;
                    </Button>
                </Grid2>
            </Grid2>
            <Grid2>{customList(labelRight, right, true)}</Grid2>
        </Grid2>
    )
}
