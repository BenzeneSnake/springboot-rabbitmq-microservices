# mq-practice

透過訂單處理流程，練習 RabbitMQ 核心概念與最佳實踐的 Spring Boot 微服務專案。

## 練習目標

| 目標 | 說明 |
|------|------|
| Producer / Consumer 解耦 | 服務間透過message queue通訊，互不依賴 |
| 非同步流程 | 訂單、付款、庫存非同步處理 |
| 消息重送與死信 | 實現 DLX (Dead Letter Exchange) |
| 冪等性控制 | 確保重複訊息不造成重複處理 |
| 水平擴展 | 支援多個 Consumer 競爭消費 |

## 架構概覽

```
[客戶端]
    │ POST /api/orders
    ▼
[Order Service]  ──→  order.exchange  ──→  order.queue
                                                │
                                     [Payment Service]
                                                │ payment.exchange
                                                ▼
                                         inventory.queue
                                                │
                                   [Inventory Service × N]
                                      (多實例競爭消費)
```

### 服務角色

| 服務 | 角色 | Port |
|------|------|------|
| `order-service` | Producer：建立訂單，發布 `OrderCreatedEvent` | 8081 |
| `payment-service` | Consumer + Producer：處理付款，發布 `PaymentCompletedEvent` | 8082 |
| `inventory-service` | Consumer：扣減庫存，支援多實例水平擴展 | 8083 |

### RabbitMQ 設計

```
Exchanges & Queues:

order.exchange (topic)
    └─ routing: order.created  →  order.queue
                                       │ 失敗
                              order.dlx.exchange  →  order.dlq

payment.exchange (topic)
    └─ routing: payment.completed  →  inventory.queue
                                           │ 失敗
                                  inventory.dlx.exchange  →  inventory.dlq
```

## 專案結構

```
mq-practice/
├── docker-compose.yml
├── order-service/
│   └── src/main/java/com/example/order/
│       ├── controller/      # OrderController
│       ├── service/         # OrderService
│       ├── entity/          # Order, OrderItem
│       ├── mq/              # OrderEventPublisher, RabbitConfig
│       └── dto/event/       # OrderCreatedEvent
├── payment-service/
│   └── src/main/java/com/example/payment/
│       ├── application/service/   # PaymentService
│       ├── entity/                # Payment, IdempotencyRecord
│       ├── mq/listener/           # OrderEventListener
│       ├── mq/publisher/          # PaymentEventPublisher
│       └── dto/event/             # OrderCreatedEvent, PaymentCompletedEvent
└── inventory-service/
    └── src/main/java/com/example/inventory/
        ├── service/         # InventoryService（樂觀鎖扣庫存）
        ├── entity/          # Inventory（@Version）, IdempotencyRecord
        ├── mq/listener/     # PaymentEventListener（concurrency: 2-5）
        └── dto/event/       # PaymentCompletedEvent
```

## 快速開始

**1. 啟動 RabbitMQ**
```bash
docker-compose up -d rabbitmq
```

**2. 啟動各服務**
```bash
# 各服務獨立啟動
cd order-service && mvn spring-boot:run
cd payment-service && mvn spring-boot:run
cd inventory-service && mvn spring-boot:run

# 或啟動多個 Inventory 實例進行水平擴展測試
docker-compose up --scale inventory-service=3
```

**3. 建立訂單**
```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "C001",
    "items": [
      {"productId": "P001", "quantity": 2, "price": 100.00}
    ]
  }'
```

**4. 查看 RabbitMQ Management UI**
```
http://localhost:15672
帳號: guest / 密碼: guest
```

## 核心實現

### 手動 ACK + DLX 重試

```java
@RabbitListener(queues = "order.queue")
public void handleOrderCreated(OrderCreatedEvent event, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
    try {
        if (retryCount >= 3) {
            channel.basicNack(tag, false, false);  // 超過重試次數 → DLQ
            return;
        }
        paymentService.processPayment(event);
        channel.basicAck(tag, false);
    } catch (RecoverableException e) {
        channel.basicNack(tag, false, true);   // 可重試，重新入隊
    } catch (Exception e) {
        channel.basicNack(tag, false, false);  // 不可重試 → DLQ
    }
}
```

### 冪等性控制

```java
@Transactional
public void processPayment(OrderCreatedEvent event) {
    // 已處理過則直接跳過
    if (idempotencyRepository.findByEventId(event.getEventId())
            .filter(r -> "PROCESSED".equals(r.getStatus())).isPresent()) {
        return;
    }
    // 插入 PROCESSING 紀錄，防止並發重複處理
    idempotencyRepository.save(new IdempotencyRecord(event.getEventId(), "PROCESSING"));
    // 業務邏輯...
    record.setStatus("PROCESSED");
}
```

### 樂觀鎖防超賣

```java
@Entity
public class Inventory {
    private String productId;
    private Integer quantity;

    @Version  // 並發扣庫存時，version 不符會拋出 OptimisticLockException
    private Long version;
}
```

### 多實例競爭消費

```yaml
# inventory-service application.yml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 10       # 每個 consumer 一次最多取 10 筆
        concurrency: 2     # 最小 2 個 worker thread
        max-concurrency: 5 # 最大 5 個 worker thread
```

## 事件流程

```
正常流程:
客戶端 → Order Service (建立訂單) → order.queue
       → Payment Service (處理付款) → inventory.queue
       → Inventory Service (扣減庫存) ✅

失敗流程:
Payment 失敗 → NACK (requeue) → 最多重試 3 次 → order.dlq → 人工介入
庫存不足    → NACK (不 requeue) → inventory.dlq → 人工介入

冪等性:
重複訊息 (eventId: E001) → 查 idempotency_record → 已 PROCESSED → 直接 ACK，跳過
```

## 進度

- [x] Order Service（建立訂單 + MQ Publisher）
- [x] Payment Service（MQ Consumer + 冪等性 + Publisher）
- [x] Inventory Service Phase 1（基礎結構）
- [x] Inventory Service Phase 2（MQ Consumer + DLX + 重試分流）
- [x] Inventory Service Phase 3（冪等性機制）
- [x] Inventory Service Phase 4（多實例消費 + 樂觀鎖並發控制）
- [ ] 加入監控和結構化日誌
- [ ] 壓力測試與調優

## 使用技術

- **Language**: Java 17
- **Framework**: Spring Boot 3
- **Message Broker**: RabbitMQ
- **Database**: H2 (in-memory)
- **Build**: Maven
- **Container**: Docker / Docker Compose
