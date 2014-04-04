package de.skuzzle.jeve;

import java.util.Collection;
import java.util.EventListener;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;


/**
 * <p>EventProviders are used to fire swing-style events. Listeners can be registered and
 * removed for a certain event. A class which implements multiple listener interfaces
 * can be registered for each listener type independently.</p>
 * 
 * <p>The strategy on how events are dispatched is implementation dependent and totally
 * transparent to client code which uses the EventProvider. You can obtain different 
 * implementations using the static factory methods or you may extend 
 * {@link AbstractEventProvider} to provide your own customized provider.</p>
 * 
 * <p>Unless stated otherwise, all EventProvider instances obtained from static factory
 * methods are thread-safe.</p>
 * 
 * @author Simon
 */
public interface EventProvider extends AutoCloseable {

    /**
     * Creates a new {@link EventProvider} which fires events sequentially in the thread
     * which calls {@link EventProvider#dispatch(Class, Event, BiConsumer)}.
     * 
     * <p>Closing the {@link EventProvider} returned by this method will have no 
     * effect.</p>
     * 
     * @return A new EventProvider instance.
     */
    public static EventProvider newDefaultEventProvider() {
        return new SynchronousEventProvider();
    }
    
    
    
    /**
     * Creates a new {@link EventProvider} which fires each event in a different thread.
     * By default, the returned {@link EventProvider} uses a single thread executor 
     * service.
     * 
     * <p>When closing the returned {@link EventProvider}, its internal 
     * {@link ExecutorService} instance will be shut down. Its not possible to reuse the
     * provider after closing it.</p>
     * 
     * @return A new EventProvider instance.
     */
    public static EventProvider newAsynchronousEventProvider() {
        return new AsynchronousEventProvider();
    }
    
    
    
    /**
     * Creates a new {@link EventProvider} which fires each event in a different thread.
     * The created provider will use the given {@link ExecutorService} to fire the events
     * asynchronously.
     * 
     * <p>Even when using multiple threads to dispatch events, the returned EventProvider 
     * will only use one thread for one dispatch action. That means that for each call to
     * {@link #dispatch(Class, Event, BiConsumer, ExceptionCallback) dispatch}, all 
     * targeted listeners are notified within the same thread. This ensures notification
     * in the order the listeners have been added.</p>
     * 
     * <p>If you require an EventListener which notifies each listener in a different 
     * thread, you need to create your own sub class of {@link AbstractEventProvider}.</p>
     * 
     * <p>When closing the returned {@link EventProvider}, the passed 
     * {@link ExecutorService} instance will be shut down. Its not possible to reuse the
     * provider after closing it.</p>
     * 
     * @param dispatcher The ExecutorService to use.
     * @return A new EventProvider instance.
     */
    public static EventProvider newAsynchronousEventProvider(ExecutorService dispatcher) {
        return new AsynchronousEventProvider(dispatcher);
    }
    
    
    
    /**
     * Create a new {@link EventProvider} which dispatches all events in the AWT event 
     * thread and waits (blocks current thread) after dispatching until all listeners
     * have been notified.
     * 
     * <p>Closing the {@link EventProvider} returned by this method will have no 
     * effect.</p>
     * 
     * @return A new EventProvider instance.
     */
    public static EventProvider newWaitingAWTEventProvider() {
        return new AWTEventProvider(true);
    }
    
    
    
    /**
     * Creates a new {@link EventProvider} which dispatches all events in the AWT event
     * thread. Dispatching with this EventProvider will return immediately and dispatching
     * of an event will be scheduled to be run later by the AWT event thread.
     * 
     * <p>Closing the {@link EventProvider} returned by this method will have no 
     * effect.</p>
     * 
     * @return A new EventProvider instance.
     */
    public static EventProvider newAsynchronousAWTEventProvider() {
        return new AWTEventProvider(false);
    }
    
    
    
    
    /**
     * Adds a listener which will be notified for every event represented by the
     * given listener class.
     * 
     * @param <T> Type of the listener to add. 
     * @param listenerClass The class representing the event(s) to listen on.
     * @param listener The listener to add.
     * @throws NullPointerException If either listenerClass or listener is 
     *          <code>null</code>.
     */
    public <T extends EventListener> void addListener(Class<T> listenerClass, 
            T listener);
    
    /**
     * Removes a listener. It will only be removed for the specified listener class and
     * can thus still be registered with this event provider if it was added for
     * further listener classes. The listener will no longer receive events represented
     * by the given listener class.
     * 
     * @param <T> Type of the listener to remove.
     * @param listenerClass The class representing the event(s) for which the listener
     *          should be removed.
     * @param listener The listener to remove.
     */
    public <T extends EventListener> void removeListener(Class<T> listenerClass, 
            T listener);
    
