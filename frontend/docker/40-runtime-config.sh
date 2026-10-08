#!/bin/sh
set -eu

runtime_config_path=/usr/share/nginx/html/runtime-config.js
{
    printf 'window.READY2RACE_CONFIG = Object.assign('
    jq -cn \
        --arg apiBaseUrl "${API_BASE_URL:-/api}" \
        --arg themeUrl "${THEME_URL:-/static/theme.json}" \
        --arg fontsUrl "${FONTS_URL:-/static/fonts}" \
        --arg logosUrl "${LOGOS_URL:-/static/logos}" \
        --arg r2rLogoUrl "${R2R_LOGO_URL:-/r2r_logo.png}" \
        '{apiBaseUrl: $apiBaseUrl, themeUrl: $themeUrl, fontsUrl: $fontsUrl,
          logosUrl: $logosUrl, r2rLogoUrl: $r2rLogoUrl}'
    printf ', window.READY2RACE_CONFIG || {});\n'
} > "$runtime_config_path"
