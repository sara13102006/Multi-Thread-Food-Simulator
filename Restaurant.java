package delivery;

import delivery.exceptions.RestaurantCapacityException;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Represents a restaurant's kitchen preparing ONE order.
 *
 * WHY Callable<Order> and not Runnable:
 * A Runnable's run() returns nothing and can't throw checked exceptions.
 * We need both: the ExecutorService caller wants the finished Order back
 * (so it can hand it to a delivery agent), and preparation can legitimately
 * fail (capacity exceeded, interrupted). Callable<V> gives us a return value
 * via Future<Order> AND lets us throw Exception, which Runnable can't.
 *
 * WHY Semaphore for capacity:
 * The kitchen has a fixed number of "slots" it can cook in parallel. A
 * Semaphore initialized with that slot count is the textbook tool for
 * "at most N concurrent workers": each Restaurant task acquires a permit
 * before cooking and releases it in a finally block, so capacity is
 * enforced correctly even if a thread is interrupted mid-cook.
 */
public class Restaurant {

    private final String name;
    private final Semaphore kitchenCapacity;
    private final BlockingQueue<Order> readyForPickupQueue;

    public Restaurant(String name, int kitchenCapacity, BlockingQueue<Order> readyForPickupQueue) {
        this.name = name;
        this.kitchenCapacity = new Semaphore(kitchenCapacity);
        this.readyForPickupQueue = readyForPickupQueue;
    }

    public String getName() {
        return name;
    }

    /**
     * Returns a Callable task that prepares the given order.
     * Each call to this method produces a fresh task tied to this
     * restaurant's shared Semaphore, so submitting many orders to the same
     * restaurant will correctly serialize them once capacity is hit.
     */
    public Callable<Order> prepare(Order order) {
        return () -> {
            String threadName = Thread.currentThread().getName();

            // Wait up to 3 seconds for a free kitchen slot. If none frees up,
            // this is a genuine business failure, not a bug -- we throw a
            // checked, custom exception instead of blocking forever.
            boolean acquired = kitchenCapacity.tryAcquire(3, TimeUnit.SECONDS);
            if (!acquired) {
                order.forceStatus(OrderStatus.FAILED);
                throw new RestaurantCapacityException(
                        name + " is at full kitchen capacity, could not accept order #" + order.getId());
            }

            try {
                if (!order.transitionStatus(OrderStatus.CREATED, OrderStatus.ACCEPTED)) {
                    throw new IllegalStateException(
                            "Order #" + order.getId() + " was not in CREATED state when " + name + " tried to accept it.");
                }
                System.out.printf("[%s] %s accepted order #%d%n", threadName, name, order.getId());

                order.transitionStatus(OrderStatus.ACCEPTED, OrderStatus.PREPARING);
                System.out.printf("[%s] %s is preparing order #%d%n", threadName, name, order.getId());

                // Simulate variable prep time (300ms - 1200ms)
                Thread.sleep(ThreadLocalRandom.current().nextInt(300, 1200));

                order.transitionStatus(OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP);
                System.out.printf("[%s] %s finished order #%d, placing in pickup queue%n", threadName, name, order.getId());

                readyForPickupQueue.put(order); // BlockingQueue: blocks if full, never loses an order
                return order;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // restore interrupt status, don't swallow it
                order.forceStatus(OrderStatus.FAILED);
                throw e;
            } finally {
                kitchenCapacity.release();
            }
        };
    }
}
