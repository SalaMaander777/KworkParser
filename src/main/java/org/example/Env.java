package org.example;

/**
 * Чтение настроек из переменных окружения с значениями по умолчанию.
 */
public final class Env {

    private Env() {
    }

    public static String get(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    public static long getLong(String name, long defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Переменная " + name + " должна быть числом, а не '" + value + "'", e);
        }
    }

    public static int getInt(String name, int defaultValue) {
        return (int) getLong(name, defaultValue);
    }
}
