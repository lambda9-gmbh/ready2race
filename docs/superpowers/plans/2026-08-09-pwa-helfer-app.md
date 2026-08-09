# Helfer-App als PWA — Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Helfer-App unter `/app` wird installierbar, übersteht Kaltstart und Funkloch, und nimmt das Schiedsrichter-Dashboard auf.

**Architecture:** Ein Service Worker mit Scope `/app/`, erreicht über den Ablageort `dist/app/sw.js` statt über einen Server-Header. Der Lese-Cache liegt bewusst in der App-Schicht (nicht im Service Worker), damit keine Teilnehmerdaten in der CacheStorage landen und die Oberfläche den Abrufzeitpunkt kennt. Das Dashboard wird von seiner Route entkoppelt und zweifach montiert.

**Tech Stack:** React 18, TypeScript, Vite 6, MUI 6, TanStack Router, vitest, vite-plugin-pwa (neu).

**Spec:** `docs/superpowers/specs/2026-08-09-pwa-helfer-app-design.md`

## Global Constraints

- Alle Kommandos laufen im Verzeichnis `frontend/`.
- Tests: `npm test` (= `vitest run`), Einzeldatei `npx vitest run <pfad>`. Die vitest-Konfiguration in `vite.config.ts` sammelt `src/**/*.test.ts` — Tests müssen auf `.test.ts` enden, nicht `.test.tsx`.
- **Die vitest-Umgebung ist `node`, es gibt kein `localStorage` und kein `sessionStorage` im Test.** Module, die Speicher anfassen, bekommen ihn injiziert; die Tests reichen eine Attrappe herein. Keine neue Test-Umgebung, keine jsdom-Abhängigkeit.
- Bestehender Teststil: reine Logik, keine Komponententests. Dabei bleibt es.
- Deutsche Texte mit echten Umlauten. Jeder neue i18n-Schlüssel wird in **allen drei** Dateien gepflegt: `src/i18n/de/translations.json`, `en`, `da`.
- Nichts außerhalb von `/app` darf sein Verhalten ändern: keine Service-Worker-Registrierung, keine geänderte Token-Ablage für die Verwaltungsoberfläche.
- Commit-Nachrichten auf Deutsch, ohne Hinweis auf KI-Beteiligung.
- Nach jeder Aufgabe muss `npm run build` durchlaufen (`tsc -b && vite build`).

---

### Task 1: Token-Ablage, die einen App-Neustart übersteht

**Files:**
- Create: `frontend/src/contexts/user/sessionToken.ts`
- Create: `frontend/src/contexts/user/sessionToken.test.ts`
- Modify: `frontend/src/contexts/user/UserProvider.tsx` (Zeilen 46, 119, 142, 155-166)

**Interfaces:**
- Consumes: nichts
- Produces:
  - `type TokenStores = {app: WebStorageLike; session: WebStorageLike}`
  - `type WebStorageLike = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>`
  - `readSessionToken(isInApp: boolean, now?: number, stores?: TokenStores): string | null`
  - `writeSessionToken(token: string, isInApp: boolean, now?: number, stores?: TokenStores): void`
  - `touchSessionToken(now?: number, stores?: TokenStores): void`
  - `clearSessionToken(stores?: TokenStores): void`
  - `SESSION_MAX_AGE_MS: number` (= 6 Stunden)

- [ ] **Step 1: Write the failing test**

Datei `frontend/src/contexts/user/sessionToken.test.ts`:

```ts
import {describe, expect, it} from 'vitest'
import {
    clearSessionToken,
    readSessionToken,
    SESSION_MAX_AGE_MS,
    touchSessionToken,
    TokenStores,
    writeSessionToken,
} from './sessionToken.ts'

const fakeStore = () => {
    const data = new Map<string, string>()
    return {
        getItem: (k: string) => data.get(k) ?? null,
        setItem: (k: string, v: string) => void data.set(k, v),
        removeItem: (k: string) => void data.delete(k),
        size: () => data.size,
    }
}

const stores = (): TokenStores & {app: ReturnType<typeof fakeStore>; session: ReturnType<typeof fakeStore>} => ({
    app: fakeStore(),
    session: fakeStore(),
})

describe('sessionToken', () => {
    it('legt Helfer-Sitzungen in die App-Ablage, nicht in die Sitzungsablage', () => {
        const s = stores()
        writeSessionToken('abc', true, 1_000, s)
        expect(s.session.size()).toBe(0)
        expect(readSessionToken(true, 1_000, s)).toBe('abc')
    })

    it('legt Verwaltungssitzungen unverändert in die Sitzungsablage', () => {
        const s = stores()
        writeSessionToken('abc', false, 1_000, s)
        expect(s.app.size()).toBe(0)
        expect(readSessionToken(false, 1_000, s)).toBe('abc')
    })

    it('gibt einen Helfer-Token innerhalb von sechs Stunden zurück', () => {
        const s = stores()
        writeSessionToken('abc', true, 1_000, s)
        expect(readSessionToken(true, 1_000 + SESSION_MAX_AGE_MS - 1, s)).toBe('abc')
    })

    it('verwirft einen Helfer-Token nach sechs Stunden und räumt ihn weg', () => {
        const s = stores()
        writeSessionToken('abc', true, 1_000, s)
        expect(readSessionToken(true, 1_000 + SESSION_MAX_AGE_MS + 1, s)).toBeNull()
        expect(s.app.size()).toBe(0)
    })

    it('schiebt die Frist mit jedem Auffrischen nach vorn', () => {
        const s = stores()
        writeSessionToken('abc', true, 1_000, s)
        touchSessionToken(1_000 + SESSION_MAX_AGE_MS - 1, s)
        expect(readSessionToken(true, 1_000 + 2 * SESSION_MAX_AGE_MS - 2, s)).toBe('abc')
    })

    it('verwirft kaputten Inhalt, statt zu werfen', () => {
        const s = stores()
        s.app.setItem('session.app', '{kein json')
        expect(readSessionToken(true, 1_000, s)).toBeNull()
        expect(s.app.size()).toBe(0)
    })

    it('löscht beide Ablagen beim Abmelden', () => {
        const s = stores()
        writeSessionToken('abc', true, 1_000, s)
        writeSessionToken('def', false, 1_000, s)
        clearSessionToken(s)
        expect(s.app.size()).toBe(0)
        expect(s.session.size()).toBe(0)
    })

    it('fällt für Helfer auf eine bestehende Sitzungsablage zurück', () => {
        const s = stores()
        s.session.setItem('session', 'alt')
        expect(readSessionToken(true, 1_000, s)).toBe('alt')
    })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/contexts/user/sessionToken.test.ts`
Expected: FAIL — `Failed to resolve import "./sessionToken.ts"`

- [ ] **Step 3: Write the implementation**

Datei `frontend/src/contexts/user/sessionToken.ts`:

