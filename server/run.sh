#!/bin/bash
echo "============================================="
echo "  MessageOnline Chat Server — Запуск"
echo "============================================="
echo

# Проверяем Maven
if ! command -v mvn &> /dev/null; then
    echo "[ОШИБКА] Maven не найден! Установите: sudo apt install maven"
    exit 1
fi

# Проверяем Java
if ! command -v java &> /dev/null; then
    echo "[ОШИБКА] Java не найдена! Установите JDK 17+"
    exit 1
fi

echo "[INFO] Сборка проекта..."
mvn package -q || { echo "[ОШИБКА] Сборка не удалась!"; exit 1; }

PORT=${1:-8888}
echo "[INFO] Запуск сервера на порту $PORT..."
echo "[INFO] Нажмите Ctrl+C для остановки"
echo

java -jar target/chat-server.jar $PORT
