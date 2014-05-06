package de.skuzzle.jeve;

import java.util.Arrays;
import java.util.Collection;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import de.skuzzle.jeve.EventProvider;
import de.skuzzle.jeve.util.EventProviderFactory;


/**
 * Runs all basic tests for all default provided event providers.
 * 
 * @author Simon Taddiken
 */
@RunWith(Parameterized.class)
public class BasicEventProviderTest extends EventProviderTestBase {

    /**
     * Parameterizes the test instances.
     * @return Collection of parameters for the constructor of 
     *          {@link EventProviderTestBase}.
     */
    @Parameters
    public final static Collection<Object[]> getParameters() {
        return Arrays.asList(
            new EventProviderFactory[] { EventProvider::newDefaultEventProvider },
            new EventProviderFactory[] { EventProvider::newParallelEventProvider },
            new EventProviderFactory[] { EventProvider::newAsynchronousEventProvider }
        );
    }
    
    
    
    /**
     * Creates new BasicEventProviderTests
     * 
     * @param factory Factory to create a single provider
     */
    public BasicEventProviderTest(EventProviderFactory factory) {
        super(factory);
    }
}