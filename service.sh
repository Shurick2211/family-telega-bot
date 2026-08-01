#!/data/data/com.termux/files/usr/bin/sh
# Подгружаем окружение Termux (чтобы пути к java и утилитам были доступны)
export PREFIX=/data/data/com.termux/files/usr
export PATH=$PREFIX/bin:$PATH

exec 2>&1

# Prevent Android Doze/CPU sleep from freezing the JVM (and its @Scheduled
# threads) once the screen turns off. Requires the Termux:API app + package
# (`pkg install termux-api`).
if command -v termux-wake-lock >/dev/null 2>&1; then
  termux-wake-lock
fi

# Переходим в директорию вашего скрипта
cd /data/data/com.termux/files/home/storage/project

# Запускаем ваш скрипт (exec передает управление runit)
exec bash start.sh