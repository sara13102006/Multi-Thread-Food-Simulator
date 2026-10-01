package delivery;

import delivery.exceptions.InvalidOrderException;
import delivery.exceptions.RestaurantCapacityException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Entry point that wires the whole simulation together.
 *
 * WHY ExecutorService instead of raw new Thread(...).start():
 * Creating a raw Thread per order does not scale and gives us no control
 * over how many run at once, and no clean way to wait for completion or
 * collect results. ExecutorService gives us a managed thread pool (so we
 * cap concurrency deliberately), Future<Order> to collect each Restaurant's
 * result asynchronously, and a clean, orderly shutdown()/awaitTermination().
 *
 * Two separate pools are used (kitchenPool, deliveryPool) because kitchen
 * work and delivery work are logically independent stages of the pipeline
 * with their own concurrency needs -- restaurants shouldn't compete with
 * delivery agents for the same threads.
 */
public class FoodDeliverySimulator {

    public static void main(String[] args) throws InterruptedException {

        // ---- Configuration ----
        final int KITCHEN_POOL_SIZE = 4;      // threads available to cook across all restaurants
        final int DELIVERY_AGENT_COUNT = 3;   // number of delivery agents working concurrently
        final int RESTAURANT_CAPACITY = 2;    // max orders a single restaurant can cook at once

        BlockingQueue<Order> readyForPickupQueue = new LinkedBlockingQueue<>();
        OrderTracker tracker = new OrderTracker();

        Restaurant pizzaPlace = new Restaurant("Pizza Palace", RESTAURANT_CAPACITY, readyForPickupQueue);
        Restaurant sushiBar = new Restaurant("Sushi Central", RESTAURANT_CAPACITY, readyForPickupQueue);

        ExecutorService kitchenPool = Executors.newFixedThreadPool(KITCHEN_POOL_SIZE);
        ExecutorService deliveryPool = Executors.newFixedThreadPool(DELIVERY_AGENT_COUNT);

        // Start delivery agents -- they run continuously, pulling from the shared queue.
        List<Future<?>> agentFutures = new ArrayList<>();
        for (int i = 1; i <= DELIVERY_AGENT_COUNT; i++) {
            DeliveryAgent agent = new DeliveryAgent("Agent-" + i, readyForPickupQueue, tracker);
            agentFutures.add(deliveryPool.submit(agent));
        }

        // ---- Build a batch of incoming orders, including some deliberately bad input ----
        Object[][] rawOrders = {
                {"Alice",   List.of("Margherita Pizza", "Garlic Bread"), 18.50, pizzaPlace},
                {"Bob",     List.of("California Roll"),                 12.00, sushiBar},
                {"",        List.of("Pepperoni Pizza"),                 15.00, pizzaPlace}, // invalid: blank name
                {"Charlie", List.of(),                                   0.0,   sushiBar},   // invalid: no items, bad amount
                {"Dana",    List.of("Salmon Nigiri", "Miso Soup"),      22.75, sushiBar},
                {"Evan",    List.of("Veggie Pizza"),                    -5.00, pizzaPlace},  // invalid: negative amount
                {"Farah",   List.of("Dragon Roll"),                     16.25, sushiBar},
                {"Grace",   List.of("BBQ Chicken Pizza", "Coke"),       19.99, pizzaPlace},
        };

        List<Future<Order>> kitchenFutures = new ArrayList<>();
        int nextId = 1;

        for (Object[] raw : rawOrders) {
            String customerName = (String) raw[0];
            @SuppressWarnings("unchecked")
            List<String> items = (List<String>) raw[1];
            double amount = (double) raw[2];
            Restaurant restaurant = (Restaurant) raw[3];

            try {
                OrderValidator.validate(customerName, items, amount);
                Order order = new Order(nextId++, customerName, items, amount, restaurant.getName());
                tracker.register(order);
                kitchenFutures.add(kitchenPool.submit(restaurant.prepare(order)));

            } catch (InvalidOrderException e) {
                // Validation failures are expected, recoverable business events --
                // we log and skip rather than letting bad input crash the batch.
                System.out.println("REJECTED order for '" + customerName + "': " + e.getMessage());
            }
        }

        // ---- Wait for every kitchen task to finish (or fail) ----
        for (Future<Order> future : kitchenFutures) {
            try {
                Order finished = future.get(10, TimeUnit.SECONDS);
                System.out.println(">> Kitchen finished: " + finished);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RestaurantCapacityException) {
                    System.out.println("FAILED (capacity): " + cause.getMessage());
                } else {
                    System.out.println("FAILED (unexpected): " + cause);
                }
                tracker.recordFailure(null);
            } catch (TimeoutException e) {
                System.out.println("FAILED (timeout waiting for kitchen): " + e.getMessage());
            }
        }

        // All kitchen work is done -- tell delivery agents to shut down once the
        // queue drains, by feeding one poison pill per agent.
        for (int i = 0; i < DELIVERY_AGENT_COUNT; i++) {
            readyForPickupQueue.put(DeliveryAgent.POISON_PILL);
        }

        kitchenPool.shutdown();
        deliveryPool.shutdown();
        kitchenPool.awaitTermination(15, TimeUnit.SECONDS);
        deliveryPool.awaitTermination(15, TimeUnit.SECONDS);

        tracker.printSummary();
    }
}