    /**
     * Gets all listeners that have been registered using 
     * {@link #addListener(Class, EventListener)} for the given listener class.
     * 
     * @param <T> Type of the listeners to return.
     * @param listenerClass The class representing the event for which the listeners
     *          should be retrieved.
     * @return A collection of listeners that should be notified about the event 
     *          represented by the given listener class.
     * @throws NullPointerException If listenerClass is <code>null</code>.          
     */
    public <T extends EventListener> Collection<T> getListeners(Class<T> listenerClass);
    
    /**
     * Removes all listeners which have been registered for the provided listener class.
     * @param <T> Type of the listeners that should be removed.
     * @param listenerClass The class representing the event for which the listeners 
     *          should be removed
     */
    public <T extends EventListener> void clearAllListeners(Class<T> listenerClass);
    
    /**
     * Removes all registered listeners from this EventProvider.
     */
    public void clearAllListeners();
    
    /**
     * Notifies all listeners of a certain kind about an occurred event. If this provider 
     * is not ready for dispatching as determined by {@link #canDispatch()}, this method 
     * returns immediately without doing anything. This method will stop notifying further
     * listeners if the passed event has been marked 'handled' using 
     * {@link Event#setHandled(boolean)}.
     * 
     * <p>If a notified listener implements {@link OneTimeEventListener} and its 
     * {@link OneTimeEventListener#workDone(EventProvider) workDone} method returns true, 
     * the listener will be removed from this EventProvider.</p>
     * 
     *  
     * <p>Consider an <tt>UserListener</tt> interface:</p>
     * <pre>
     * public interface UserListener {
     *     public void userAdded(UserEvent e);
     *     
     *     public void userDeleted(UserEvent e);
     * }
     * </pre>
     * 
     * Notifying all registered UserListeners about an added user is as easy as calling
     * <pre>
     * eventProvider.dispatchEvent(UserListener.class, event, UserListener::userAdded)
     * </pre>
     * 
     * This method ignores exceptions thrown by notified listeners. If an exception 
     * occurs, its stacktrace will be printed and the next listener will be notified.
     * 
     * @param <L> Type of the listeners which will be notified.
     * @param <E> Type of the event which will be passed to a listener.
     * @param listenerClass The kind of listeners to notify.
     * @param event The occurred event which shall be passed to each listener.
     * @param bc Function to delegate the event to the specific callback method of the 
     *          listener.
     */
    public default <L extends EventListener, E extends Event<?>> void dispatch(
            Class<L> listenerClass, E event, BiConsumer<L, E> bc) {
        this.dispatch(listenerClass, event, bc, e -> e.printStackTrace());
    }
    
    /**
     * Notifies all listeners of a certain kind about an occurred event. If this provider 
     * is not ready for dispatching as determined by {@link #canDispatch()}, this method 
     * returns immediately without doing anything. This method will stop notifying further
     * listeners if the passed event has been marked 'handled' using 
     * {@link Event#setHandled(boolean)}.
     * 
     * <p>If a notified listener implements {@link OneTimeEventListener} and its 
     * {@link OneTimeEventListener#workDone(EventProvider) workDone} method returns true, 
     * the listener will be removed from this EventProvider.</p>
     *  
     * <p>Consider an <tt>UserListener</tt> interface:</p>
     * <pre>
     * public interface UserListener {
     *     public void userAdded(UserEvent e);
     *     
     *     public void userDeleted(UserEvent e);
     * }
     * </pre>
     * 
     * Notifying all registered UserListeners about an added user is as easy as calling
     * <pre>
     * eventProvider.dispatchEvent(UserListener.class, event, UserListener::userAdded, 
     *      e -&gt; logger.error(e));
     * </pre>
     * 
     * The {@link ExceptionCallback} gets notified when any of the listeners throws an
     * unexpected exception. If the exception handler itself throws an exception, it will
     * be ignored.
     * 
     * @param <L> Type of the listeners which will be notified.
     * @param <E> Type of the event which will be passed to a listener.
     * @param listenerClass The kind of listeners to notify.
     * @param event The occurred event which shall be passed to each listener.
     * @param bc Function to delegate the event to the specific callback method of the 
     *          listener.
     * @param ec Callback to be notified when any of the listeners throws an exception.
     */
    public <L extends EventListener, E extends Event<?>> void dispatch(
            Class<L> listenerClass, E event, BiConsumer<L, E> bc, ExceptionCallback ec);
    
    /**
     * Gets whether this EventProvider is ready for dispatching.
     * 
     * @return Whether further events can be dispatched using 
     *          {@link #dispatch(Class, Event, BiConsumer, ExceptionCallback) dispatch}
     */
    public boolean canDispatch();
    
    /**
     * Closes this EventProvider. Depending on its implementation, it might not be 
     * able to dispatch further events after disposing. On some implementations closing
     * might have no effect.
     */
    @Override
    public void close();
}