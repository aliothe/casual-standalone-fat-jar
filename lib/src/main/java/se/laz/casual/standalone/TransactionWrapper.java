package se.laz.casual.standalone;

import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAResource;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static javax.transaction.xa.XAResource.TMSUCCESS;

public class TransactionWrapper
{
    private static final Logger LOG = Logger.getLogger(TransactionWrapper.class.getName());
    private final TransactionManager transactionManager;
    private final Object transactLock = new Object();

    private TransactionWrapper(TransactionManager transactionManager)
    {
        this.transactionManager = transactionManager;
    }

    public static TransactionWrapper of(TransactionManager transactionManager)
    {
        Objects.requireNonNull(transactionManager, "transactionManager can not be null");
        return new TransactionWrapper(transactionManager);
    }

    public <T> T execute(Supplier<T> supplier, XAResource xaResource)
    {
        synchronized (transactLock)
        {
            try
            {
                transactionManager.getTransaction().enlistResource(xaResource);
                T answer = supplier.get();
                transactionManager.getTransaction().delistResource(xaResource, TMSUCCESS);
                return answer;
            }
            catch (Exception e)
            {
                LOG.warning(() -> "transaction exception: " + e + " will try to set rollback only");
                try
                {
                    transactionManager.setRollbackOnly();
                }
                catch (SystemException ex)
                {
                    throw new TransactionException("failed setRollbackOnly",e);
                }
                throw new TransactionException(e);
            }
        }
    }

}