```ts
/**
 * Ablage des Sitzungstokens.
 *
 * Für die Verwaltungsoberfläche bleibt alles beim Alten: `sessionStorage`, weg beim Schließen
 * des Tabs. Helfer-Sitzungen unter `/app` liegen dagegen in `localStorage`, damit die
 * installierte App einen Neustart durch das Betriebssystem übersteht — Schiedsrichter legen das
 * Telefon weg und schauen eine Stunde später wieder drauf.
 *
 * Die Frist von sechs Stunden spiegelt das gleitende Fenster des Servers (AuthService.kt:
 * `tokenLifetime = 6.hours`, bei jedem Request neu gesetzt). Ein älterer Token wäre serverseitig
 * ohnehin ungültig; ihn hier zu verwerfen spart den 401-Umweg.
 *
 * Der Speicher wird injiziert, weil die vitest-Umgebung `node` ist und dort weder `localStorage`
 * noch `sessionStorage` existiert.
 */

export type WebStorageLike = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

export type TokenStores = {
    app: WebStorageLike
    session: WebStorageLike
}

const APP_KEY = 'session.app'
const SESSION_KEY = 'session'

export const SESSION_MAX_AGE_MS = 6 * 60 * 60 * 1000

type StoredToken = {
    token: string
    lastUsedAt: number
}

const browserStores = (): TokenStores => ({app: localStorage, session: sessionStorage})

const readStored = (stores: TokenStores): StoredToken | null => {
    const raw = stores.app.getItem(APP_KEY)
    if (raw === null) {
        return null
    }
    try {
        const parsed = JSON.parse(raw) as StoredToken
        if (typeof parsed?.token === 'string' && typeof parsed?.lastUsedAt === 'number') {
            return parsed
        }
    } catch {
        // faellt unten auf Aufraeumen durch
    }
    stores.app.removeItem(APP_KEY)
    return null
}

export const readSessionToken = (
    isInApp: boolean,
    now: number = Date.now(),
    stores: TokenStores = browserStores(),
): string | null => {
    if (!isInApp) {
        return stores.session.getItem(SESSION_KEY)
    }
    const stored = readStored(stores)
    if (stored === null) {
        return stores.session.getItem(SESSION_KEY)
    }
    if (now - stored.lastUsedAt > SESSION_MAX_AGE_MS) {
        stores.app.removeItem(APP_KEY)
        return null
    }
    return stored.token
}

export const writeSessionToken = (
    token: string,
    isInApp: boolean,
    now: number = Date.now(),
    stores: TokenStores = browserStores(),
): void => {
    if (isInApp) {
        stores.app.setItem(APP_KEY, JSON.stringify({token, lastUsedAt: now} satisfies StoredToken))
    } else {
        stores.session.setItem(SESSION_KEY, token)
    }
}

/** Schiebt die Frist nach vorn. Ohne gespeicherten Helfer-Token ein No-op. */
export const touchSessionToken = (
    now: number = Date.now(),
    stores: TokenStores = browserStores(),
): void => {
    const stored = readStored(stores)
    if (stored !== null) {
        stores.app.setItem(
            APP_KEY,
            JSON.stringify({token: stored.token, lastUsedAt: now} satisfies StoredToken),
        )
    }
}

export const clearSessionToken = (stores: TokenStores = browserStores()): void => {
    stores.app.removeItem(APP_KEY)
    stores.session.removeItem(SESSION_KEY)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/contexts/user/sessionToken.test.ts`
Expected: PASS, 8 Tests

- [ ] **Step 5: UserProvider auf das Modul umstellen**

In `frontend/src/contexts/user/UserProvider.tsx` den Import ergänzen:

```ts
import {clearSessionToken, readSessionToken, touchSessionToken, writeSessionToken} from './sessionToken.ts'
```

Zeile 44-49 — der Initialwert liest jetzt über das Modul, `isInApp` wird vorher berechnet, weil es die Ablage bestimmt:

```ts
    const initialIsInApp = router.state.resolvedLocation.pathname.startsWith('/app')
    const [userData, setUserData] = useState<UserData>({
        userInfo: undefined,
        token: readSessionToken(initialIsInApp),
        isInApp: initialIsInApp,
        authStatus: 'initial',
    })
```

Zeile 119 (`sessionStorage.removeItem('session')` im `onResponse` von `checkUserLogin`) wird zu:

```ts
                clearSessionToken()
```

Zeile 142 (`sessionStorage.setItem('session', token)` in `setAuth`) wird zu:

```ts
            writeSessionToken(token, isInApp)
```

`logout` (Zeile 155-166) räumt künftig auch dann auf, wenn der Server nicht erreichbar ist — sonst bleibt der Token nach einem Abmelden im Funkloch auf dem Gerät liegen:

```ts
    const logout = async (isInApp: boolean = false) => {
        await userLogout()
        clearSessionToken()
        setUserData({
            userInfo: undefined,
            isInApp,
            token: null,
            authStatus: 'pending',
        })
    }
```

Im Response-Interceptor (Zeile 54-65) wird die Frist bei jeder erfolgreichen Antwort nachgezogen:

```ts
    useEffect(() => {
        const f = async (res: Response) => {
            if (res.status === 401 && userData.authStatus === 'authenticated') {
                const isInApp = router.state.resolvedLocation.pathname.startsWith('/app')
                await logout(isInApp)
            } else if (res.ok) {
                touchSessionToken()
            }
            return res
        }

        client.interceptors.response.use(f)
        return () => client.interceptors.response.eject(f)
    }, [])
```

- [ ] **Step 6: Build und Gesamttests**

Run: `cd frontend && npm test && npm run build`
Expected: alle Tests grün, Build ohne TypeScript-Fehler

- [ ] **Step 7: Commit**

```bash
git add frontend/src/contexts/user/sessionToken.ts frontend/src/contexts/user/sessionToken.test.ts frontend/src/contexts/user/UserProvider.tsx
git commit -m "Helfer-Sitzung übersteht einen Neustart der App"
```

---

### Task 2: Lese-Cache als eigenes Modul

**Files:**
- Create: `frontend/src/pwa/readCache.ts`
- Create: `frontend/src/pwa/readCache.test.ts`

**Interfaces:**
- Consumes: `WebStorageLike` aus `src/contexts/user/sessionToken.ts`
- Produces:
  - `CACHE_MAX_AGE_MS: number` (= 12 Stunden)
  - `type CachedRead<T> = {payload: T; fetchedAt: number}`
  - `writeCachedRead<T>(kind: string, userId: string, eventId: string, payload: T, now?: number, store?: WebStorageLike): void`
  - `readCachedRead<T>(kind: string, userId: string, eventId: string, now?: number, store?: WebStorageLike): CachedRead<T> | null`
  - `clearCachedReads(store?: WebStorageLike): void`

- [ ] **Step 1: Write the failing test**

Datei `frontend/src/pwa/readCache.test.ts`:

```ts
import {describe, expect, it} from 'vitest'
import {
    CACHE_MAX_AGE_MS,
    clearCachedReads,
    readCachedRead,
    writeCachedRead,
} from './readCache.ts'

const fakeStore = () => {
    const data = new Map<string, string>()
    return {
        getItem: (k: string) => data.get(k) ?? null,
        setItem: (k: string, v: string) => void data.set(k, v),
        removeItem: (k: string) => void data.delete(k),
        key: (i: number) => [...data.keys()][i] ?? null,
        get length() {
            return data.size
        },
    }
}

describe('readCache', () => {
    it('gibt zurück, was hineingelegt wurde', () => {
        const store = fakeStore()
        writeCachedRead('dashboard', 'u1', 'e1', {a: 1}, 5_000, store)
        expect(readCachedRead('dashboard', 'u1', 'e1', 5_000, store)).toEqual({
            payload: {a: 1},
            fetchedAt: 5_000,
        })
    })

    it('gibt nichts für einen anderen Nutzer zurück', () => {
        const store = fakeStore()
        writeCachedRead('dashboard', 'u1', 'e1', {a: 1}, 5_000, store)
        expect(readCachedRead('dashboard', 'u2', 'e1', 5_000, store)).toBeNull()
    })

    it('gibt nichts für ein anderes Event zurück', () => {
        const store = fakeStore()
        writeCachedRead('dashboard', 'u1', 'e1', {a: 1}, 5_000, store)
        expect(readCachedRead('dashboard', 'u1', 'e2', 5_000, store)).toBeNull()
    })

    it('gibt nichts für eine andere Art zurück', () => {
        const store = fakeStore()
        writeCachedRead('dashboard', 'u1', 'e1', {a: 1}, 5_000, store)
        expect(readCachedRead('events', 'u1', 'e1', 5_000, store)).toBeNull()
    })

    it('hält einen Eintrag knapp unter zwölf Stunden', () => {
        const store = fakeStore()
        writeCachedRead('dashboard', 'u1', 'e1', {a: 1}, 5_000, store)
        const entry = readCachedRead('dashboard', 'u1', 'e1', 5_000 + CACHE_MAX_AGE_MS - 1, store)
        expect(entry?.payload).toEqual({a: 1})
    })

    it('verwirft einen Eintrag nach zwölf Stunden und räumt ihn weg', () => {
        const store = fakeStore()
        writeCachedRead('dashboard', 'u1', 'e1', {a: 1}, 5_000, store)
        expect(readCachedRead('dashboard', 'u1', 'e1', 5_000 + CACHE_MAX_AGE_MS + 1, store)).toBeNull()
        expect(store.length).toBe(0)
    })

    it('verwirft kaputten Inhalt, statt zu werfen', () => {
        const store = fakeStore()
        store.setItem('r2r.readCache.dashboard.u1.e1', '{kein json')
        expect(readCachedRead('dashboard', 'u1', 'e1', 5_000, store)).toBeNull()
    })

    it('gibt nichts aus einem leeren Speicher zurück', () => {
        expect(readCachedRead('dashboard', 'u1', 'e1', 5_000, fakeStore())).toBeNull()
    })

    it('löscht beim Abmelden nur die eigenen Einträge', () => {
        const store = fakeStore()
        writeCachedRead('dashboard', 'u1', 'e1', {a: 1}, 5_000, store)
        store.setItem('fremd', 'bleibt')
        clearCachedReads(store)
        expect(store.getItem('fremd')).toBe('bleibt')
        expect(readCachedRead('dashboard', 'u1', 'e1', 5_000, store)).toBeNull()
    })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/pwa/readCache.test.ts`
