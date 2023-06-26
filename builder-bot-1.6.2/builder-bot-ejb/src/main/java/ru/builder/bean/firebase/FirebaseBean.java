package ru.builder.bean.firebase;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;
import ru.builder.bean.admin.Admin;
import ru.builder.model.document.Document;
import ru.builder.model.document.DocumentResponse;

import javax.ejb.Stateless;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Default
@Stateless
public class FirebaseBean implements Firebase {

    // Логгер для записи сообщений
    private final Logger logger = Logger.getLogger(this.getClass().getName());

    // Форматтер для форматирования даты
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").toPattern());

    // OkHttpClient для выполнения HTTP-запросов
    private final OkHttpClient client = new OkHttpClient();

    // Внедренный экземпляр Admin
    @Inject
    private Admin admin;

    // Получение информации о заказах из Firebase Firestore
    @Override
    public List<Document> getOrderInfo() {
        // Рассчитываем фильтрующую дату, вычитая 30 минут от текущего времени в UTC
        String filterDateMinus30Min = LocalDateTime
                .now(ZoneOffset.UTC)
                .minusMinutes(30)
                .toString()
                .concat("Z");

        // Формируем структурированный запрос для получения информации о заказах
        String structuredQuery = "{structuredQuery: {" +
                "select: {" +
                "fields: [" +
                "{ fieldPath: \"comment\" }," +
                "{ fieldPath: \"address\" }," +
                "{ fieldPath: \"createdAt\" }," +
                "{ fieldPath: \"userPhone\" }," +
                "{ fieldPath: \"userName\" }," +
                "{ fieldPath: \"paymentMethod\" }," +
                "{ fieldPath: \"typeID\" }," +
                "{ fieldPath: \"ownerId\" }," +
                "{ fieldPath: \"location\" }," +
                "{ fieldPath: \"createTime\" }," +
                "{ fieldPath: \"id\" }," +
                "]," +
                "}," +
                "from: [{ collectionId: \"heavy_machinery_orders\" }]," +
                "where: {" +
                "fieldFilter: {" +
                "  field: {" +
                "    fieldPath: \"createdAt\"," +
                "  }," +
                "  op: \"GREATER_THAN_OR_EQUAL\"," +
                "  value: {" +
                "    timestampValue: \"" + filterDateMinus30Min + "\"," +
                "  }," +
                "}," +
                "}," +
                "orderBy: [{ field: { fieldPath: \"createdAt\" }, direction: \"ASCENDING\" }]," +
                "}}";

        // Выполняем запрос к Firebase Firestore и возвращаем список документов
        return invoiceFireBase(structuredQuery);
    }

    // Приватный вспомогательный метод для преобразования UTC-даты в локальное время
    private String convertDate(String utcDate) {
        logger.log(Level.INFO, "Input utcDate - {0}", utcDate);
        if (utcDate == null) {
            logger.log(Level.INFO, "Не удалось преобразовать дату, так как значение равно null...");
            return null;
        }
        Instant timestamp = Instant.parse(utcDate);
        ZonedDateTime europeDateTime = timestamp.atZone(ZoneId.of("Europe/Moscow"));
        String convertedDate = europeDateTime.format(formatter);
        logger.log(Level.INFO, "Преобразованная дата - {0}", convertedDate);
        return convertedDate;
    }

    // Получение названия тяжелой техники по ее идентификатору из Firebase Firestore
    @Override
    public String getHeavyMachineryNameById(String typeId) {
        // Формируем структурированный запрос для получения названия тяжелой техники по идентификатору
        String structuredQuery = "{structuredQuery: {" +
                "from: [{ collectionId: \"heavy_machinery_types\" }]," +
                "select: {" +
                "fields: [" +
                "{ fieldPath: \"name\" }," +
                "]," +
                "}," +
                "where: {" +
                "fieldFilter: {" +
                "field: { fieldPath: \"id\",}," +
                "op: \"EQUAL\"," +
                "value: { stringValue: \"" + typeId + "\",}," +
                "}," +
                "}," +
                "}" +
                "}";

        // Выполняем запрос к Firebase Firestore и возвращаем название тяжелой техники
        return invoiceFireBase(structuredQuery).get(0).getFields().getName().getStringValue();
    }

    // Получение списка всех названий тяжелой техники из Firebase Firestore
    @Override
    public List<Document> getAllHeavyMachineryName() {
        String structuredQuery = "{" +
                "  structuredQuery: {" +
                "from: [{ collectionId: \"heavy_machinery_types\" }]," +
                "select: {" +
                "fields: [" +
                "{ fieldPath: \"name\"}," +
                "{ fieldPath: \"id\"}," +
                "]," +
                "}," +
                "}" +
                "}";
        return invoiceFireBase(structuredQuery);
    }

    // Вспомогательный метод для выполнения запроса к Firebase Firestore
    private List<Document> invoiceFireBase(String query) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        Gson gson = gsonBuilder.create();

        RequestBody body = RequestBody.create(query, MediaType.parse("application/json"));
        String getFirebaseResponse = null;
        Request request = new Request.Builder()
                .url("https://firestore.googleapis.com/v1/projects/builders-bdaff/databases/(default)/documents:runQuery")
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            logger.log(Level.SEVERE, "Статус ответа POST - {0}", response.code());

            if (response.body() != null) {
                getFirebaseResponse = response.body().string();
                logger.log(Level.INFO, "getFirebaseResponse - {0}", getFirebaseResponse);
            }

            // Если произошла непредвиденная ошибка, отправляем сообщение в технический канал
            if (response.code() != 200) {
                logger.log(Level.SEVERE, "Произошла непредвиденная ошибка - {0}", getFirebaseResponse);
                admin.sendErrorMessagesToTechChannel("❗️При вызове Firebase API произошла непредвиденная ошибка! Что-то пошло не так!\n".concat("Json response - " + getFirebaseResponse));
                return null;
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, null, e);
        }

        // Парсим ответ от Firebase Firestore в список документов
        List<DocumentResponse> documents = gson.fromJson(getFirebaseResponse, new TypeToken<List<DocumentResponse>>() {
        }.getType());

        if (documents == null
                || documents.isEmpty()
                || documents.get(0).getDocument() == null) {
            logger.log(Level.SEVERE, "Документы равны null, возвращаем null...");
            return null;
        }

        logger.log(Level.INFO, "Документы - {0}", gson.toJson(documents));

        // Преобразуем дату и добавляем документы в список для возврата
        List<Document> documentList = new ArrayList<>();
        documents.forEach(elem -> {
            if (elem.getDocument().getFields().getCreatedAt() != null) {
                elem.getDocument().getFields().getCreatedAt().setTimestampValue(convertDate(elem.getDocument().getFields().getCreatedAt().getTimestampValue()));
            }
            documentList.add(elem.getDocument());
        });

        return documentList;
    }
}
