package ru.builder.bean.firebase;

import ru.builder.model.document.Document;

import java.util.List;

public interface Firebase {

    /**
     * Получает информацию о заказах.
     *
     * @return список документов заказов
     */
    List<Document> getOrderInfo();

    /**
     * Получает название тяжелой техники по ее идентификатору.
     *
     * @param typeId идентификатор типа тяжелой техники
     * @return название тяжелой техники
     */
    String getHeavyMachineryNameById(String typeId);

    /**
     * Получает список всех названий тяжелой техники.
     *
     * @return список документов с информацией о названиях тяжелой техники
     */
    List<Document> getAllHeavyMachineryName();

}
