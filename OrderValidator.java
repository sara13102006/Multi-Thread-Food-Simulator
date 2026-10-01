package delivery;

import delivery.exceptions.InvalidOrderException;

import java.util.List;

/**
 * Validates raw order input BEFORE an Order object is ever created.
 *
 * Inputs are never blindly trusted: every order is checked for a real
 * customer name, a non-empty item list, and a sane, positive total amount.
 * Anything that fails validation throws InvalidOrderException so the caller
 * has to explicitly decide what to do about it.
 */
public final class OrderValidator {

    private OrderValidator() {
        // utility class, no instances
    }

    public static void validate(String customerName, List<String> items, double totalAmount)
            throws InvalidOrderException {

        if (customerName == null || customerName.isBlank()) {
            throw new InvalidOrderException("Customer name must not be null or blank.");
        }

        if (items == null || items.isEmpty()) {
            throw new InvalidOrderException("Order must contain at least one item.");
        }

        for (String item : items) {
            if (item == null || item.isBlank()) {
                throw new InvalidOrderException("Order items must not contain null/blank entries.");
            }
        }

        if (totalAmount <= 0.0) {
            throw new InvalidOrderException("Total amount must be greater than zero, got: " + totalAmount);
        }

        if (Double.isNaN(totalAmount) || Double.isInfinite(totalAmount)) {
            throw new InvalidOrderException("Total amount must be a finite number, got: " + totalAmount);
        }
    }
}
