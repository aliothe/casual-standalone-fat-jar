/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.standalone.outbound;

import se.laz.casual.network.outbound.NetworkListener;

import javax.transaction.TransactionManager;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

public class CasualManagedConnectionImpl implements CasualManagedConnection, NetworkListener, ReconnectAble
{
    private static final Logger LOG = Logger.getLogger(CasualManagedConnectionImpl.class.getName());
    private final String host;
    private int port;
    private Caller caller;
    private Object disconnectLock = new Object();
    private boolean closed = true;
    TransactionManager transactionManager;

    private CasualManagedConnectionImpl(TransactionManager transactionManager,
                                        String host,
                                        int port)
    {
        this.host = host;
        this.port = port;
        this.transactionManager = transactionManager;
        caller = CallerProducerImpl.of().createCaller(transactionManager, this.host, this.port, this);
        connect();
    }

    public static CasualManagedConnection of(TransactionManager transactionManager,
                                             String host,
                                             int port)
    {
        Objects.requireNonNull(transactionManager, "transactionManager can not be null");
        Objects.requireNonNull(host, "host can not be null");
        return new CasualManagedConnectionImpl(transactionManager, host, port);
    }

    @Override
    public void disconnected(Exception exception)
    {
        synchronized (disconnectLock)
        {
            LOG.warning(() -> String.format("%s:%d disconnected -> %s", host, port, exception));
            caller = null;
            AutoReconnect.of(this,
                    host,
                    port,
                    transactionManager,
                    StaggeredOptions.of(Duration.of(500, ChronoUnit.MILLIS),
                            Duration.of(1000, ChronoUnit.MILLIS),
                            2),
                    this,
                    CallerProducerImpl.of());
        }
    }

    @Override
    public Optional<Caller> getCaller()
    {
        return Optional.ofNullable(caller);
    }

    @Override
    public void close()
    {
        //getCaller().ifPresent(Caller::close);
        closed = true;
        // TODO:
        // Should only ever happen after current transaction is finished?
        //LOG.info(() -> "close?" + closed);
    }

    public void connect()
    {
        closed = false;
    }

    public boolean isClosed()
    {
        //LOG.info(() -> "connection isClosed?" + closed + " => " + this);
        return closed;
    }

    @Override
    public void setCaller(Caller caller)
    {
        LOG.info(() -> String.format("%s:%d reconnected", host, port));
        this.caller = caller;
    }
}
