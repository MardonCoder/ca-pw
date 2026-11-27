package org.mardon.index;

import java.util.List;

public final class AugmentedRBTree {
    private static final boolean RED = true;
    private static final boolean BLACK = false;

    static final class Node {
        long parentId;
        long entityId;
        long expiryTime;
        IndexedCacheEntry entry;

        Node left;
        Node right;
        Node parent;
        boolean color;

        // Augmented fields (+20 bytes raw, +24 bytes aligned to 8-byte boundary)
        long minExpiry;
        long maxExpiry;
        int subtreeSize;

        Node() {
            this.color = BLACK;
            this.minExpiry = Long.MAX_VALUE;
            this.maxExpiry = Long.MIN_VALUE;
            this.subtreeSize = 0;
        }

        Node(IndexedCacheEntry entry, Node nil) {
            this.parentId = entry.parentId();
            this.entityId = entry.entityId();
            this.expiryTime = entry.expiryTime();
            this.entry = entry;
            this.left = nil;
            this.right = nil;
            this.parent = nil;
            this.color = RED;
            this.minExpiry = entry.expiryTime();
            this.maxExpiry = entry.expiryTime();
            this.subtreeSize = 1;
        }
    }

    private final Node NIL;
    private Node root;

    public AugmentedRBTree() {
        this.NIL = new Node();
        this.NIL.left = NIL;
        this.NIL.right = NIL;
        this.NIL.parent = NIL;
        this.root = NIL;
    }

    public int size() {
        return root.subtreeSize;
    }

    private static int compareKey(long p1, long c1, long p2, long c2) {
        int cmp = Long.compare(p1, p2);
        return cmp != 0 ? cmp : Long.compare(c1, c2);
    }

    private void pullUp(Node x) {
        if (x == NIL) return;
        x.minExpiry = Math.min(x.expiryTime, Math.min(x.left.minExpiry, x.right.minExpiry));
        x.maxExpiry = Math.max(x.expiryTime, Math.max(x.left.maxExpiry, x.right.maxExpiry));
        x.subtreeSize = 1 + x.left.subtreeSize + x.right.subtreeSize;
    }

    private void pullUpToRoot(Node curr) {
        while (curr != NIL) {
            pullUp(curr);
            curr = curr.parent;
        }
    }

    private void leftRotate(Node x) {
        Node y = x.right;
        x.right = y.left;
        if (y.left != NIL) {
            y.left.parent = x;
        }
        y.parent = x.parent;
        if (x.parent == NIL) {
            root = y;
        } else if (x == x.parent.left) {
            x.parent.left = y;
        } else {
            x.parent.right = y;
        }
        y.left = x;
        x.parent = y;

        // Strict bottom-up recalculation: dropped node first, risen node second
        pullUp(x);
        pullUp(y);
    }

    private void rightRotate(Node y) {
        Node x = y.left;
        y.left = x.right;
        if (x.right != NIL) {
            x.right.parent = y;
        }
        x.parent = y.parent;
        if (y.parent == NIL) {
            root = x;
        } else if (y == y.parent.right) {
            y.parent.right = x;
        } else {
            y.parent.left = x;
        }
        x.right = y;
        y.parent = x;

        pullUp(y);
        pullUp(x);
    }

    public void insertOrUpdate(IndexedCacheEntry entry) {
        Node y = NIL;
        Node x = root;
        while (x != NIL) {
            y = x;
            int cmp = compareKey(entry.parentId(), entry.entityId(), x.parentId, x.entityId);
            if (cmp == 0) {
                x.expiryTime = entry.expiryTime();
                x.entry = entry;
                pullUpToRoot(x);
                return;
            }
            x = cmp < 0 ? x.left : x.right;
        }

        Node z = new Node(entry, NIL);
        z.parent = y;
        if (y == NIL) {
            root = z;
        } else if (compareKey(z.parentId, z.entityId, y.parentId, y.entityId) < 0) {
            y.left = z;
        } else {
            y.right = z;
        }

        pullUpToRoot(z.parent);
        insertFixup(z);
    }

