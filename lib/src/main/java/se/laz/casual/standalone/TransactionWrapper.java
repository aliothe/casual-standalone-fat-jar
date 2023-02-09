/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.standalone;

import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAResource;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static javax.transaction.xa.XAResource.TMSUCCESS;

public class TransactionWrapper
{
    private static final Logger LOG = Logger.getLogger(TransactionWrapper.class.getName());
    private final TransactionManager transactionManager;
    private final Object transactionLock = new Object();

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
        try
        {
            if( null == transactionManager.getTransaction() )
            {
                return supplier.get();
            }
            synchronized (transactionLock)
            {
                if (!transactionManager.getTransaction().enlistResource(xaResource))
                {
                    throw new TransactionException("could not enlist resource!");
                }
                T answer = supplier.get();
                if (!transactionManager.getTransaction().delistResource(xaResource, TMSUCCESS))
                {
                    throw new TransactionException("could not delist resource!");
                }
                return answer;
            }
        }
        catch (Exception e)
        {
            LOG.log(Level.WARNING, e, () -> "transaction exception - will try to set rollback only");
            try
            {
                transactionManager.setRollbackOnly();
                throw new TransactionException(e);
            }
            catch (SystemException ex)
            {
                throw new TransactionException("failed setRollbackOnly",e);
            }
        }
    }

    private static class XASynchronization implements Synchronization
    {
        private final Transaction transaction;
        private final XAResource xaResource;
        private XASynchronization(Transaction transaction, XAResource xaResource)
        {
            this.transaction = transaction;
            this.xaResource = xaResource;
        }
        public static XASynchronization of(Transaction transaction, XAResource xaResource)
        {
            Objects.requireNonNull(transaction, "transaction can not be null");
            Objects.requireNonNull(xaResource, "xaResource can not be null");
            return new XASynchronization(transaction, xaResource);
        }

        @Override
        public void beforeCompletion()
        {
            // NOP
        }

        @Override
        public void afterCompletion(int status)
        {
            if( status == Status.STATUS_COMMITTED )
            {
                LOG.log(Level.INFO, () -> "afterCompletion: Status.STATUS_COMMITTED" );
                return;
            }
            LOG.severe(() -> "Transaction: " + transaction + ", xaResource: " + xaResource + " NOT COMMITED, status: " + status);
        }
    }
}
