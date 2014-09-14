package de.skuzzle.jeve;

import java.util.function.BiConsumer;

import de.skuzzle.jeve.builder.ConfiguratorImpl;
import de.skuzzle.jeve.builder.EventProviderConfigurator;

/**
 * <p>
 * EventProvider instances are the heart of jeve and can be obtained using
 * static factory methods of the {@link EventProviders} class. They manage
 * listener classes mapped to a collection of {@link Listener Listeners} to
 * represent one kind of event. All listeners registered for a certain listener
 * class can be notified about an {@link Event}. The way in which they are
 * notified is an internal property of the actual EventProvider instance. For
 * example, one kind of EventProvider might create a new thread for notifying
 * the registered listeners or it may simply notify them using the current
 * thread.
 * </p>
 *
 * <pre>
 * EventProvider eventProvider = EventProviders.newDefaultEventProvider();
 * </pre>
 *
 * <h2>Managing and Notifying Listeners</h2>
 * <p>
 * Listeners can be registered using {@link #addListener(Class, Listener)} and
 * unregistered using {@link #removeListener(Class, Listener)}. The same
 * listener object can be registered for distinct listener classes if it
 * implements different listeners. The {@link Listener} interface has two
 * default methods which are called when a listener is registered or removed
 * respectively. Listeners registered for a certain class can be obtained by
 * {@link #getListeners(Class)}. Client code should avoid using this method as
 * it is not needed in most cases.
 * </p>
 *
 * <p>
 * The reason why not to query the registered listeners from client code, is
 * that EventProviders use <em>internal iteration</em> when notifying listeners.
 * This reduces the use cases where client code explicitly needs a list of
 * listeners. The logic of how listeners are iterated is moved into the
 * framework, reducing duplicated and error prone code on the client side.
 * </p>
 *
 * <p>
 * To notify the registered listeners, you need to specify the {@link Event}
 * instance which is passed to each listener and the actual method to call on
 * each listener. Jeve obtains the class of the listeners to notify from the
 * event's {@link Event#getListenerClass() getListenerClass} method. Thus, the
 * class has not to be specified explicitly for each dispatch action. Here is an
 * example of notifying listeners which have been registered for the class
 * {@code UserListener}.
 * </p>
 *
 * <pre>
 * // create an event which holds its source and some additional data
 * UserEvent e = new UserEvent(this, user);
 *
 * // notify all UserListeners with this event.
 * eventProvider.dispatch(e, UserListener::userAdded);
 * </pre>
 *
 * <p>
 * On each listener which is registered for the class {@code UserListener}, the
 * method {@code userAdded} is called and gets passed the event instance
 * {@code e}. {@link #dispatch(Event, BiConsumer) Dispatch} is the core of any
 * EventProvider. It implements the logic of how the listeners are notified in a
 * way that is transparent for the user of the EventProvider.
 * </p>
 *
 * <h2>Error handling</h2>
 * <p>
 * The main goal of jeve is, that event delegation must never be interrupted
 * unintentionally. When handling events, you don't want the dispatching process
 * to stop if one of the listeners throws an unchecked exception. Therefore,
 * jeve uses {@link ExceptionCallback ExceptionCallbacks} to notify client code
 * about any exception. After notifying the callback, event delegation continues
 * with the next listener. {@link AbortionException} can be thrown from within
 * the callback method to explicitly stop event delegation with an exception.
 * All other exceptions thrown by the callback will be swallowed.
 * </p>
 *
 * <p>
 * A default {@link ExceptionCallback} can be set by using
 * {@link #setExceptionCallback(ExceptionCallback)}. Additionally, you can set a
 * callback for a single dispatch action by using an override of
 * {@link #dispatch(Event, BiConsumer, ExceptionCallback) dispatch}. If you do
 * not specify a callback, a {@link #DEFAULT_HANDLER default} instance will be
 * used.
 * </p>
 *
 * <h2>Sequential EventProviders</h2>
 * <p>
 * An EventProvider is said to be <em>sequential</em>, if it guarantees that
 * listeners are notified in the order in which they were registered for a
 * certain listener class. EventProviders report this property with
 * {@link #isSequential()}. Whether an EventProvider actually is sequential
 * depends on its implementation of the dispatch method and the currently used
 * ListenerFilter (see section below). For example, a provider which notifies
 * each listener within a separate thread is not sequential.
 * </p>
 *
 * <h2>Aborting Event Delegation</h2>
 * <p>
 * As stated above, event delegation can generally not be interrupted by
 * throwing exceptions. Instead, listeners can modify the passed Event instance
 * and set its {@link Event#setHandled(boolean) handled} property to
 * <code>true</code>. Before notifying the next listener, the EventProvider
 * queries the {@link Event#isHandled() isHandled} property of the currently
 * processed event. If it is handled, event delegation stops and no further
 * listeners are notified.
 * </p>
 *
 * <p>
 * The behavior of aborting event delegation on non-sequential EventProviders is
 * undefined.
 * </p>
 *
 * @param <S> The type of the ListenerStore this EventProvider uses.
 * @author Simon Taddiken
 * @since 1.0.0
 * @version 2.0.0
 */
