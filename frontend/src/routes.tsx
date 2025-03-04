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
    readEventGlobal,
    readUserGlobal,
    updateEventGlobal,
    updateUserGlobal,
} from './authorization/privileges.ts'
import UsersPage from './pages/user/UsersPage.tsx'
import UserPage from './pages/user/UserPage.tsx'
import RolesPage from './pages/user/RolesPage.tsx'
import EventsPage from './pages/event/EventsPage.tsx'
import EventPage from './pages/event/EventPage.tsx'
import CompetitionPage from './pages/event/CompetitionPage.tsx'
import EventDayPage from './pages/event/EventDayPage.tsx'
import CompetitionConfigPage from './pages/event/CompetitionConfigPage.tsx'
import RegistrationPage from './pages/user/RegistrationPage.tsx'
import ResetPasswordPage from './pages/user/resetPassword/ResetPasswordPage.tsx'
import InitResetPasswordPage from './pages/user/resetPassword/InitResetPasswordPage.tsx'
import VerifyRegistrationPage from './pages/user/VerifyRegistrationPage.tsx'
import ConfigurationPage from './pages/ConfigurationPage.tsx'

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
        // Todo: Shouldn't it be && instead of ||?
        throw redirect({to: '/dashboard'})
    }
}

export const rootRoute = createRootRouteWithContext<User>()({
    component: () => <RootLayout />,
})

export const indexRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/',
    beforeLoad: ({context}) => {
        throw redirect({to: context.loggedIn ? '/dashboard' : '/login'})
    },
})

type LoginSearch = {
    redirect?: string
}

export const loginRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'login',
    component: () => <LoginPage />,
    beforeLoad: ({context}) => {
        if (context.loggedIn) {
            throw redirect({to: '/dashboard'})
        }
    },
    validateSearch: ({redirect}: {redirect?: string} & SearchSchemaInput): LoginSearch => ({
        redirect,
    }),
})

export const registrationRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'registration',
})

export const registrationIndexRoute = createRoute({
    getParentRoute: () => registrationRoute,
    path: '/',
    component: () => <RegistrationPage />,
    beforeLoad: ({context}) => {
        if (context.loggedIn) {
            throw redirect({to: '/dashboard'})
        }
    },
})

export const registrationTokenRoute = createRoute({
    getParentRoute: () => registrationRoute,
    path: '$registrationToken',
    component: () => <VerifyRegistrationPage />,
    beforeLoad: ({context}) => {
        if (context.loggedIn) {
            throw redirect({to: '/dashboard'})
        }
    },
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
    component: () => <>dashboard</>,
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
})

export const eventsRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'event',
})

export const eventsIndexRoute = createRoute({
    getParentRoute: () => eventsRoute,
    path: '/',
    component: () => <EventsPage />,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location, readEventGlobal)
    },
})

export const eventRoute = createRoute({
    getParentRoute: () => eventsRoute,
    path: '$eventId',
})

export const eventIndexRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: '/',
    component: () => <EventPage />,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location, readEventGlobal)
    },
})

export const eventDayRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: 'eventDay/$eventDayId',
})

export const eventDayIndexRoute = createRoute({
    getParentRoute: () => eventDayRoute,
    path: '/',
    component: () => <EventDayPage />,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location)
    },
})

export const competitionRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: 'competition/$competitionId',
})

export const competitionIndexRoute = createRoute({
    getParentRoute: () => competitionRoute,
    path: '/',
    component: () => <CompetitionPage />,
    beforeLoad: ({context, location}) => {
        checkAuth(context, location)
    },
})

export const competitionConfigRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: 'competitionConfig',
})

export const competitionConfigIndexRoute = createRoute({
    getParentRoute: () => competitionConfigRoute,
    path: '/',
    component: () => <CompetitionConfigPage />,
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
            eventDayRoute.addChildren([eventDayIndexRoute]),
            competitionRoute.addChildren([competitionIndexRoute]),
        ]),
    ]),
    competitionConfigRoute.addChildren([competitionConfigIndexRoute]),
    usersRoute.addChildren([usersIndexRoute, userRoute.addChildren([userIndexRoute])]),
    rolesRoute.addChildren([rolesIndexRoute]),
    registrationRoute.addChildren([registrationIndexRoute, registrationTokenRoute]),
    resetPasswordRoute.addChildren([resetPasswordIndexRoute, resetPasswordTokenRoute]),
])

export const router = createRouter({
    routeTree,
    context: undefined!,
})

declare module '@tanstack/react-router' {
    interface Register {
        router: typeof router
    }
}
