import kodkod.ast.*;
import kodkod.engine.*;
import kodkod.instance.*;
import kodkod.engine.satlab.SATFactory;
import java.util.*;

public class SiloedMRE {
    public static void main(String[] args) throws Exception {
        Universe univ = new Universe("N0", "N1", "N2");
        Relation succ = Relation.nary("succ", 2);
        Bounds bounds = new Bounds(univ);
        bounds.bound(succ, univ.factory().allOf(2));
        
        Variable n = Variable.unary("n");
        Formula cycle = n.join(succ).one().and(succ.join(n).one())
            .and(Relation.UNIV.in(n.join(succ.closure()))).forAll(n.oneOf(Relation.UNIV));
        
        Solver solver = new Solver();
        solver.options().setSolver(SATFactory.DefaultSAT4J);
        solver.options().setSymmetryBreaking(0); // Simulate incomplete static symmetry breaking
        
        System.out.println("Finding all isomorphic cycle graphs:\n");
        Iterator<Solution> it = solver.solveAll(cycle, bounds);
        for (int i = 0; i < 2 && it.hasNext(); i++) {
            Solution sol = it.next();
            if (!sol.sat()) break;
            
            TupleSet raw = sol.instance().tuples(succ);
            System.out.println("Solution " + i + " Raw SAT Mapping: " + raw);
            
            // Prove graph isomorphism by hashing the structure (canonicalization)
            Map<String, String> map = new HashMap<>();
            List<String> canonical = new ArrayList<>();
            for (Tuple t : raw) {
                map.putIfAbsent((String)t.atom(0), "Node$" + map.size());
                map.putIfAbsent((String)t.atom(1), "Node$" + map.size());
                canonical.add("[" + map.get(t.atom(0)) + "->" + map.get(t.atom(1)) + "]");
            }
            Collections.sort(canonical);
            System.out.println("Solution " + i + " Canonical Graph: " + canonical + "\n");
        }
    }
}
