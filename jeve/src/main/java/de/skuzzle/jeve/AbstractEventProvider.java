package de.skuzzle.jeve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;


/**
 * Implementation of basic {@link EventProvider} methods. All implementations are 
 * thread-safe.
 * 
 * <p>Note about thread safe interface: All publicly accessible methods are thread safe,
 * internal and protected helper methods are not thread safe.</p>
 * 
 * @author Simon Taddiken
 * @since 1.0.0
 */
public abstract class AbstractEventProvider implements EventProvider {
            
    /**
     * Copies a list of listeners into a new list, casting each element to the target
     * listener type.
     * 
     * @param <T> Type of listeners in the result.
     * @param listeners List to copy.
     * @param listenerClass Target listener type.
     * @return A new typed list of listeners.
     */
    private static <T extends Listener> List<T> copyList(List<Object> listeners, 
            Class<T> listenerClass) {
        final List<T> result = new ArrayList<>(listeners.size());
        listeners.forEach(l -> result.add(listenerClass.cast(l)));
        return result;
    }
    
    
    
    /** Holds the listener classes mapped to listener instances */
    protected final Map<Class<? extends Listener>, List<Object>> listeners;
    
    /** Default callback to handle event handler exceptions */
    protected ExceptionCallback exceptionHandler;
    
    
    
    /**
     * Creates a new {@link AbstractEventProvider}.
     */
    public AbstractEventProvider() {
        this.listeners = new HashMap<>();
        this.exceptionHandler = DEFAULT_HANDLER;
    }

    
    
    @Override
    public <T extends Listener> Listeners<T> getListeners(Class<T> listenerClass) {
        if (listenerClass == null) {
            throw new IllegalArgumentException("listenerClass");
        }
        synchronized (this.listeners) {
            final List<Object> listeners = this.listeners.get(listenerClass);
            if (listeners == null) {
                return Listeners.empty(this, listenerClass);
            }
            return new Listeners<T>(
                copyList(listeners, listenerClass), listenerClass, this);
        }
    }
    
    
    
    @Override
    public <T extends Listener> void clearAllListeners(Class<T> listenerClass) {
        synchronized (this.listeners) {
            final List<Object> listeners = this.listeners.get(listenerClass);
            if (listeners != null) {
                final Iterator<Object> it = listeners.iterator();
                while (it.hasNext()) {
                    this.removeInternal(listenerClass, it);
                }
                this.listeners.remove(listenerClass);
            }
        }
    }
    
    
    
    @Override
    public void clearAllListeners() {
        synchronized (this.listeners) {
            this.listeners.forEach((k, v) -> {
                final Iterator<Object> it = v.iterator();
                while (it.hasNext()) {
                    removeInternal(k, it);
                }
            });
            this.listeners.clear();
        }
    }
    
    
    
