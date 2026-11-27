package org.mardon.index;

import java.util.*;

public final class StandardRBTreeRegion implements L2RegionIndex {
    private static final boolean RED = true;
    private static final boolean BLACK = false;

    private static final class Node {
        long parentId;
        long entityId;
        long expiryTime;
        IndexedCacheEntry entry;
        Node left, right, parent;
        boolean color;

        Node() {
            this.color = BLACK;
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
        }
    }

    private final Map<Long, IndexedCacheEntry> primaryStore;
    private final Node NIL;
    private Node root;

    public StandardRBTreeRegion(int expectedCapacity) {
        this.primaryStore = new HashMap<>(expectedCapacity);
        this.NIL = new Node();
        this.NIL.left = NIL;
        this.NIL.right = NIL;
        this.NIL.parent = NIL;
        this.root = NIL;
    }

    private static int compareKey(long p1, long c1, long p2, long c2) {
        int cmp = Long.compare(p1, p2);
        return cmp != 0 ? cmp : Long.compare(c1, c2);
    }

    @Override
    public IndexedCacheEntry get(long entityId, long now) {
        IndexedCacheEntry entry = primaryStore.get(entityId);
        return (entry != null && entry.expiryTime() > now) ? entry : null;
    }

    @Override
    public void put(IndexedCacheEntry entry) {
        IndexedCacheEntry existing = primaryStore.put(entry.entityId(), entry);
        if (existing != null && existing.parentId() != entry.parentId()) {
            deleteKey(existing.parentId(), existing.entityId());
        }
        insertOrUpdate(entry);
    }

    @Override
    public boolean remove(long entityId) {
        IndexedCacheEntry removed = primaryStore.remove(entityId);
        if (removed == null) return false;
        deleteKey(removed.parentId(), removed.entityId());
        return true;
    }

    @Override
    public List<IndexedCacheEntry> batchResolve(long parentId, long now) {
        List<IndexedCacheEntry> result = new ArrayList<>();
        batchResolveRec(root, parentId, now, result);
        return result;
    }

