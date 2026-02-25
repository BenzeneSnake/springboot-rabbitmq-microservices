-- 初始庫存資料（與 RabbitMQ 練習測試訊息對應）
INSERT INTO inventories (product_id, quantity, version, updated_at) VALUES
    ('P001', 100, 0, CURRENT_TIMESTAMP),
    ('P002', 50,  0, CURRENT_TIMESTAMP),
    ('P003', 200, 0, CURRENT_TIMESTAMP);
