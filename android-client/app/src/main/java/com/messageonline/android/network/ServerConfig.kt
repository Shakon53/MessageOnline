package com.messageonline.android.network

/**
 * ╔══════════════════════════════════════════════════╗
 * ║         КОНФИГУРАЦИЯ СЕРВЕРА (WebSocket)         ║
 * ╚══════════════════════════════════════════════════╝
 *
 * После деплоя на Railway скопируй URL из:
 * Railway Dashboard → твой сервис → Settings → Networking → Generate Domain
 * Формат: wss://ИМЯ-СЕРВИСА.up.railway.app
 *
 * Для локального теста:
 * WS_URL = "ws://192.168.X.X:8080"
 */
object ServerConfig {
    const val WS_URL = "wss://messageonline-production.up.railway.app"
}
