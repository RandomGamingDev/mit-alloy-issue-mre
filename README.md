# MIT Alloy Issue MRE

## Quickstart

```
./run.sh
```

## Explanation

MIT Alloy has a [hidden issue](https://github.com/AlloyTools/org.alloytools.alloy/blame/master/org.alloytools.alloy.application/src/main/java/edu/mit/csail/sdg/alloy4whole/SimpleReporter.java#L688-L710C6) as well as a downstream GUI-API discprenancy resultant from it:
```java
                if (!sol.satisfiable()) {
                    cb("pop", "There are no more satisfying instances.\n\n" + "Note: due to symmetry breaking and other optimizations,\n" + "some equivalent solutions may have been omitted.");
                    return;
                }
                String toString = sol.toString();
                synchronized (SimpleReporter.class) {
                    if (!latestKodkods.add(toString))
                        if (tries < 100) {
                            tries++;
                            continue;
                        }
                    // The counter is needed to avoid a Kodkod bug where
                    // sometimes we might repeat the same solution infinitely
                    // number of times; this at least allows the user to keep
                    // going
                    writeXML(null, mod, filename, sol, latestKodkodSRC);
                    latestKodkod = sol;
                }
                cb("declare", filename);
                return;
            }
        }
    }
```
However, unlike what the comments suggest, it appears not to be a bug with Kodkod but with Alloy translator, which funnels the differing Kodkod instances into byte-for-byte identical diagrams and resultant XMLs.

This is what **MIT Alloy Issue MRE** demonstrates.

Specifically, Kodkod generates instances like: n0->n0, n1->n1, n2-n2, etc.

However, MIT Alloy flattens these all into n0->n0 via its canonicalization process, resulting in unchecked duplicates.



Notably, it may also be worth taking a look at the symmetry option since, no matter how high it is set, it doesn't seem to prevent the isomorphisms that funnel into the same instance.
