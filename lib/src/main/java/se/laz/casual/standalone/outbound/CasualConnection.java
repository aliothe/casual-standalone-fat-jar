package se.laz.casual.standalone.outbound;

import se.laz.casual.network.api.NetworkConnection;
import se.laz.casual.standalone.CasualXAResource;

import javax.transaction.xa.Xid;
import java.util.Objects;
import java.util.UUID;

public class CasualConnection
{
    private final NetworkConnection networkConnection;
    private final UUID id;
    private CasualXAResource casualXAResource;
    private int timeout;

    private CasualConnection(NetworkConnection networkConnection)
    {
        this.networkConnection = networkConnection;
        this.id = networkConnection.getId();
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

    public UUID getId()
    {
        return id;
    }

    public void close()
    {
        getNetworkConnection().close();
    }
}