    private void insertFixup(Node z) {
        while (z.parent.color == RED) {
            if (z.parent == z.parent.parent.left) {
                Node uncle = z.parent.parent.right;
                if (uncle.color == RED) {
                    z.parent.color = BLACK;
                    uncle.color = BLACK;
                    z.parent.parent.color = RED;
                    z = z.parent.parent;
                } else {
                    if (z == z.parent.right) {
                        z = z.parent;
                        leftRotate(z);
                    }
                    z.parent.color = BLACK;
                    z.parent.parent.color = RED;
                    rightRotate(z.parent.parent);
                }
            } else {
                Node uncle = z.parent.parent.left;
                if (uncle.color == RED) {
                    z.parent.color = BLACK;
                    uncle.color = BLACK;
                    z.parent.parent.color = RED;
                    z = z.parent.parent;
                } else {
                    if (z == z.parent.left) {
                        z = z.parent;
                        rightRotate(z);
                    }
                    z.parent.color = BLACK;
                    z.parent.parent.color = RED;
                    leftRotate(z.parent.parent);
                }
            }
        }
        root.color = BLACK;
    }

    private Node findNode(long parentId, long entityId) {
        Node curr = root;
        while (curr != NIL) {
            int cmp = compareKey(parentId, entityId, curr.parentId, curr.entityId);
            if (cmp == 0) return curr;
            curr = cmp < 0 ? curr.left : curr.right;
        }
        return NIL;
    }

    private void transplant(Node u, Node v) {
        if (u.parent == NIL) {
            root = v;
        } else if (u == u.parent.left) {
            u.parent.left = v;
        } else {
            u.parent.right = v;
        }
        v.parent = u.parent;
    }

    private Node minimum(Node x) {
        while (x.left != NIL) {
            x = x.left;
        }
        return x;
    }

    public boolean delete(long parentId, long entityId) {
        Node z = findNode(parentId, entityId);
        if (z == NIL) return false;
        deleteNode(z);
        return true;
    }

    private void deleteNode(Node z) {
        Node y = z;
        boolean yOriginalColor = y.color;
        Node x;
        Node lowestAffected;

        if (z.left == NIL) {
            x = z.right;
            lowestAffected = z.parent;
            transplant(z, z.right);
        } else if (z.right == NIL) {
            x = z.left;
            lowestAffected = z.parent;
            transplant(z, z.left);
        } else {
            y = minimum(z.right);
            yOriginalColor = y.color;
            x = y.right;
            if (y.parent == z) {
                x.parent = y;
                lowestAffected = y;
            } else {
                lowestAffected = y.parent;
                transplant(y, y.right);
                y.right = z.right;
                y.right.parent = y;
            }
            transplant(z, y);
            y.left = z.left;
            y.left.parent = y;
            y.color = z.color;
        }

        // Update augmented fields BEFORE fixup rotations rely on child metadata
        pullUpToRoot(lowestAffected);

        if (yOriginalColor == BLACK) {
            deleteFixup(x);
        }
        NIL.parent = NIL;
    }

    private void deleteFixup(Node x) {
        while (x != root && x.color == BLACK) {
            if (x == x.parent.left) {
                Node w = x.parent.right;
                if (w.color == RED) {
                    w.color = BLACK;
                    x.parent.color = RED;
                    leftRotate(x.parent);
                    w = x.parent.right;
                }
                if (w.left.color == BLACK && w.right.color == BLACK) {
                    w.color = RED;
                    x = x.parent;
                } else {
                    if (w.right.color == BLACK) {
                        w.left.color = BLACK;
                        w.color = RED;
                        rightRotate(w);
                        w = x.parent.right;
                    }
                    w.color = x.parent.color;
                    x.parent.color = BLACK;
                    w.right.color = BLACK;
                    leftRotate(x.parent);
                    x = root;
                }
            } else {
                Node w = x.parent.left;
                if (w.color == RED) {
                    w.color = BLACK;
                    x.parent.color = RED;
                    rightRotate(x.parent);
                    w = x.parent.left;
                }
                if (w.right.color == BLACK && w.left.color == BLACK) {
                    w.color = RED;
                    x = x.parent;
                } else {
                    if (w.left.color == BLACK) {
                        w.right.color = BLACK;
                        w.color = RED;
                        leftRotate(w);
                        w = x.parent.left;
                    }
                    w.color = x.parent.color;
                    x.parent.color = BLACK;
                    w.left.color = BLACK;
                    rightRotate(x.parent);
                    x = root;
                }
            }
        }
        x.color = BLACK;
    }

    // --- New Augmented Operations ---

    public void batchResolve(long parentId, long now, List<IndexedCacheEntry> result) {
        batchResolveRec(root, parentId, now, result);
    }

