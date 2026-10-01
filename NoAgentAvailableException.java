package delivery.exceptions;

/**
 * Thrown when an order sits in the ready-for-pickup queue longer than the
 * simulator is willing to wait for any delivery agent to grab it.
 */
public class NoAgentAvailableException extends Exception {
    public NoAgentAvailableException(String message) {
        super(message);
    }
}
