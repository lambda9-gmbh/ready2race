# Implementation Plan: Admin-Customizable Theme System

## Overview

Implement a theme customization system where admins can configure:
- Primary color, text color, background color
- Custom font file upload (woff/woff2)
- Theme stored in `/static/theme.json`
- Fonts stored in `/static/fonts/`
- Frontend loads directly from static path (no API fetch)
- Admin UI with reset to default functionality

## Theme JSON Structure

```json
{
  "version": "1.0",
  "primaryColor": "#4d9f85",
  "textColor": "#1d1d1d",
  "backgroundColor": "#ffffff",
  "customFont": {
    "enabled": false,
    "filename": null
  }
}
```

## Implementation Steps

### 1. Backend Infrastructure

#### 1.1 Create Static Directory
- Create `/home/thore/Projekte/ready2race/backend/static/` directory
- Create `/home/thore/Projekte/ready2race/backend/static/fonts/` subdirectory
- Create default `theme.json` with default values

#### 1.2 Add Environment Configuration

**File: `backend/template.env`**
- Add: `STATIC_FILES_PATH=/path/to/backend/static`

**File: `backend/src/main/kotlin/de/lambda9/ready2race/backend/config/Config.kt`**
- Add `staticFilesPath: String` field to `Config` data class (line 29)
- Add parsing in `parseConfig()` (after admin section ~line 186):
  ```kotlin
  val staticFilesPath = !required("STATIC_FILES_PATH")
  ```
- Add to Config constructor (line 235)

#### 1.3 Configure Static File Serving

**File: `backend/src/main/kotlin/de/lambda9/ready2race/backend/plugins/StaticFiles.kt` (NEW)**
```kotlin
package de.lambda9.ready2race.backend.plugins

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import java.io.File

fun Application.configureStaticFiles(staticFilesPath: String) {
    routing {
        staticFiles("/static", File(staticFilesPath))
    }
}
```

**File: `backend/src/main/kotlin/de/lambda9/ready2race/backend/Application.kt`**
- Import and call `configureStaticFiles(config.staticFilesPath)` in `module()` function

### 2. Backend Domain (Global Configurations)

#### 2.1 Entity Classes

**File: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/globalConfigurations/entity/ThemeConfigDto.kt` (NEW)**
- Define `ThemeConfigDto` with version, colors, customFont
- Define `CustomFontDto` with enabled, filename

**File: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/globalConfigurations/entity/UpdateThemeRequest.kt` (NEW)**
- Define request DTO with color validation (hex format: `^#[0-9a-fA-F]{6}$`)
- Implement `Validatable` interface

**File: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/globalConfigurations/entity/ThemeError.kt` (NEW)**
- Define errors: `ThemeFileNotFound`, `ThemeFileMalformed`, `FontFileInvalid`, `FontFileTooLarge`

#### 2.2 Repository

**File: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/globalConfigurations/control/ThemeRepo.kt` (NEW)**
- `getTheme()`: Read theme.json, return default if not found
- `updateTheme()`: Write theme.json with pretty printing
- `saveFontFile()`: Validate (5MB limit, woff/woff2 only) and save to fonts/
- `deleteFontFile()`: Remove font file
- `resetToDefault()`: Reset theme.json and delete all fonts

#### 2.3 Service

**File: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/globalConfigurations/boundary/ThemeService.kt` (NEW)**
- `getTheme()`: Return current theme config
- `updateTheme()`: Handle font upload, delete old font if replaced, update theme.json
- `resetTheme()`: Reset to defaults

#### 2.4 Routes

**File: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/globalConfigurations/boundary/globalConfigurations.kt`**
- Add `env: JEnv` parameter to function signature
- Add theme routes under `/globalConfigurations/theme`:
  - `GET`: Get current theme (no auth required - public)
  - `PUT`: Update theme (multipart with request JSON + optional fontFile)
  - `DELETE`: Reset to default
- Use `authenticate(Privilege.UpdateAdministrationConfigGlobal)` for PUT/DELETE

**File: `backend/src/main/kotlin/de/lambda9/ready2race/backend/plugins/Routing.kt`**
- Import `JEnv` and pass `env` from Application.module
- Update `globalConfigurations()` call to `globalConfigurations(env)`

### 3. OpenAPI Specification

**File: `backend/src/main/resources/openapi/documentation.yaml`**

Add paths:
```yaml
/globalConfigurations/theme:
  get:
    operationId: getThemeConfig
  put:
    operationId: updateThemeConfig
    requestBody:
      multipart/form-data:
        schema:
          properties:
            request: string (JSON)
            fontFile: binary (optional)
  delete:
    operationId: resetThemeConfig
```

Add schemas:
- `ThemeConfigDto`
- `CustomFontDto`
- `UpdateThemeRequest`

