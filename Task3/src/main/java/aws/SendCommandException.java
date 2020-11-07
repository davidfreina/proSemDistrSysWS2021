package aws;

public class SendCommandException extends RuntimeException {
    public SendCommandException(String errorMessage) {
        super(errorMessage);
    }
}
