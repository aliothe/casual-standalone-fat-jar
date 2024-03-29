/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.standalone.outbound;

import se.laz.casual.internal.network.NetworkConnection;
import se.laz.casual.jca.DomainId;
import se.laz.casual.standalone.CasualXAResource;

import javax.transaction.xa.Xid;
import java.util.Objects;

public class CasualConnection
{
    private final NetworkConnection networkConnection;
    private final DomainId id;
    private CasualXAResource casualXAResource;
    private int timeout;

    private CasualConnection(NetworkConnection networkConnection)
    {
        this.networkConnection = networkConnection;
        this.id = networkConnection.getDomainId();
    }

    public CasualConnection setCasualXAResource(CasualXAResource casualXAResource)
    {
        Objects.requireNonNull(casualXAResource, "casualXAResource can not be null");
        this.casualXAResource = casualXAResource;
        return this;
    }

    public CasualXAResource getCasualXAResource()
    {
        return casualXAResource;
    }

    public static CasualConnection of(NetworkConnection networkConnection)
    {
        Objects.requireNonNull(networkConnection, "networkConnection can not be null");
        return new CasualConnection(networkConnection);
    }

    public NetworkConnection getNetworkConnection()
    {
        return networkConnection;
    }

    public void setTransactionTimeout(int timeout)
    {
        this.timeout = timeout;
    }

    public int getTransactionTimeout()
    {
        return timeout;
    }

    public Xid getCurrentXid()
    {
        return casualXAResource.getCurrentXid();
    }

    public DomainId getId()
    {
        return id;
    }

    public void close()
    {
        getNetworkConnection().close();
    }
}
