package mycolor;
/**
 * This class the special exception for the EPSGrapthics.
 * @author Evgeny Mirkes (University of Leicester, UK)
*/
public class MyEPSException extends RuntimeException {
    @SuppressWarnings("compatibility:4120342022699198989")
    private static final long serialVersionUID = 43284893790740006L;

    /**
     * @param string is the message for the created exception
     */
    public MyEPSException(String string) {
        super(string);
    }

}
