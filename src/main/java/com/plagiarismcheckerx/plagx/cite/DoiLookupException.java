package com.plagiarismcheckerx.plagx.cite;

/** Thrown when a DOI lookup fails or the DOI can't be found. */
public class DoiLookupException extends Exception {
    private static final long serialVersionUID = 1L;

    /** HTTP status returned by the server, or 0 for a network error. */
    private final int statusCode;

    /**
     * Creates the exception.
     *
     * @param message readable description, e.g. {@code DOI not found: 10.1234/x}
     * @param statusCode HTTP status returned by the server, or 0 for a network error
     */
    public DoiLookupException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * HTTP status returned by the server.
     *
     * @return the status, or 0 for a network error
     */
    public int getStatusCode() {
        return statusCode;
    }
}
