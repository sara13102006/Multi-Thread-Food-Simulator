package delivery;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central, thread-safe record of every order in the system and simple
 * running statistics.
 *
 * WHY ConcurrentHashMap instead of a synchronized HashMap:
 * Many threads (every restaurant + every delivery agent + the main thread)
 * read and write this map at once. A plain HashMap is not thread-safe at
 * all. Wrapping a HashMap with Collections.synchronizedMap() would work but
 * forces every single get/put through one lock, serializing all threads.
 * ConcurrentHashMap uses fine-grained locking internally, so unrelated
 * orders can be read/written by different threads truly in parallel.
 *
 * WHY AtomicInteger for the counters:
 * delivered/failed counts are incremented from many threads simultaneously.
 * A plain int++ is NOT atomic (it's a read-modify-write across 3 steps) and
 * would lose increments under contention. AtomicInteger's incrementAndGet()
 * is a single atomic CPU-level operation, so no locking is needed and no
 * counts are lost.
 */
public class OrderTracker {

    private final ConcurrentMap<Integer, Order> orders = new ConcurrentHashMap<>();
    private final AtomicInteger totalDelivered = new AtomicInteger(0);
    private final AtomicInteger totalFailed = new AtomicInteger(0);

    public void register(Order order) {
        orders.put(order.getId(), order);
    }

    public void recordCompletion(Order order) {
        totalDelivered.incrementAndGet();
    }

    public void recordFailure(Order order) {
        totalFailed.incrementAndGet();
    }

    public Order get(int orderId) {
        return orders.get(orderId);
    }

    public int getTotalDelivered() {
        return totalDelivered.get();
    }

    public int getTotalFailed() {
        return totalFailed.get();
    }

    public int getTotalOrders() {
        return orders.size();
    }

    public void printSummary() {
        System.out.println();
        System.out.println("===== SIMULATION SUMMARY =====");
        System.out.println("Total orders placed : " + getTotalOrders());
        System.out.println("Delivered           : " + getTotalDelivered());
        System.out.println("Failed              : " + getTotalFailed());
        System.out.println();
        orders.values().stream()
                .sorted((a, b) -> Integer.compare(a.getId(), b.getId()))
                .forEach(o -> System.out.println("  " + o));
        System.out.println("===============================");
    }
}
