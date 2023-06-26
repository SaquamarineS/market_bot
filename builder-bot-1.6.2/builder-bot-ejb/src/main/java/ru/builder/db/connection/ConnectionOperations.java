package ru.builder.db.connection;

/**
 * Интерфейс ConnectionOperations определяет операции связанные с подключением.
 */
public interface ConnectionOperations {

    /**
     * Метод createTable() используется для создания таблицы.
     */
    void createTable();

    /**
     * Метод getCountOfConnectionsByEquipmentType(String equipmentType) используется для получения количества
     * подключений по типу оборудования.
     *
     * @param equipmentType Тип оборудования, по которому требуется получить количество подключений.
     * @return Количество подключений в виде целочисленного значения.
     */
    int getCountOfConnectionsByEquipmentType(String equipmentType);

    /**
     * Метод getNameByEquipmentType(String equipmentType) используется для получения имени, связанного с типом
     * оборудования.
     *
     * @param equipmentType Тип оборудования, по которому требуется получить имя.
     * @return Имя, связанное с типом оборудования, в виде строки.
     */
    String getNameByEquipmentType(String equipmentType);
}
