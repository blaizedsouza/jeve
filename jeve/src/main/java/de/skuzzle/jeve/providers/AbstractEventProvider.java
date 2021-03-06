package de.skuzzle.jeve.providers;

import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import de.skuzzle.jeve.AbortionException;
import de.skuzzle.jeve.DefaultDispatchable;
import de.skuzzle.jeve.Event;
import de.skuzzle.jeve.EventProvider;
import de.skuzzle.jeve.ExceptionCallback;
import de.skuzzle.jeve.ExceptionCallbacks;
import de.skuzzle.jeve.Listener;
import de.skuzzle.jeve.ListenerSource;
import de.skuzzle.jeve.invoke.EventInvocation;
import de.skuzzle.jeve.invoke.EventInvocationFactory;

/**
 * Implementation of basic {@link EventProvider} methods. All implementations
 * are thread-safe.
 *
 * <p>
 * Note about thread safe interface: All publicly accessible methods are thread
 * safe, internal and protected helper methods are not thread safe.
 * </p>
 *
 * @author Simon Taddiken
 * @since 1.0.0
 */
public abstract class AbstractEventProvider implements EventProvider {

    /** The listener store associated with this provider */
    private final ListenerSource source;

    /** Callback to handle event handler exceptions. */
    protected ExceptionCallback exceptionHandler;

    /**
     * Factory that creates {@link EventInvocation} objects for notifying a
     * single listener.
     *
     * @since 4.0.0
     */
    protected EventInvocationFactory invocationFactory;

    /**
     * Whether the listener loop will break if the notifying thread is
     * interrupted.
     *
     * @since 4.0.0
     */
    protected boolean interruptAware;

    /**
     * Creates a new {@link AbstractEventProvider}.
     *
     * @param source Responsible for storing and retrieving listeners of this
     *            provider.
     */
    public AbstractEventProvider(ListenerSource source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        this.source = source;
        this.exceptionHandler = ExceptionCallbacks.ignore();
        this.invocationFactory = EventInvocation::of;
    }

    @Override
    public final ListenerSource getListenerSource() {
        return this.source;
    }

    @Override
    public <L extends Listener, E extends Event<?, L>> void dispatch(
            E event, BiConsumer<L, E> bc) {
        this.dispatch(event, bc, this.exceptionHandler);
    }

    @Override
    public <L extends Listener, E extends Event<?, L>> void dispatch(
            E event, BiConsumer<L, E> bc, ExceptionCallback ec) {
        checkDispatchArgs(event, bc, ec);
        if (canDispatch()) {
            notifyListeners(event, bc, ec);
        }
    }

    @Override
    public void dispatch(DefaultDispatchable event) {
        if (event == null) {
            throw new IllegalArgumentException("event is null");
        }
        event.defaultDispatch(this, this.exceptionHandler);
    }

    /**
     * Helper method which serves for throwing {@link IllegalArgumentException}
     * if any of the passed arguments is null.
     *
     * @param <L> Type of the listeners which will be notified.
     * @param <E> Type of the event which will be passed to a listener.
     * @param event The event.
     * @param bc The method to call on the listener
     * @param ec The ExceptionCallback
     * @throws IllegalArgumentException If any argument is <code>null</code>.
     */
    protected <L extends Listener, E extends Event<?, ?>> void checkDispatchArgs(
            E event, Object bc, ExceptionCallback ec) {
        if (event == null) {
            throw new IllegalArgumentException("event is null");
        } else if (bc == null) {
            throw new IllegalArgumentException("bc is null");
        } else if (ec == null) {
            throw new IllegalArgumentException("ec is null");
        }
    }

    @Override
    public synchronized void setExceptionCallback(ExceptionCallback callBack) {
        final ExceptionCallback ec;
        if (callBack == null) {
            ec = ExceptionCallbacks.ignore();
        } else {
            ec = callBack;
        }
        this.exceptionHandler = ec;
    }

    @Override
    public synchronized void setInvocationFactory(EventInvocationFactory factory) {
        final EventInvocationFactory f;
        if (factory == null) {
            f = EventInvocation::of;
        } else {
            f = factory;
        }
        this.invocationFactory = f;
    }

    @Override
    public void setInterruptAware(boolean interruptAware) {
        this.interruptAware = interruptAware;
    }

