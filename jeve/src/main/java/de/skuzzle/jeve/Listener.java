package de.skuzzle.jeve;

import java.util.EventListener;

/**
 * This is the base interface for event listeners. It specifies default methods
 * that are notified when the listener is registered or removed to or from a
 * {@link ListenerStore}.
 *
 * Normally, you create an interface extending {@code Listener} and add some
 * <em>listening methods</em>. By convention, those methods must adhere to the
 * signature:
 *
 * <pre>
 * public void &lt;listeningName&gt;(&lt;subclass of Event&gt; e);
 * </pre>
 *
 * This allows you to provide a method reference conforming to the
 * {@link java.util.function.BiConsumer BiConsumer} functional interface to the
 * {@link EventProvider#dispatch(Event, java.util.function.BiConsumer) dispatch}
 * method of an EventProvider.
 *
 * <pre>
 * eventProvider.dispatch(MyListener.class, someEventInstance,
 *         MyListener::listeningMethod);
 * </pre>
 *
 * <h2>ListenerInterface Annotation</h2>
 * <p>
 * To enable compile time checks for whether your listener definition adheres to
 * the different kind of listening methods, you may tag it with
 * {@link de.skuzzle.jeve.annotation.ListenerInterface ListenerInterface}. This
 * is completely optional but makes your intentions clear to other programmers.
 * </p>
 *
 * @author Simon Taddiken
 * @since 1.0.0
 */
public interface Listener extends EventListener {

    /**
     * This method is called right after this listener has been registered to a
     * new {@link EventProvider}. Setting the passed Event's {@code handled}
     * attribute to <code>true</code> will have no effect.
     *
     * <p>
     * Note: The default implementation does nothing.
     * </p>
     *
     * @param e This event object holds the new parent EventProvider and the
     *            class for which this listener has been registered.
     */
    public default void onRegister(RegistrationEvent e) {
        // default: do nothing
    }

    /**
     * This method is called right after this listener has been removed from an
     * {@link EventProvider}. If this method throws an unchecked exception, it
     * will be covered by the former EventProvider's {@link ExceptionCallback}.
     *
     * <p>
     * Note: The default implementation does nothing.
     * </p>
     *
     * @param e This event object holds the former parent EventProvider and the
     *            class for which this listener has been unregistered.
     */
    public default void onUnregister(RegistrationEvent e) {
        // default: do nothing
    }
}
