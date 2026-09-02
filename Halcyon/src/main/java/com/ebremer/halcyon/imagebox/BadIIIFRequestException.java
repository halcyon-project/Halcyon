package com.ebremer.halcyon.imagebox;

/**
 * A malformed IIIF request — the client's fault, not ours (L9).
 * <p>
 * It exists so {@code ImageServer} can tell "you sent nonsense" apart from "we
 * broke". Everything {@code IIIFProcessor} used to throw for a bad URL was an
 * unchecked {@code ArrayIndexOutOfBoundsException} or
 * {@code NumberFormatException}, which landed in the catch-all and became a
 * <b>500 plus a logged stack trace</b>. That is wrong twice over: it tells the
 * client the server failed when the request was invalid, and it lets anyone
 * inflate the error rate and the log volume with a one-line curl.
 *
 * @author erich
 */
public class BadIIIFRequestException extends RuntimeException {

    public BadIIIFRequestException(String message) {
        super(message);
    }
}