Expected: FAIL — `Failed to resolve import "./readCache.ts"`

- [ ] **Step 3: Write the implementation**

Datei `frontend/src/pwa/readCache.ts`:

```ts
/**
 * Lese-Cache der Helfer-App.
 *
 * Liegt bewusst in der App-Schicht statt im Service Worker: Ein Runtime-Cache auf die API würde
 * Teilnehmerdaten mit Klarnamen und Jahrgängen in der CacheStorage ablegen — auf Geräten, die
 * sich mehrere Leute teilen. Hier ist der Schlüssel an die Nutzerin gebunden, das Abmelden räumt
 * auf, und die Oberfläche bekommt den Abrufzeitpunkt für ihr Veraltet-Banner mitgeliefert.
 *
 * Die zwölf Stunden liegen über dem Sechs-Stunden-Fenster der Sitzung: Der Cache soll nie der
 * begrenzende Faktor sein. Überlebt er die Sitzung, ist er ohne Belang, weil dann ohnehin der
 * Anmeldebildschirm kommt.
 */

import {WebStorageLike} from '@contexts/user/sessionToken.ts'

/** `key`/`length` braucht nur das Aufräumen, deshalb getrennt vom schmalen Schreib-/Lesetyp. */
type EnumerableStorage = WebStorageLike & Pick<Storage, 'key' | 'length'>

const PREFIX = 'r2r.readCache.'

export const CACHE_MAX_AGE_MS = 12 * 60 * 60 * 1000

export type CachedRead<T> = {
    payload: T
    fetchedAt: number
}

type StoredRead<T> = CachedRead<T> & {
    userId: string
    eventId: string
}

const browserStore = (): EnumerableStorage => localStorage

const keyFor = (kind: string, userId: string, eventId: string) =>
    `${PREFIX}${kind}.${userId}.${eventId}`

export const writeCachedRead = <T>(
    kind: string,
    userId: string,
    eventId: string,
    payload: T,
    now: number = Date.now(),
    store: WebStorageLike = browserStore(),
): void => {
    const stored: StoredRead<T> = {payload, fetchedAt: now, userId, eventId}
    try {
        store.setItem(keyFor(kind, userId, eventId), JSON.stringify(stored))
    } catch {
        // Voller Speicher darf den Abruf nicht scheitern lassen - der Cache ist Beiwerk.
    }
}

export const readCachedRead = <T>(
    kind: string,
    userId: string,
    eventId: string,
    now: number = Date.now(),
    store: WebStorageLike = browserStore(),
): CachedRead<T> | null => {
    const key = keyFor(kind, userId, eventId)
    const raw = store.getItem(key)
    if (raw === null) {
        return null
    }
    let stored: StoredRead<T>
    try {
        stored = JSON.parse(raw) as StoredRead<T>
    } catch {
        store.removeItem(key)
        return null
    }
    if (
        typeof stored?.fetchedAt !== 'number' ||
        stored.userId !== userId ||
        stored.eventId !== eventId
    ) {
        store.removeItem(key)
        return null
    }
    if (now - stored.fetchedAt > CACHE_MAX_AGE_MS) {
        store.removeItem(key)
        return null
    }
    return {payload: stored.payload, fetchedAt: stored.fetchedAt}
}

export const clearCachedReads = (store: EnumerableStorage = browserStore()): void => {
    const keys: string[] = []
    for (let i = 0; i < store.length; i++) {
        const key = store.key(i)
        if (key !== null && key.startsWith(PREFIX)) {
            keys.push(key)
        }
    }
    keys.forEach(key => store.removeItem(key))
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/pwa/readCache.test.ts`
Expected: PASS, 9 Tests

- [ ] **Step 5: Aufräumen beim Abmelden anhängen**

In `frontend/src/contexts/user/UserProvider.tsx` den Import ergänzen:

```ts
import {clearCachedReads} from '@pwa/readCache.ts'
```

und in `logout` direkt nach `clearSessionToken()` einfügen:

```ts
        clearCachedReads()
```

Hinweis: `@pwa` ist noch kein Pfad-Alias. Prüfe `frontend/tsconfig.app.json` auf die vorhandenen `paths`-Einträge (`@components`, `@contexts`, …) und ergänze `"@pwa/*": ["src/pwa/*"]` nach demselben Muster. `vite-tsconfig-paths` übernimmt das für den Build ohne weitere Änderung.

- [ ] **Step 6: Build und Gesamttests**

Run: `cd frontend && npm test && npm run build`
Expected: alle Tests grün, Build ohne Fehler

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pwa/readCache.ts frontend/src/pwa/readCache.test.ts frontend/src/contexts/user/UserProvider.tsx frontend/tsconfig.app.json
git commit -m "Lese-Cache für die Helfer-App"
```

---

### Task 3: Dashboard von seiner Route entkoppeln

**Files:**
- Modify: `frontend/src/pages/event/LiveDashboardPage.tsx` (Zeile 89 und die Komponentensignatur)
- Modify: `frontend/src/routes.tsx` (`eventLiveDashboardRoute`, Zeile 316-323)

**Interfaces:**
- Consumes: nichts
- Produces: `LiveDashboardPage` nimmt `{eventId}: {eventId: string}` als Props statt `useParams`. Task 4 montiert dieselbe Komponente ein zweites Mal.

- [ ] **Step 1: Komponentensignatur umstellen**

In `frontend/src/pages/event/LiveDashboardPage.tsx`: Die Zeile

```ts
    const {eventId} = eventLiveDashboardRoute.useParams()
