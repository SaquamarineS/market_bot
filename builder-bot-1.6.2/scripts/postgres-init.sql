-- Таблица с информацией по сообщениям с заказами в Telegram
CREATE TABLE IF NOT EXISTS order_info(
    chat_id VARCHAR,
    document_id VARCHAR,
    message_id VARCHAR,
    date VARCHAR,
    doc_date VARCHAR);

-- Таблица с заказами из БД Firebase
CREATE TABLE IF NOT EXISTS documents(
    id VARCHAR PRIMARY KEY,
    message_id VARCHAR,
    chat_id VARCHAR,
    user_name VARCHAR,
    user_phone VARCHAR,
    created_at VARCHAR,
    comment VARCHAR,
    owner_id VARCHAR,
    address VARCHAR,
    type_id VARCHAR,
    latitude VARCHAR,
    longitude VARCHAR,
    payment_method VARCHAR,
    processed BOOLEAN
    );