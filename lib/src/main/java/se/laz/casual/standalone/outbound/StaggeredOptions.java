package se.laz.casual.standalone.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.logging.Logger;

public class StaggeredOptions
{
    private static final Logger LOG = Logger.getLogger(StaggeredOptions.class.getName());
    private final Duration initialDelay;
    private Duration subsequentDelay;
    private int staggerFactor;
    private boolean initial = true;

    private StaggeredOptions(Duration initialDelay, Duration subsequentDelay, int staggerFactor)
    {
        this.initialDelay = initialDelay;
        this.subsequentDelay = subsequentDelay;
        this.staggerFactor = staggerFactor;
    }

    public static StaggeredOptions of(Duration initialDelay, Duration subsequentDelay, int staggerFactor)
    {
        Objects.requireNonNull(initialDelay, "initialDelay can not be null");
        Objects.requireNonNull(subsequentDelay, "subsequentDelay can not be null");
        if(staggerFactor <= 0)
        {
            throw new IllegalArgumentException("staggerFactor equal to or below zero is not supported");
        }
        return new StaggeredOptions(initialDelay, subsequentDelay,staggerFactor);
    }

    public Duration getNext()
    {
        if(initial)
        {
            initial = false;
            return initialDelay;
        }
        subsequentDelay = Duration.ofMillis(subsequentDelay.toMillis() * staggerFactor);
        LOG.finest(() -> " delay: " + subsequentDelay);
        return subsequentDelay;
    }

}
