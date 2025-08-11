import * as React from 'react'
import {
    Alert,
    AlertTitle,
    Box,
    Button,
    Stack,
    Step,
    StepLabel,
    Stepper,
    Typography,
} from '@mui/material'
import {FormContainer, useFieldArray, UseFormReturn} from 'react-hook-form-mui'
import {PersonAdd} from '@mui/icons-material'
import {EventRegistrationParticipantForm} from './EventRegistrationParticipantForm.tsx'
import {useTranslation} from 'react-i18next'
import {v4 as uuid} from 'uuid'
import {formatISO} from 'date-fns'
import {FormInputText} from '../form/input/FormInputText.tsx'
import {EventRegistrationSingleCompetitionForm} from './EventRegistrationSingleCompetitionForm.tsx'
import EventRegistrationTeamCompetitionForm from './EventRegistrationTeamCompetitionForm.tsx'
import {EventRegistrationFeeDisplay} from '@components/eventRegistration/EventRegistrationFeeDisplay.tsx'
import {EventRegistrationConfirmDocumentsForm} from '@components/eventRegistration/EventRegistrationConfirmDocumentsForm.tsx'
import {EventRegistrationFormData} from "../../pages/eventRegistration/EventRegistrationCreatePage.tsx";
import {useEventRegistration} from "@contexts/eventRegistration/EventRegistrationContext.ts";

export type EventRegistrationStep = {
    label: string
    validateKeys: Array<keyof EventRegistrationFormData>
    content: React.ReactNode
}

function RegistrationEventDayInfo(props: {
    name: string | undefined | null
    date: string
    description: string | undefined | null
}) {
    return (
        <Stack direction={'row'} spacing={1}>
            <Typography variant="body2">
                {formatISO(props.date, {representation: 'date'})} {props.name}
            </Typography>
            <Typography variant="body2">{props.description}</Typography>
        </Stack>
    )
}

const EventRegistrationForm = ({
    stepsBefore,
    onSubmit,
    formContext,
    adminEdit,
}: {
    stepsBefore?: EventRegistrationStep[]
    onSubmit: (data: Partial<EventRegistrationFormData>) => void
    formContext: UseFormReturn<EventRegistrationFormData>
    adminEdit?: boolean
}) => {
    const {t} = useTranslation()
    const [activeStep, setActiveStep] = React.useState(0)
    const {info} = useEventRegistration()

    const handleNext = () => {
        formContext.trigger(steps[activeStep]?.validateKeys, {shouldFocus: true}).then(valid => {
            if (valid) {
                setActiveStep(prevActiveStep => prevActiveStep + 1)
            }
        })
    }

    const handleBack = () => {
        setActiveStep(prevActiveStep => prevActiveStep - 1)
    }

    const {
        fields: participantFields,
        append: appendParticipant,
        remove: removeParticipant,
    } = useFieldArray({
        control: formContext.control,
        name: 'participants',
        keyName: 'fieldId',
    })

    const steps: EventRegistrationStep[] = [
        ...(stepsBefore || []),
        {
            label: t('club.participant.title'),
            validateKeys: ['participants'],
            content: (
                <Stack spacing={2} pt={2}>
                    {participantFields.map((field, index) => (
                        <EventRegistrationParticipantForm
                            key={'remove' + field.fieldId}
                            index={index}
                            removeParticipant={() => {
                                removeParticipant(index)
                            }}
                        />
                    ))}
                    <Button
                        onClick={() =>
                            appendParticipant({
                                id: uuid(),
                                firstname: '',
                                lastname: '',
                                year: 1990,
                                gender: 'F',
                                isNew: true,
                            })
                        }>
                        <PersonAdd sx={{mr: 1}} />
                        {t('event.registration.addParticipant')}
                    </Button>
                </Stack>
            ),
        },
        {
            label: t('event.registration.singleCompetition'),
            validateKeys: ['participants'],
            content: (
                <EventRegistrationSingleCompetitionForm />
            ),
        },
        {
            label: t('event.registration.teamCompetition'),
            validateKeys: ['competitionRegistrations'],
            content: <EventRegistrationTeamCompetitionForm />,
        },
        ...(adminEdit
            ? []
            : [
                  {
                      label: t('event.registration.summary'),
                      validateKeys: [],
                      content: (
                          <React.Fragment>
                              <Stack spacing={1}>
                                  {(info?.days?.length ?? 0) > 0 && (
                                      <Alert severity={'info'}>
                                          <AlertTitle>
                                              {t('event.registration.schedule')}
                                          </AlertTitle>
                                          <Stack spacing={1}>
                                              {info?.days.map((day) => (
                                                  <RegistrationEventDayInfo
                                                      key={day.id}
                                                      name={day.name}
                                                      description={day.description}
                                                      date={day.date}
                                                  />
                                              ))}
                                          </Stack>
                                      </Alert>
                                  )}
                                  {info?.documentTypes && (
                                      <EventRegistrationConfirmDocumentsForm
                                          documentTypes={info?.documentTypes}
                                      />
                                  )}
                                  <FormInputText
                                      fullWidth
                                      multiline={true}
                                      rows={5}
                                      name={'message'}
                                      label={t('event.registration.message')}
                                  />
                              </Stack>
                          </React.Fragment>
                      ),
                  },
              ]),
    ]

    return (
        <Box
            style={{
                padding: '10px',
                background: 'white',
                minWidth: '320px',
                borderRadius: '10px',
            }}>
            <FormContainer formContext={formContext} onSuccess={onSubmit}>
                <Stack>
                    <Stack
                        direction={'row'}
                        justifyContent={'space-between'}
                        alignItems={'center'}
                        p={2}>
                        <Typography variant={'h2'}>{info?.name}</Typography>
                        <EventRegistrationFeeDisplay />
                    </Stack>
                    <Stepper activeStep={activeStep}>
                        {steps.map(({label}) => {
                            return (
                                <Step key={label}>
                                    <StepLabel>{label}</StepLabel>
                                </Step>
                            )
                        })}
                    </Stepper>
                    <Box>
                        <Box sx={{p: 2}}>
                            {steps[activeStep]?.content}
                            <React.Fragment>
                                <Box sx={{display: 'flex', flexDirection: 'row', pt: 2}}>
                                    <Button
                                        color="inherit"
                                        disabled={activeStep === 0}
                                        onClick={handleBack}
                                        sx={{mr: 1}}>
                                        {t('common.back')}
                                    </Button>
                                    <Box sx={{flex: '1 1 auto'}} />
                                    {activeStep === steps.length - 1 ? (
                                        <Button
                                            key={'submit-form'}
                                            variant="contained"
                                            type={'submit'}>
                                            {adminEdit
                                                ? t('common.save')
                                                : t('event.registration.finish')}
                                        </Button>
                                    ) : (
                                        <Button onClick={() => handleNext()}>
                                            {t('common.next')}
                                        </Button>
                                    )}
                                </Box>
                            </React.Fragment>
                        </Box>
                    </Box>
                </Stack>
            </FormContainer>
        </Box>
    )
}

export default EventRegistrationForm
