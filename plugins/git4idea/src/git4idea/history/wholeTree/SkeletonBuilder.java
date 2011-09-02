/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.openapi.vcs.Ring;
import com.intellij.util.SmartList;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.ReadonlyList;

import java.util.*;

/**
 * @author irengrig
 */
public class SkeletonBuilder {
  private final WireEventsListener mySkeleton;
  private final MultiMap<AbstractHash, WaitingItem> myAwaitingParents;
  private final MultiMap<Integer, WaitingItem> myBackIndex;
  // next available idx
  private final Ring.IntegerRing myRing;
  private final BidirectionalMap<Integer, Integer> mySeizedWires;
  // !! can intersect with existing, but own lifecycle
  private final BidirectionalMap<Integer, AbstractHash> myFutureSeizedWires;
  private final Convertor<Integer,List<Integer>> myFutureConvertor;

  public SkeletonBuilder(WireEventsListener treeNavigation) {
    mySkeleton = treeNavigation;

    myAwaitingParents = new MultiMap<AbstractHash, WaitingItem>();
    myBackIndex = new MultiMap<Integer, WaitingItem>();
    myRing = new Ring.IntegerRing();
    // wire number -> last commit on that wire
    mySeizedWires = new BidirectionalMap<Integer, Integer>();
    myFutureSeizedWires = new BidirectionalMap<Integer, AbstractHash>();
    myFutureConvertor = new Convertor<Integer, List<Integer>>() {
      @Override
      public List<Integer> convert(Integer o) {
        return getFutureWireStarts(o);
      }
    };
  }

  public void consume(final CommitI commitI, final List<AbstractHash> parents, final ReadonlyList<CommitI> commits, final int rowCount) {
    int wireNumber = -1;

    // will become real!
    // todo: superflous information both in waiting item and in future map
    myFutureSeizedWires.removeValue(commitI.getHash());
    final Collection<WaitingItem> awaiting = myAwaitingParents.remove(commitI.getHash());

    if (awaiting != null) {
      final List<WaitingItem> awaitingList = (List<WaitingItem>) awaiting;
      if (awaitingList.size() > 1) {
        Collections.sort(awaitingList, CommitsComparator.getInstance());
      }

      final List<WaitingItem> willReturnTheirWires = new SmartList<WaitingItem>();
      for (final WaitingItem waiting : awaitingList) {
        Collection<WaitingItem> waitingCommits = myBackIndex.get(waiting.myIdx);
        waitingCommits.remove(waiting);
        if (waitingCommits.isEmpty()) {
          myBackIndex.remove(waiting.myIdx);
        }

        if (wireNumber == -1) {
          wireNumber = waiting.getWire();
        } else {
          assert wireNumber == waiting.getWire();
        }

        final CommitI waitingI = commits.get(waiting.myIdx);
        //waiting.parentFound();

        if (waiting.isMerge()) {
          // put/update start event - now we know index so can create/update wire event
          mySkeleton.addStartToEvent(waiting.myIdx, rowCount);
        }

        final Integer seized = mySeizedWires.get(waitingI.getWireNumber());
        AbstractHash something = myFutureSeizedWires.get(waitingI.getWireNumber());
        if (seized != null && seized == waiting.myIdx && waitingI.getWireNumber() != wireNumber && something == null) {
          // return
          willReturnTheirWires.add(waiting);
          /*if (wireNumber == -1) {     // if this commit still doesn't have wire
            // there is no other commits on the wire after parent -> use it
            wireNumber = waitingI.getWireNumber();
          }
          else {
            // if there are no other children of that commit. wire dies
            if (waiting.allParentsFound()) {
              myRing.back(waitingI.getWireNumber());
              // end of waiting commits' wire
              mySkeleton.parentWireEnds(rowCount, waiting.myIdx);
            }
          }*/
        }
      }

      for (WaitingItem waitingItem : willReturnTheirWires) {
        final CommitI waitingI = commits.get(waitingItem.myIdx);
        myRing.back(waitingI.getWireNumber());
        mySeizedWires.remove(waitingI.getWireNumber());
        mySkeleton.parentWireEnds(rowCount, waitingItem.myIdx);
      }

      // event about branch!
      if (awaitingList.size() > 1) {
        // merge event
        //mySkeleton.parentWireEnds();  // fix?
        final int[] ends = new int[awaitingList.size()];
        for (int i = 0; i < awaitingList.size(); i++) {
          final WaitingItem waiting = awaitingList.get(i);
          ends[i] = waiting.myIdx;
        }
        mySkeleton.setEnds(rowCount, ends);
      }
    } else {
      // a start (head): no children. Use new wire
      wireNumber = myRing.getFree();
      // this is start
      mySkeleton.wireStarts(rowCount);
      mySkeleton.setEnds(rowCount, new int[] {-1});
    }

    // register what we choose
    if (mySeizedWires.containsValue(rowCount) && mySeizedWires.getKeysByValue(rowCount).iterator().next() != wireNumber) {
      System.out.println("caught on adding!");
    }
    mySeizedWires.put(wireNumber, rowCount);
    commitI.setWireNumber(wireNumber);

    if (parents.isEmpty()) {
      // end event
      mySkeleton.wireEnds(rowCount);
      // free
      myRing.back(wireNumber);
      mySeizedWires.remove(wireNumber);
    } else {
      boolean selfUsed = false;
      for (AbstractHash parent : parents) {
        WaitingItem item;
        Collection<WaitingItem> existing = myAwaitingParents.get(parent);
        if (existing != null && ! existing.isEmpty()) {
          // use its wire!
          item = new WaitingItem(rowCount, existing.iterator().next().getWire(), parents.size() > 1);
        } else {
          // a start (head): no children. Use new wire
          Integer parentWire;
          if (! selfUsed) {
            parentWire = wireNumber;
            selfUsed = true;
          }
          else {
            // this is start
            parentWire = myRing.getFree();
            mySkeleton.wireStarts(rowCount);
          }
          myFutureSeizedWires.put(parentWire, parent);
          item = new WaitingItem(rowCount, parentWire, parents.size() > 1);
        }
        myAwaitingParents.putValue(parent, item);
        myBackIndex.putValue(item.myIdx, item);
      }
    }
  }
  
