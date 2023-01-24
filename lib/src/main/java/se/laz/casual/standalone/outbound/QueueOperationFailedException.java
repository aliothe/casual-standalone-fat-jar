package se.laz.casual.standalone.outbound;

public class QueueOperationFailedException extends RuntimeException
{
    private static final long serialVersionUID = 1L;
    public QueueOperationFailedException(String s)
    {
        super(s);
    }
}
