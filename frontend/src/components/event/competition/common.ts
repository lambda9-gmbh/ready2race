import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {AutocompleteOption} from '@utils/types.ts'
import {
    CompetitionPropertiesDto,
    CompetitionPropertiesRequest,
    CompetitionSetupTemplateOverviewDto,
} from '@api/types.gen.ts'

export type CompetitionForm = {
    identifier: string
    name: string
    shortName: string
    description: string
    competitionCategory: AutocompleteOption
    namedParticipants: {
        namedParticipant: AutocompleteOption
        countMales: string
        countFemales: string
        countNonBinary: string
        countMixed: string
    }[]
    fees: {
        fee: AutocompleteOption
        required: boolean
        amount: string
        lateAmount: string
    }[]
    setupTemplate: AutocompleteOption
    lateRegistrationAllowed: boolean
    ratingCategoryRequired: boolean
    challengeConfirmationImage: boolean
    challengeStartAt: string
    challengeEndAt: string
}

export const competitionFormDefaultValues: CompetitionForm = {
    identifier: '',
    name: '',
    shortName: '',
    description: '',
    competitionCategory: null,
    namedParticipants: [],
    fees: [],
    setupTemplate: null,
    lateRegistrationAllowed: false,
    ratingCategoryRequired: false,
    challengeConfirmationImage: false,
    challengeStartAt: '',
    challengeEndAt: '',
}

export function mapCompetitionFormToCompetitionPropertiesRequest(
    formData: CompetitionForm,
    isChallengeEvent: boolean,
): CompetitionPropertiesRequest {
    return {
        identifier: formData.identifier,
        name: formData.name,
        shortName: takeIfNotEmpty(formData.shortName),
        description: takeIfNotEmpty(formData.description),
        competitionCategory: takeIfNotEmpty(formData.competitionCategory?.id),
        namedParticipants: formData.namedParticipants.map(value => ({
            namedParticipant: value.namedParticipant?.id ?? '',
            countMales: Number(value.countMales),
            countFemales: Number(value.countFemales),
            countNonBinary: Number(value.countNonBinary),
            countMixed: Number(value.countMixed),
        })),
        fees: formData.fees.map(value => ({
            fee: value.fee?.id ?? '',
            required: value.required,
            amount: value.amount.replace(',', '.'),
            lateAmount: takeIfNotEmpty(value.lateAmount.replace(',', '.')),
        })),
        setupTemplate: !isChallengeEvent ? takeIfNotEmpty(formData.setupTemplate?.id) : undefined,
        lateRegistrationAllowed: formData.lateRegistrationAllowed,
        ratingCategoryRequired: formData.ratingCategoryRequired,
        challengeConfig: isChallengeEvent
            ? {
                  resultConfirmationImageRequired: formData.challengeConfirmationImage,
                  startAt: formData.challengeStartAt,
                  endAt: formData.challengeEndAt,
              }
            : undefined,
    }
}

export function mapCompetitionPropertiesToCompetitionForm(
    dto: CompetitionPropertiesDto,
    decimalPoint: string,
    setupTemplate?: CompetitionSetupTemplateOverviewDto,
): CompetitionForm {
    return {
        identifier: dto.identifier,
        name: dto.name,
        shortName: dto.shortName ?? '',
        description: dto.description ?? '',
        competitionCategory: dto.competitionCategory
            ? {
                  id: dto.competitionCategory.id,
                  label: dto.competitionCategory.name,
              }
            : null,
        namedParticipants: dto.namedParticipants.map(value => ({
            namedParticipant: {id: value.id, label: value.name},
            countMales: value.countMales.toString(),
            countFemales: value.countFemales.toString(),
            countNonBinary: value.countNonBinary.toString(),
            countMixed: value.countMixed.toString(),
        })),
        fees: dto.fees.map(value => ({
            fee: {id: value.id, label: value.name},
            required: value.required,
            amount: value.amount.replace('.', decimalPoint),
            lateAmount: value.lateAmount?.replace('.', decimalPoint) ?? '',
        })),
        setupTemplate: setupTemplate
            ? {
                  id: setupTemplate.id,
                  label: setupTemplate.name,
              }
            : null,
        lateRegistrationAllowed: dto.lateRegistrationAllowed,
        ratingCategoryRequired: dto.ratingCategoryRequired,
        challengeConfirmationImage: dto.challengeConfig
            ? dto.challengeConfig.resultConfirmationImageRequired
            : false,
        challengeStartAt: dto.challengeConfig ? dto.challengeConfig.startAt : '',
        challengeEndAt: dto.challengeConfig ? dto.challengeConfig.endAt : '',
    }
}

export function competitionLabelName(identifier: string, name: string) {
    return `${identifier} | ${name}`
}
