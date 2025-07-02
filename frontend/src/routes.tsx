import {
    createRootRouteWithContext,
    createRoute,
    createRouter,
    ParsedLocation,
    redirect,
    SearchSchemaInput,
} from '@tanstack/react-router'
import {AuthenticatedUser, User} from './contexts/user/UserContext.ts'
import RootLayout from './layouts/RootLayout.tsx'
import LoginPage from './pages/user/LoginPage.tsx'
import {Action, Privilege, Resource, Scope} from './api'
import {
    readUserGlobal,
    updateEventGlobal,
    updateUserGlobal,
} from './authorization/privileges.ts'
import UsersPage from './pages/user/UsersPage.tsx'
import UserPage from './pages/user/UserPage.tsx'
import RolesPage from './pages/user/RolesPage.tsx'
import EventsPage from './pages/event/EventsPage.tsx'
import EventPage, {EventTab} from './pages/event/EventPage.tsx'
import CompetitionPage, {CompetitionTab} from './pages/event/CompetitionPage.tsx'
import EventDayPage from './pages/event/EventDayPage.tsx'
import RegistrationPage from './pages/user/RegistrationPage.tsx'
import ResetPasswordPage from './pages/user/resetPassword/ResetPasswordPage.tsx'
import InitResetPasswordPage from './pages/user/resetPassword/InitResetPasswordPage.tsx'
import VerifyRegistrationPage from './pages/user/VerifyRegistrationPage.tsx'
import ClubsPage from './pages/club/ClubsPage.tsx'
import ClubPage from './pages/club/ClubPage.tsx'
import EventRegistrationCreatePage from './pages/eventRegistration/EventRegistrationCreatePage.tsx'
import ConfigurationPage, {ConfigurationTab} from './pages/ConfigurationPage.tsx'
import AcceptInvitationPage from './pages/user/AcceptInvitationPage.tsx'
import Dashboard from './pages/Dashboard.tsx'
import LandingPage from './pages/LandingPage.tsx'
import EventRegistrationPage from './pages/eventRegistration/EventRegistrationPage.tsx'

const checkAuth = (context: User, location: ParsedLocation, privilege?: Privilege) => {
    if (!context.loggedIn) {
        throw redirect({to: '/login', search: {redirect: location.href}})
    }
    if (privilege && !context.checkPrivilege(privilege)) {
        throw redirect({to: '/dashboard'})
    }
}

const checkAuthWith = (
    context: User,
    location: ParsedLocation,
    action: Action,
    resource: Resource,
    f: (authenticated: AuthenticatedUser, scope: Scope) => boolean,
) => {
    if (!context.loggedIn) {
        throw redirect({to: '/login', search: {redirect: location.href}})
    }
    const scope = context.getPrivilegeScope(action, resource)
    if (!scope || !f(context, scope)) {
        throw redirect({to: '/dashboard'})
    }
}

export const rootRoute = createRootRouteWithContext<User>()({
    component: () => <RootLayout />,
})

export const indexRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/',
    component: () => <LandingPage />,
    beforeLoad: ({context}) => {
        if (context.loggedIn) {
            throw redirect({to: '/dashboard'})
        }
    },
})

type LoginSearch = {
    redirect?: string
}

// TODO: @Type-Safety: page-specific instead of typed like this? because in Links it just builds a union containing all different TabTypes this way (which is still better than just 'string')
type TabSearch<TabType extends string> = {
    tab?: TabType
}

const validateTabSearch = <TabType extends string,>(search: TabSearch<TabType>): TabSearch<TabType> => {
    return {
        tab: search.tab,
    }
}

export const loginRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'login',
    component: () => <LoginPage />,
    validateSearch: ({redirect}: {redirect?: string} & SearchSchemaInput): LoginSearch => ({
        redirect,
    }),
})

export const invitationTokenRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'invitation/$invitationToken',
    component: () => <AcceptInvitationPage />,
})

export const registrationRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'registration',
})

export const registrationIndexRoute = createRoute({
    getParentRoute: () => registrationRoute,
    path: '/',
    component: () => <RegistrationPage />,
})

export const registrationTokenRoute = createRoute({
    getParentRoute: () => registrationRoute,
    path: '$registrationToken',
    component: () => <VerifyRegistrationPage />,
})

export const resetPasswordRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'resetPassword',
})

export const resetPasswordIndexRoute = createRoute({
    getParentRoute: () => resetPasswordRoute,
    path: '/',
    component: () => <InitResetPasswordPage />,
})

export const resetPasswordTokenRoute = createRoute({
    getParentRoute: () => resetPasswordRoute,
    path: '$passwordResetToken',
    component: () => <ResetPasswordPage />,
})

