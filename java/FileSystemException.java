package vfs;

/**
 * Base exception for all virtual file system errors.
 * Sub-classes allow callers to distinguish error types if needed,
 * while still being caught uniformly with a single catch block.
 */
public class FileSystemException extends RuntimeException {
    public FileSystemException(String message) {
        super(message);
    }

    /** File or directory entry not found */
    public static class NotFound extends FileSystemException {
        public NotFound(String name) { super("not found: " + name); }
    }

    /** Name already exists in directory */
    public static class AlreadyExists extends FileSystemException {
        public AlreadyExists(String name) { super("already exists: " + name); }
    }

    /** File is currently open */
    public static class FileOpen extends FileSystemException {
        public FileOpen(String name) { super("file is open: " + name); }
    }

    /** File is not open / invalid OFT index */
    public static class NotOpen extends FileSystemException {
        public NotOpen(int index) { super("not open: " + index); }
    }

    /** No free resource (block, descriptor, OFT slot) */
    public static class NoSpace extends FileSystemException {
        public NoSpace(String resource) { super("no free " + resource); }
    }

    /** Memory or file access out of bounds */
    public static class OutOfBounds extends FileSystemException {
        public OutOfBounds(String detail) { super("out of bounds: " + detail); }
    }

    /** Invalid filename (length, characters) */
    public static class InvalidName extends FileSystemException {
        public InvalidName(String name) { super("invalid filename: " + name); }
    }
}
