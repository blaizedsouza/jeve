package de.skuzzle.jeve;

/**
 * Can be thrown by {@link Listener Listeners} or {@link ExceptionCallback
 * ExceptionCallbacks} to make the event dispatching explicitly fail with an
 * exception. All other kinds of exceptions will be swallowed by the
 * {@link EventProvider}.
 * 
 * @author Simon Taddiken
 * @since 1.1.0
 */
public class AbortionException extends RuntimeException {

    private static final long serialVersionUID = 1L;



    /**
     * Creates a new AbortionException
     */
    public AbortionException() {
    }



    /**
     * Creates a new AbortionException with a message.
     * 
     * @param message
     *            The message
     */
    public AbortionException(String message) {
        super(message);
    }



    /**
     * Creates a new AbortionException with message and a cause
     * 
     * @param message
     *            The message
     * @param cause
     *            The cause.
     */
    public AbortionException(String message, Exception cause) {
        super(message, cause);
    }



    /**
     * Creates a new AbortionException with a cause
     * 
     * @param cause
     *            The cause.
     */
    public AbortionException(Exception cause) {
        super(cause);
    }
}