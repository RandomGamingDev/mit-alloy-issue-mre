package edu.mit.csail.sdg.translator;

import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.ast.Command;
import edu.mit.csail.sdg.ast.Sig;
import edu.mit.csail.sdg.parser.CompModule;
import edu.mit.csail.sdg.parser.CompUtil;
import kodkod.ast.Relation;
import kodkod.engine.Evaluator;
import kodkod.instance.Bounds;

import java.lang.reflect.Field;

public class DuplicateMRE {
    public static void main(String[] args) throws Exception {
        A4Reporter reporter = new A4Reporter();
        CompModule world = CompUtil.parseEverything_fromFile(reporter, null, "Model.als");

        A4Options options = new A4Options();
        // Use a non-prover solver to trigger the incomplete symmetry breaking issue
        options.solver = kodkod.engine.satlab.SATFactory.get("sat4j");
        // Force skolemDepth to 0 to prove this isn't caused by hidden skolem variables
        options.skolemDepth = 0;
        options.symmetry = 20;

        for (Command command : world.getAllCommands()) {
            if (!command.check) {
                A4Solution solution = TranslateAlloyToKodkod.execute_command(
                        reporter, world.getAllReachableSigs(), command, options);

                int count = 0;
                while (solution.satisfiable() && count < 2) {
                    System.out.println("=========================================");
                    System.out.println("Solution " + count + ":");
                    System.out.println("=========================================");
                    
                    // 1. Print raw Kodkod assignments using Reflection
                    System.out.println("RAW KODKOD ASSIGNMENTS (The actual boolean SAT mapping):");
                    Field f = A4Solution.class.getDeclaredField("eval");
                    f.setAccessible(true);
                    Evaluator eval = (Evaluator) f.get(solution);
                    
                    Bounds bounds = solution.getBounds();
                    for (Relation r : bounds.relations()) {
                        if (r.name().contains("this/Node")) {
                            System.out.println("  " + r.name() + " -> " + eval.instance().tuples(r));
                        }
                    }

                    // 2. Print Canonicalized Alloy assignments
                    System.out.println("\nCANONICALIZED ALLOY OUTPUT (What the user sees in the GUI/XML):");
                    for (Sig s : world.getAllReachableSigs()) {
                        if (s.label.equals("this/Node")) {
                            System.out.println("  this/Node -> " + solution.eval(s));
                            if (!s.getFields().isEmpty()) {
                                System.out.println("  this/Node.succ -> " + solution.eval(s.getFields().get(0)));
                            }
                        }
                    }
                    System.out.println();
                    
                    count++;
                    solution = solution.next();
                }
                
                System.out.println("CONCLUSION:");
                System.out.println("Notice that the RAW KODKOD ASSIGNMENTS are using different symmetric atoms (e.g., Node$1 vs Node$2).");
                System.out.println("Because these are different boolean variables, Kodkod's `notModel` blocking clause is satisfied, so it yields it as a 'new' solution.");
                System.out.println("However, because the graph is isomorphic, Alloy's canonicalization (A4Solution.rename) maps both solutions to `Node$0`, causing identical duplicate XMLs/outputs.");
                
                break;
            }
        }
    }
}
