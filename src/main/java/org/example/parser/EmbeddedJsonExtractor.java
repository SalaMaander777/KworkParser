package org.example.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Утилита для вытаскивания JSON-литерала вида `window.stateData = {...}`
 * из отдаваемого сервером HTML. Алгоритм балансировки скобок из исходника
 * был в целом верным — regex тут не подходит (JSON может быть произвольно
 * вложенным), так что подход сохранён, но:
 *  - вынесен в отдельный класс с одной ответственностью;
 *  - вместо null при ошибке — понятное исключение с деталями;
 *  - явно задокументирован разбор экранирования и строк.
 */
public final class EmbeddedJsonExtractor {

    private EmbeddedJsonExtractor() {
    }

    public static String extract(String html, String variableName) throws KworkParsingException {
        Matcher assignment = Pattern
                .compile(Pattern.quote(variableName) + "\\s*=\\s*")
                .matcher(html);
        if (!assignment.find()) {
            throw new KworkParsingException("Переменная '" + variableName + "' не найдена в HTML-странице");
        }

        int start = html.indexOf('{', assignment.end());
        if (start == -1) {
            throw new KworkParsingException("После '" + variableName + "=' не найден открывающий '{'");
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < html.length(); i++) {
            char c = html.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return html.substring(start, i + 1);
                }
            }
        }

        throw new KworkParsingException(
                "Не удалось сбалансировать JSON для '" + variableName + "' — объект не закрыт корректно");
    }
}
