package delivery.exceptions;

/**
 * Thrown when a restaurant cannot accept an order because its kitchen is
 * already at full capacity (all Semaphore permits are taken and none freed
 * up within the wait window). This is a business failure, not a bug -- the
 * simulator catches it and marks the order FAILED rather than crashing a
 * worker thread.
 */
public class RestaurantCapacityException extends Exception {
    public RestaurantCapacityException(String message) {
        super(message);
    }
}
