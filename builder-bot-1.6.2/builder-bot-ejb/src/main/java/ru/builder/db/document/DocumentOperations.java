package ru.builder.db.document;


import ru.builder.model.document.Document;

import java.util.List;

public interface DocumentOperations {

    void addDocument(Document document);

    Document getDocumentById(String id);

    List<Document> getFirstTwoNotProcessedDocuments();

    void markDocumentAsProcessed(String id);

    boolean checkDocumentId(String documentId);

    void deleteOlderDocuments(String documentIds);

}