    /**
     * Notifies all listeners registered for the provided class with the
     * provided event. This method is failure tolerant and will continue
     * notifying listeners even if one of them threw an exception. Exceptions
     * are passed to the provided {@link ExceptionCallback}.
     *
     * <p>
     * This method does not check whether this provider is ready for dispatching
     * and might thus throw an exception when trying to dispatch an event while
     * the provider is not ready.
     * </p>
     *
     * @param <L> Type of the listeners which will be notified.
     * @param <E> Type of the event which will be passed to a listener.
     * @param event The event to pass to each listener.
     * @param bc The method of the listener to call.
     * @param ec The callback which gets notified about exceptions.
     * @throws AbortionException If the ExceptionCallback threw an
     *             AbortionException
     */
    protected <L extends Listener, E extends Event<?, L>> void notifyListeners(
            E event, BiConsumer<L, E> bc, ExceptionCallback ec) {
        notifyListeners(getListenerSource(), event, bc, ec);
    }

    /**
     * Notifies all listeners registered for the provided class with the
     * provided event. This method is failure tolerant and will continue
     * notifying listeners even if one of them threw an exception. Exceptions
     * are passed to the provided {@link ExceptionCallback}.
     *
     * <p>
     * This method does not check whether this provider is ready for dispatching
     * and might thus throw an exception when trying to dispatch an event while
     * the provider is not ready.
     * </p>
     *
     * @param <L> Type of the listeners which will be notified.
     * @param <E> Type of the event which will be passed to a listener.
     * @param source The source to get listeners from.
     * @param event The event to pass to each listener.
     * @param bc The method of the listener to call.
     * @param ec The callback which gets notified about exceptions.
     * @throws AbortionException If the ExceptionCallback threw an
     *             AbortionException
     */
    protected <L extends Listener, E extends Event<?, L>> void notifyListeners(
            ListenerSource source, E event, BiConsumer<L, E> bc, ExceptionCallback ec) {

        final Stream<L> listeners = source.get(event.getListenerClass());

        final Iterator<L> it = listeners.iterator();
        while (it.hasNext() && checkInterrupt()) {
            final L listener = it.next();
            notifySingle(listener, event, bc, ec);
        }
    }

    /**
     * Checks whether the next listener should be notified, taking the
     * {@link EventProvider#setInterruptAware(boolean) interrupt aware flag} and
     * the thread's interrupted state into account.
     *
     * @return Whether the next listener should be notified.
     * @since 4.0.0
     * @see EventProvider#setInterruptAware(boolean)
     */
    protected boolean checkInterrupt() {
        return !(this.interruptAware && !Thread.currentThread().isInterrupted());
    }

    /**
     * Notifies a single listener and internally handles exceptions using the
     * {@link ExceptionCallback}.
     *
     * @param <L> Type of the listeners which will be notified.
     * @param <E> Type of the event which will be passed to a listener.
     * @param listener The single listener to notify.
     * @param event The event to pass to the listener.
     * @param bc The method of the listener to call.
     * @param ec The callback which gets notified about exceptions.
     * @throws AbortionException If the {@code ExceptionCallback} or the
     *             {@code listener} threw an {@code AbortionException}.
     * @since 1.1.0
     */
    protected <L extends Listener, E extends Event<?, L>> void notifySingle(
            L listener, E event, BiConsumer<L, E> bc, ExceptionCallback ec) {

        createInvocation(listener, event, bc, ec).notifyListener();
    }

    /**
     * Creates a new {@link EventInvocation} object for dispatching the given
     * event to the given listener.
     *
     * @param <L> Type of the listeners which will be notified.
     * @param <E> Type of the event which will be passed to a listener.
     * @param listener The listener to notify.
     * @param event The event to pass to the listener
     * @param bc The method of the listener to call.
     * @param ec The callback which gets notified about exceptions.
     * @return The EventInvocation instance.
     * @since 3.0.0
     */
    protected <L extends Listener, E extends Event<?, L>> EventInvocation
            createInvocation(L listener, E event, BiConsumer<L, E> bc,
                    ExceptionCallback ec) {
        return this.invocationFactory.create(listener, event, bc, ec);
    }

    @Override
    public final boolean isSequential() {
        return this.source.isSequential() && isImplementationSequential();
    }

    /**
     * Whether this EventProvider implementation is sequential. The
     * {@link #isSequential()} method considers the result of this method and
     * the result of the current ListenerStore's
     * {@link ListenerSource#isSequential() isSequential} method for determining
     * whether dispatch events of this provider are sequential.
     *
     * @return Whether this EventProvider implementation is sequential.
     */
    protected abstract boolean isImplementationSequential();

    @Override
    public void close() {}

    @Override
    public String toString() {
        return this.source.toString();
    }
}
