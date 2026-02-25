package com.example.inventory.service;

import com.example.inventory.dto.InventoryResponse;
import com.example.inventory.dto.event.PaymentCompletedEvent;
import com.example.inventory.entity.IdempotencyRecord;
import com.example.inventory.entity.Inventory;
import com.example.inventory.exception.InventoryErrorCode;
import com.example.inventory.exception.InventoryException;
import com.example.inventory.repository.IdempotencyRepository;
import com.example.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final IdempotencyRepository idempotencyRepository;

    @Transactional(readOnly = true)
    public InventoryResponse getInventory(String productId) {
        log.info("Getting inventory for product: {}", productId);

        Inventory inventory = inventoryRepository.findById(productId)
                .orElseThrow(() -> InventoryException.of(InventoryErrorCode.PRODUCT_NOT_FOUND, productId));

        return toInventoryResponse(inventory);
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> getAllInventory() {
        log.info("Getting all inventory");

        return inventoryRepository.findAll().stream()
                .map(this::toInventoryResponse)
                .toList();
    }

    @Transactional
    public void deductInventory(String productId, int quantity) {
        log.info("Deducting inventory: productId={}, quantity={}", productId, quantity);

        Inventory inventory = inventoryRepository.findById(productId)
                .orElseThrow(() -> InventoryException.of(InventoryErrorCode.PRODUCT_NOT_FOUND, productId));

        if (inventory.getQuantity() < quantity) {
            log.warn("Insufficient inventory: productId={}, available={}, requested={}",
                    productId, inventory.getQuantity(), quantity);
            throw InventoryException.of(InventoryErrorCode.INSUFFICIENT_INVENTORY, productId);
        }

        log.debug("Optimistic lock check: productId={}, version={}", productId, inventory.getVersion());
        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventoryRepository.save(inventory);  // @Version 不符時拋出 ObjectOptimisticLockingFailureException

        log.info("Inventory deducted: productId={}, remaining={}, version={}",
                productId, inventory.getQuantity(), inventory.getVersion());
    }

    /**
     * MQ Consumer 進入點：處理付款完成事件，依序扣減訂單內所有商品庫存。
     *
     * 冪等性流程：
     *  1. 查 idempotency_record，若已存在 → 直接返回（skip）
     *  2. saveAndFlush PROCESSING（觸發 unique constraint，防止並發雙重處理）
     *  3. 執行庫存扣減
     *  4. 更新 status = PROCESSED
     *
     * 若步驟 2 因 unique constraint 失敗（DataIntegrityViolationException），
     * 表示另一實例正在處理，Listener 捕捉後 requeue → 下次重試找到 PROCESSED → skip。
     */
    @Transactional
    public void processPaymentEvent(PaymentCompletedEvent event) {
        String eventId = event.getEventId();

        // 1. 冪等性檢查
        Optional<IdempotencyRecord> existing = idempotencyRepository.findByEventId(eventId);
        if (existing.isPresent()) {
            log.info("Duplicate event {}: status={}, skipping", eventId, existing.get().getStatus());
            return;
        }

        // 2. 插入 PROCESSING（saveAndFlush 立即觸發 unique constraint 檢查）
        IdempotencyRecord record = IdempotencyRecord.builder()
                .eventId(eventId)
                .eventType("PAYMENT_COMPLETED")
                .status("PROCESSING")
                .build();
        idempotencyRepository.saveAndFlush(record);

        // 3. 業務邏輯：扣減所有商品庫存
        log.info("Processing PaymentCompletedEvent: orderId={}, itemCount={}",
                event.getOrderId(), event.getItems().size());

        event.getItems().forEach(item ->
                deductInventory(item.getProductId(), item.getQuantity()));

        // 4. 標記為 PROCESSED
        record.setStatus("PROCESSED");
        record.setProcessedAt(LocalDateTime.now());
        idempotencyRepository.save(record);

        log.info("All items deducted for orderId={}", event.getOrderId());
    }

    private InventoryResponse toInventoryResponse(Inventory inventory) {
        return InventoryResponse.builder()
                .productId(inventory.getProductId())
                .quantity(inventory.getQuantity())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }
}
