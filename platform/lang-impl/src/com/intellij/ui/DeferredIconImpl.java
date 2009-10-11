/*
 * @author max
 */
package com.intellij.ui;

import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.util.Alarm;
import com.intellij.util.Function;
import com.intellij.util.ui.EmptyIcon;

import javax.swing.*;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.LinkedHashSet;
import java.util.Set;

public class DeferredIconImpl<T> implements DeferredIcon {
  private static final RepaintScheduler ourRepaintScheduler = new RepaintScheduler();
  private volatile Icon myDelegateIcon;
  private final Function<T, Icon> myEvaluator;
  private volatile boolean myIsScheduled = false;
  private final T myParam;
  private WeakReference<Component> myLastTarget = null;
  private static final EmptyIcon EMPTY_ICON = new EmptyIcon(16, 16);
  private boolean myNeedReadAction;

  public DeferredIconImpl(Icon baseIcon, T param, Function<T, Icon> evaluator) {
    this(baseIcon, param, true, evaluator);
  }

  public DeferredIconImpl(Icon baseIcon, T param, final boolean needReadAction, Function<T, Icon> evaluator) {
    myParam = param;
    myDelegateIcon = nonNull(baseIcon);
    myEvaluator = evaluator;
    myNeedReadAction = needReadAction;
  }

  private static Icon nonNull(final Icon icon) {
    return icon != null ? icon : EMPTY_ICON;
  }

  public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
    myDelegateIcon.paintIcon(c, g, x, y);

    if (!myIsScheduled) {
      myIsScheduled = true;

      final Component target;

      final Container list = SwingUtilities.getAncestorOfClass(JList.class, c);
      if (list != null) {
        target = list;
      }
      else {
        final Container tree = SwingUtilities.getAncestorOfClass(JTree.class, c);
        if (tree != null) {
          target = tree;
        }
        else {
          final Container table = SwingUtilities.getAncestorOfClass(JTable.class, c);
          if (table != null) {
            target = table;
          }
          else {
            target = c;
          }
        }
      }

      myLastTarget = new WeakReference<Component>(target);

      final Job<Object> job = JobScheduler.getInstance().createJob("Evaluating deferred icon", Job.DEFAULT_PRIORITY);
      job.addTask(new Runnable() {
        public void run() {
          int oldWidth = myDelegateIcon.getIconWidth();
          myDelegateIcon = evaluate();

          final boolean shouldRevalidate = myDelegateIcon.getIconWidth() != oldWidth;

          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              if (shouldRevalidate) {
                // revalidate will not work: jtree caches size of nodes
                if (target instanceof JTree) {
                  final TreeUI ui = ((JTree)target).getUI();
                  if (ui instanceof BasicTreeUI) {
                    // yep, reset size cache
                    ((BasicTreeUI)ui).setLeftChildIndent(((Integer)UIManager.get("Tree.leftChildIndent")).intValue());
                  }
                }
              }

              if (c == target) {
                c.repaint(x, y, getIconWidth(), getIconHeight());
              }
              else {
                ourRepaintScheduler.pushDirtyComponent(target);
              }
            }
          });
        }
      });

      job.schedule();
    }
  }

  public Icon evaluate() {
    final Icon[] evaluated = new Icon[1];
    final Runnable runnable = new Runnable() {
      public void run() {
        try {
          evaluated[0] = nonNull(myEvaluator.fun(myParam));
        }
        catch (ProcessCanceledException e) {
          evaluated[0] = EMPTY_ICON;
        }
        catch (IndexNotReadyException e) {
          evaluated[0] = EMPTY_ICON;
        }
      }
    };
    if (myNeedReadAction) {
      IconDeferrerImpl.evaluateDeferredInReadAction(runnable);
    }
    else {
      IconDeferrerImpl.evaluateDeferred(runnable);
    }

    checkDoesntReferenceThis(evaluated[0]);

    return evaluated[0];
  }

  private void checkDoesntReferenceThis(final Icon icon) {
    if (icon == this) {
      throw new IllegalStateException("Loop in icons delegation");
    }

    if (icon instanceof DeferredIconImpl) {
      checkDoesntReferenceThis(((DeferredIconImpl)icon).myDelegateIcon);
    }
    else if (icon instanceof LayeredIcon) {
      for (Icon layer : ((LayeredIcon)icon).getAllLayers()) {
        checkDoesntReferenceThis(layer);
      }
    }
    else if (icon instanceof RowIcon) {
      final RowIcon rowIcon = (RowIcon)icon;
      final int count = rowIcon.getIconCount();
      for (int i = 0; i < count; i++) {
        checkDoesntReferenceThis(rowIcon.getIcon(i));
      }
    }
  }

  public int getIconWidth() {
    return myDelegateIcon.getIconWidth();
  }

  public int getIconHeight() {
    return myDelegateIcon.getIconHeight();
  }

  public void invalidate() {
    myIsScheduled = false;
    Component lastTarget = myLastTarget != null ? myLastTarget.get() : null;
    if (lastTarget != null) {
      lastTarget.repaint();
    }
  }

  private static class RepaintScheduler {
    private final Alarm myAlarm = new Alarm();
    private final Set<Component> myQueue = new LinkedHashSet<Component>();

    public void pushDirtyComponent(Component c) {
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(new Runnable() {
        public void run() {
          for (Component component : myQueue) {
            component.repaint();
          }
          myQueue.clear();
        }
      }, 50);

      myQueue.add(c);
    }
  }
}
