package se.laz.casual.standalone;

import se.laz.casual.api.flags.Flag;
import se.laz.casual.api.flags.XAFlags;
import se.laz.casual.api.network.protocol.messages.CasualNWMessage;
import se.laz.casual.api.util.PrettyPrinter;
import se.laz.casual.api.xa.XAReturnCode;
import se.laz.casual.api.xa.XID;
import se.laz.casual.network.protocol.messages.CasualNWMessageImpl;
import se.laz.casual.network.protocol.messages.transaction.CasualTransactionResourceCommitReplyMessage;
import se.laz.casual.network.protocol.messages.transaction.CasualTransactionResourceCommitRequestMessage;
import se.laz.casual.network.protocol.messages.transaction.CasualTransactionResourcePrepareReplyMessage;
import se.laz.casual.network.protocol.messages.transaction.CasualTransactionResourcePrepareRequestMessage;
import se.laz.casual.network.protocol.messages.transaction.CasualTransactionResourceRollbackReplyMessage;
import se.laz.casual.network.protocol.messages.transaction.CasualTransactionResourceRollbackRequestMessage;
import se.laz.casual.standalone.outbound.CasualConnection;
import se.laz.casual.standalone.outbound.CasualResourceManager;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class CasualXAResource implements XAResource
{
    private static final Logger LOG = Logger.getLogger(CasualXAResource.class.getName());
    private static final Xid[] NO_XIDS = {};
    private final CasualConnection connection;
    private final int resourceManagerId;
    private Xid currentXid = null;
    private boolean readOnly = false;

    private CasualXAResource(final CasualConnection connection, int resourceManagerId)
    {
        this.connection = connection;
        this.resourceManagerId = resourceManagerId;
    }

    public static CasualXAResource of(final CasualConnection connection, int resourceManagerId)
    {
        Objects.requireNonNull(connection, "connection can not be null");
        return new CasualXAResource(connection, resourceManagerId);
    }

    public Xid getCurrentXid()
    {
        return (currentXid == null) ? XID.NULL_XID : currentXid;
    }

    @Override
    public void commit(Xid xid, boolean onePhaseCommit) throws XAException
    {
        Flag<XAFlags> flags = Flag.of(XAFlags.TMNOFLAGS);
        if (onePhaseCommit)
        {
            flags = Flag.of(XAFlags.TMONEPHASE);
        }
        LOG.finest(() -> String.format("trying to commit, xid: %s ( %s ) onePhase?%b", PrettyPrinter.casualStringify(xid), xid, onePhaseCommit));
        CasualTransactionResourceCommitRequestMessage commitRequest =
                CasualTransactionResourceCommitRequestMessage.of(UUID.randomUUID(), xid, resourceManagerId, flags);
        CasualNWMessage<CasualTransactionResourceCommitRequestMessage> requestEnvelope = CasualNWMessageImpl.of(UUID.randomUUID(), commitRequest);
        CompletableFuture<CasualNWMessage<CasualTransactionResourceCommitReplyMessage>> replyEnvelopeFuture = connection.getNetworkConnection().request(requestEnvelope);

        CasualNWMessage<CasualTransactionResourceCommitReplyMessage> replyEnvelope = replyEnvelopeFuture.join();
        CasualTransactionResourceCommitReplyMessage replyMsg = replyEnvelope.getMessage();
        throwWhenTransactionErrorCode(replyMsg.getTransactionReturnCode());
        LOG.finest(() -> String.format("commited, xid: %s ( %s )", PrettyPrinter.casualStringify(xid), xid));
    }

    /**
     * Removes the
     *
     * @param xid
     * @param flag - TMSUCCESS, TMFAIL, or TMSUSPEND.
     * @throws XAException
     */
    @Override
    public void end(Xid xid, int flag) throws XAException
    {
        LOG.finest(() -> "end, xid: " + PrettyPrinter.casualStringify(xid));
        if ((flag & (TMSUSPEND | TMFAIL)) != 0)
        {
            // can only suspend the associated xid
            if (!equals(currentXid, xid))
            {
                throw new XAException(XAException.XAER_PROTO);
            }
            CasualResourceManager.getInstance().remove(xid);
            disassociate();
        }
        else if ((flag & TMSUCCESS) == TMSUCCESS)
        {
            // disassociate if this is the current xid
            if (equals(currentXid, xid))
            {
                CasualResourceManager.getInstance().remove(xid);
                disassociate();
            }
        }
        else
        {
            throw new XAException(XAException.XAER_INVAL);
        }
    }

    private boolean equals(Xid xid1, Xid xid2)
    {
        if (xid1 == xid2)
        {
            return true;
        }
        if (xid1 == null ^ xid2 == null)
        {
            return false;
        }
        return xid1.getFormatId() == xid2.getFormatId() && Arrays.equals(xid1.getBranchQualifier(), xid2.getBranchQualifier())
                && Arrays.equals(xid1.getGlobalTransactionId(), xid2.getGlobalTransactionId());
    }

    /**
     * Removes the records for a heuristically completed
     * transaction
     *
     * @param xid - ID of heuristically complete transaction
     * @throws XAException - Possible exception values are XAER_RMERR, XAER_RMFAIL, XAER_NOTA, XAER_INVAL, or XAER_PROTO.
     */
    @Override
    public void forget(Xid xid) throws XAException
    {
        throw new XAException(XAException.XAER_NOTA);
    }

    @Override
    public int getTransactionTimeout() throws XAException
    {
        return connection.getTransactionTimeout();
    }

    @Override
    public boolean isSameRM(XAResource xaResource) throws XAException
    {
        // This trivial implementation makes sure that the
        // application server doesn't try to use another connection
        // for prepare, commit and rollback commands.
        if (xaResource == null)
        {
            return false;
        }
        if (!(xaResource instanceof CasualXAResource))
        {
            return false;
        }
        CasualXAResource resource = (CasualXAResource) xaResource;
        return connection.getId().equals(resource.connection.getId());
    }

    @Override
    public int prepare(Xid xid) throws XAException
    {
        if (isReadOnly())
        {
            return XAResource.XA_RDONLY;
        }
        if(null == xid)
        {
            throw new XAException(XAException.XAER_PROTO);
        }
        LOG.finest(() -> String.format("trying to prepare, xid: %s ( %s )", PrettyPrinter.casualStringify(xid), xid));
        Flag<XAFlags> flags = Flag.of(XAFlags.TMNOFLAGS);
        CasualTransactionResourcePrepareRequestMessage prepareRequest = CasualTransactionResourcePrepareRequestMessage.of(UUID.randomUUID(), xid, resourceManagerId, flags);
        CasualNWMessage<CasualTransactionResourcePrepareRequestMessage> requestEnvelope = CasualNWMessageImpl.of(UUID.randomUUID(), prepareRequest);
        CompletableFuture<CasualNWMessage<CasualTransactionResourcePrepareReplyMessage>> replyEnvelopeFuture = connection.getNetworkConnection().request(requestEnvelope);

        CasualNWMessage<CasualTransactionResourcePrepareReplyMessage> replyEnvelope = replyEnvelopeFuture.join();
        CasualTransactionResourcePrepareReplyMessage replyMsg = replyEnvelope.getMessage();
        throwWhenTransactionErrorCode(replyMsg.getTransactionReturnCode());
        LOG.finest(() -> String.format("prepared, xid: %s ( %s )", PrettyPrinter.casualStringify(xid), xid));
        return replyMsg.getTransactionReturnCode().getId();
    }

    @Override
    public Xid[] recover(int i) throws XAException
    {
        return NO_XIDS;
    }

    @Override
    public void rollback(Xid xid) throws XAException
    {
        LOG.finest(() -> String.format("trying to rollback, xid: %s ( %s )", PrettyPrinter.casualStringify(xid), xid));
        Flag<XAFlags> flags = Flag.of(XAFlags.TMNOFLAGS);
        CasualTransactionResourceRollbackRequestMessage request =
                    CasualTransactionResourceRollbackRequestMessage.of(UUID.randomUUID(), xid, resourceManagerId, flags);
        CasualNWMessage<CasualTransactionResourceRollbackRequestMessage> requestEnvelope = CasualNWMessageImpl.of(UUID.randomUUID(), request);
        CompletableFuture<CasualNWMessage<CasualTransactionResourceRollbackReplyMessage>> replyEnvelopeFuture = connection.getNetworkConnection().request(requestEnvelope);

        CasualNWMessage<CasualTransactionResourceRollbackReplyMessage> replyEnvelope = replyEnvelopeFuture.join();
        CasualTransactionResourceRollbackReplyMessage replyMsg = replyEnvelope.getMessage();
        throwWhenTransactionErrorCode(replyMsg.getTransactionReturnCode());
        LOG.finest(() ->  String.format("rolled, xid: %s ( %s )", PrettyPrinter.casualStringify(xid), xid));
    }

    @Override
    public boolean setTransactionTimeout(int i) throws XAException
    {
        connection.setTransactionTimeout(i);
        return true;
    }

    @Override
    public void start(Xid xid, int i) throws XAException
    {
        LOG.finest(()-> String.format("start, xid: %s ( %s ) flag: %d ", PrettyPrinter.casualStringify(xid), xid, i));
        if(null == xid)
        {
            LOG.warning(()-> String.format("start, xid is null!"));
            throw new XAException(XAException.XAER_PROTO);
        }
        if(currentXid != null)
        {
            LOG.warning(()-> String.format("start, current xid is not null! xid: %s ( %s ) flag: %d ", PrettyPrinter.casualStringify(currentXid), currentXid, i));
            throw new XAException(XAException.XAER_PROTO);
        }
        readOnly = false;
        if(!(XAFlags.TMJOIN.getValue() == i || XAFlags.TMRESUME.getValue() == i) &&
                CasualResourceManager.getInstance().isPending(xid))
        {
            LOG.finest(()->"throwing XAException.XAER_DUPID");
            throw new XAException(XAException.XAER_DUPID);
        }
        associate(xid);
        if(!CasualResourceManager.getInstance().isPending(currentXid))
        {
            CasualResourceManager.getInstance().put(currentXid);
        }
    }

    @Override
    public String toString()
    {
        return "CasualXAResource{" +
                "currentXid=" + currentXid +
                '}';
    }

    private void associate(Xid xid)
    {
        currentXid = xid;
    }

    public void disassociate()
    {
        currentXid = null;
    }

    public void setReadOnly()
    {
        readOnly = true;
    }

    public boolean isReadOnly()
    {
        return readOnly;
    }

    private void throwWhenTransactionErrorCode(final XAReturnCode transactionReturnCode) throws XAException
    {
        LOG.finest(()->"XAReturnCode: " + transactionReturnCode);
        switch( transactionReturnCode )
        {
            case XA_OK:
            case XA_RDONLY:
                break;
            default:
                LOG.finest(()->"throwing XAException for XAReturnCode: " + transactionReturnCode);
                throw new XAException( transactionReturnCode.getId());
        }
    }
}

