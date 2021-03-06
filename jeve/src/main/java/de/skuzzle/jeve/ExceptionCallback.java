package de.skuzzle.jeve;

import de.skuzzle.jeve.invoke.FailedEventInvocation;

/**
 * Interface for providing errors which occur during event dispatching to the
 * caller.
 *
 * @author Simon Taddiken
 * @since 1.0.0
 * @see ExceptionCallbacks
 */
public interface ExceptionCallback {

    /**
     * Callback method which gets passed an exception. This method will be
     * called by an {@link EventProvider} if an instance of this interface has
     * been set as exception callback. This method is generally called within
     * the same thread in which the attempt to notify the listener has been
     * made.
     *
     * <p>
     * Note: If this method throws any unchecked exceptions other than
     * {@link AbortionException}, they will be swallowed by the EventProvider
     * during error handling.
     * </p>
     *
     * @param e The exception which occurred during event dispatching.
     * @param source The event listener which caused the exception.
     * @param event The event which is currently being processed.
     * @throws AbortionException can be thrown to make event dispatching
     *             explicitly fail with an exception. No further listeners will
     *             be notified and the caller of
     *             {@link EventProvider#dispatch(Event, java.util.function.BiConsumer)
     *             dispatch} will receive this exception.
     * @deprecated Since 3.0.0 - use {@link #exception(FailedEventInvocation)}
     *             instead.
     */
    @Deprecated
    public default void exception(Exception e, Listener source, Event<?, ?> event) {

    }

    /**
     * Callback method which is notified about exceptions that may occur during
     * event dispatching. This method will be called by an {@link EventProvider}
     * if an instance of this interface has been set as exception callback. This
     * method is generally called within the same thread in which the attempt to
     * notify the listener has been made.
     *
     * <p>
     * Note: If this method throws any unchecked exceptions other than
     * {@link AbortionException}, they will be swallowed by the EventProvider
     * during error handling.
     * </p>
     *
     * <p>
     * Implementation note: the default implementation calls the deprecated method
     * {@link #exception(Exception, Listener, Event)} for compatibility reasons.
     * </p>
     *
     * @param invocation {@link FailedEventInvocation} object which describes the failure.
     * @throws AbortionException can be thrown to make event dispatching
     *             explicitly fail with an exception. No further listeners will
     *             be notified and the caller of
     *             {@link EventProvider#dispatch(Event, java.util.function.BiConsumer)
     *             dispatch} will receive this exception.
     * @since 3.0.0
     */
    public default void exception(FailedEventInvocation invocation) {
        exception(invocation.getException(), invocation.getListener(),
                invocation.getEvent());
    }
}