    private void batchResolveRec(Node node, long parentId, long now, List<IndexedCacheEntry> result) {
        if (node == NIL) return; // No maxExpiry pruning possible
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

    @Override
    public long nextExpiration() {
        long min = Long.MAX_VALUE;
        for (IndexedCacheEntry e : primaryStore.values()) {
            if (e.expiryTime() < min) min = e.expiryTime();
        }
        return min;
    }

    @Override
    public int evictExpired(long now, int limit) {
        if (limit <= 0) return 0;
        List<IndexedCacheEntry> toEvict = new ArrayList<>(Math.min(limit, 1024));
        for (IndexedCacheEntry e : primaryStore.values()) {
            if (e.expiryTime() <= now) {
                toEvict.add(e);
                if (toEvict.size() == limit) break;
            }
        }
        for (IndexedCacheEntry e : toEvict) {
            primaryStore.remove(e.entityId());
            deleteKey(e.parentId(), e.entityId());
        }
        return toEvict.size();
    }

    @Override
    public int countParentEntries(long parentId) {
        return countRangeRec(root, parentId);
    }

    private int countRangeRec(Node node, long parentId) {
        if (node == NIL) return 0;
        if (node.parentId < parentId) return countRangeRec(node.right, parentId);
        if (node.parentId > parentId) return countRangeRec(node.left, parentId);
        return 1 + countRangeRec(node.left, parentId) + countRangeRec(node.right, parentId);
    }

    @Override
    public int size() {
        return primaryStore.size();
    }

    // --- Standard RB-Tree internals (no augmented fields) ---

    private void leftRotate(Node x) {
        Node y = x.right;
        x.right = y.left;
        if (y.left != NIL) y.left.parent = x;
        y.parent = x.parent;
        if (x.parent == NIL) root = y;
        else if (x == x.parent.left) x.parent.left = y;
        else x.parent.right = y;
        y.left = x;
        x.parent = y;
    }

    private void rightRotate(Node y) {
        Node x = y.left;
        y.left = x.right;
        if (x.right != NIL) x.right.parent = y;
        x.parent = y.parent;
        if (y.parent == NIL) root = x;
        else if (y == y.parent.right) y.parent.right = x;
        else y.parent.left = x;
        x.right = y;
        y.parent = x;
    }

    private void insertOrUpdate(IndexedCacheEntry entry) {
        Node y = NIL, x = root;
        while (x != NIL) {
            y = x;
            int cmp = compareKey(entry.parentId(), entry.entityId(), x.parentId, x.entityId);
            if (cmp == 0) {
                x.expiryTime = entry.expiryTime();
                x.entry = entry;
                return;
            }
            x = cmp < 0 ? x.left : x.right;
        }
        Node z = new Node(entry, NIL);
        z.parent = y;
        if (y == NIL) root = z;
        else if (compareKey(z.parentId, z.entityId, y.parentId, y.entityId) < 0) y.left = z;
        else y.right = z;
        insertFixup(z);
    }

    private void insertFixup(Node z) {
        while (z.parent.color == RED) {
            if (z.parent == z.parent.parent.left) {
                Node u = z.parent.parent.right;
                if (u.color == RED) {
                    z.parent.color = BLACK;
                    u.color = BLACK;
                    z.parent.parent.color = RED;
                    z = z.parent.parent;
                } else {
                    if (z == z.parent.right) { z = z.parent; leftRotate(z); }
                    z.parent.color = BLACK;
                    z.parent.parent.color = RED;
                    rightRotate(z.parent.parent);
                }
            } else {
                Node u = z.parent.parent.left;
                if (u.color == RED) {
                    z.parent.color = BLACK;
                    u.color = BLACK;
                    z.parent.parent.color = RED;
                    z = z.parent.parent;
                } else {
                    if (z == z.parent.left) { z = z.parent; rightRotate(z); }
                    z.parent.color = BLACK;
                    z.parent.parent.color = RED;
                    leftRotate(z.parent.parent);
                }
            }
        }
        root.color = BLACK;
    }

    private void transplant(Node u, Node v) {
        if (u.parent == NIL) root = v;
        else if (u == u.parent.left) u.parent.left = v;
        else u.parent.right = v;
        v.parent = u.parent;
    }

    private Node minimum(Node x) {
        while (x.left != NIL) x = x.left;
        return x;
    }

    private void deleteKey(long parentId, long entityId) {
        Node z = root;
        while (z != NIL) {
            int cmp = compareKey(parentId, entityId, z.parentId, z.entityId);
            if (cmp == 0) break;
            z = cmp < 0 ? z.left : z.right;
        }
        if (z == NIL) return;

        Node y = z;
        boolean yOriginalColor = y.color;
        Node x;
        if (z.left == NIL) {
            x = z.right;
            transplant(z, z.right);
        } else if (z.right == NIL) {
            x = z.left;
            transplant(z, z.left);
        } else {
            y = minimum(z.right);
            yOriginalColor = y.color;
            x = y.right;
            if (y.parent == z) {
                x.parent = y;
            } else {
                transplant(y, y.right);
                y.right = z.right;
                y.right.parent = y;
            }
            transplant(z, y);
            y.left = z.left;
            y.left.parent = y;
            y.color = z.color;
        }
        if (yOriginalColor == BLACK) deleteFixup(x);
        NIL.parent = NIL;
    }

    private void deleteFixup(Node x) {
        while (x != root && x.color == BLACK) {
            if (x == x.parent.left) {
                Node w = x.parent.right;
                if (w.color == RED) { w.color = BLACK; x.parent.color = RED; leftRotate(x.parent); w = x.parent.right; }
                if (w.left.color == BLACK && w.right.color == BLACK) { w.color = RED; x = x.parent; }
                else {
                    if (w.right.color == BLACK) { w.left.color = BLACK; w.color = RED; rightRotate(w); w = x.parent.right; }
                    w.color = x.parent.color; x.parent.color = BLACK; w.right.color = BLACK; leftRotate(x.parent); x = root;
                }
            } else {
                Node w = x.parent.left;
                if (w.color == RED) { w.color = BLACK; x.parent.color = RED; rightRotate(x.parent); w = x.parent.left; }
                if (w.right.color == BLACK && w.left.color == BLACK) { w.color = RED; x = x.parent; }
                else {
                    if (w.left.color == BLACK) { w.right.color = BLACK; w.color = RED; leftRotate(w); w = x.parent.left; }
                    w.color = x.parent.color; x.parent.color = BLACK; w.left.color = BLACK; rightRotate(x.parent); x = root;
                }
            }
        }
        x.color = BLACK;
    }
}