package vfs;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Command-line shell for the virtual file system.
 *
 * <p>Reads commands from a file (one per line) and prints results to stdout
 * and an output file.  Keeps each "in" command's results visually separated.
 *
 * <p>Supported commands:
 * <pre>
 *   in              – re-initialise
 *   cr &lt;name&gt;       – create file
 *   de &lt;name&gt;       – destroy file
 *   op &lt;name&gt;       – open file  → prints "&lt;name&gt; opened &lt;index&gt;"
 *   cl &lt;index&gt;      – close file
 *   rd &lt;idx&gt; &lt;mpos&gt; &lt;cnt&gt;  – read from file to memory
 *   wr &lt;idx&gt; &lt;mpos&gt; &lt;cnt&gt;  – write from memory to file
 *   sk &lt;idx&gt; &lt;pos&gt;  – seek
 *   dr              – directory listing
 *   wm &lt;pos&gt; &lt;data&gt; – write string to memory
 *   rm &lt;pos&gt; &lt;cnt&gt;  – read from memory
 * </pre>
 */
public class Shell {

    private static final String OUTPUT_FILE = "output.txt";

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter input file path: ");
        String inputPath = scanner.nextLine().trim();

        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(inputPath));
        } catch (IOException e) {
            System.err.println("Input file not found: " + inputPath);
            return;
        }

        FileSystem fs     = new FileSystem();
        boolean    first  = true;

        // Clear / create output file
        try (PrintWriter out = new PrintWriter(new FileWriter(OUTPUT_FILE, false))) {
            // intentionally empty — truncate
        }

        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            String cmd = parts[0];
            String result;

            try {
                result = dispatch(fs, cmd, parts);
            } catch (FileSystemException e) {
                result = "error";
            } catch (Exception e) {
                result = "error";
            }

            // Print with blank-line separator between sessions
            if (cmd.equals("in")) {
                if (!first) System.out.println();
                first = false;
            }
            System.out.println(result);
            appendOutput(result);
        }
    }

    // ── Command dispatch ──────────────────────────────────────────────────────

    private static String dispatch(FileSystem fs, String cmd, String[] parts) {
        return switch (cmd) {
            case "in" -> {
                if (parts.length != 1) throw new FileSystemException("bad args");
                yield fs.initialize();
            }
            case "cr" -> {
                requireArgs(parts, 2);
                yield fs.create(parts[1]);
            }
            case "de" -> {
                requireArgs(parts, 2);
                yield fs.destroy(parts[1]);
            }
            case "op" -> {
                requireArgs(parts, 2);
                yield fs.open(parts[1]);
            }
            case "cl" -> {
                requireArgs(parts, 2);
                yield fs.close(parseInt(parts[1]));
            }
            case "rd" -> {
                requireArgs(parts, 4);
                yield fs.read(parseInt(parts[1]), parseInt(parts[2]), parseInt(parts[3]));
            }
            case "wr" -> {
                requireArgs(parts, 4);
                yield fs.write(parseInt(parts[1]), parseInt(parts[2]), parseInt(parts[3]));
            }
            case "sk" -> {
                requireArgs(parts, 3);
                yield fs.seek(parseInt(parts[1]), parseInt(parts[2]));
            }
            case "dr" -> {
                if (parts.length != 1) throw new FileSystemException("bad args");
                yield fs.directoryListing();
            }
            case "wm" -> {
                if (parts.length < 3) throw new FileSystemException("bad args");
                String data = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
                yield fs.writeMemory(parseInt(parts[1]), data);
            }
            case "rm" -> {
                requireArgs(parts, 3);
                yield fs.readMemory(parseInt(parts[1]), parseInt(parts[2]));
            }
            default -> throw new FileSystemException("unknown command: " + cmd);
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void requireArgs(String[] parts, int expected) {
        if (parts.length != expected)
            throw new FileSystemException("bad args for " + parts[0]);
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) {
            throw new FileSystemException("not a number: " + s);
        }
    }

    private static void appendOutput(String line) {
        try (PrintWriter out = new PrintWriter(new FileWriter(OUTPUT_FILE, true))) {
            out.println(line);
        } catch (IOException e) {
            System.err.println("Warning: could not write to " + OUTPUT_FILE);
        }
    }
}
