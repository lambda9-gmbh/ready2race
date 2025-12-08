import {AppUserRegisterRequest, ParticipantRegisterRequest} from '@api/types.gen.ts'
import {i18nLanguage, languageMapping} from '@utils/helpers.ts'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {AutocompleteOption} from '@utils/types.ts'
import {Gender} from '@api/types.gen.ts'
import {PasswordFormPart} from '@components/form/NewPassword.tsx'

export enum RegistrationStep {
    REGISTRATION_TYPE = 0,
    BASIC_INFORMATION = 1,
    COMPETITIONS = 2,
    CONFIRMATION = 3,
}

export type CompetitionRegistration = {
    checked: boolean
    competitionId: string
    optionalFees: string[]
    ratingCategory?: string
}

export type RegistrationForm = {
    clubname: string
    clubId?: string
    firstname: string
    lastname: string
    isParticipant: boolean
    isChallengeManager: boolean
    event: AutocompleteOption
    competitions: CompetitionRegistration[]
    birthYear: string
    gender?: Gender
    emailRequired: string
    emailOptional: string
    captcha: number
} & PasswordFormPart

export function mapFormToAppUserRegisterRequest(
    formData: RegistrationForm,
): AppUserRegisterRequest {
    return {
        email: formData.emailRequired!,
        password: formData.password,
        firstname: formData.firstname,
        lastname: formData.lastname,
        clubId: formData.clubId,
        clubname: formData.clubId ? undefined : formData.clubname,
        language: languageMapping[i18nLanguage()],
        callbackUrl: location.origin + location.pathname + '/',
        registerToSingleCompetitions: formData.competitions
            .filter(competition => competition.checked)
            .map(competition => ({
                competitionId: competition.competitionId,
                optionalFees: competition.optionalFees,
                ratingCategory:
                    competition.ratingCategory !== 'none' ? competition.ratingCategory : undefined,
            })),
        birthYear: formData.birthYear !== '' ? Number(formData.birthYear) : undefined,
        gender: formData.gender,
    }
}

export function mapFormToParticipantRegisterRequest(
    formData: RegistrationForm,
): ParticipantRegisterRequest {
    return {
        firstname: formData.firstname,
        lastname: formData.lastname,
        gender: formData.gender!,
        birthYear: Number(formData.birthYear!),
        email: takeIfNotEmpty(formData.emailOptional),
        clubId: formData.clubId!,
        language: languageMapping[i18nLanguage()],
        registerToSingleCompetitions: formData.competitions
            .filter(competition => competition.checked)
            .map(competition => ({
                competitionId: competition.competitionId,
                optionalFees: competition.optionalFees,
                ratingCategory:
                    competition.ratingCategory !== 'none' ? competition.ratingCategory : undefined,
            })),
    }
}
