import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.ast.Command;
import edu.mit.csail.sdg.ast.Module;
import edu.mit.csail.sdg.parser.CompUtil;
import edu.mit.csail.sdg.translator.A4Options;
import edu.mit.csail.sdg.translator.A4Solution;
import edu.mit.csail.sdg.translator.TranslateAlloyToKodkod;

import java.util.*;

/**
 * Minimal reproduction: the Alloy API ({@code A4Solution.next()} enumeration)
 * returns multiple instances with IDENTICAL Alloy output, even though the
 * UNDERLYING Kodkod instances are DISTINCT (isomorphic-but-not-equal).
 *
 * This proves the duplication is introduced by Alloy's translation/output
 * layer, not by Kodkod: Kodkod hands Alloy different instances, and Alloy
 * collapses them to the same output without deduplicating on the API path.
 * The GUI hides this via SimpleReporter's `latestKodkods` dedup.
 */
public class SiloedMRE {

    // Inlined model confirmed (by an Alloy maintainer) to exhibit the duplicate.
    static final String MODEL =
        "sig Node {\n" +
        "  succ: set Node,\n" +
        "  node_data: set Data\n" +
        "}\n" +
        "sig Data {}\n" +
        "\n" +
        "pred Invariant {\n" +
        "  some Node\n" +
        "  all d : Data | some node_data.d\n" +
        "  all node : Node | one node.succ and one succ.node\n" +
        "  all node : Node | node in node.^succ\n" +
        "  all node1, node2: Node | node1 in node2.^succ\n" +
        "}\n" +
        "\n" +
        "pred Example {\n" +
        "  Invariant\n" +
        "}\n" +
        "\n" +
        "run Example for 5 expect 1\n";

    /** Bundle of what we record for each first-seen Alloy output. */
    static final class Record {
        final int index;
        final String kodkod;   // raw Kodkod instance (debugExtractKInstance)
        Record(int index, String kodkod) { this.index = index; this.kodkod = kodkod; }
    }

    public static void main(String[] args) throws Exception {
        A4Reporter rep = new A4Reporter();
        Module world = CompUtil.parseEverything_fromString(rep, MODEL);
        Command cmd = world.getAllCommands().get(0);

        A4Options options = new A4Options();
        options.symmetry = 20;   // default symmetry breaking, matching the GUI

        A4Solution ans = TranslateAlloyToKodkod.execute_command(
                rep, world.getAllReachableSigs(), cmd, options);

        System.out.println("Enumerating all instances via the Alloy API "
                + "(A4Solution.next()) ...");
        System.out.println("For each Alloy-output duplicate, the UNDERLYING Kodkod");
        System.out.println("instances are printed to show they are DISTINCT.\n");

        // Alloy output -> first record (index + its Kodkod instance).
        Map<String, Record> firstSeenAt = new LinkedHashMap<>();

        int total = 0;
        int duplicateCount = 0;
        int distinctKodkodAmongDuplicates = 0;
        int identicalKodkodAmongDuplicates = 0;
        List<int[]> duplicatePairs = new ArrayList<>();

        while (ans != null && ans.satisfiable()) {
            String alloyOut = ans.toString();
            String kodkod = normalizeKodkod(String.valueOf(ans.debugExtractKInstance()));

            Record prior = firstSeenAt.get(alloyOut);
            if (prior != null) {
                duplicateCount++;
                duplicatePairs.add(new int[]{prior.index, total});
                boolean kodkodIdentical = kodkod.equals(prior.kodkod);
                if (kodkodIdentical) identicalKodkodAmongDuplicates++;
                else distinctKodkodAmongDuplicates++;

                System.out.println("==============================================================");
                System.out.println("DUPLICATE Alloy output: solution #" + total
                        + " == earlier solution #" + prior.index
                        + "  (identical per A4Solution.toString())");
                System.out.println("--------------------------------------------------------------");
                System.out.println("  Underlying Kodkod instance of THIS solution (#" + total + "):");
                System.out.println("    Node.succ      = " + extract(kodkod, "succ"));
                System.out.println("    Node.node_data = " + extract(kodkod, "node_data"));
                System.out.println("  Underlying Kodkod instance of EARLIER solution (#"
                        + prior.index + "):");
                System.out.println("    Node.succ      = " + extract(prior.kodkod, "succ"));
                System.out.println("    Node.node_data = " + extract(prior.kodkod, "node_data"));
                System.out.println("  Kodkod instances raw-identical? " + kodkodIdentical
                        + (kodkodIdentical ? "" : "   <-- DISTINCT: Alloy collapsed them"));
                System.out.println("==============================================================");
            } else {
                firstSeenAt.put(alloyOut, new Record(total, kodkod));
            }

            total++;
            ans = ans.next();
        }

        System.out.println("\n========================= SUMMARY =========================");
        System.out.println("Total instances returned by the API        : " + total);
        System.out.println("Distinct Alloy outputs                      : " + firstSeenAt.size());
        System.out.println("Duplicate Alloy outputs                     : " + duplicateCount);
        System.out.println("  of which backed by DISTINCT Kodkod inst.  : "
                + distinctKodkodAmongDuplicates);
        System.out.println("  of which backed by IDENTICAL Kodkod inst. : "
                + identicalKodkodAmongDuplicates);

        if (duplicateCount > 0 && identicalKodkodAmongDuplicates == 0) {
            System.out.println("\nVERDICT: every duplicate Alloy output is backed by a DISTINCT");
            System.out.println("Kodkod instance. Kodkod is correct (no repeated instances); the");
            System.out.println("collapse to identical Alloy output happens in Alloy's translation/");
            System.out.println("output layer. The API forwards these without deduplicating, while");
            System.out.println("the GUI suppresses them via SimpleReporter's `latestKodkods` set.");
        } else if (identicalKodkodAmongDuplicates > 0) {
            System.out.println("\nNOTE: some duplicates are backed by IDENTICAL Kodkod instances;");
            System.out.println("those would indicate a Kodkod-side repeat. Inspect separately.");
        } else {
            System.out.println("\nNo duplicates observed in this run/config.");
        }
    }

    /**
     * The raw Kodkod instance string carries a lot of boilerplate (Int/, seq/,
     * String, skolems). Trim to keep the report readable; the comparison itself
     * uses the full normalized string so nothing is lost in the equality check.
     */
    static String normalizeKodkod(String raw) {
        // Collapse whitespace so formatting differences never masquerade as
        // instance differences.
        return raw.replaceAll("\\s+", " ").trim();
    }

    /**
     * Pull a named user relation (e.g. "succ", "node_data") out of the raw
     * Kodkod dump for display. Matches "...succ=[[...]]" up to the closing
     * "]]" or "succ=[]" for the empty case. Display-only; not used for equality.
     */
    static String extract(String kodkod, String field) {
        // try non-empty: <field>=[[ ... ]]
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\b" + java.util.regex.Pattern.quote(field) + "=\\[(\\[.*?\\])\\]")
                .matcher(kodkod);
        if (m.find()) return "[" + m.group(1) + "]";
        // try empty: <field>=[]
        java.util.regex.Matcher m2 = java.util.regex.Pattern
                .compile("\\b" + java.util.regex.Pattern.quote(field) + "=\\[\\]")
                .matcher(kodkod);
        if (m2.find()) return "[]";
        return "<not found>";
    }
}