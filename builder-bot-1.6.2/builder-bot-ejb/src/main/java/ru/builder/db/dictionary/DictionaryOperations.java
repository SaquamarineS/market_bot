package ru.builder.db.dictionary;

public interface DictionaryOperations {

    void createTable();

    String getErrorDescriptionByReason(String reason);

}