export const dashboardRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'dashboard',
    component: () => <Dashboard />,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location)
    },
})

export const usersRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'user',
})

export const usersIndexRoute = createRoute({
    getParentRoute: () => usersRoute,
    path: '/',
    component: () => <UsersPage />,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location, readUserGlobal)
    },
})

export const userRoute = createRoute({
    getParentRoute: () => usersRoute,
    path: '$userId',
})

export const userIndexRoute = createRoute({
    getParentRoute: () => userRoute,
    path: '/',
    component: () => <UserPage />,
    beforeLoad: ({context, location, params}) => {
        checkAuthWith(
            context,
            location,
            'READ',
            'USER',
            (user, scope) => scope === 'GLOBAL' || params.userId === user.id,
        )
    },
})

export const rolesRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'role',
})

export const rolesIndexRoute = createRoute({
    getParentRoute: () => rolesRoute,
    path: '/',
    component: () => <RolesPage />,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location, updateUserGlobal)
    },
})

export const configurationRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'config',
})

export const configurationIndexRoute = createRoute({
    getParentRoute: () => configurationRoute,
    path: '/',
    component: () => <ConfigurationPage />,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location, updateEventGlobal)
    },
    validateSearch: validateTabSearch<ConfigurationTab>,
})

export const eventsRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'event',
})

export const eventsIndexRoute = createRoute({
    getParentRoute: () => eventsRoute,
    path: '/',
    component: () => <EventsPage />,
})

export const eventRoute = createRoute({
    getParentRoute: () => eventsRoute,
    path: '$eventId',
})

export const eventIndexRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: '/',
    component: () => <EventPage />,
    validateSearch: validateTabSearch<EventTab>,
})

export const eventRegisterRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: '/register',
})

export const eventRegisterIndexRoute = createRoute({
    getParentRoute: () => eventRegisterRoute,
    path: '/',
    component: () => <EventRegistrationCreatePage />,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location)
        if (context.loggedIn && context.clubId == undefined) {
            throw redirect({to: '..'})
        }
    },
})

export const eventRegistrationRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: 'registration/$eventRegistrationId',
    component: () => <EventRegistrationPage />,
})

export const eventDayRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: 'eventDay/$eventDayId',
})

export const eventDayIndexRoute = createRoute({
    getParentRoute: () => eventDayRoute,
    path: '/',
    component: () => <EventDayPage />,
})

export const competitionRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: 'competition/$competitionId',
})

export const competitionIndexRoute = createRoute({
    getParentRoute: () => competitionRoute,
    path: '/',
    component: () => <CompetitionPage />,
    validateSearch: validateTabSearch<CompetitionTab>,
})

export const clubRoute = createRoute({
    getParentRoute: () => clubsRoute,
    path: '$clubId',
})

export const clubIndexRoute = createRoute({
    getParentRoute: () => clubRoute,
    path: '/',
    component: () => <ClubPage />,
    beforeLoad: ({context, location, params}) => {
        checkAuthWith(
            context,
            location,
            'READ',
            'CLUB',
            (user, scope) => scope === 'GLOBAL' || params.clubId === user.clubId
        )
    },
})

export const clubsRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'club',
})

export const clubsIndexRoute = createRoute({
    getParentRoute: () => clubsRoute,
    path: '/',
    component: () => <ClubsPage />,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location)
    },
})

const routeTree = rootRoute.addChildren([
    indexRoute,
    loginRoute,
    dashboardRoute,
    configurationRoute.addChildren([configurationIndexRoute]),
    eventsRoute.addChildren([
        eventsIndexRoute,
        eventRoute.addChildren([
            eventIndexRoute,
            eventRegistrationRoute,
            eventDayRoute.addChildren([eventDayIndexRoute]),
            competitionRoute.addChildren([
                competitionIndexRoute,
            ]),
            eventRegisterRoute.addChildren([eventRegisterIndexRoute]),
        ]),
    ]),
    usersRoute.addChildren([usersIndexRoute, userRoute.addChildren([userIndexRoute])]),
    rolesRoute.addChildren([rolesIndexRoute]),
    invitationTokenRoute,
    registrationRoute.addChildren([registrationIndexRoute, registrationTokenRoute]),
    resetPasswordRoute.addChildren([resetPasswordIndexRoute, resetPasswordTokenRoute]),
    clubsRoute.addChildren([clubsIndexRoute, clubRoute.addChildren([clubIndexRoute])]),
])

const basepath = document.getElementById('ready2race-root')!.dataset.basepath

export const router = createRouter({
    routeTree,
    context: undefined!,
    basepath
})

declare module '@tanstack/react-router' {
    interface Register {
        router: typeof router
    }
}
