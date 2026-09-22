package org.example.parser;

import org.example.db.KworkEntity;

import java.util.List;

/**
 * Результат разбора одной страницы: сами заказы + сведения о пагинации,
 * нужные вызывающему коду, чтобы понять, сколько страниц обходить дальше.
 */
public record PageResult(List<KworkEntity> kworks, int currentPage, int lastPage) {
}
