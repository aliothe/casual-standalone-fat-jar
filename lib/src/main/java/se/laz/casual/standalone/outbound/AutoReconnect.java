package se.laz.casual.standalone.outbound;

import se.laz.casual.network.outbound.NetworkListener;

import javax.transaction.TransactionManager;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AutoReconnect implements Runnable
{
    private final ReconnectAble reconnectAble;
    private final String host;
    private final StaggeredOptions staggeredOptions;
    private final ScheduledExecutorService scheduledExecutorService;
    private final NetworkListener networkListener;
    private int port;
    private TransactionManager transactionManager;
    private final CallerProducer callerProducer;

    private AutoReconnect(ReconnectAble reconnectAble, String host, int port, TransactionManager transactionManager, StaggeredOptions staggeredOptions, NetworkListener networkListener, CallerProducer callerProducer)
    {
        this.reconnectAble = reconnectAble;
        this.host = host;
        this.port = port;
        this.transactionManager = transactionManager;
        this.staggeredOptions = staggeredOptions;
        this.networkListener = networkListener;
        this.callerProducer = callerProducer;
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.schedule(this, staggeredOptions.getNext().toMillis(), TimeUnit.MILLISECONDS);
    }

    public static AutoReconnect of(ReconnectAble reconnectAble, String host, int port, TransactionManager transactionManager, StaggeredOptions staggeredOptions, NetworkListener networkListener, CallerProducer callerProducer)
    {
        Objects.requireNonNull(reconnectAble, "reconnectAble can not be null");
        Objects.requireNonNull(host, "host can not be null");
        Objects.requireNonNull(transactionManager, "transactionManager can not be null");
        Objects.requireNonNull(staggeredOptions, "staggeredOptions can not be null");
        Objects.requireNonNull(callerProducer, "callerProducer can not be null");
        return new AutoReconnect(reconnectAble, host, port, transactionManager, staggeredOptions, networkListener, callerProducer);
    }

    @Override
    public void run()
    {
        try
        {
            Caller caller = callerProducer.createCaller(transactionManager, this.host, this.port, networkListener);
            reconnectAble.setCaller(caller);
        }
        catch(Exception e)
        {
            scheduledExecutorService.schedule(this, staggeredOptions.getNext().toMillis(), TimeUnit.MILLISECONDS);
        }
    }
}
