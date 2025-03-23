package io.github.vatisteve;

/**
 * @author tinhnv - Mar 23 2025
 */
public class PermissionDeniedException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public PermissionDeniedException(String message) {
        super(message);
    }
}