  public void oldBecameNew(final Map<Integer, Integer> map) {
    final MultiMap<Integer, WaitingItem> backCopy = new MultiMap<Integer, WaitingItem>();
    backCopy.putAllValues(myBackIndex);

    myBackIndex.clear();
    for (Map.Entry<Integer, Collection<WaitingItem>> entry : backCopy.entrySet()) {
      Integer key = map.get(entry.getKey());
      Collection<WaitingItem> items = entry.getValue();
      if (key == null) {
        key = entry.getKey();
      }
      else {
        for (WaitingItem item : items) {
          item.myIdx = key;
        }
      }
      myBackIndex.put(key, items);
    }
    
    // seized
    BidirectionalMap<Integer, Integer> copy = new BidirectionalMap<Integer, Integer>();
    copy.putAll(mySeizedWires);
    mySeizedWires.clear();

    for (Integer oldIdx : copy.values()) {
      List<Integer> wires = copy.getKeysByValue(oldIdx);
      if (wires == null || wires.size() != 1) {
        System.out.println("www");
      }
      assert (wires != null && wires.size() == 1);
      Integer newIdx = map.get(oldIdx);
      newIdx = newIdx == null ? oldIdx : newIdx;
      mySeizedWires.put(wires.get(0), newIdx);
    }
  }

  /*public void oldBecameNew(int was, int is) {
    Collection<WaitingItem> removed = myBackIndex.remove(was);
    if (removed != null) {
      for (WaitingItem commit : removed) {
        commit.myIdx = is;
      }
      myBackIndex.put(is, removed);
    }

    List<Integer> keysByValue = mySeizedWires.getKeysByValue(was);
    if (keysByValue != null && ! keysByValue.isEmpty()) {
      if (keysByValue.size() > 1) {
        System.out.println("Ooops!");
      }
      assert keysByValue.size() == 1; // each commit only on one wire
      mySeizedWires.remove(was);
      int value = keysByValue.get(0);
      mySeizedWires.put(value, is);
    }
  }*/
  
  // just some order
  private static class CommitsComparator implements Comparator<WaitingItem> {
    private final static CommitsComparator ourInstance = new CommitsComparator();

    public static CommitsComparator getInstance() {
      return ourInstance;
    }
    
    @Override
    public int compare(WaitingItem wc1, WaitingItem wc2) {
      return new Integer(wc1.getWire()).compareTo(wc2.getWire());
    }
  }
  
  private static class WaitingItem {
    private int myIdx;
    private int myWire;
    private boolean myIsMerge;

    private WaitingItem(int idx, int wire, boolean isMerge) {
      myIdx = idx;
      myWire = wire;
      myIsMerge = isMerge;
    }

    public boolean isMerge() {
      return myIsMerge;
    }

    public int getIdx() {
      return myIdx;
    }

    public int getWire() {
      return myWire;
    }
  }

  private static class WaitingCommit {
    private int myIdx;        // id of commit that's wait for parents (self)
    private int myNumParents; // i.e. a start
    private final boolean myIsMerge;
    private int myWire;

    private WaitingCommit(int idx, int numParents) {
      myIdx = idx;
      myNumParents = numParents;
      myIsMerge = myNumParents > 1;
    }

    public boolean isMerge() {
      return myIsMerge;
    }

    public void parentFound() {
      -- myNumParents;
    }

    public boolean allParentsFound() {
      return myNumParents == 0;
    }

    public int getWire() {
      return myWire;
    }

    public void setWire(int wire) {
      myWire = wire;
    }
  }
  
  public List<Integer> getFutureWireStarts(final int idx) {
    Collection<WaitingItem> waitingItems = myBackIndex.get(idx);
    if (waitingItems == null || waitingItems.isEmpty()) return Collections.emptyList();
    final List<Integer> result = new ArrayList<Integer>();
    for (WaitingItem item : waitingItems) {
      result.add(item.getWire());
    }
    return result;
  }

  public Convertor<Integer, List<Integer>> getFutureConvertor() {
    return myFutureConvertor;
  }
}
