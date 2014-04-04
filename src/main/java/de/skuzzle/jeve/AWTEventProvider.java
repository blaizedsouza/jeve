package de.skuzzle.jeve;

import java.lang.reflect.InvocationTargetException;
import java.util.EventListener;
import java.util.function.BiConsumer;

import javax.swing.SwingUtilities;

/**
 * {@link EventProvider} implementation that dispatches all events in the AWT event 
 * thread.
 * 
 * @author Simon
 */
class AWTEventProvider extends AbstractEventProvider {

    private final boolean invokeNow;
    
    /**
     * Creates a new AwtEventProvider. You can decide whether events shall be 
     * scheduled for later execution via 
     * {@link SwingUtilities#invokeLater(Runnable)} or your current thread should wait
     * until all listeners are notified (uses 
     * {@link SwingUtilities#invokeAndWait(Runnable)} to run notify the listeners).
     * 
     * @param invokeNow If <code>true</code>, {@link #dispatch(Class, Event, BiConsumer, ExceptionCallback) dispatch} 
     *      uses <code>invokeAndWait</code>, otherwise <code>invokeLater</code>.
     */
    public AWTEventProvider(boolean invokeNow) {
        this.invokeNow = invokeNow;
    }
    
    
    
    @Override
    public <L extends EventListener, E extends Event<?>> void dispatch(
            final Class<L> listenerClass, final E event, final BiConsumer<L, E> bc, 
            ExceptionCallback ec) {

        if (this.invokeNow) {
            if (SwingUtilities.isEventDispatchThread()) {
                notifyListeners(listenerClass, event, bc, ec);
            } else {
                try {
                    SwingUtilities.invokeAndWait(
                            () -> notifyListeners(listenerClass, event, bc, ec));
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            SwingUtilities.invokeLater(() -> notifyListeners(listenerClass, event, bc, ec));
        }
    }
    
    

    @Override
    public boolean canDispatch() {
        return true;
    }

    
    
    @Override
    public void close() {}
}