    private void batchResolveRec(Node node, long parentId, long now, List<IndexedCacheEntry> result) {
        if (node == NIL || node.maxExpiry <= now) {
            return; // O(1) subtree pruning
        }
        if (node.parentId < parentId) {
            batchResolveRec(node.right, parentId, now, result);
        } else if (node.parentId > parentId) {
            batchResolveRec(node.left, parentId, now, result);
        } else {
            batchResolveRec(node.left, parentId, now, result);
            if (node.expiryTime > now) {
                result.add(node.entry);
            }
            batchResolveRec(node.right, parentId, now, result);
        }
    }

    public long nextExpiration() {
        return root == NIL ? Long.MAX_VALUE : root.minExpiry;
    }

    public IndexedCacheEntry deleteEarliestExpired(long now) {
        if (root == NIL || root.minExpiry > now) {
            return null;
        }
        Node target = findEarliestExpired(root);
        IndexedCacheEntry removed = target.entry;
        deleteNode(target);
        return removed;
    }

    private Node findEarliestExpired(Node node) {
        Node curr = node;
        while (true) {
            if (curr.expiryTime == curr.minExpiry) {
                return curr;
            }
            if (curr.left != NIL && curr.left.minExpiry == curr.minExpiry) {
                curr = curr.left;
            } else {
                curr = curr.right;
            }
        }
    }

    public int countParentEntries(long parentId) {
        return countParentLessOrEqual(parentId) - countParentLess(parentId);
    }

    private int countParentLess(long parentId) {
        int count = 0;
        Node curr = root;
        while (curr != NIL) {
            if (curr.parentId < parentId) {
                count += curr.left.subtreeSize + 1;
                curr = curr.right;
            } else {
                curr = curr.left;
            }
        }
        return count;
    }

    private int countParentLessOrEqual(long parentId) {
        int count = 0;
        Node curr = root;
        while (curr != NIL) {
            if (curr.parentId <= parentId) {
                count += curr.left.subtreeSize + 1;
                curr = curr.right;
            } else {
                curr = curr.left;
            }
        }
        return count;
    }

    // --- Full Invariant Verification (used in JUnit tests) ---

    public void verifyInvariants() {
        if (NIL.color != BLACK || NIL.subtreeSize != 0
                || NIL.minExpiry != Long.MAX_VALUE || NIL.maxExpiry != Long.MIN_VALUE) {
            throw new AssertionError("NIL sentinel corrupted");
        }
        if (root != NIL && root.color != BLACK) {
            throw new AssertionError("Root must be BLACK");
        }
        if (root != NIL && root.parent != NIL) {
            throw new AssertionError("Root parent must be NIL");
        }
        verifySubtree(root, null, null);
    }

    private int verifySubtree(Node node, Node minBound, Node maxBound) {
        if (node == NIL) {
            return 1;
        }
        if (minBound != null && compareKey(minBound.parentId, minBound.entityId, node.parentId, node.entityId) >= 0) {
            throw new AssertionError("BST ordering violated (lower bound)");
        }
        if (maxBound != null && compareKey(node.parentId, node.entityId, maxBound.parentId, maxBound.entityId) >= 0) {
            throw new AssertionError("BST ordering violated (upper bound)");
        }
        if (node.left != NIL && node.left.parent != node) {
            throw new AssertionError("Broken left parent pointer");
        }
        if (node.right != NIL && node.right.parent != node) {
            throw new AssertionError("Broken right parent pointer");
        }
        if (node.color == RED && (node.left.color == RED || node.right.color == RED)) {
            throw new AssertionError("Red node has red child");
        }

        int leftBH = verifySubtree(node.left, minBound, node);
        int rightBH = verifySubtree(node.right, node, maxBound);
        if (leftBH != rightBH) {
            throw new AssertionError("Black-height mismatch: " + leftBH + " vs " + rightBH);
        }

        long expectedMin = Math.min(node.expiryTime, Math.min(node.left.minExpiry, node.right.minExpiry));
        long expectedMax = Math.max(node.expiryTime, Math.max(node.left.maxExpiry, node.right.maxExpiry));
        int expectedSize = 1 + node.left.subtreeSize + node.right.subtreeSize;

        if (node.minExpiry != expectedMin || node.maxExpiry != expectedMax || node.subtreeSize != expectedSize) {
            throw new AssertionError("Augmented fields mismatch at (" + node.parentId + "," + node.entityId + ")");
        }

        return leftBH + (node.color == BLACK ? 1 : 0);
    }
}