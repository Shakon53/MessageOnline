package com.messageonline.android.network

/**
 * ╔══════════════════════════════════════════════════╗
 * ║         КОНФИГУРАЦИЯ СЕРВЕРА                     ║
 * ║  Измени HOST на адрес своего облачного сервера   ║
 * ╚══════════════════════════════════════════════════╝
 *
 * Варианты:
 *
 * 1. Fly.io (бесплатно, постоянно):
 *    HOST = "messageonline-chat.fly.dev"
 *    PORT = 8888
 *
 * 2. Railway (бесплатно, $5 кредит/месяц):
 *    HOST = "xxxx.railway.app"   ← из Railway Dashboard → Settings → Networking
 *    PORT = XXXXX                ← публичный TCP порт из Railway
 *
 * 3. ngrok (для быстрого теста):
 *    HOST = "0.tcp.eu.ngrok.io"  ← из вывода команды ngrok tcp 8888
 *    PORT = 12345                ← случайный порт от ngrok
 *
 * 4. Локальная сеть (эмулятор):
 *    HOST = "10.0.2.2"
 *    PORT = 8888
 *
 * 5. Локальная сеть (реальный телефон):
 *    HOST = "192.168.X.X"        ← IP твоего компьютера
 *    PORT = 8888
 */
object ServerConfig {

    // ★ ИЗМЕНИ ЭТИ ЗНАЧЕНИЯ НА СВОЙ СЕРВЕР ★
    const val HOST: String = "messageonline-chat.fly.dev"
    const val PORT: Int    = 8888

    // Таймаут подключения (мс)
    const val CONNECT_TIMEOUT_MS: Int = 8000
}
