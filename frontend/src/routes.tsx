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
    readInvoiceGlobal,
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
import AppLayout from './layouts/AppLayout.tsx'
import QrScannerPage from './pages/app/QrScannerPage.tsx'
import QrEventsPage from "./pages/app/QrEventsPage.tsx";
import QrAppuserPage from "./pages/app/QrAppuserPage.tsx";
import QrParticipantPage from "./pages/app/QrParticipantPage.tsx";
import QrAssignPage from "./pages/app/QrAssignPage.tsx";
import AppLoginPage from './pages/app/AppLoginPage.tsx'
import ForbiddenPage from './pages/app/ForbiddenPage.tsx'
import AppFunctionSelectPage from './pages/app/AppFunctionSelectPage.tsx'
import EventRegistrationPage from './pages/eventRegistration/EventRegistrationPage.tsx'
import InvoicesPage from './pages/AdministrationPage.tsx'

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

const checkAuthApp = (context: User, location: ParsedLocation, privilege?: Privilege) => {
    if (!context.loggedIn) {
        throw redirect({to: '/app/login', search: {redirect: location.href}})
    }
    if (privilege && !context.checkPrivilege(privilege)) {
        throw redirect({to: '/app/function'})
    }
}

export const rootRoute = createRootRouteWithContext<User>()({})

export const mainLayoutRoute = createRoute({
    getParentRoute: () => rootRoute,
    id: 'main-layout',
    component: () => <RootLayout/>,
})

export const indexRoute = createRoute({
    getParentRoute: () => mainLayoutRoute,
    path: '/',
    component: () => <LandingPage/>,
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

const validateTabSearch = <TabType extends string >(search: TabSearch<TabType>,
): TabSearch<TabType> => {
    return {
        tab: search.tab,
    }
}

export const loginRoute = createRoute({
    getParentRoute: () => mainLayoutRoute,
    path: 'login',
    component: () => <LoginPage/>,
    validateSearch: ({redirect}: { redirect?: string } & SearchSchemaInput): LoginSearch => ({
        redirect,
    }),
})

export const invitationTokenRoute = createRoute({
    getParentRoute: () => mainLayoutRoute,
    path: 'invitation/$invitationToken',
    component: () => <AcceptInvitationPage/>,
})

export const registrationRoute = createRoute({
    getParentRoute: () => mainLayoutRoute,
    path: 'registration',
})

export const registrationIndexRoute = createRoute({
    getParentRoute: () => registrationRoute,
    path: '/',
    component: () => <RegistrationPage/>,
})

export const registrationTokenRoute = createRoute({
    getParentRoute: () => registrationRoute,
    path: '$registrationToken',
    component: () => <VerifyRegistrationPage/>,
})

export const resetPasswordRoute = createRoute({
    getParentRoute: () => mainLayoutRoute,
    path: 'resetPassword',
})

export const resetPasswordIndexRoute = createRoute({
    getParentRoute: () => resetPasswordRoute,
    path: '/',
    component: () => <InitResetPasswordPage/>,
})

export const resetPasswordTokenRoute = createRoute({
    getParentRoute: () => resetPasswordRoute,
    path: '$passwordResetToken',
    component: () => <ResetPasswordPage/>,
})

export const dashboardRoute = createRoute({
    getParentRoute: () => mainLayoutRoute,
    path: 'dashboard',
    component: () => <Dashboard/>,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location)
    },
})

export const usersRoute = createRoute({
    getParentRoute: () => mainLayoutRoute,
    path: 'user',
})

export const usersIndexRoute = createRoute({
    getParentRoute: () => usersRoute,
    path: '/',
    component: () => <UsersPage/>,
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
    component: () => <UserPage/>,
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
    getParentRoute: () => mainLayoutRoute,
    path: 'role',
})

export const rolesIndexRoute = createRoute({
    getParentRoute: () => rolesRoute,
    path: '/',
    component: () => <RolesPage/>,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location, updateUserGlobal)
    },
})

export const configurationRoute = createRoute({
    getParentRoute: () => mainLayoutRoute,
    path: 'config',
})

export const configurationIndexRoute = createRoute({
    getParentRoute: () => configurationRoute,
    path: '/',
    component: () => <ConfigurationPage/>,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location, updateEventGlobal)
    },
    validateSearch: validateTabSearch<ConfigurationTab>,
})

export const qrEventRoute = createRoute({
    getParentRoute: () => appRoute,
    path: '$eventId',
    beforeLoad: ({context, location}) => {
        checkAuthApp(context, location)
    }
})

export const qrEventsIndexRoute = createRoute({
    getParentRoute: () => appRoute,
    path: '/',
    component: () => <QrEventsPage/>,
    beforeLoad: ({context, location}) => {
        checkAuthApp(context, location)
    }
})

export const eventsRoute = createRoute({
    getParentRoute: () => mainLayoutRoute,
    path: 'event',
})

export const eventsIndexRoute = createRoute({
    getParentRoute: () => eventsRoute,
    path: '/',
    component: () => <EventsPage/>,
})

