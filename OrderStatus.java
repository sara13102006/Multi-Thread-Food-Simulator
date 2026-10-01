package delivery;

/**
 * Represents every state an Order can be in during its lifecycle.
 *
 * Using an enum (rather than raw ints/strings) gives us compile-time safety
 * and makes illegal states impossible to type by accident.
 */
public enum OrderStatus {
    CREATED,
    ACCEPTED,
    PREPARING,
    READY_FOR_PICKUP,
    PICKED_UP,
    DELIVERED,
    CANCELLED,
    FAILED
}
