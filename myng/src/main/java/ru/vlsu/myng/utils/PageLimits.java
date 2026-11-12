package ru.vlsu.myng.utils;

/**
 * Ограничения для параметров постраничного вывода, приходящих от клиента.
 *
 * <p>
 * Без них запрос вида {@code ?size=1000000} заставлял сервер выгрузить из БД
 * и отрисовать сразу все записи (в каталоге — вместе с обложками игр).
 * </p>
 */
public final class PageLimits {

    public static final int MAX_PAGE_SIZE = 50;

    private PageLimits() {
    }

    /** Размер страницы: не меньше 1 и не больше {@link #MAX_PAGE_SIZE}. */
    public static int size(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    /** Номер страницы: не меньше 0. */
    public static int page(int page) {
        return Math.max(page, 0);
    }
}