    /**
     * Internal method for removing a single listener and notifying it about the
     * removal. Prior to calling this method, the passed iterators 
     * {@link Iterator#hasNext() hasNext} method must hold <code>true</code>.
     * 
     * @param <T> Type of the listener to remove
     * @param listenerClass The class of the listener to remove.
     * @param it Iterator which provides the next listener to remove.
     */
    protected <T extends Listener> void removeInternal(Class<T> listenerClass, 
            Iterator<Object> it) {
        final Object next = it.next();
        final T listener = listenerClass.cast(next);
        it.remove();
        try {
            final RegistrationEvent e = new RegistrationEvent(this, listenerClass);
            listener.onUnregister(e);
        } catch (Exception e) {
            this.handleException(this.exceptionHandler, e, listener, null);
        }
    }
    

    
    @Override
    public <T extends Listener> void addListener(Class<T> listenerClass, T listener) {
        if (listenerClass == null) {
            throw new IllegalArgumentException("listenerClass is null");
        } else if (listener == null) {
            throw new IllegalArgumentException("listener is null");
        }
        synchronized (this.listeners) {
            List<Object> listeners = this.listeners.get(listenerClass);
            if (listeners == null) {
                listeners = new LinkedList<>();
                this.listeners.put(listenerClass, listeners);
            }
            listeners.add(listener);
        }
        try {
            final RegistrationEvent e = new RegistrationEvent(this, listenerClass);
            listener.onRegister(e);
        } catch (Exception e) {
            this.handleException(this.exceptionHandler, e, listener, null);
        }
    }


    
    @Override
    public <T extends Listener> void removeListener(Class<T> listenerClass, 
            T listener) {
        if (listenerClass == null || listener == null) {
            return;
        }
        synchronized (this.listeners) {
            final List<Object> listeners = this.listeners.get(listenerClass);
            if (listeners == null) {
                return;
            }
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                this.listeners.remove(listenerClass);
            }
        }
        try {
            final RegistrationEvent e = new RegistrationEvent(this, listenerClass);
            listener.onUnregister(e);
        } catch (Exception e) {
            this.handleException(this.exceptionHandler, e, listener, null);
        }
    }
    
    
    
    @Override
    public <L extends Listener, E extends Event<?>> void dispatch(
            Class<L> listenerClass, E event, BiConsumer<L, E> bc) {
        this.dispatch(listenerClass, event, bc, this.exceptionHandler);
    }
    
    
    
    @Override
    public <L extends Listener, E extends Event<?>> void dispatch(
            Class<L> listenerClass, E event, BiConsumer<L, E> bc, ExceptionCallback ec) {
        this.checkDispatchArgs(listenerClass, event, bc, ec);
        if (this.canDispatch()) {
            this.notifyListeners(listenerClass, event, bc, ec);
        }
    }
    
    
    
    @Override
    public <L extends Listener, E extends Event<?>> void dispatch(
            Class<L> listenerClass, E event, BiFunction<L, E, Boolean> bf) {
        this.dispatch(listenerClass, event, bf, this.exceptionHandler);
    }
    
    
    
    @Override
    public <L extends Listener, E extends Event<?>> void dispatch(
            Class<L> listenerClass, E event, BiFunction<L, E, Boolean> bf,
            ExceptionCallback ec) {
        
        this.checkDispatchArgs(listenerClass, event, bf, ec);
        if (this.canDispatch()) {
            this.notifyListeners(listenerClass, event, bf, ec);
        }
    }
    
    
    
    /**
     * Helper method which serves for throwing {@link IllegalArgumentException} if any of
     * the passed arguments is null.
     * 
     * @param <L> Type of the listeners which will be notified.
     * @param <E> Type of the event which will be passed to a listener.
     * @param listenerClass The listener class.
     * @param event The event.
     * @param bc The method to call on the listener
     * @param ec The ExceptionCallback
     * @throws IllegalArgumentException If any argument is <code>null</code>.
     */
    protected <L extends Listener, E extends Event<?>> void checkDispatchArgs(
            Class<L> listenerClass, E event, Object bc, ExceptionCallback ec) {
        if (listenerClass == null) {
            throw new IllegalArgumentException("listenerClass is null");
        } else if (event == null) {
            throw new IllegalArgumentException("event is null");
        } else if (bc == null) {
            throw new IllegalArgumentException("bc is null");
        } else if (ec == null) {
            throw new IllegalArgumentException("ec is null");
        }
    }
    
    
    
    @Override
    public synchronized void setExceptionCallback(ExceptionCallback callBack) {
        if (callBack == null) {
            callBack = DEFAULT_HANDLER;
        }
        this.exceptionHandler = callBack;
    }
    
    
    
    /**
     * Notifies all listeners registered for the provided class with the provided event.
     * This method is failure tolerant and will continue notifying listeners even if one
     * of them threw an exception. Exceptions are passed to the provided 
     * {@link ExceptionCallback}.
     * 
     * <p>This method does not check whether this provider is ready for dispatching and
     * might thus throw an exception when trying to dispatch an event while the provider
     * is not ready.</p>
     * 
     * @param <L> Type of the listeners which will be notified.
     * @param <E> Type of the event which will be passed to a listener.
     * @param listenerClass The class of listeners that should be notified.
     * @param event The event to pass to each listener.
     * @param bc The method of the listener to call.
     * @param ec The callback which gets notified about exceptions.
     */
    @SuppressWarnings("deprecation")
    protected <L extends Listener, E extends Event<?>> void notifyListeners(
            Class<L> listenerClass, E event, BiConsumer<L, E> bc, ExceptionCallback ec) {
        // HINT: getListeners is thread safe
        final Listeners<L> listeners = this.getListeners(listenerClass);
        for (L listener : listeners) {
            // TODO: remove as of deprecation
            if (event.isHandled()) {
                return;
            }
            this.notifySingle(listenerClass, listener, event, bc, ec);
        }
    }
   
    
    
    /**
     * Notifies a single listener and internally handles exceptions using the 
     * {@link ExceptionCallback}.
     * 
     * @param <L> Type of the listeners which will be notified.
     * @param <E> Type of the event which will be passed to a listener.
     * @param listenerClass The class of listeners that should be notified.
     * @param listener The single listener to notify.
     * @param event The event to pass to the listener.
     * @param bc The method of the listener to call.
     * @param ec The callback which gets notified about exceptions.
     * @since 1.1.0
     */
    @SuppressWarnings("deprecation")
    protected <L extends Listener, E extends Event<?>> void notifySingle(
            Class<L> listenerClass, L listener, E event, BiConsumer<L, E> bc, 
            ExceptionCallback ec) {
        try {
            bc.accept(listener, event);
            
            // Compatibility to v1.0.0
            if (listener.workDone(this)) {
                this.removeListener(listenerClass, listener);
            }
        } catch (RuntimeException e) {
            this.handleException(ec, e, listener, event);
        }
    }
    
    
    
    
    @SuppressWarnings("deprecation")
    protected <L extends Listener, E extends Event<?>> boolean notifyListeners(
            Class<L> listenerClass, E event, BiFunction<L, E, Boolean> bf, 
            ExceptionCallback ec) {
        // HINT: getListeners is thread safe
        final Listeners<L> listeners = this.getListeners(listenerClass);
        for (L listener : listeners) {
            // TODO: remove as of deprecation
            if (event.isHandled()) {
                return false;
            }
            
            if (!this.notifySingle(listenerClass, listener, event, bf, ec)) {
                return false;
            }
        }
        return true;
    }
    
    
    
    @SuppressWarnings("deprecation")
    protected <L extends Listener, E extends Event<?>> boolean notifySingle(
            Class<L> listenerClass, L listener, E event, BiFunction<L, E, Boolean> bf, 
            ExceptionCallback ec) {
        boolean result = true;
        try {
            result = bf.apply(listener, event);
            
            // Compatibility to v1.0.0
            if (listener.workDone(this)) {
                this.removeListener(listenerClass, listener);
            }
        } catch (RuntimeException e) {
            this.handleException(ec, e, listener, event);
        }
        return result;
    }
    
    
    
    /**
     * Internal method for notifying the {@link ExceptionCallback}. This method swallows
     * every error raised by the passed exception callback.
     * 
     * @param ec The ExceptionCallback to handle the exception.
     * @param e The occurred exception.
     * @param listener The listener which caused the exception.
     * @param ev The event which is currently being dispatched.
     */
    protected void handleException(ExceptionCallback ec, Exception e, Listener listener, 
            Event<?> ev) {
        try {
            ec.exception(e, listener, ev);
        } catch (Exception ignore) {
            ignore.printStackTrace();
            // where is your god now?
        }
    }
    
    
    
    @Override
    public void close() {
        this.clearAllListeners();
    }
    
    
    
    @Override
    public String toString() {
        return this.listeners.toString();
    }
}