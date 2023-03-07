package se.laz.casual.standalone.outbound;

import se.laz.casual.api.CasualRuntimeException;

import javax.transaction.TransactionManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

// Appserver kinda pool logic

public class ManagedConnectionPool
{
    private static final Logger LOG = Logger.getLogger(ManagedConnectionPool.class.getName());
    private static final Map<Address, List<CasualManagedConnection>> POOL = new ConcurrentHashMap<>();
    private static final int POOL_SIZE = 1000;
    private static final Object connectionCreateLock = new Object();

    public static CasualManagedConnection getConnection(Address address, Supplier<TransactionManager> transactionManagerSupplier)
    {
        Optional<CasualManagedConnection> pooledConnection = findPooledConnection(address);
        //LOG.info(() -> "found?" + pooledConnection);
        return pooledConnection.orElseGet(() -> createNewManagedConnection(address, transactionManagerSupplier));
    }

    private static Optional<CasualManagedConnection> findPooledConnection(Address address)
    {
        List<CasualManagedConnection> connections = POOL.get(address);
        if(null == connections)
        {
            return Optional.empty();
        }
        return connections.stream()
                          .map( connection -> (CasualManagedConnectionImpl) connection)
                          .filter(connection -> connection.isClosed())
                          .map(connection -> {
                              connection.connect();
                              return (CasualManagedConnection) connection;
                          })
                          .findFirst();
    }

    private static CasualManagedConnection createNewManagedConnection(Address address, Supplier<TransactionManager> transactionManagerSupplier)
    {
        List<CasualManagedConnection> pooledConnections = POOL.computeIfAbsent(address, key -> new ArrayList<>());
        if(pooledConnections.size() == POOL_SIZE)
        {
            throw new CasualRuntimeException("Already at max pool size: " + POOL_SIZE + " for pool: " + address);
        }
        synchronized (connectionCreateLock)
        {
            CasualManagedConnection managedConnection = CasualManagedConnectionProducer.create(transactionManagerSupplier, address.getHostName(), address.getPort());
            pooledConnections.add(managedConnection);
            LOG.info(() -> "# of managed connections for address: " + address + " => " + pooledConnections.size());
            return managedConnection;
        }
    }
}
