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
 * yields multiple instances with IDENTICAL Alloy output. The GUI suppresses
 * these via the {@code latestKodkods} dedup in SimpleReporter; the API does not,
 * so API consumers receive duplicate instances.
 *
 * Build (alloy.jar only):
 *   javac -cp alloy.jar AlloyApiMRE.java -d out
 *   java  -cp alloy.jar:out AlloyApiMRE
 *
 * Expected: the run prints "DUPLICATE INSTANCE (identical Alloy output)" at
 * least once, with duplicateCount > 0 in the summary.
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

    public static void main(String[] args) throws Exception {
        A4Reporter rep = new A4Reporter();
        Module world = CompUtil.parseEverything_fromString(rep, MODEL);
        Command cmd = world.getAllCommands().get(0);

        A4Options options = new A4Options();
        // Default symmetry breaking, matching the GUI. The GUI dedups the
        // resulting isomorphic survivors; the API does not.
        options.symmetry = 20;

        A4Solution ans = TranslateAlloyToKodkod.execute_command(
                rep, world.getAllReachableSigs(), cmd, options);

        System.out.println("Enumerating all instances via the Alloy API "
                + "(A4Solution.next()) ...\n");

        // Dedup key = Alloy's OWN finalized instance representation. This is
        // what API consumers receive and what the GUI dedups internally.
        // (Two instances with identical toString() are, to Alloy, the same
        // instance presented twice.)
        Map<String, Integer> firstSeenAt = new LinkedHashMap<>();
        int total = 0;
        int duplicateCount = 0;
        List<int[]> duplicatePairs = new ArrayList<>();

        while (ans != null && ans.satisfiable()) {
            String alloyOut = ans.toString();   // Alloy's own representation

            if (firstSeenAt.containsKey(alloyOut)) {
                duplicateCount++;
                int firstIdx = firstSeenAt.get(alloyOut);
                duplicatePairs.add(new int[]{firstIdx, total});
                System.out.println("==============================================================");
                System.out.println("DUPLICATE INSTANCE (identical Alloy output)");
                System.out.println("  solution #" + total
                        + " is byte-for-byte identical (per A4Solution.toString())");
                System.out.println("  to earlier solution #" + firstIdx + ".");
                System.out.println("==============================================================");
            } else {
                firstSeenAt.put(alloyOut, total);
            }

            total++;
            ans = ans.next();
        }

        System.out.println("\n========================= SUMMARY =========================");
        System.out.println("Total instances returned by the API : " + total);
        System.out.println("Distinct Alloy outputs               : " + firstSeenAt.size());
        System.out.println("Duplicate instances (identical out)  : " + duplicateCount);
        if (!duplicatePairs.isEmpty()) {
            System.out.print("Duplicate solution-index pairs       : ");
            List<String> ps = new ArrayList<>();
            for (int[] p : duplicatePairs) ps.add("(" + p[0] + "==" + p[1] + ")");
            System.out.println(ps);
        }

        if (duplicateCount > 0) {
            System.out.println("\nBUG CONFIRMED: the Alloy API returned " + duplicateCount
                    + " instance(s) that are identical to instances it already returned.");
            System.out.println("The GUI hides these via SimpleReporter's `latestKodkods` dedup;");
            System.out.println("API consumers receive them. Kodkod is NOT at fault: it emits");
            System.out.println("distinct boolean assignments (isomorphic-but-distinct instances)");
            System.out.println("because static symmetry breaking is incomplete by design; Alloy");
            System.out.println("then renders these to identical output without deduplicating on");
            System.out.println("the API path. Fix belongs in the shared A4Solution enumeration,");
            System.out.println("not in SimpleReporter.");
        } else {
            System.out.println("\nNo duplicates observed in this run/config.");
        }
    }
}