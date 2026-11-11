#!/bin/sh
# Quick Termux sync helper for rooted Android
PKG="com.fitifyworkouts.bodyweight.workoutapp"
TARGET_DIR="/sdcard/FitifyBackups"

echo "=== Fitify Root Extractor for Termux ==="
if [ "$(id -u)" -ne 0 ]; then
    echo "[*] Requesting root via su..."
    exec su -c "$0 $@"
fi

mkdir -p "$TARGET_DIR"
DB_SRC="/data/data/$PKG/databases"

if [ ! -d "$DB_SRC" ]; then
    DB_SRC="/data/user/0/$PKG/databases"
fi

if [ ! -d "$DB_SRC" ]; then
    echo "[!] Fitify database directory not found."
    exit 1
fi

echo "[*] Copying Fitify databases to $TARGET_DIR..."
cp -v "$DB_SRC"/* "$TARGET_DIR/" 2>/dev/null
chmod 666 "$TARGET_DIR"/*

echo "[+] Done! Databases backed up to $TARGET_DIR"
ls -la "$TARGET_DIR"