public interface EventProvider<S extends ListenerStore> extends AutoCloseable {

    public static EventProviderConfigurator configure() {
        return new ConfiguratorImpl();
    }

    /**
     * The default {@link ExceptionCallback} which prints some information about
     * the occurred error to the standard output. The exact format is not
     * specified.
     */
    public static final ExceptionCallback DEFAULT_HANDLER = (e, l, ev) -> {
        System.err.println(
                "Listener threw an exception while being notified\n" +
                        "Details\n" +
                        "    Listener: " + l + "\n" +
                        "    Event: " + ev + "\n" +
                        "    Message: " + e.getMessage() + "\n" +
                        "    Current Thread: " + Thread.currentThread().getName() + "\n" +
                        "    Stacktrace: "
                );
        e.printStackTrace();
    };

    public S listeners();

    /**
     * Notifies all listeners of a certain kind about an occurred event. If this
     * provider is not ready for dispatching as determined by
     * {@link #canDispatch()}, this method returns immediately without doing
     * anything. This method will stop notifying further listeners if the passed
     * event has been marked 'handled' using {@link Event#setHandled(boolean)}.
     *
     * <p>
     * Consider an {@code UserListener} interface:
     * </p>
     *
     * <pre>
     * public interface UserListener extends Listener {
     *     public void userAdded(UserEvent e);
     *
     *     public void userDeleted(UserEvent e);
     * }
     * </pre>
     *
     * Notifying all registered UserListeners about an added user is as easy as
     * calling
     *
     * <pre>
     * eventProvider.dispatchEvent(event, UserListener::userAdded)
     * </pre>
     *
     * <p>
     * This method uses the global {@link ExceptionCallback} provided to
     * {@link #setExceptionCallback(ExceptionCallback)} or an default instance
     * if none has been explicitly set.
     * </p>
     *
     * <p>
     * Note on concurrency: This method operates on a copy of the list of
     * targeted listeners. This allows you to add/remove listeners from within a
     * listening method.
     * </p>
     *
     * <p>
     * Please note that neither parameter to this method must be null.
     * </p>
     *
     * @param <L> Type of the listeners which will be notified.
     * @param <E> Type of the event which will be passed to a listener.
     * @param event The occurred event which shall be passed to each listener.
     * @param bc Function to delegate the event to the specific callback method
     *            of the listener.
     * @throws IllegalArgumentException If any of the passed arguments is
     *             <code>null</code>.
     * @throws AbortionException If a listener threw an AbortionException.
     */
    public <L extends Listener, E extends Event<?, L>> void dispatch(
            E event, BiConsumer<L, E> bc);

