import kodkod.ast.*;
import kodkod.engine.*;
import kodkod.instance.*;
import kodkod.engine.satlab.SATFactory;
import java.util.*;

public class SiloedMRE {
    public static void main(String[] args) throws Exception {
        // A universe of 4 nodes
        Universe univ = new Universe("N0", "N1", "N2", "N3");
        Relation r = Relation.nary("r", 2);
        Bounds bounds = new Bounds(univ);
        bounds.bound(r, univ.factory().allOf(2));
        
        // Constraint: relation r has exactly 2 edges
        Formula f = r.count().eq(IntConstant.constant(2));
        
        Solver solver = new Solver();
        solver.options().setSolver(SATFactory.DefaultSAT4J);
        // We explicitly ENABLE static symmetry breaking.
        // Kodkod's static symmetry breaking is incomplete for many symmetric states.
        solver.options().setSymmetryBreaking(20); 
        solver.options().setBitwidth(4);
        
        System.out.println("Finding all non-isomorphic instances of exactly 2 edges:\n");
        Iterator<Solution> it = solver.solveAll(f, bounds);
        
        int count = 0;
        Set<String> canonicals = new HashSet<>();
        
        while(it.hasNext()) {
            Solution sol = it.next();
            if (!sol.sat()) break;
            
            TupleSet raw = sol.instance().tuples(r);
            
            // Prove graph isomorphism by hashing the structure (canonicalization)
            Map<String, String> map = new HashMap<>();
            List<String> canonical = new ArrayList<>();
            for (Tuple t : raw) {
                map.putIfAbsent((String)t.atom(0), "Node$" + map.size());
                map.putIfAbsent((String)t.atom(1), "Node$" + map.size());
                canonical.add("[" + map.get(t.atom(0)) + "->" + map.get(t.atom(1)) + "]");
            }
            Collections.sort(canonical);
            String canStr = canonical.toString();
            
            if (canonicals.contains(canStr)) {
                System.out.println("=========================================");
                System.out.println("BUG REPRODUCED: IDENTICAL DUPLICATE FOUND");
                System.out.println("=========================================");
                System.out.println("Despite `setSymmetryBreaking(20)`, Kodkod's SolutionIterator yielded:");
                System.out.println("Raw SAT Mapping: " + raw);
                System.out.println("Canonical Graph: " + canStr);
                System.out.println("\nBecause the static symmetry breaker missed this symmetry, the boolean");
                System.out.println("assignment was different, satisfying `AbstractKodkodSolver`'s naive `notModel`");
                System.out.println("blocking clause, thus yielding an isomorphic duplicate.");
                return;
            }
            
            canonicals.add(canStr);
            System.out.println("Solution " + count + " Raw SAT: " + raw + " | Canonical: " + canStr);
            count++;
        }
    }
}
