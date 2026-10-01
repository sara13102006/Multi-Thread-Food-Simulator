package delivery;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a single customer order.
 *
 * WHY AtomicReference for status:
 * An Order's status field is read and written from multiple threads at once
 * (the restaurant thread moves it CREATED -> PREPARING -> READY_FOR_PICKUP,
 * a delivery agent thread later moves it PICKED_UP -> DELIVERED, and the main
 * thread / tracker reads it for reporting). Wrapping it in an
 * AtomicReference<OrderStatus> and only ever changing it through
 * compareAndSet() means a transition only succeeds if the order is still in
 * the state we expect it to be in. That rules out lost updates without
 * needing a synchronized block around every read.
 */
public class Order {

    private final int id;
    private final String customerName;
    private final List<String> items;
    private final double totalAmount;
    private final String restaurantName;
    private final AtomicReference<OrderStatus> status;
    private final LocalDateTime createdAt;

    public Order(int id, String customerName, List<String> items, double totalAmount, String restaurantName) {
        this.id = id;
        this.customerName = customerName;
        this.items = Collections.unmodifiableList(items);
        this.totalAmount = totalAmount;
        this.restaurantName = restaurantName;
        this.status = new AtomicReference<>(OrderStatus.CREATED);
        this.createdAt = LocalDateTime.now();
    }

    public int getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public List<String> getItems() {
        return items;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public String getRestaurantName() {
        return restaurantName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public OrderStatus getStatus() {
        return status.get();
    }

    /**
     * Attempts to move the order from expected to next.
     * Returns false (instead of throwing) if another thread already changed
     * the status -- callers decide whether that's an error or just a race
     * they should retry/skip.
     */
    public boolean transitionStatus(OrderStatus expected, OrderStatus next) {
        return status.compareAndSet(expected, next);
    }

    /** Force-set the status regardless of current value (used for CANCELLED/FAILED). */
    public void forceStatus(OrderStatus next) {
        status.set(next);
    }

    @Override
    public String toString() {
        return String.format("Order#%d[customer=%s, restaurant=%s, items=%s, total=%.2f, status=%s]",
                id, customerName, restaurantName, items, totalAmount, status.get());
    }
}
