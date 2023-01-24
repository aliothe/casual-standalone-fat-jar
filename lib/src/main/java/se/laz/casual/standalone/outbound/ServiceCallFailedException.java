package se.laz.casual.standalone.outbound;

public class ServiceCallFailedException extends RuntimeException
{
    private static final long serialVersionUID = 1L;
    public ServiceCallFailedException(String s)
    {
        super(s);
    }
}
