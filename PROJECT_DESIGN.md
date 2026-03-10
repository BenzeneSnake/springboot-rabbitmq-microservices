# RabbitMQ 練功專案 - 完整架構設計

## 📋 目錄
- [專案目標](#專案目標)
- [模組概覽](#模組概覽)
- [RabbitMQ 設計](#rabbitmq-設計)
- [服務詳細設計](#服務詳細設計)
- [事件流程](#事件流程)
- [練功目標實現](#練功目標實現)

---

## 🎯 專案目標

本專案透過訂單處理流程，練習 RabbitMQ 的核心概念與最佳實踐：

1. **Producer / Consumer 解耦** - 服務間透過訊息佇列通訊
2. **非同步流程** - 訂單、付款、庫存非同步處理
3. **消息重送、失敗、死信** - 實現 DLX (Dead Letter Exchange)
4. **重複消費（Idempotent）** - 確保訊息冪等性
5. **多實例消費（水平擴展）** - 支援多個 consumer 競爭消費

---

## 🏗️ 模組概覽

專案: mq-practice
語言: Java + Spring Boot
模組概覽:
1. order-service: 訂單服務 (Producer)
   - 核心檔案: OrderService, OrderController, OrderRepository
   - 功能: 訂單建立、訂單事件發佈
2. payment-service: 支付服務 (Consumer + Producer)
   - 核心檔案: PaymentService, ProcessPaymentCommand, PaymentRepository
   - 功能: 處理支付、發佈支付完成事件、冪等性控制
3. inventory-service: 庫存服務 (Consumer)
   - 核心檔案: InventoryService, InventoryRepository
   - 功能: 庫存扣減、冪等性控制
4. common-event: 共用事件定義
   - 核心事件: OrderCreatedEvent, PaymentCompletedEvent
   
```
mq-practice/
├─ docker-compose.yml              # RabbitMQ + 所有服務
│
├─ order-service/                  # 訂單服務 (Producer)
│  ├─ src/main/java/com/example/order/
│  │  ├─ OrderApplication.java
│  │  ├─ controller/
│  │  │  └─ OrderController.java
│  │  ├─ service/
│  │  │  └─ OrderService.java
│  │  ├─ repository/
│  │  │  └─ OrderRepository.java
│  │  ├─ entity/
│  │  │  └─ Order.java
│  │  ├─ mq/
│  │  │  ├─ OrderEventPublisher.java
│  │  │  └─ RabbitConfig.java
│  │  └─ dto/
│  │     ├─ OrderRequest.java
│  │     ├─ OrderResponse.java
│  │     └─ event/
│  │        └─ OrderCreatedEvent.java
│  ├─ src/main/resources/
│  │  └─ application.yml
│  └─ pom.xml
│
├─ payment-service/                # 付款服務 (Consumer + Producer)
│  ├─ src/main/java/com/example/payment/
│  │  ├─ PaymentApplication.java
│  │  ├─ applicaton/
│  │  │  └─ command/
│  │  │        └─ ProcessPaymentCommand.java
│  │  │  └─ service/
│  │  │        └─ PaymentService.java
│  │  ├─ repository/
│  │  │  ├─ PaymentRepository.java
│  │  │  └─ IdempotencyRepository.java
│  │  ├─ entity/
│  │  │  ├─ Payment.java
│  │  │  └─ IdempotencyRecord.java
│  │  ├─ mq/
│  │  │  ├─ listener/
│  │  │  │  └─ OrderEventListener.java
│  │  │  ├─ publisher/
│  │  │  │  └─ PaymentEventPublisher.java
│  │  │  └─ RabbitConfig.java
│  │  └─ dto/
│  │     └─ event/
│  │        ├─ OrderCreatedEvent.java
│  │        └─ PaymentCompletedEvent.java
│  ├─ src/main/resources/
│  │  └─ application.yml
│  └─ pom.xml
│
├─ inventory-service/              # 庫存服務 (Consumer)
│  ├─ src/main/java/com/example/inventory/
│  │  ├─ InventoryApplication.java
│  │  ├─ service/
│  │  │  └─ InventoryService.java
│  │  ├─ repository/
│  │  │  ├─ InventoryRepository.java
│  │  │  └─ IdempotencyRepository.java
│  │  ├─ entity/
│  │  │  ├─ Inventory.java
│  │  │  └─ IdempotencyRecord.java
│  │  ├─ mq/
│  │  │  ├─ listener/
│  │  │  │  └─ PaymentEventListener.java
│  │  │  └─ RabbitConfig.java
│  │  └─ dto/
│  │     └─ event/
│  │        └─ PaymentCompletedEvent.java
│  ├─ src/main/resources/
│  │  └─ application.yml
│  └─ pom.xml
│
└─ common-event/                   # 共用事件定義 (Optional)
   ├─ src/main/java/com/example/common/event/
   │  ├─ OrderCreatedEvent.java
   │  └─ PaymentCompletedEvent.java
   └─ pom.xml
```

---

## 🐰 RabbitMQ 設計

### Exchange 與 Queue 架構

```
                         ┌─────────────────────┐
                         │  order.exchange     │
                         │   (topic)           │
                         └──────────┬──────────┘
                                    │ order.created
                                    ↓
                         ┌─────────────────────┐
                         │  order.queue        │
                         └──────────┬──────────┘
                                    │
                         ┌──────────↓──────────┐
                         │  payment-service    │
                         │    (Consumer)       │
                         └──────────┬──────────┘
                                    │ 處理成功
                                    ↓
                         ┌─────────────────────┐
                         │  payment.exchange   │
                         │   (topic)           │
                         └──────────┬──────────┘
                                    │ payment.completed
                                    ↓
                         ┌─────────────────────┐
                         │  inventory.queue    │
                         └──────────┬──────────┘
                                    │
                         ┌──────────↓──────────┐
                         │  inventory-service  │
                         │    (Consumers)      │
                         │   [多個實例競爭]     │
                         └─────────────────────┘
```

### Dead Letter Exchange (DLX) 設計

```
order.queue ─(失敗)→ order.dlx.exchange → order.dlq
                                            ↓
                                     (人工介入處理)

inventory.queue ─(失敗)→ inventory.dlx.exchange → inventory.dlq
                                                    ↓
                                             (人工介入處理)
```

### 詳細配置

#### 1. Order Service (Producer)

```yaml
Exchanges:
  - order.exchange (type: topic, durable: true)

Queues:
  - order.queue
    - durable: true
    - x-dead-letter-exchange: order.dlx.exchange
    - x-dead-letter-routing-key: order.failed
    - x-message-ttl: 600000 (10分鐘)

  - order.dlq (Dead Letter Queue)
    - durable: true

Bindings:
  - order.exchange → order.queue (routing key: order.created)
  - order.dlx.exchange → order.dlq (routing key: order.failed)
```

#### 2. Payment Service (Consumer + Producer)

```yaml
Exchanges:
  - payment.exchange (type: topic, durable: true)
  - payment.dlx.exchange (type: topic, durable: true)

Queues:
  - inventory.queue
    - durable: true
    - x-dead-letter-exchange: inventory.dlx.exchange
    - x-dead-letter-routing-key: inventory.failed

  - inventory.dlq
    - durable: true

Bindings:
  - payment.exchange → inventory.queue (routing key: payment.completed)
  - inventory.dlx.exchange → inventory.dlq (routing key: inventory.failed)
```

#### 3. Inventory Service (Consumer)

```yaml
Consumer Configuration:
  - prefetch-count: 10  # 每次最多取 10 個訊息
  - acknowledge-mode: manual  # 手動 ACK
  - concurrency: 2-5  # 每個實例 2-5 個 worker thread
```

---

## 📦 服務詳細設計

### 1. Order Service (訂單服務)

#### 目錄結構
```
order-service/
├─ controller/
│  └─ OrderController.java          # REST API 端點
├─ service/
│  └─ OrderService.java              # 業務邏輯
├─ repository/
│  └─ OrderRepository.java           # 資料存取
├─ entity/
│  └─ Order.java                     # 訂單實體
├─ mq/
│  ├─ OrderEventPublisher.java      # 發送訊息
│  └─ RabbitConfig.java              # RabbitMQ 配置
└─ dto/
   ├─ OrderRequest.java              # API 請求
   ├─ OrderResponse.java             # API 回應
   └─ event/
      └─ OrderCreatedEvent.java      # 訂單建立事件
```

#### 核心類別說明

**OrderController.java**
```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    // POST /api/orders - 建立訂單
    // GET /api/orders/{orderId} - 查詢訂單
}
```

**OrderService.java**
```java
@Service
public class OrderService {
    // createOrder() - 建立訂單並發送事件
    // 使用 @Transactional 確保資料庫與訊息的一致性
}
```

**OrderEventPublisher.java**
```java
@Component
public class OrderEventPublisher {
    // publishOrderCreated() - 發送 OrderCreatedEvent
    // 使用 RabbitTemplate.convertAndSend()
}
```

**RabbitConfig.java**
```java
@Configuration
public class RabbitConfig {
    // 定義 order.exchange
    // 定義 order.queue (with DLX)
    // 定義 order.dlx.exchange 和 order.dlq
}
```

**OrderCreatedEvent.java**
```java
public class OrderCreatedEvent {
    private String orderId;
    private String customerId;
    private BigDecimal amount;
    private List<OrderItem> items;
    private LocalDateTime createdAt;
    private String eventId;  // 用於冪等性檢查
}
```

---

### 2. Payment Service (付款服務)

#### 目錄結構
```
payment-service/
├─ service/
│  └─ PaymentService.java                # 付款業務邏輯
├─ repository/
│  ├─ PaymentRepository.java             # 付款紀錄
│  └─ IdempotencyRepository.java         # 冪等性紀錄
├─ entity/
│  ├─ Payment.java                       # 付款實體
│  └─ IdempotencyRecord.java             # 冪等性實體
├─ mq/
│  ├─ listener/
│  │  └─ OrderEventListener.java        # 監聽訂單事件
│  ├─ publisher/
│  │  └─ PaymentEventPublisher.java     # 發送付款事件
│  └─ RabbitConfig.java                  # RabbitMQ 配置
└─ dto/
   └─ event/
      ├─ OrderCreatedEvent.java          # 接收的事件
      └─ PaymentCompletedEvent.java      # 發送的事件
```

#### 核心類別說明

**OrderEventListener.java**
```java
@Component
public class OrderEventListener {
    // @RabbitListener(queues = "order.queue")
    // 接收 OrderCreatedEvent
    // 實現重試邏輯（最多重試 3 次）
    // 實現冪等性檢查
    // 手動 ACK/NACK
}
```

**PaymentService.java**
```java
@Service
public class PaymentService {
    // processPayment() - 處理付款
    // checkIdempotency() - 檢查是否已處理過
    // 使用 @Transactional
}
```

**IdempotencyRecord.java**
```java
@Entity
public class IdempotencyRecord {
    private String eventId;        // 唯一識別
    private String eventType;      // 事件類型
    private LocalDateTime processedAt;
    private String status;         // PROCESSED/FAILED
}
```

**RabbitConfig.java**
```java
@Configuration
public class RabbitConfig {
    // 定義 payment.exchange
    // 定義 inventory.queue (with DLX)
    // 定義 inventory.dlx.exchange 和 inventory.dlq
    // 設定 SimpleRabbitListenerContainerFactory
    //   - acknowledgeMode: MANUAL
    //   - prefetchCount: 10
}
```

---

### 3. Inventory Service (庫存服務)

#### 目錄結構
```
inventory-service/
├─ service/
│  └─ InventoryService.java              # 庫存業務邏輯
├─ repository/
│  ├─ InventoryRepository.java           # 庫存資料
│  └─ IdempotencyRepository.java         # 冪等性紀錄
├─ entity/
│  ├─ Inventory.java                     # 庫存實體
│  └─ IdempotencyRecord.java             # 冪等性實體
├─ mq/
│  ├─ listener/
│  │  └─ PaymentEventListener.java      # 監聽付款事件
│  └─ RabbitConfig.java                  # RabbitMQ 配置
└─ dto/
   └─ event/
      └─ PaymentCompletedEvent.java      # 接收的事件
```

#### 核心類別說明

**PaymentEventListener.java**
```java
@Component
public class PaymentEventListener {
    // @RabbitListener(queues = "inventory.queue", concurrency = "2-5")
    // 接收 PaymentCompletedEvent
    // 實現重試邏輯
    // 實現冪等性檢查
    // 手動 ACK/NACK
}
```

**InventoryService.java**
```java
@Service
public class InventoryService {
    // deductInventory() - 扣減庫存
    // checkIdempotency() - 檢查是否已處理過
    // 支援多實例並發
    // 使用樂觀鎖 (version) 或悲觀鎖
}
```

**RabbitConfig.java**
```java
@Configuration
public class RabbitConfig {
    // 設定 SimpleRabbitListenerContainerFactory
    //   - acknowledgeMode: MANUAL
    //   - prefetchCount: 10
    //   - concurrency: "2-5"
}
```

---

## 🔄 事件流程

### 正常流程

```
1. 客戶端 → Order Service
   POST /api/orders
   { "customerId": "C001", "items": [...] }

2. Order Service
   ✓ 建立訂單 (DB)
   ✓ 發送 OrderCreatedEvent → order.exchange (routing key: order.created)

3. RabbitMQ
   → order.queue

4. Payment Service (監聽 order.queue)
   ✓ 接收 OrderCreatedEvent
   ✓ 檢查冪等性 (eventId)
   ✓ 處理付款 (DB)
   ✓ ACK 訊息
   ✓ 發送 PaymentCompletedEvent → payment.exchange (routing key: payment.completed)

5. RabbitMQ
   → inventory.queue

6. Inventory Service (多個實例競爭消費 inventory.queue)
   ✓ 接收 PaymentCompletedEvent
   ✓ 檢查冪等性 (eventId)
   ✓ 扣減庫存 (DB with Lock)
   ✓ ACK 訊息

✅ 訂單處理完成
```

### 失敗與重試流程

```
情境 1: Payment Service 處理失敗 (可重試)

1. Payment Service 收到訊息
2. 處理失敗 (例如：第三方 API timeout)
3. NACK 訊息 (requeue = true)
4. 訊息重新進入 order.queue
5. 重試最多 3 次
6. 第 3 次失敗 → 訊息進入 order.dlq (Dead Letter Queue)
```

```
情境 2: Inventory Service 處理失敗 (不可重試)

1. Inventory Service 收到訊息
2. 處理失敗 (例如：庫存不足)
3. NACK 訊息 (requeue = false)
4. 訊息直接進入 inventory.dlq
5. 觸發告警，人工介入處理
```

### 冪等性處理流程

```
情境: 重複消費同一訊息

1. Inventory Service Instance 1 收到 PaymentCompletedEvent (eventId: E001)
2. 檢查 IdempotencyRecord
   - 若不存在: 繼續處理
   - 若存在且 status = PROCESSED: 直接 ACK，不重複處理
   - 若存在且 status = PROCESSING: 等待或拒絕

3. 開始處理前，先插入 IdempotencyRecord
   INSERT INTO idempotency_record (event_id, status, processed_at)
   VALUES ('E001', 'PROCESSING', NOW())
   ON CONFLICT (event_id) DO NOTHING;

4. 處理業務邏輯

5. 更新 IdempotencyRecord status = 'PROCESSED'

6. ACK 訊息
```

---

## 🎓 練功目標實現

### 1. Producer / Consumer 解耦 ✅

**實現方式:**
- Order Service 不需要知道 Payment Service 的存在
- Payment Service 不需要知道 Inventory Service 的存在
- 各服務只需要知道要發送什麼事件、監聽什麼事件

**驗證方式:**
```bash
# 可以隨時停止任一服務，其他服務不受影響
docker-compose stop payment-service
# Order Service 仍可正常建立訂單並發送訊息
```

---

### 2. 非同步流程 ✅

**實現方式:**
- Order Service API 立即返回 (不等待後續處理)
- Payment 和 Inventory 處理在背景進行

**驗證方式:**
```bash
# API 回應時間應該很快 (< 100ms)
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"C001","items":[...]}'

# 立即收到回應:
{
  "orderId": "O001",
  "status": "PENDING",
  "message": "Order created and processing..."
}
```

---

### 3. 消息重送、失敗、死信 ✅

**實現方式:**

**重試機制:**
```java
@RabbitListener(queues = "order.queue")
public void handleOrderCreated(OrderCreatedEvent event, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
    try {
        // 取得重試次數
        Integer retryCount = getRetryCount(event);

        if (retryCount >= 3) {
            // 超過重試次數，拒絕並進入 DLQ
            channel.basicNack(tag, false, false);
            return;
        }

        // 處理業務邏輯
        paymentService.processPayment(event);

        // 成功，確認訊息
        channel.basicAck(tag, false);

    } catch (RecoverableException e) {
        // 可重試的錯誤，重新入隊
        channel.basicNack(tag, false, true);
    } catch (Exception e) {
        // 不可重試的錯誤，進入 DLQ
        channel.basicNack(tag, false, false);
    }
}
```

**DLX 配置:**
```java
@Bean
Queue orderQueue() {
    return QueueBuilder.durable("order.queue")
        .withArgument("x-dead-letter-exchange", "order.dlx.exchange")
        .withArgument("x-dead-letter-routing-key", "order.failed")
        .withArgument("x-message-ttl", 600000)  // 10 分鐘 TTL
        .build();
}
```

**驗證方式:**
```bash
# 1. 模擬處理失敗
# 在 Payment Service 加入錯誤注入
# 觀察訊息重試和進入 DLQ

# 2. 查看 RabbitMQ Management UI
http://localhost:15672
# 檢查 order.dlq 中的訊息
```

---

### 4. 重複消費（Idempotent）✅

**實現方式:**

**IdempotencyRecord 實體:**
```java
@Entity
@Table(name = "idempotency_record",
       uniqueConstraints = @UniqueConstraint(columnNames = "event_id"))
public class IdempotencyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String status;  // PROCESSED, FAILED

    private LocalDateTime processedAt;
}
```

**冪等性檢查:**
```java
@Transactional
public void processPayment(OrderCreatedEvent event) {
    // 1. 檢查是否已處理
    Optional<IdempotencyRecord> existing =
        idempotencyRepository.findByEventId(event.getEventId());

    if (existing.isPresent() && "PROCESSED".equals(existing.get().getStatus())) {
        log.info("Event {} already processed, skipping", event.getEventId());
        return;  // 已處理過，直接返回
    }

    // 2. 建立處理中的紀錄（防止並發重複處理）
    IdempotencyRecord record = new IdempotencyRecord();
    record.setEventId(event.getEventId());
    record.setEventType("ORDER_CREATED");
    record.setStatus("PROCESSING");
    idempotencyRepository.save(record);

    // 3. 處理業務邏輯
    Payment payment = new Payment();
    // ... 處理付款
    paymentRepository.save(payment);

    // 4. 更新為已處理
    record.setStatus("PROCESSED");
    record.setProcessedAt(LocalDateTime.now());
    idempotencyRepository.save(record);
}
```

**驗證方式:**
```bash
# 1. 手動重送同一訊息
# 在 RabbitMQ Management UI 中，從 queue 複製訊息重新發送

# 2. 確認 DB 中只有一筆處理紀錄
SELECT * FROM payment WHERE order_id = 'O001';
# 應該只有一筆

# 3. 確認 idempotency_record
SELECT * FROM idempotency_record WHERE event_id = 'E001';
# status 應為 PROCESSED
```

---

### 5. 多實例消費（水平擴展）✅

**實現方式:**

**Consumer 配置:**
```yaml
# application.yml (inventory-service)
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 10              # 每次最多取 10 個訊息
        concurrency: 2            # 最小 2 個 worker
        max-concurrency: 5        # 最大 5 個 worker
```

```java
@RabbitListener(queues = "inventory.queue", concurrency = "2-5")
public void handlePaymentCompleted(PaymentCompletedEvent event,
                                    Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
    log.info("Instance: {}, Thread: {}, Processing: {}",
             instanceId, Thread.currentThread().getName(), event.getOrderId());
    // ... 處理邏輯
}
```

**樂觀鎖防止並發問題:**
```java
@Entity
public class Inventory {
    @Id
    private String productId;

    private Integer quantity;

    @Version  // 樂觀鎖
    private Long version;
}

// Service
@Transactional
public void deductInventory(String productId, int quantity) {
    Inventory inventory = inventoryRepository.findById(productId)
        .orElseThrow();

    if (inventory.getQuantity() < quantity) {
        throw new InsufficientInventoryException();
    }

    inventory.setQuantity(inventory.getQuantity() - quantity);
    inventoryRepository.save(inventory);  // 若 version 不符會拋出異常
}
```

**驗證方式:**
```bash
# 1. 啟動多個 Inventory Service 實例
docker-compose up --scale inventory-service=3

# 2. 發送大量訊息
for i in {1..100}; do
  curl -X POST http://localhost:8081/api/orders -d '{...}'
done

# 3. 觀察 logs，確認多個實例都有處理訊息
docker-compose logs -f inventory-service

# 輸出應類似:
# inventory-service-1 | Processing order O001
# inventory-service-2 | Processing order O002
# inventory-service-3 | Processing order O003
# inventory-service-1 | Processing order O004
# ...

# 4. 確認庫存正確（無重複扣減）
SELECT * FROM inventory WHERE product_id = 'P001';
```

---

## 🚀 快速開始

### 1. 啟動 RabbitMQ
```bash
cd mq-practice
docker-compose up -d rabbitmq
```

### 2. 啟動服務
```bash
# Order Service
cd order-service && mvn spring-boot:run

# Payment Service
cd payment-service && mvn spring-boot:run

# Inventory Service (可啟動多個)
cd inventory-service && mvn spring-boot:run
# 或使用 docker-compose
docker-compose up --scale inventory-service=3
```

### 3. 測試流程
```bash
# 建立訂單
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "C001",
    "items": [
      {"productId": "P001", "quantity": 2, "price": 100.00}
    ]
  }'

# 查看訂單狀態
curl http://localhost:8081/api/orders/{orderId}

# 查看 RabbitMQ Management
open http://localhost:15672
# username: guest, password: guest
```

---

## 📊 監控與除錯

### RabbitMQ Management UI
- URL: http://localhost:15672
- 查看 Exchanges, Queues, Connections, Channels
- 查看訊息流量、消費速率
- 手動發送/接收訊息進行測試

### 日誌重點
```bash
# Order Service - 確認訊息發送
[OrderEventPublisher] Published OrderCreatedEvent: orderId=O001

# Payment Service - 確認訊息接收與處理
[OrderEventListener] Received OrderCreatedEvent: orderId=O001
[PaymentService] Payment processed: orderId=O001, paymentId=P001
[PaymentEventPublisher] Published PaymentCompletedEvent: orderId=O001

# Inventory Service - 確認多實例處理
[PaymentEventListener] Instance=inv-1, Thread=pool-1, orderId=O001
[InventoryService] Inventory deducted: productId=P001, remaining=98
```

### Dead Letter Queue 監控
```bash
# 定期檢查 DLQ 是否有訊息
# 若有訊息，需人工介入處理

# 可以設定告警
# 當 order.dlq 或 inventory.dlq 有訊息時發送通知
```

---

### 學習情境：現有的 Order 系統要添加 MQ 與 Payment 溝通

#### 第一步：先有一個完整可運行的 Order Service（不含 MQ）

  現有系統：
  ├── OrderController    → 接收 API 請求
  ├── OrderService       → 處理業務邏輯（建立訂單、存入 DB）
  ├── OrderRepository    → 資料存取
  ├── Order / OrderItem  → 實體
  └── DTOs               → 請求/回應物件

  這時候 OrderService.createOrder() 的流程是：
  API 請求 → 驗證 → 建立訂單 → 存入 DB → 回應

####  第二步：識別整合點 - 在哪裡需要發送消息？

  問自己：「訂單建立成功後，需要通知誰？」

  答案：Payment Service 需要知道有新訂單，才能進行付款處理。

####  第三步：添加 MQ 組件（最小化改動）

  需要新增：
  ├── mq/
  │   ├── RabbitConfig.java        → 定義 Exchange、Queue
  │   └── OrderEventPublisher.java → 負責發送消息
  └── dto/event/
      └── OrderCreatedEvent.java   → 消息的資料結構

####  第四步：在 Service 層適當位置調用 Publisher

  // OrderService.java - 修改前
  public Order createOrder(OrderRequest request) {
      Order order = buildOrder(request);
      return orderRepository.save(order);
  }

  // OrderService.java - 修改後（只加一行）
  public Order createOrder(OrderRequest request) {
      Order order = buildOrder(request);
      Order savedOrder = orderRepository.save(order);

      orderEventPublisher.publishOrderCreated(savedOrder);  // ← 新增這行

      return savedOrder;
  }


## 🎯 練功重點提醒

1. **訊息持久化**: 所有 Exchange, Queue, Message 都要設定 durable
2. **手動 ACK**: 使用 MANUAL 模式，確保訊息處理成功才 ACK
3. **冪等性**: 每個 Consumer 都要實現冪等性檢查
4. **重試策略**: 區分可重試和不可重試的錯誤
5. **並發控制**: 使用樂觀鎖或悲觀鎖防止庫存超賣
6. **事務邊界**: 確保 DB 操作和訊息 ACK 的一致性
7. **監控告警**: 設定 DLQ 監控，及時發現問題

---

## 📚 下一步

- [x] 實現 Order Service 基本結構（不含 MQ）
- [x] 添加 MQ 到 Order Service
- [x] 設定 RabbitMQ (docker-compose.yml)
- [x] 實現 Payment Service（Phase 1~4 全部完成）
- [x] 實現 Inventory Service Phase 1（基礎結構，不含 MQ）
- [x] 實現 Inventory Service Phase 2（MQ Consumer + DLX 設定 + 重試分流）
- [x] 實現 Inventory Service Phase 3（冪等性機制）
- [x] 實現 Inventory Service Phase 4（多實例消費與並發控制驗證）
- [ ] 加入監控和日誌
- [ ] 壓力測試與調優

---

## 📝 實作進度記錄

##### API 設計

| Method | Endpoint | 說明 |
|--------|----------|------|
| GET | `/api/payments/{paymentId}` | 查詢付款詳情 |
| GET | `/api/payments/order/{orderId}` | 依訂單 ID 查詢付款 |
| GET | `/api/payments?customerId=xxx` | 查詢客戶所有付款紀錄 |

---

### Inventory Service 設計

Inventory Service 是純 Consumer 角色：
- **Consumer**: 監聽 `inventory.queue`，接收 `PaymentCompletedEvent`
- **多實例競爭消費**: 支援水平擴展，多個實例分擔訊息
- **樂觀鎖**: 防止並發扣庫存導致超賣

##### 目錄結構設計

```
inventory/
├── pom.xml
├── src/main/resources/
│   └── application.yml
└── src/main/java/com/example/inventory/
    ├── InventoryApplication.java
    ├── entity/
    │   ├── Inventory.java               # 庫存實體（含樂觀鎖 @Version）
    │   └── IdempotencyRecord.java       # 冪等性記錄
    ├── repository/
    │   ├── InventoryRepository.java     # 庫存資料存取
    │   └── IdempotencyRepository.java   # 冪等性資料存取
    ├── dto/
    │   ├── InventoryResponse.java       # API 回應
    │   └── event/
    │       ├── PaymentCompletedEvent.java  # 接收的付款事件
    │       └── OrderItemEvent.java         # 訂單項目事件
    ├── service/
    │   └── InventoryService.java        # 庫存業務邏輯（含樂觀鎖扣減）
    ├── controller/
    │   └── InventoryController.java     # REST API（查詢庫存）
    ├── mq/
    │   ├── listener/
    │   │   └── PaymentEventListener.java  # 監聽付款事件
    │   └── RabbitConfig.java              # RabbitMQ 配置
    └── exception/
        ├── InventoryException.java
        └── InventoryErrorCode.java
```

##### Entity 設計

**Inventory.java**
```java
@Entity
@Table(name = "inventories")
public class Inventory {
    @Id
    private String productId;          // 商品 ID

    @Column(nullable = false)
    private Integer quantity;          // 庫存數量

    @Version                           // 樂觀鎖，防止並發超賣
    private Long version;
}
```

##### API 設計

| Method | Endpoint | 說明 |
|--------|----------|------|
| GET | `/api/inventory/{productId}` | 查詢商品庫存 |
| GET | `/api/inventory` | 查詢所有庫存 |

##### 實作步驟

**Phase 1: 基礎結構（不含 MQ）**
- [x] Step 1: 建立 `pom.xml`（依賴：Web、JPA、H2、AMQP、Lombok）
- [x] Step 2: 建立 `application.yml`（Server Port: 8083、資料庫設定）
- [x] Step 3: 建立 Entity（Inventory 含 @Version 樂觀鎖）
- [x] Step 4: 建立 Repository（InventoryRepository）
- [x] Step 5: 建立 DTO（InventoryResponse）
- [x] Step 6: 建立 Service（InventoryService - 查詢 + 扣庫存邏輯）
- [x] Step 7: 建立 Controller（InventoryController）

**Phase 2: 添加 MQ Consumer**
- [x] Step 1: 建立事件 DTO（PaymentCompletedEvent、OrderItemEvent）
- [x] Step 2: 建立 RabbitConfig.java（Listener Container 設定，concurrency: 2-5）
- [x] Step 3: 建立 PaymentEventListener.java（監聽 inventory.queue，手動 ACK）
- [x] Step 4: 修改 InventoryService 處理來自 MQ 的扣庫存請求

**Phase 3: 添加冪等性機制**
- [x] Step 1: 建立 IdempotencyRecord Entity
- [x] Step 2: 建立 IdempotencyRepository
- [x] Step 3: 在處理訊息前檢查冪等性

**Phase 4: 多實例消費與並發控制**
- [x] Step 1: 設定 prefetch、concurrency（支援多 worker thread）
- [x] Step 2: 驗證樂觀鎖在並發扣庫存時正確運作（version log in deductInventory）
- [x] Step 3: 處理 OptimisticLockException（重試或進 DLQ）

---

### 設定 RabbitMQ (docker-compose.yml)

##### 實作步驟

- [x] Step 1: 建立 `docker-compose.yml`（RabbitMQ + Management UI）
- [x] Step 2: 設定 RabbitMQ port（5672、15672）與帳密
- [x] Step 3: 驗證各服務可連線到 RabbitMQ
- [ ] Step 4: 透過 Management UI 確認 Exchange、Queue、Binding 正確建立

---

### 加入 DLX 和重試邏輯

##### 實作步驟

- [ ] Step 1: Payment Service - OrderEventListener 加入重試次數判斷（最多 3 次）
- [ ] Step 2: Payment Service - 區分可重試錯誤（NACK + requeue）和不可重試錯誤（NACK → DLQ）
- [ ] Step 3: Inventory Service - PaymentEventListener 加入重試次數判斷
- [ ] Step 4: Inventory Service - 庫存不足直接進 DLQ（不可重試）
- [ ] Step 5: 驗證訊息在重試失敗後正確進入對應的 DLQ

---

### 加入監控和日誌

##### 實作步驟

- [ ] Step 1: 統一日誌格式（包含 orderId、eventId、instanceId）
- [ ] Step 2: 加入關鍵流程的 log（訊息收發、冪等性命中、重試次數）
- [ ] Step 3: 透過 RabbitMQ Management UI 監控 DLQ 訊息數量

---

### 壓力測試與調優

##### 實作步驟

- [ ] Step 1: 批量建立訂單（100+ 筆），驗證端到端流程
- [ ] Step 2: 啟動多個 Inventory Service 實例，觀察競爭消費
- [ ] Step 3: 驗證冪等性（手動重送訊息，確認不重複處理）
- [ ] Step 4: 驗證 DLX（模擬失敗，確認訊息進入 DLQ）
- [ ] Step 5: 驗證樂觀鎖（並發扣庫存，確認不超賣）

---

#### MQ 基礎概念筆記

**為什麼需要 MQ？**
- 解耦：Order 不需要知道 Payment 的存在
- 非同步：Order 丟完訊息就可以回應客戶
- 可靠：MQ 會保存訊息，Payment 掛了重啟還能收到
- 擴展：可以啟動多個 Payment 來分擔工作

**RabbitMQ 核心概念（郵局比喻）：**
```
Producer ──→ Exchange ──→ Queue ──→ Consumer
(寄件人)     (分信中心)    (信箱)    (收件人)
```

1. **Producer（生產者）** = 寄信的人（Order Service）
2. **Exchange（交換機）** = 郵局分信中心
3. **Queue（佇列）** = 收件人的信箱
4. **Consumer（消費者）** = 收信的人（Payment Service）
5. **Routing Key** = 郵遞區號，決定訊息送到哪個 Queue

**RabbitMQ ACK（Acknowledgement）機制**
ACK 是消費者告訴 RabbitMQ「我已成功處理這條訊息」的確認機制。                                                                      流程：
1. RabbitMQ 發送訊息給消費者
2. 消費者處理訊息
3. 消費者發送 ACK 確認
4. RabbitMQ 收到 ACK 後才刪除該訊息
                                  
為什麼需要 ACK？
- 防止訊息遺失：如果消費者在處理過程中崩潰，未 ACK 的訊息會重新排隊
- 確保可靠性：只有確認處理完成，訊息才會從 queue 中移除

三種確認模式
  ┌────────┬────────────────────────────────┐
  │  模式  │              說明              │
  ├────────┼────────────────────────────────┤
  │ ack    │ 確認成功，刪除訊息             │
  ├────────┼────────────────────────────────┤
  │ nack   │ 否定確認，可選擇重新排隊或丟棄 │
  ├────────┼────────────────────────────────┤
  │ reject │ 拒絕單條訊息                   │
  └────────┴────────────────────────────────┘
Spring AMQP 三種確認模式，若是要手動ack要在yml添加以下程式

  spring:
    rabbitmq:
      listener:
        simple:
          acknowledge-mode: manual  # 手動確認（推薦）
          # acknowledge-mode: auto   # 自動確認（處理成功自動 ACK，拋異常自動 NACK）
          # acknowledge-mode: none   # 不確認（訊息送達即刪除，不安全）
		  prefetch: 10
		  # 每個 consumer 一次最多拿 10 筆「尚未 ACK」的訊息
		  concurrency: 2
		  # 啟動 2 條 consumer thread
		  max-concurrency: 5
		  # 當流量變大時，最多可以擴充到 5 條 consumer
  ┌────────┬─────────────────────────────────────────┐
  │  模式  │                  說明                   │
  ├────────┼─────────────────────────────────────────┤
  │ manual │ 完全手動控制，需呼叫 basicAck/basicNack │
  ├────────┼─────────────────────────────────────────┤
  │ auto   │ Spring 根據方法是否拋異常自動處理       │
  ├────────┼─────────────────────────────────────────┤
  │ none   │ 不確認，訊息送達即視為成功              │
  └────────┴─────────────────────────────────────────┘

**冪等性（Idempotency）機制**

為什麼需要冪等性？
在 MQ 環境中，同一條訊息可能被消費多次：
- Consumer 處理完成，但 ACK 還沒送出就掛了 → RabbitMQ 重送
- 網路問題導致訊息重複發送
- 人工從 DLQ 重新發送訊息

沒有冪等性的後果：
- 付款訊息重複 → 客戶被扣款 2 次
- 庫存扣減重複 → 庫存多扣
- 發送通知重複 → 客戶收到 N 封信

實現方式：記住處理過的訊息
```
每條訊息都有唯一的 eventId

第一次收到 eventId = "E001"
  1. 查 DB：有處理過 E001 嗎？→ 沒有
  2. 記錄：INSERT idempotency_record (event_id = 'E001', status = 'PROCESSING')
  3. 處理業務邏輯（付款）
  4. 更新：UPDATE status = 'PROCESSED'
  5. ACK

第二次收到 eventId = "E001"（重複訊息）
  1. 查 DB：有處理過 E001 嗎？→ 有，status = 'PROCESSED'
  2. 直接跳過，ACK
  3. 不會重複付款！
```

資料庫設計：
```
idempotency_record 表
┌──────────┬───────────────┬────────────┬─────────────────────┐
│ event_id │ event_type    │ status     │ processed_at        │
├──────────┼───────────────┼────────────┼─────────────────────┤
│ E001     │ ORDER_CREATED │ PROCESSED  │ 2026-02-06 10:00:00 │
│ E002     │ ORDER_CREATED │ PROCESSING │ 2026-02-06 10:01:00 │
└──────────┴───────────────┴────────────┴─────────────────────┘
```

---

**專案版本:** v1.1
**更新日期:** 2026-02-09
**作者:** RabbitMQ 練功專案