    /**
     * Notifies all listeners of a certain kind about an occurred event with
     * explicit error handling. If this provider is not ready for dispatching as
     * determined by {@link #canDispatch()}, this method returns immediately
     * without doing anything. This method will stop notifying further listeners
     * if the passed event has been marked 'handled' using
     * {@link Event#setHandled(boolean)}.
     *
     * <p>
     * Consider an {@code UserListener} interface:
     * </p>
     *
     * <pre>
     * public interface UserListener extends Listener {
     *     public void userAdded(UserEvent e);
     *
     *     public void userDeleted(UserEvent e);
     * }
     * </pre>
     *
     * Notifying all registered UserListeners about an added user is as easy as
     * calling
     *
     * <pre>
     * final ExceptionCallback callBack = new EceptionCallback() { ... };
     * eventProvider.dispatchEvent(event, UserListener::userAdded, callBack);
     * </pre>
     *
     * <p>
     * The {@link ExceptionCallback} gets notified when any of the listeners
     * throws an unexpected exception. If the exception handler itself throws an
     * exception, it will be ignored. The callback provided to this method takes
     * precedence over the global callback provided by
     * {@link #setExceptionCallback(ExceptionCallback)}.
     * </p>
     *
     * <p>
     * Note on concurrency: This method operates on a copy of the list of
     * targeted listeners. This allows you to add/remove listeners from within a
     * listening method.
     * </p>
     *
     * <p>
     * Please note that neither parameter to this method must be null.
     * </p>
     *
     * @param <L> Type of the listeners which will be notified.
     * @param <E> Type of the event which will be passed to a listener.
     * @param event The occurred event which shall be passed to each listener.
     * @param bc Function to delegate the event to the specific callback method
     *            of the listener.
     * @param ec Callback to be notified when any of the listeners throws an
     *            exception.
     * @throws IllegalArgumentException If any of the passed arguments is
     *             <code>null</code>.
     * @throws AbortionException If a listener threw an AbortionException.
     */
    public <L extends Listener, E extends Event<?, L>> void dispatch(
            E event, BiConsumer<L, E> bc, ExceptionCallback ec);

    /**
     * Dispatches a {@link DefaultTargetEvent} with the logic of
     * {@link #dispatch(Event, BiConsumer)}.
     *
     * @param <L> Type of the listeners which will be notified.
     * @param <E> Type of the event which will be passed to a listener.
     * @param event The occurred event which shall be passed to each listener.
     * @throws IllegalArgumentException If any of the passed arguments is
     *             <code>null</code>.
     * @throws AbortionException If a listener threw an AbortionException.
     */
    public default <L extends Listener, E extends DefaultTargetEvent<?, E, L>> void dispatch(
            E event) {
        dispatch(event, event.getTarget());
    }

    /**
     * Dispatches a {@link DefaultTargetEvent} with the logic of
     * {@link #dispatch(Event, BiConsumer, ExceptionCallback)}.
     *
     * @param <L> Type of the listeners which will be notified.
     * @param <E> Type of the event which will be passed to a listener.
     * @param event The occurred event which shall be passed to each listener.
     * @param ec Callback to be notified when any of the listeners throws an
     *            exception.
     * @throws IllegalArgumentException If any of the passed arguments is
     *             <code>null</code>.
     * @throws AbortionException If a listener threw an AbortionException.
     */
    public default <L extends Listener, E extends DefaultTargetEvent<?, E, L>> void dispatch(
            E event, ExceptionCallback ec) {
        dispatch(event, event.getTarget(), ec);
    }

    /**
     * Gets whether this EventProvider is ready for dispatching.
     *
     * @return Whether further events can be dispatched using
     *         {@link #dispatch(Event, BiConsumer, ExceptionCallback) dispatch}
     */
    public boolean canDispatch();

    /**
     * Sets the default {@link ExceptionCallback} which will be notified about
     * exceptions when dispatching events without explicitly specifying an
     * ExceptionCallback. The ExceptionCallback which is installed by default
     * simply prints the stack traces to the error console.
     *
     * <p>
     * You can reset the ExceptionCallback to the default handler by providing
     * <code>null</code> as parameter.
     * </p>
     *
     * @param ec The ExceptionCallback for handling event handler exceptions, or
     *            <code>null</code> to use the default behavior.
     */
    public void setExceptionCallback(ExceptionCallback ec);

    /**
     * Returns whether this EventProvider is sequential, which means it strictly
     * notifies listeners in the order in which they were registered for a
     * certain event.
     *
     * <p>
     * <b>Note:</b> Implementors must obey the result of the
     * {@link ListenerFilter#isSequential() isSequential} property of the
     * currently used ListenerFilter. If the filter is not sequential, this
     * provider won't be either.
     * </p>
     *
     * @return Whether this instance is sequential.
     */
    public boolean isSequential();

    /**
     * Closes this EventProvider and removes all registered listeners. Depending
     * on the actual implementation, the EventProvider might not be able to
     * dispatch further events after closing. On some implementations closing
     * might have no additional effect.
     */
    @Override
    public void close();
}