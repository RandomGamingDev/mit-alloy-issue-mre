sig Node {
  succ: set Node
}

pred Invariant {
  some Node
  all node : Node | one node.succ and one succ.node
  all node : Node | node in node.^succ
  all node1, node2: Node | node1 in node2.^succ
}

run { Invariant } for 3 expect 1