### 4. Frontend Theme Provider

#### 4.1 Context

**File: `frontend/src/contexts/theme/ThemeContext.ts` (NEW)**
- Define `ThemeContextValue` interface
- Export `useThemeConfig()` hook

**File: `frontend/src/contexts/theme/ThemeProvider.tsx` (NEW)**
- Fetch theme from `/static/theme.json` on mount
- Fallback to hardcoded defaults if fetch fails
- Dynamically inject @font-face for custom fonts
- Provide `reloadTheme()` method

#### 4.2 Integration

**File: `frontend/src/main.tsx`**
- Wrap with `<ThemeProvider>`

**File: `frontend/src/App.tsx`**
- Use `useThemeConfig()` hook
- Pass `themeConfig` to `muiTheme(locale, themeConfig)`

**File: `frontend/src/theme.ts`**
- Add `themeConfig: ThemeConfigDto` parameter to `muiTheme()`
- Update palette colors from themeConfig
- Set fontFamily conditionally based on customFont.enabled

### 5. Frontend Admin UI

#### 5.1 Theme Form Component

**File: `frontend/src/components/administration/ThemeConfigForm.tsx` (NEW)**
- Form with color pickers for primary/text/background colors
- File input for font upload (.woff/.woff2)
- Submit button (calls `updateThemeConfig` API)
- Reset button (calls `resetThemeConfig` API)
- Use `updateAdministrationConfigGlobal` privilege check
- Show current font filename if exists
- Use confirmation dialogs for both actions
- Call `reloadTheme()` after successful update

#### 5.2 Administration Page

**File: `frontend/src/pages/AdministrationPage.tsx`**
- Add Material-UI Tabs component
- Tab 0: SMTP Configuration (existing)
- Tab 1: Theme Customization (new `<ThemeConfigForm />`)

#### 5.3 Translations

**Files: `frontend/src/i18n/de/translations.json` and `frontend/src/i18n/en/translations.json`**

Add under `administration`:
```json
"tabs": {
  "smtp": "SMTP Configuration",
  "theme": "Theme Customization"
},
"theme": {
  "title": "Theme Customization",
  "info": "...",
  "primaryColor": "Primary Color",
  "textColor": "Text Color",
  "backgroundColor": "Background Color",
  "customFont": "Custom Font",
  "fontHint": "Upload .woff or .woff2 (max 5MB)",
  "submit": "Save Theme",
  "reset": "Reset to Default",
  "success": {...},
  "errors": {...}
}
```

### 6. Build & Validation

#### 6.1 Backend Compilation
```bash
cd backend
./mvnw clean compile
```

#### 6.2 Generate TypeScript API Client
```bash
cd frontend
npm run generate
```

#### 6.3 Frontend Build
```bash
cd frontend
npm run build
npm run lint
```

### 7. Testing Checklist

1. ✅ Create static directory and default theme.json
2. ✅ Update .env with STATIC_FILES_PATH
3. ✅ Verify `/static/theme.json` accessible via browser
4. ✅ Login as admin → Administration page → Theme tab
5. ✅ Change colors → Submit → Verify theme.json updated
6. ✅ Upload font → Verify appears in /static/fonts/
7. ✅ Verify UI reflects new theme (may need refresh)
8. ✅ Click Reset → Verify theme.json and fonts reset
9. ✅ Test error cases: invalid file type, file too large, non-admin access

## Critical Files

**Backend:**
- `backend/src/main/kotlin/de/lambda9/ready2race/backend/config/Config.kt`
- `backend/src/main/kotlin/de/lambda9/ready2race/backend/plugins/StaticFiles.kt` (NEW)
- `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/globalConfigurations/control/ThemeRepo.kt` (NEW)
- `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/globalConfigurations/boundary/ThemeService.kt` (NEW)
- `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/globalConfigurations/boundary/globalConfigurations.kt`
- `backend/src/main/resources/openapi/documentation.yaml`

**Frontend:**
- `frontend/src/contexts/theme/ThemeProvider.tsx` (NEW)
- `frontend/src/theme.ts`
- `frontend/src/components/administration/ThemeConfigForm.tsx` (NEW)
- `frontend/src/pages/AdministrationPage.tsx`
- `frontend/src/i18n/de/translations.json`
- `frontend/src/i18n/en/translations.json`

## Design Decisions

1. **Domain**: Extended `globalConfigurations` domain (admin-level settings)
2. **Storage**: File-based (theme.json) for direct static serving
3. **Authorization**: Uses existing `UpdateAdministrationConfigGlobal` privilege
4. **Fallback**: Frontend gracefully falls back to hardcoded defaults
5. **Font Handling**: Single font file, 5MB limit, woff/woff2 only
6. **Reset**: Deletes theme.json and all font files, returns to defaults