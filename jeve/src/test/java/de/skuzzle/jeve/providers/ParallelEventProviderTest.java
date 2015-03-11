package de.skuzzle.jeve.providers;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import de.skuzzle.jeve.ListenerStore;

public class ParallelEventProviderTest extends
        AbstractExecutorAwareEventProviderTest<ParallelEventProvider<ListenerStore>> {

    @Override
    protected ParallelEventProvider<ListenerStore> createSubject(ListenerStore store) {
        return new ParallelEventProvider<ListenerStore>(store, this.executor);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorExecutorNull() {
        new ParallelEventProvider<>(Mockito.mock(ListenerStore.class), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetExecutorServiceNull() {
        this.subject.setExecutorService(null);
    }

    @Test
    public void testIsSequential() throws Exception {
        Mockito.when(this.store.isSequential()).thenReturn(true);
        Assert.assertFalse(this.subject.isSequential());
    }

    @Test
    public void testCanNotDispatch() {
        Mockito.when(this.executor.isTerminated()).thenReturn(true);
        this.subject.dispatch(this.event, SampleListener::onEvent, this.ec);
        Mockito.verify(this.store, Mockito.never()).get(Mockito.any());
    }

    @Test
    @Override
    public void testDispatch() throws Exception {
        final SampleListener listener2 = Mockito.mock(SampleListener.class);
        Mockito.when(this.store.get(SampleListener.class)).thenReturn(
                Arrays.asList(this.listener, listener2).stream());
        Mockito.when(this.event.getListenerClass()).thenReturn(SampleListener.class);
        this.subject.dispatch(this.event, SampleListener::onEvent);
        Mockito.verify(this.executor, Mockito.times(2)).execute(Mockito.any());
    }

    @Test
    public void testDispatchExceptionFromExecutor() {
        final RuntimeException e = new RuntimeException();
        Mockito.doThrow(e).when(this.executor).execute(Mockito.any());
        final SampleListener listener2 = Mockito.mock(SampleListener.class);
        Mockito.when(this.store.get(SampleListener.class)).thenReturn(
                Arrays.asList(this.listener, listener2).stream());
        Mockito.when(this.event.getListenerClass()).thenReturn(SampleListener.class);

        this.subject.dispatch(this.event, SampleListener::onEvent, this.ec);

        Mockito.verify(this.ec).exception(this.subject, e, this.listener, this.event);
        Mockito.verify(this.ec).exception(this.subject, e, listener2, this.event);
    }
}