export const eventRoute = createRoute({
    getParentRoute: () => eventsRoute,
    path: '$eventId',
})

export const eventIndexRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: '/',
    component: () => <EventPage/>,
    validateSearch: validateTabSearch<EventTab>,
})

export const eventRegisterRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: '/register',
})

export const eventRegisterIndexRoute = createRoute({
    getParentRoute: () => eventRegisterRoute,
    path: '/',
    component: () => <EventRegistrationCreatePage/>,
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
    component: () => <EventDayPage/>,
})

export const competitionRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: 'competition/$competitionId',
})

export const competitionIndexRoute = createRoute({
    getParentRoute: () => competitionRoute,
    path: '/',
    component: () => <CompetitionPage/>,
    validateSearch: validateTabSearch<CompetitionTab>,
})

export const clubRoute = createRoute({
    getParentRoute: () => clubsRoute,
    path: '$clubId',
})

export const clubIndexRoute = createRoute({
    getParentRoute: () => clubRoute,
    path: '/',
    component: () => <ClubPage/>,
    beforeLoad: ({context, location, params}) => {
        checkAuthWith(
            context,
            location,
            'READ',
            'CLUB',
            (user, scope) => scope === 'GLOBAL' || params.clubId === user.clubId,
        )
    },
})

export const clubsRoute = createRoute({
    getParentRoute: () => mainLayoutRoute,
    path: 'club',
})

export const clubsIndexRoute = createRoute({
    getParentRoute: () => clubsRoute,
    path: '/',
    component: () => <ClubsPage/>,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location)
    },
})

export const appRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'app',
    component: () => <AppLayout/>,
})

export const qrScanRoute = createRoute({
    getParentRoute: () => qrEventRoute,
    path: 'scanner',
    component: () => <QrScannerPage/>,
    beforeLoad: ({context, location}) => {
        checkAuthApp(context, location)
    }
})

export const qrUserRoute = createRoute({
    getParentRoute: () => qrEventRoute,
    path: 'user',
    component: () => <QrAppuserPage/>,
    beforeLoad: ({context, location}) => {
        checkAuthApp(context, location)
    }
})

export const qrParticipantRoute = createRoute({
    getParentRoute: () => qrEventRoute,
    path: 'participant',
    component: () => <QrParticipantPage/>,
    beforeLoad: ({context, location}) => {
        checkAuthApp(context, location)
    }
})

export const qrAssignRoute = createRoute({
    getParentRoute: () => qrEventRoute,
    path: 'assign',
    component: () => <QrAssignPage/>,
    beforeLoad: ({context, location}) => {
        checkAuthApp(context, location)
    }
})

export const appLoginRoute = createRoute({
    getParentRoute: () => appRoute,
    path: 'login',
    component: () => <AppLoginPage/>,
    validateSearch: ({redirect}: { redirect?: string } & SearchSchemaInput) => ({ redirect }),
})

export const appForbiddenRoute = createRoute({
    getParentRoute: () => appRoute,
    path: 'forbidden',
    component: () => <ForbiddenPage/>,
});

export const appFunctionSelectRoute = createRoute({
    getParentRoute: () => appRoute,
    path: 'function',
    component: () => <AppFunctionSelectPage/>,
    beforeLoad: ({context, location}) => {
        checkAuthApp(context, location)
    }
});

export const invoicesRoute = createRoute({
    getParentRoute: () => mainLayoutRoute,
    path: 'invoices',
    component: () => <InvoicesPage />,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location, readInvoiceGlobal)
    },
})

const routeTree = rootRoute.addChildren([
    mainLayoutRoute.addChildren([
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
                competitionRoute.addChildren([competitionIndexRoute]),
                eventRegisterRoute.addChildren([eventRegisterIndexRoute]),
            ]),
        ]),
        usersRoute.addChildren([usersIndexRoute, userRoute.addChildren([userIndexRoute])]),
        rolesRoute.addChildren([rolesIndexRoute]),
        invitationTokenRoute,
        registrationRoute.addChildren([registrationIndexRoute, registrationTokenRoute]),
        resetPasswordRoute.addChildren([resetPasswordIndexRoute, resetPasswordTokenRoute]),
        clubsRoute.addChildren([clubsIndexRoute, clubRoute.addChildren([clubIndexRoute])]),
        invoicesRoute,
    ]),
    appRoute.addChildren([
        appLoginRoute,
        qrEventsIndexRoute,
        qrEventRoute.addChildren([
            qrScanRoute,
            qrUserRoute,
            qrParticipantRoute,
            qrAssignRoute
        ]),
        appForbiddenRoute,
        appFunctionSelectRoute,
    ]),
])

const basepath = document.getElementById('ready2race-root')!.dataset.basepath

export const router = createRouter({
    routeTree,
    context: undefined!,
    basepath,
})

declare module '@tanstack/react-router' {
    interface Register {
        router: typeof router
    }
}
