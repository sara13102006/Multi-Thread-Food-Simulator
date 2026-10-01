package delivery;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Represents one delivery agent. Runs as a long-lived Runnable that keeps
 * polling a shared ready-for-pickup queue until it receives a "poison pill"
 * (a sentinel Order signalling shutdown) or is interrupted.
 *
 * WHY Runnable and not Callable here:
 * This task never produces a single final result the caller needs back --
 * it just keeps working until told to stop. Runnable is the right interface
 * for a long-running worker submitted to an ExecutorService where we only
 * care about the side effects (deliveries + tracker updates), not a return
 * value.
 *
 * WHY BlockingQueue for the shared queue:
 * Multiple restaurant threads PUT ready orders in, multiple agent threads
 * TAKE/POLL them out. A BlockingQueue (LinkedBlockingQueue) already handles
 * all the producer/consumer synchronization internally -- we don't need to
 * write our own wait/notify logic or synchronized blocks around it.
 */
public class DeliveryAgent implements Runnable {

    /** Sentinel order used to tell an agent thread "no more work, shut down". */
    public static final Order POISON_PILL = new Order(-1, "SYSTEM", java.util.List.of("shutdown"), 0.01, "SYSTEM");

    private final String agentName;
    private final BlockingQueue<Order> readyForPickupQueue;
    private final OrderTracker tracker;

    public DeliveryAgent(String agentName, BlockingQueue<Order> readyForPickupQueue, OrderTracker tracker) {
        this.agentName = agentName;
        this.readyForPickupQueue = readyForPickupQueue;
        this.tracker = tracker;
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        try {
            while (true) {
                Order order = readyForPickupQueue.poll(5, TimeUnit.SECONDS);

                if (order == null) {
                    System.out.printf("[%s] %s timed out waiting for orders, shutting down.%n", threadName, agentName);
                    return;
                }

                if (order == POISON_PILL) {
                    System.out.printf("[%s] %s received shutdown signal.%n", threadName, agentName);
                    return;
                }

                deliver(order, threadName);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.printf("[%s] %s was interrupted, shutting down.%n", threadName, agentName);
        }
    }

    private void deliver(Order order, String threadName) throws InterruptedException {
        if (!order.transitionStatus(OrderStatus.READY_FOR_PICKUP, OrderStatus.PICKED_UP)) {
            System.out.printf("[%s] %s found order #%d already claimed, skipping.%n", threadName, agentName, order.getId());
            return;
        }
        System.out.printf("[%s] %s picked up order #%d%n", threadName, agentName, order.getId());

        Thread.sleep(ThreadLocalRandom.current().nextInt(400, 1500)); // simulate travel time

        order.transitionStatus(OrderStatus.PICKED_UP, OrderStatus.DELIVERED);
        System.out.printf("[%s] %s delivered order #%d%n", threadName, agentName, order.getId());

        tracker.recordCompletion(order);
    }
}
