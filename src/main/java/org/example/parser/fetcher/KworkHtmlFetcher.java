package org.example.parser.fetcher;

import org.example.parser.KworkParsingException;

/**
 * Абстракция над получением HTML страницы. Раньше sendRequest был
 * приватным статическим методом внутри парсера — это гвоздями
 * прибивало парсер к сети и делало юнит-тесты невозможными.
 * Через интерфейс в тестах подставляется fake-реализация,
 * возвращающая заранее записанный HTML.
 */
public interface KworkHtmlFetcher {
    String fetchPage(int pageNumber) throws KworkParsingException;
}
