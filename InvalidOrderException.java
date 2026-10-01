package delivery.exceptions;

/**
 * Thrown when an order fails basic validation (missing customer name,
 * empty item list, non-positive amount, etc). This is a checked exception
 * on purpose: a caller creating an order MUST decide what to do about bad
 * input (reject it, log it, ask again) rather than letting it silently blow
 * up somewhere downstream in a worker thread.
 */
public class InvalidOrderException extends Exception {
    public InvalidOrderException(String message) {
        super(message);
    }
}