```

entfällt. Stattdessen bekommt die Komponente Props. Die Deklaration (`const LiveDashboardPage = () => {`) wird zu:

```tsx
export type LiveDashboardPageProps = {
    eventId: string
}

const LiveDashboardPage = ({eventId}: LiveDashboardPageProps) => {
```

Der Import `import {eventLiveDashboardRoute} from '@routes'` wird ersatzlos entfernt — er ist damit die einzige verbleibende Verbindung zur Route und würde sonst einen Kreis zwischen `routes.tsx` und der Seite offenhalten.

- [ ] **Step 2: Bestehende Route den Parameter übergeben lassen**

In `frontend/src/routes.tsx` wird `eventLiveDashboardRoute` (Zeile 316-323) zu:

```tsx
export const eventLiveDashboardRoute = createRoute({
    getParentRoute: () => eventRoute,
    path: 'liveDashboard',
    component: function EventLiveDashboard() {
        const {eventId} = eventLiveDashboardRoute.useParams()
        return <LiveDashboardPage eventId={eventId} />
    },
    beforeLoad: ({context, location}) => {
        checkAuth(context, location, readLiveDashboardGlobal)
    },
})
```

- [ ] **Step 3: Build prüfen**

Run: `cd frontend && npm run build`
Expected: kein TypeScript-Fehler. Schlägt er mit „Cannot find name 'eventLiveDashboardRoute'" in der Seite fehl, ist der Import aus Schritt 1 nicht entfernt worden.

- [ ] **Step 4: Gesamttests**

Run: `cd frontend && npm test`
Expected: alle Tests grün (die vorhandenen `liveDashboard`-Tests prüfen reine Logik und sind von der Signatur nicht betroffen)

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/event/LiveDashboardPage.tsx frontend/src/routes.tsx
git commit -m "Dashboard nimmt die Veranstaltung als Eigenschaft entgegen"
```

---

### Task 4: Dashboard-Route unter `/app`

**Files:**
- Create: `frontend/src/pages/app/AppDashboardPage.tsx`
- Modify: `frontend/src/contexts/app/AppSessionContext.tsx` (`AppView`, `appViewPaths`)
- Modify: `frontend/src/routes.tsx` (neue Route, Eintrag im `routeTree`)

**Interfaces:**
- Consumes: `LiveDashboardPage` mit `eventId`-Prop aus Task 3
- Produces: Route `/app/dashboard`, `AppView`-Wert `'APP_Dashboard'`

- [ ] **Step 1: AppView erweitern**

In `frontend/src/contexts/app/AppSessionContext.tsx` den Union um einen Wert ergänzen:

```ts
export type AppView =
    | 'APP_Event_List'
    | 'APP_Function_Select'
    | 'APP_Scanner'
    | 'APP_Participant'
    | 'App_Assign'
    | 'App_User'
    | 'App_Login'
    | 'APP_Forbidden'
    | 'APP_Dashboard'
```

und die Pfadtabelle direkt darunter:

```ts
const appViewPaths: Record<AppView, string> = {
    APP_Event_List: '/app',
    APP_Function_Select: '/app/function',
    APP_Scanner: '/app/scanner',
    APP_Participant: '/app/participant',
    App_Assign: '/app/assign',
    App_User: '/app/user',
    App_Login: '/app/login',
    APP_Forbidden: '/app/forbidden',
    APP_Dashboard: '/app/dashboard',
}
```

- [ ] **Step 2: Wrapper-Seite schreiben**

Datei `frontend/src/pages/app/AppDashboardPage.tsx`:

```tsx
import {Alert} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useAppSession} from '@contexts/app/AppSessionContext.tsx'
import LiveDashboardPage from '../event/LiveDashboardPage.tsx'

/**
 * Dasselbe Dashboard wie in der Verwaltungsoberfläche, nur im App-Layout und ohne Chrome.
 * Die Veranstaltung kommt aus der App-Sitzung statt aus der Route.
 */
const AppDashboardPage = () => {
    const {t} = useTranslation()
    const {eventId} = useAppSession()

    if (!eventId) {
        return <Alert severity="info">{t('app.dashboard.noEvent')}</Alert>
    }

    return <LiveDashboardPage eventId={eventId} />
}

export default AppDashboardPage
```

- [ ] **Step 3: Route anlegen**

In `frontend/src/routes.tsx` den Import ergänzen:

```ts
import AppDashboardPage from './pages/app/AppDashboardPage.tsx'
```

Nach `appFunctionSelectRoute` einfügen:

```tsx
export const appDashboardRoute = createRoute({
    getParentRoute: () => appRoute,
    path: 'dashboard',
    component: () => <AppDashboardPage />,
    beforeLoad: ({context}) => {
        checkAuthApp(context)
    },
})
```

Im `routeTree` gehört `appDashboardRoute` in die Kinderliste von `appRoute` — dort, wo `appFunctionSelectRoute` schon steht.

- [ ] **Step 4: Übersetzung ergänzen**

In allen drei Dateien `frontend/src/i18n/{de,en,da}/translations.json` unter `app` einen Block `dashboard` anlegen:

- de: `"dashboard": {"noEvent": "Keine Veranstaltung gewählt."}`
- en: `"dashboard": {"noEvent": "No event selected."}`
- da: `"dashboard": {"noEvent": "Ingen begivenhed valgt."}`

- [ ] **Step 5: Build und Tests**

Run: `cd frontend && npm test && npm run build`
Expected: alle Tests grün, Build ohne Fehler

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/app/AppDashboardPage.tsx frontend/src/routes.tsx frontend/src/contexts/app/AppSessionContext.tsx frontend/src/i18n
git commit -m "Schiedsrichter-Dashboard ist unter /app/dashboard erreichbar"
```

---

### Task 5: Kachel in der Funktionsauswahl, ohne den Scanner zu vergiften

**Files:**
- Modify: `frontend/src/components/qrApp/common.ts`
- Create: `frontend/src/components/qrApp/appEntries.test.ts`
- Modify: `frontend/src/pages/app/AppFunctionSelectPage.tsx`
- Modify: `frontend/src/i18n/{de,en,da}/translations.json`

**Interfaces:**
- Consumes: `AppView` `'APP_Dashboard'` aus Task 4
- Produces:
  - `type AppEntry = {key: string; labelKey: string; target: AppView; appFunction: AppFunction}`
  - `appEntries(user: User): AppEntry[]`

**Warum nicht einfach in `AppFunction`:** Der Union wird auch von `QrScannerPage.tsx:82` benutzt, um das Scanner-Verhalten abzuleiten. Ein Dashboard-Wert darin würde dort als unbekannte Scanner-Funktion auftauchen.

- [ ] **Step 1: Write the failing test**

Datei `frontend/src/components/qrApp/appEntries.test.ts`:

```ts
import {describe, expect, it} from 'vitest'
import {appEntries} from './common.ts'
import {User} from '@contexts/user/UserContext.ts'
import {Privilege} from '@api/types.gen.ts'

const userWith = (privileges: Privilege[]): User =>
    ({
        loggedIn: true,
        checkPrivilege: (p: Privilege) =>
            privileges.some(
                q => q.action === p.action && q.resource === p.resource && q.scope === p.scope,
            ),
    }) as unknown as User

const qrManagement: Privilege = {action: 'UPDATE', resource: 'APP_QR_MANAGEMENT', scope: 'GLOBAL'}
const liveDashboard: Privilege = {action: 'READ', resource: 'LIVE_DASHBOARD', scope: 'GLOBAL'}

describe('appEntries', () => {
    it('gibt ohne Rechte nichts zurück', () => {
        expect(appEntries(userWith([]))).toEqual([])
    })

    it('gibt Scanner-Funktionen mit dem Scanner als Ziel zurück', () => {
        const entries = appEntries(userWith([qrManagement]))
        expect(entries).toHaveLength(1)
        expect(entries[0].appFunction).toBe('APP_QR_MANAGEMENT')
        expect(entries[0].target).toBe('APP_Scanner')
    })

    it('gibt das Dashboard mit eigenem Ziel zurück', () => {
        const entries = appEntries(userWith([liveDashboard]))
        expect(entries).toHaveLength(1)
        expect(entries[0].key).toBe('LIVE_DASHBOARD')
        expect(entries[0].target).toBe('APP_Dashboard')
        expect(entries[0].appFunction).toBeNull()
    })

    it('gibt bei beiden Rechten beide Einträge zurück', () => {
        expect(appEntries(userWith([qrManagement, liveDashboard]))).toHaveLength(2)
    })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/components/qrApp/appEntries.test.ts`
Expected: FAIL — `appEntries is not a function` bzw. kein Export dieses Namens

- [ ] **Step 3: `appEntries` implementieren**

In `frontend/src/components/qrApp/common.ts` unten anfügen (bestehende Funktionen unverändert lassen):

```ts
import {AppView} from '@contexts/app/AppSessionContext.tsx'
import {readLiveDashboardGlobal} from '@authorization/privileges.ts'

/**
 * Ein Eintrag der Funktionsauswahl. Scanner-Funktionen tragen ihre `AppFunction`; das
 * Dashboard ist keine Scanner-Funktion und trägt deshalb `null` - der Scanner darf nichts
 * bekommen, womit er nichts anfangen kann.
 */
export type AppEntry = {
    key: string
    labelKey: string
    target: AppView
    appFunction: AppFunction
}

const scannerLabels: Record<Exclude<AppFunction, null>, string> = {
    APP_QR_MANAGEMENT: 'app.functionSelect.functions.qrManagement',
    APP_COMPETITION_CHECK: 'app.functionSelect.functions.competitionCheck',
    APP_EVENT_REQUIREMENT: 'app.functionSelect.functions.eventRequirement',
    APP_CATERER: 'app.functionSelect.functions.caterer',
}

export const appEntries = (user: User): AppEntry[] => {
    const entries: AppEntry[] = getUserAppRights(user)
        .filter((fn): fn is Exclude<AppFunction, null> => fn !== null)
        .map(fn => ({
            key: fn,
            labelKey: scannerLabels[fn],
            target: 'APP_Scanner' as AppView,
            appFunction: fn,
        }))

    if (user.checkPrivilege(readLiveDashboardGlobal)) {
        entries.push({
            key: 'LIVE_DASHBOARD',
            labelKey: 'app.functionSelect.functions.liveDashboard',
            target: 'APP_Dashboard',
            appFunction: null,
        })
    }

    return entries
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/components/qrApp/appEntries.test.ts`
Expected: PASS, 4 Tests

- [ ] **Step 5: Auswahlseite umstellen**

In `frontend/src/pages/app/AppFunctionSelectPage.tsx`:

- Import `getUserAppRights` durch `appEntries` ersetzen, `DashboardIcon` ergänzen:

```ts
import {appEntries} from '@components/qrApp/common.ts'
import SportsScoreIcon from '@mui/icons-material/SportsScore'
```

- Die Konstante `APP_FUNCTIONS` entfällt. An ihre Stelle tritt eine Symboltabelle:

```ts
const ENTRY_ICONS: Record<string, typeof QrCodeIcon> = {
    APP_QR_MANAGEMENT: QrCodeIcon,
    APP_COMPETITION_CHECK: CheckCircleIcon,
    APP_EVENT_REQUIREMENT: AssignmentIcon,
    APP_CATERER: RestaurantIcon,
    LIVE_DASHBOARD: SportsScoreIcon,
}
```

- `const availableAppFunctions = getUserAppRights(user)` wird zu:

```ts
    const entries = appEntries(user)
```

- Der `useEffect` prüft künftig die Einträge statt der Scanner-Funktionen:

```ts
    useEffect(() => {
        if (entries.length === 0 && user.loggedIn) {
            navigateTo('APP_Forbidden')
        }
    }, [entries.length, user.loggedIn, navigateTo])
```

- `handleSelect` nimmt den Eintrag und folgt seinem Ziel:

```ts
    const handleSelect = (entry: AppEntry) => {
        setAppFunction(entry.appFunction)
        navigateTo(entry.target)
    }
```

- Im Kachelraster tritt `entries.map(...)` an die Stelle von `APP_FUNCTIONS.filter(...).map(...)`; `key` und `onClick` verwenden `entry.key` bzw. `handleSelect(entry)`, das Symbol kommt aus `ENTRY_ICONS[entry.key]`, die Beschriftung aus `t(entry.labelKey)`.

- [ ] **Step 6: Übersetzungen ergänzen**

Unter `app.functionSelect.functions` in allen drei Dateien:

- de: `"liveDashboard": "Schiedsrichter-Dashboard"`
- en: `"liveDashboard": "Referee dashboard"`
- da: `"liveDashboard": "Dommerpanel"`

- [ ] **Step 7: Build und Gesamttests**

Run: `cd frontend && npm test && npm run build`
Expected: alle Tests grün, Build ohne Fehler

- [ ] **Step 8: Commit**

```bash
git add frontend/src/components/qrApp frontend/src/pages/app/AppFunctionSelectPage.tsx frontend/src/i18n
git commit -m "Funktionsauswahl führt zum Schiedsrichter-Dashboard"
```

---

### Task 6: Offline-Verhalten des Dashboards

**Files:**
- Modify: `frontend/src/pages/event/LiveDashboardPage.tsx`
- Create: `frontend/src/components/event/liveDashboard/staleState.ts`
- Create: `frontend/src/components/event/liveDashboard/staleState.test.ts`
- Modify: `frontend/src/i18n/{de,en,da}/translations.json`

**Interfaces:**
- Consumes: `readCachedRead` / `writeCachedRead` aus Task 2
- Produces: `describeStale(fetchedAt: number | null, stale: boolean, now: number): StaleState` mit `type StaleState = {show: boolean; fromCache: boolean; actionsLocked: boolean}`

- [ ] **Step 1: Write the failing test**

Datei `frontend/src/components/event/liveDashboard/staleState.test.ts`:

```ts
import {describe, expect, it} from 'vitest'
import {describeStale} from './staleState.ts'

describe('describeStale', () => {
    it('meldet nichts, solange der Abruf frisch ist', () => {
        expect(describeStale(1_000, false, 2_000)).toEqual({
            show: false,
            fromCache: false,
            actionsLocked: false,
        })
    })

    it('sperrt Aktionen, sobald der Abruf fehlschlägt', () => {
        expect(describeStale(1_000, true, 2_000)).toEqual({
            show: true,
            fromCache: false,
            actionsLocked: true,
        })
    })

    it('erkennt einen Stand aus dem Cache an seinem Alter', () => {
        expect(describeStale(1_000, true, 1_000 + 60_000)).toEqual({
            show: true,
            fromCache: true,
            actionsLocked: true,
        })
    })

    it('sperrt Aktionen auch ohne jeden Abrufzeitpunkt', () => {
        expect(describeStale(null, true, 2_000)).toEqual({
            show: true,
            fromCache: false,
            actionsLocked: true,
        })
    })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/components/event/liveDashboard/staleState.test.ts`
Expected: FAIL — `Failed to resolve import "./staleState.ts"`

- [ ] **Step 3: Write the implementation**

Datei `frontend/src/components/event/liveDashboard/staleState.ts`:

```ts
/**
 * Wie die Anzeige mit einem nicht mehr frischen Stand umgeht.
 *
 * `fromCache` unterscheidet „Abruf gerade eben fehlgeschlagen" von „das hier ist der Stand von
 * vorhin": Ab einer halben Minute Abstand nennt das Banner Datum und Uhrzeit, statt nur eine
 * gestörte Verbindung zu melden.
 */

/** Ab hier gilt ein Stand als von früher, nicht als eben verpasste Aktualisierung. */
const FROM_CACHE_AFTER_MS = 30_000

export type StaleState = {
    show: boolean
    fromCache: boolean
    actionsLocked: boolean
}

export const describeStale = (
    fetchedAt: number | null,
    stale: boolean,
    now: number,
): StaleState => {
    if (!stale) {
        return {show: false, fromCache: false, actionsLocked: false}
    }
    const fromCache = fetchedAt !== null && now - fetchedAt >= FROM_CACHE_AFTER_MS
    return {show: true, fromCache, actionsLocked: true}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/components/event/liveDashboard/staleState.test.ts`
Expected: PASS, 4 Tests

- [ ] **Step 5: Cache und Sperre in die Seite einbauen**

In `frontend/src/pages/event/LiveDashboardPage.tsx`:

- Importe ergänzen:

```ts
import {readCachedRead, writeCachedRead} from '@pwa/readCache.ts'
import {describeStale} from '@components/event/liveDashboard/staleState.ts'
```

- Beim ersten Rendern den gespeicherten Stand als Startwert nehmen. `user.id` gibt es nur bei angemeldeten Nutzerinnen; ohne Anmeldung kommt die Seite ohnehin nicht zustande:

```ts
    const cacheUserId = user.loggedIn ? user.id : ''
    // Einmal lesen, dreifach verwenden: Der Startwert aller drei Zustände kommt aus demselben
    // Eintrag, und der useState-Initialisierer läuft nur beim ersten Rendern.
    const [cachedStart] = useState(() =>
        readCachedRead<LiveDashboardDto>('dashboard', cacheUserId, eventId),
    )
    const [dashboard, setDashboard] = useState<LiveDashboardDto | null>(
        cachedStart?.payload ?? null,
    )
    const [lastUpdated, setLastUpdated] = useState<Date | null>(
        cachedStart ? new Date(cachedStart.fetchedAt) : null,
    )
    // Ein Stand aus dem Cache gilt bis zum ersten erfolgreichen Abruf als veraltet - sonst
    // stünden die Aktionen auf Daten von vorhin offen.
    const [stale, setStale] = useState(cachedStart !== null)
```

  Die bisherigen drei `useState`-Zeilen für `dashboard`, `lastUpdated` und `stale` entfallen dafür.

- Im `onResponse` des `useFetch` (dort, wo heute `setDashboard(data)` steht) den Stand mitschreiben:

```ts
                    setDashboard(data)
                    writeCachedRead('dashboard', cacheUserId, eventId, data)
```

- Der Zustandsblock direkt vor dem `return` der Seite:

```ts
    const staleState = describeStale(lastUpdated?.getTime() ?? null, stale, now.getTime())
```

- Die fünf Handlungen (heute Zeilen 340-344) hängen zusätzlich an der Sperre:

```ts
        onFinish: mayFinish && !staleState.actionsLocked ? handleFinish : undefined,
        onSetActivated: mayControl && !staleState.actionsLocked ? handleSetActivated : undefined,
        onMarkStarted: mayControl && !staleState.actionsLocked ? handleMarkStarted : undefined,
        onResumeAutoPull: mayControl && !staleState.actionsLocked ? handleResumeAutoPull : undefined,
        onSkipSlot: mayControl && !staleState.actionsLocked ? handleSkipSlot : undefined,
```

- Das vorhandene Warnbanner (heute Zeile 427-429) unterscheidet künftig die beiden Fälle:

```tsx
                {staleState.show && dashboard && (
                    <Alert severity="warning">
                        {staleState.fromCache && lastUpdated
                            ? t('event.liveDashboard.staleSince', {
                                  time: format(lastUpdated, t('format.datetime')),
                              })
                            : t('event.liveDashboard.staleWarning')}
                    </Alert>
                )}
```

  `format.datetime` ist der vorhandene Schlüssel (`"dd.MM.yyyy, HH:mm"`, `translations.json:168`) — kleingeschrieben, kein neuer Schlüssel nötig.

- [ ] **Step 6: Übersetzung ergänzen**

Unter `event.liveDashboard` in allen drei Dateien:

- de: `"staleSince": "Stand von {{time}} — ohne Verbindung sind keine Aktionen möglich."`
- en: `"staleSince": "As of {{time}} — no actions are possible while offline."`
- da: `"staleSince": "Pr. {{time}} — ingen handlinger er mulige uden forbindelse."`

- [ ] **Step 7: Build und Gesamttests**

Run: `cd frontend && npm test && npm run build`
Expected: alle Tests grün, Build ohne Fehler

- [ ] **Step 8: Commit**

```bash
git add frontend/src/pages/event/LiveDashboardPage.tsx frontend/src/components/event/liveDashboard/staleState.ts frontend/src/components/event/liveDashboard/staleState.test.ts frontend/src/i18n
git commit -m "Dashboard zeigt ohne Verbindung den letzten Stand ohne Aktionen"
```

---

### Task 7: Service Worker, Manifest und Icons

**Files:**
- Modify: `frontend/package.json`, `frontend/vite.config.ts`, `frontend/index.html`
- Create: `frontend/src/pwa/sw.ts`, `frontend/src/pwa/registerAppSW.ts`
- Create: `frontend/public/app/icon-192.png`, `icon-512.png`, `icon-maskable-512.png`, `apple-touch-icon.png`
- Modify: `frontend/src/layouts/AppLayout.tsx`

**Interfaces:**
- Consumes: nichts
- Produces:
  - `useRegisterAppSW(): {needRefresh: boolean; updateApp: () => void}` — registriert den Worker, meldet neue Fassungen
  - `resetAppInstallation(): Promise<void>` — freie Funktion, registriert nichts
  - Task 8 baut die Oberfläche auf beidem

- [ ] **Step 1: Abhängigkeit aufnehmen**

Run: `cd frontend && npm install -D vite-plugin-pwa workbox-precaching workbox-routing workbox-strategies`
Expected: `package.json` und `package-lock.json` geändert. Die installierte Fassung von `vite-plugin-pwa` muss Vite 6 unterstützen (v1 oder neuer); bei einer Peer-Warnung zu Vite die Fassung prüfen, bevor es weitergeht. Die drei `workbox-*`-Pakete werden im eigenen Service Worker direkt importiert und deshalb ausdrücklich aufgeführt, statt sich auf transitive Auflösung zu verlassen.

- [ ] **Step 2: Icons erzeugen**

Run (macOS, `sips` ist vorinstalliert):

```bash
cd frontend && mkdir -p public/app && \
sips -Z 168 public/r2r_logo.png --out /tmp/r2r-168.png >/dev/null && \
sips --padToHeightWidth 192 192 --padColor FFFFFF /tmp/r2r-168.png --out public/app/icon-192.png >/dev/null && \
sips -Z 448 public/r2r_logo.png --out /tmp/r2r-448.png >/dev/null && \
sips --padToHeightWidth 512 512 --padColor FFFFFF /tmp/r2r-448.png --out public/app/icon-512.png >/dev/null && \
sips -Z 358 public/r2r_logo.png --out /tmp/r2r-358.png >/dev/null && \
sips --padToHeightWidth 512 512 --padColor FFFFFF /tmp/r2r-358.png --out public/app/icon-maskable-512.png >/dev/null && \
sips -Z 158 public/r2r_logo.png --out /tmp/r2r-158.png >/dev/null && \
sips --padToHeightWidth 180 180 --padColor FFFFFF /tmp/r2r-158.png --out public/app/apple-touch-icon.png >/dev/null && \
sips -g pixelWidth -g pixelHeight public/app/icon-192.png public/app/icon-512.png public/app/icon-maskable-512.png public/app/apple-touch-icon.png
```

Expected: vier Dateien mit 192×192, 512×512, 512×512 und 180×180. Das maskierbare Symbol hat sichtbar mehr Rand — das ist Absicht, Android beschneidet es kreisförmig.

- [ ] **Step 3: Service Worker schreiben**

Datei `frontend/src/pwa/sw.ts`:

```ts
/// <reference lib="webworker" />
import {cleanupOutdatedCaches, precacheAndRoute} from 'workbox-precaching'
import {NavigationRoute, registerRoute} from 'workbox-routing'
import {NetworkFirst} from 'workbox-strategies'

declare const self: ServiceWorkerGlobalScope

/**
 * Service Worker der Helfer-App.
 *
 * Liegt unter /app/, damit sein Scope allein aus dem Ablageort folgt - der Header
 * `Service-Worker-Allowed` braucht Zugriff aufs Hosting, den wir nicht haben.
 *
 * Er bedient ausschließlich die Shell. Die API bleibt netzwerk-only: Antworten enthalten
 * Teilnehmerdaten mit Klarnamen, die nichts in der CacheStorage eines geteilten Geräts zu
 * suchen haben. Der Lese-Cache liegt stattdessen in der App-Schicht (src/pwa/readCache.ts).
 */

precacheAndRoute(self.__WB_MANIFEST)
cleanupOutdatedCaches()

// Bewusst NICHT createHandlerBoundToURL('index.html'): Das setzt voraus, dass die index.html im
// Precache liegt, und genau die ist per globIgnores ausgenommen (sie wird beim Ausliefern nicht
// veraendert, aber ein eingefrorener Shell-Einstieg ist bei einem Regatta-Update das Letzte, was
// wir wollen). NetworkFirst holt sie frisch, faellt offline auf die zuletzt gesehene zurueck.
registerRoute(
    new NavigationRoute(new NetworkFirst({cacheName: 'app-shell'}), {
        allowlist: [/^\/app(\/|$)/],
        denylist: [/^\/api\//, /^\/static\//],
    }),
)

// Kein stilles Übernehmen: Die Oberfläche fragt erst, dann wird gewechselt. Mitten im
// Rennbetrieb soll sich das Dashboard nicht unter den Händen austauschen.
self.addEventListener('message', event => {
    if (event.data?.type === 'SKIP_WAITING') {
        void self.skipWaiting()
    }
})
```

- [ ] **Step 4: Plugin konfigurieren**

`frontend/vite.config.ts` vollständig:

```ts
import { defineConfig, Plugin } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tsconfigPaths from "vite-tsconfig-paths";
import { VitePWA } from 'vite-plugin-pwa'
import { renameSync, mkdirSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * Legt den gebauten Service Worker nach dist/app/ um.
 *
 * Der Scope eines Service Workers folgt seinem Ablageort. Nur unter /app/ kontrolliert er die
 * Helfer-App und sonst nichts - der Header `Service-Worker-Allowed` waere die Alternative und
 * setzt Zugriff aufs Hosting voraus, den wir nicht haben. vite-plugin-pwa schreibt die Datei ins
 * Wurzelverzeichnis von dist; das Verschieben danach ist der verlaessliche Weg, weil keine
 * Plugin-Option dafuer dokumentiert ist.
 *
 * Die Precache-Eintraege im Worker sind absolute Pfade ab '/', das Verschieben beruehrt sie nicht.
 */
const moveServiceWorkerToApp = (): Plugin => ({
  name: 'r2r-move-sw-to-app',
  apply: 'build',
  closeBundle() {
    const dist = resolve(__dirname, 'dist')
    mkdirSync(resolve(dist, 'app'), {recursive: true})
    renameSync(resolve(dist, 'sw.js'), resolve(dist, 'app/sw.js'))
  },
})

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    tsconfigPaths(),
    react(),
    VitePWA({
      strategies: 'injectManifest',
      srcDir: 'src/pwa',
      filename: 'sw.ts',
      injectRegister: false,
      manifestFilename: 'app/manifest.webmanifest',
      injectManifest: {
        // Die index.html wird bewusst nicht vorgeladen, sie laeuft ueber NetworkFirst.
        globIgnores: ['**/index.html'],
      },
      manifest: {
        name: 'Ready2Race',
        short_name: 'R2R',
        start_url: '/app',
        scope: '/app/',
        display: 'standalone',
        background_color: '#ffffff',
        theme_color: '#4d9f85',
        icons: [
          {src: '/app/icon-192.png', sizes: '192x192', type: 'image/png'},
          {src: '/app/icon-512.png', sizes: '512x512', type: 'image/png'},
          {src: '/app/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable'},
        ],
      },
    }),
    moveServiceWorkerToApp(),
  ],
  server: {
    host: '0.0.0.0',
    port: 5123,
  },
  test: {
    include: ['src/**/*.test.ts'],
  },
})
```

`moveServiceWorkerToApp()` steht bewusst **nach** `VitePWA(...)`, damit sein `closeBundle` später läuft. Schlägt es mit `ENOENT` auf `dist/sw.js` fehl, hat das Plugin die Datei anders benannt — dann den tatsächlichen Namen aus `ls dist/*.js` übernehmen, nicht den Umzug weglassen. Der Ablageort ist die tragende Eigenschaft dieses Entwurfs.

- [ ] **Step 5: Registrierung schreiben**

Datei `frontend/src/pwa/registerAppSW.ts`:

```ts
import {useCallback, useEffect, useRef, useState} from 'react'

/**
 * Registriert den Service Worker der Helfer-App - und nur sie. Aufgerufen wird der Hook
 * ausschließlich aus dem AppLayout, damit die Verwaltungsoberfläche keinen bekommt.
 *
 * Registriert wird von Hand statt über `virtual:pwa-register`: Das virtuelle Modul registriert
 * den Worker unter seinem Standardpfad, und der Pfad ist hier die tragende Eigenschaft - nur
 * '/app/sw.js' ergibt den Scope '/app/'.
 */
export const useRegisterAppSW = () => {
    const [needRefresh, setNeedRefresh] = useState(false)
    const registrationRef = useRef<ServiceWorkerRegistration | null>(null)

    useEffect(() => {
        if (!('serviceWorker' in navigator)) {
            return
        }
        let cancelled = false

        const watchInstalling = (registration: ServiceWorkerRegistration) => {
            const installing = registration.installing
            if (installing === null) {
                return
            }
            installing.addEventListener('statechange', () => {
                // Ein Controller ist nur vorhanden, wenn schon eine Fassung lief - beim
                // allerersten Besuch ist das kein Update, sondern die Erstinstallation.
                if (installing.state === 'installed' && navigator.serviceWorker.controller) {
                    setNeedRefresh(true)
                }
            })
        }

        navigator.serviceWorker
            .register('/app/sw.js', {scope: '/app/'})
            .then(registration => {
                if (cancelled) {
                    return
                }
                registrationRef.current = registration
                if (registration.waiting && navigator.serviceWorker.controller) {
                    setNeedRefresh(true)
                }
                registration.addEventListener('updatefound', () => watchInstalling(registration))
            })
            .catch(() => {
                // Kein HTTPS, privater Modus, Browser ohne Unterstuetzung: Die App laeuft
                // unveraendert weiter, nur ohne Offline. Kein Fehlerdialog.
            })

        return () => {
            cancelled = true
        }
    }, [])

    const updateApp = useCallback(() => {
        const waiting = registrationRef.current?.waiting
        if (!waiting) {
            window.location.reload()
            return
        }
        // Erst wenn der neue Worker das Ruder uebernommen hat, wird neu geladen - sonst zeigt die
        // frisch geladene Seite wieder die alte Fassung.
        navigator.serviceWorker.addEventListener(
            'controllerchange',
            () => window.location.reload(),
            {once: true},
        )
        waiting.postMessage({type: 'SKIP_WAITING'})
    }, [])

    return {needRefresh, updateApp}
}

/**
 * Notausstieg für ein einzelnes klemmendes Gerät: Registrierung und Caches weg, dann neu laden.
 *
 * Bewusst eine freie Funktion und nicht Teil des Hooks - sonst müsste jede Stelle, die nur den
 * Notausstieg braucht, den Worker mitregistrieren.
 */
export const resetAppInstallation = async (): Promise<void> => {
    if ('serviceWorker' in navigator) {
        const registrations = await navigator.serviceWorker.getRegistrations()
        await Promise.all(registrations.map(r => r.unregister()))
    }
    if ('caches' in window) {
        const keys = await caches.keys()
        await Promise.all(keys.map(k => caches.delete(k)))
    }
    window.location.reload()
}
```

Kein Eintrag in `vite-env.d.ts` nötig — es wird kein virtuelles Modul importiert, nur Browser-APIs, die TypeScript über `lib.dom` kennt.

- [ ] **Step 6: Metatags in die index.html**

`frontend/index.html` im `<head>` ergänzen:

```html
    <meta name="theme-color" content="#4d9f85" />
    <link rel="apple-touch-icon" href="/app/apple-touch-icon.png" />
    <meta name="apple-mobile-web-app-capable" content="yes" />
    <meta name="apple-mobile-web-app-status-bar-style" content="default" />
    <meta name="apple-mobile-web-app-title" content="Ready2Race" />
    <link rel="manifest" href="/app/manifest.webmanifest" />
```

- [ ] **Step 7: Im AppLayout aufrufen**

In `frontend/src/layouts/AppLayout.tsx` den Hook aufrufen; die Rückgabe bleibt in dieser Aufgabe ungenutzt, Task 8 baut die Oberfläche darauf:

```tsx
import {useRegisterAppSW} from '@pwa/registerAppSW.ts'
```

und als erste Zeile in der Komponente:

```tsx
    useRegisterAppSW()
```

- [ ] **Step 8: Build prüfen**

Run: `cd frontend && npm run build && ls -la dist/app/`
Expected: `dist/app/` enthält `sw.js`, `manifest.webmanifest` und die vier Icons.

Run: `cd frontend && grep -c "precache" dist/app/sw.js`
Expected: mindestens 1 — der Precache-Eintrag steht im gebauten Service Worker.

- [ ] **Step 9: Gesamttests**

Run: `cd frontend && npm test`
Expected: alle Tests grün

- [ ] **Step 10: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/vite.config.ts frontend/index.html frontend/src/pwa frontend/src/layouts/AppLayout.tsx frontend/public/app
git commit -m "Helfer-App wird zur installierbaren PWA"
```

---

### Task 8: Update-Hinweis und Notausstieg

**Files:**
- Create: `frontend/src/pwa/swKill.ts`
- Modify: `frontend/src/layouts/AppLayout.tsx`
- Modify: `frontend/src/pages/app/AppFunctionSelectPage.tsx`
- Modify: `frontend/src/i18n/{de,en,da}/translations.json`

**Interfaces:**
- Consumes: `useRegisterAppSW` und `resetAppInstallation` aus Task 7, `confirmAction` aus `@contexts/confirmation/ConfirmationContext.ts`
- Produces: nichts, worauf spätere Aufgaben bauen

- [ ] **Step 1: Update-Snackbar im AppLayout**

In `frontend/src/layouts/AppLayout.tsx` den Aufruf aus Task 7 erweitern und über `notistack` melden:

```tsx
import {useEffect} from 'react'
import {useSnackbar} from 'notistack'
import {Button} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useRegisterAppSW} from '@pwa/registerAppSW.ts'
```

In der Komponente:

```tsx
    const {t} = useTranslation()
    const {enqueueSnackbar} = useSnackbar()
    const {needRefresh, updateApp} = useRegisterAppSW()

    useEffect(() => {
        if (needRefresh) {
            enqueueSnackbar(t('app.update.available'), {
                variant: 'info',
                persist: true,
                action: (
                    <Button color="inherit" size="small" onClick={updateApp}>
                        {t('app.update.reload')}
                    </Button>
                ),
            })
        }
    }, [needRefresh, enqueueSnackbar, t, updateApp])
```

- [ ] **Step 2: Notausstieg in der Funktionsauswahl**

In `frontend/src/pages/app/AppFunctionSelectPage.tsx` unter den vorhandenen Knöpfen ergänzen. `resetApp` kommt aus demselben Hook; der bestätigte Dialog nutzt den vorhandenen `ConfirmationContext`:

```tsx
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
```

```tsx
import {resetAppInstallation} from '@pwa/registerAppSW.ts'
```

```tsx
    const {confirmAction} = useConfirmation()

    const handleReset = () => {
        confirmAction(() => void resetAppInstallation(), {
            content: t('app.reset.confirm'),
            okText: t('app.reset.button'),
        })
    }
```

und im JSX:

```tsx
            <Button onClick={handleReset} variant="text" size="small" sx={{mt: 1}}>
                {t('app.reset.button')}
            </Button>
```

Die Signatur ist `confirmAction(action: () => void, options?: ConfirmationOptions)` mit `ConfirmationOptions = {title?, content?, cancelText?, okText?, cancelAction?, buttonsSX?}` — siehe `frontend/src/contexts/confirmation/ConfirmationContext.ts`.

- [ ] **Step 3: Notfall-Service-Worker hinterlegen**

Datei `frontend/src/pwa/swKill.ts`:

```ts
/// <reference lib="webworker" />

declare const self: ServiceWorkerGlobalScope

/**
 * Notfallvariante. Wird NUR eingespielt, wenn eine ausgelieferte Fassung flächendeckend klemmt:
 * In vite.config.ts `filename` auf 'swKill.ts' stellen, bauen, ausliefern. Der Worker ersetzt
 * seinen Vorgänger, raeumt alles weg und verschwindet selbst.
 *
 * Ohne Zugriff aufs Hosting ist ein neuer Build der einzige Weg, einen kaputten Service Worker
 * loszuwerden - deshalb liegt diese Datei fertig im Repo und wird nicht erst im Ernstfall
 * geschrieben.
 */

self.addEventListener('install', () => {
    void self.skipWaiting()
})

self.addEventListener('activate', event => {
    event.waitUntil(
        (async () => {
            const keys = await caches.keys()
            await Promise.all(keys.map(key => caches.delete(key)))
            await self.registration.unregister()
            const clients = await self.clients.matchAll({type: 'window'})
            clients.forEach(client => {
                if ('navigate' in client) {
                    void (client as WindowClient).navigate(client.url)
                }
            })
        })(),
    )
})

export {}
```

- [ ] **Step 4: Übersetzungen ergänzen**

Unter `app` in allen drei Dateien:

- de: `"update": {"available": "Neue Version verfügbar", "reload": "Jetzt laden"}, "reset": {"button": "App zurücksetzen", "confirm": "Setzt die App auf dem Gerät zurück und lädt sie neu. Angemeldet bleibst du."}`
- en: `"update": {"available": "New version available", "reload": "Reload now"}, "reset": {"button": "Reset app", "confirm": "Resets the app on this device and reloads it. You stay signed in."}`
- da: `"update": {"available": "Ny version tilgængelig", "reload": "Indlæs nu"}, "reset": {"button": "Nulstil app", "confirm": "Nulstiller appen på denne enhed og genindlæser den. Du forbliver logget ind."}`

- [ ] **Step 5: Build und Gesamttests**

Run: `cd frontend && npm test && npm run build`
Expected: alle Tests grün, Build ohne Fehler

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pwa/swKill.ts frontend/src/layouts/AppLayout.tsx frontend/src/pages/app/AppFunctionSelectPage.tsx frontend/src/i18n
git commit -m "Update-Hinweis und Notausstieg für die Helfer-App"
```

---

## Nach der letzten Aufgabe: Handprüfliste

Die Punkte aus Abschnitt 11 der Spec sind **nicht** durch Tests abgedeckt und gehören vor den 14.08. an ein echtes Gerät. Besonders die Nummern 2, 3, 5 und 8 — Kaltstart im Flugmodus mit und ohne gültige Sitzung, das Wiederkommen nach einer Stunde, und der Nachweis, dass die Verwaltungsoberfläche keinen Service Worker registriert bekommt.

Die Prüfung braucht HTTPS: Ein Service Worker läuft auf `http://localhost` (dort erlaubt), aber der Test auf dem Telefon im WLAN braucht die ausgelieferte Fassung. Wer vorher lokal prüfen will, nutzt `npm run build && npm run preview` und ruft die Vorschau über `localhost` auf, nicht über die IP.
