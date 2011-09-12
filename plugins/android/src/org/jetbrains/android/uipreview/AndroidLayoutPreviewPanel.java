 package org.jetbrains.android.uipreview;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.HyperlinkLabel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLayoutPreviewPanel extends JPanel {
  private static final double EPS = 0.0000001;
  private static final double MAX_ZOOM_FACTOR = 2.0;
  private static final double ZOOM_STEP = 1.25;

  private RenderingErrorMessage myErrorMessage;
  private String myWarnMessage;
  private BufferedImage myImage;

  private final HyperlinkLabel myErrorLabel = new HyperlinkLabel("", Color.BLUE, getBackground(), Color.BLUE);

  private double myZoomFactor = 1.0;
  private boolean myZoomToFit = true;

  private final JPanel myImagePanel = new JPanel() {
    @Override
    public void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (myImage == null) {
        return;
      }
      final Dimension scaledDimension = getScaledDimension();
      final Graphics2D g2 = (Graphics2D)g;
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.drawImage(myImage, 0, 0, scaledDimension.width, scaledDimension.height, 0, 0, myImage.getWidth(), myImage.getHeight(), null);
    }
  };

  public AndroidLayoutPreviewPanel() {
    super(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true));
    setBackground(Color.WHITE);
    setOpaque(true);
    myImagePanel.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.GRAY));

    myErrorLabel.addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(final HyperlinkEvent e) {
        final Runnable quickFix = myErrorMessage.myQuickFix;
        if (quickFix != null && e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          quickFix.run();
        }
      }
    });
    myErrorLabel.setOpaque(false);

    add(myErrorLabel);
    add(new MyImagePanelWrapper());
  }

  public void setImage(@Nullable final BufferedImage image) {
    myImage = image;
    doRevalidate();
  }

  private void doRevalidate() {
    revalidate();
    updateImageSize();
    repaint();
  }

  public void setErrorMessage(@Nullable RenderingErrorMessage errorMessage) {
    myErrorMessage = errorMessage;
  }

  public void setWarnMessage(String warnMessage) {
    myWarnMessage = warnMessage;
  }

  public void update() {
    if (myErrorMessage != null) {
      myErrorLabel.setHyperlinkText(myErrorMessage.myBeforeLinkText,
                                    myErrorMessage.myLinkText,
                                    myErrorMessage.myAfterLinkText);
      myErrorLabel.setIcon(Messages.getErrorIcon());
      myErrorLabel.setVisible(true);
    }
    else if (myWarnMessage != null && myWarnMessage.length() > 0) {
      myErrorLabel.setHyperlinkText(myWarnMessage, "", "");
      myErrorLabel.setIcon(Messages.getWarningIcon());
      myErrorLabel.setVisible(true);
    }
    else {
      myErrorLabel.setVisible(false);
    }

    repaint();
  }

  void updateImageSize() {
    if (myImage == null) {
      myImagePanel.setSize(0, 0);
    }
    else {
      myImagePanel.setSize(getScaledDimension());
    }
  }

  private Dimension getScaledDimension() {
    if (myZoomToFit) {
      final Dimension panelSize = getParent().getSize();
      if (myImage.getWidth() <= panelSize.width && myImage.getHeight() <= panelSize.height) {
        return new Dimension(myImage.getWidth(), myImage.getHeight());
      }

      if (myImage.getWidth() <= panelSize.width) {
        final double f = panelSize.getHeight() / myImage.getHeight();
        return new Dimension((int)(myImage.getWidth() * f), (int)(myImage.getHeight() * f));
      }
      else if (myImage.getHeight() <= panelSize.height) {
        final double f = panelSize.getWidth() / myImage.getWidth();
        return new Dimension((int)(myImage.getWidth() * f), (int)(myImage.getHeight() * f));
      }

      double f = panelSize.getWidth() / myImage.getWidth();
      int candidateWidth = (int)(myImage.getWidth() * f);
      int candidateHeight = (int)(myImage.getHeight() * f);
      if (candidateWidth <= panelSize.getWidth() && candidateHeight <= panelSize.getHeight()) {
        return new Dimension(candidateWidth, candidateHeight);
      }
      f = panelSize.getHeight() / myImage.getHeight();
      return new Dimension((int)(myImage.getWidth() * f), (int)(myImage.getHeight() * f));
    }
    return new Dimension((int)(myImage.getWidth() * myZoomFactor), (int)(myImage.getHeight() * myZoomFactor));
  }

  private void setZoomFactor(double zoomFactor) {
    myZoomFactor = zoomFactor;
    doRevalidate();
  }

  private double computeCurrentZoomFactor() {
    if (myImage == null) {
      return myZoomFactor;
    }
    return (double) myImagePanel.getWidth() / (double) myImage.getWidth();
  }

  private double getZoomFactor() {
    return myZoomToFit ? computeCurrentZoomFactor() : myZoomFactor;
  }

  public void zoomOut() {
    setZoomFactor(Math.max(getMinZoomFactor(), myZoomFactor / ZOOM_STEP));
  }

  public boolean canZoomOut() {
    return myZoomFactor > getMinZoomFactor() + EPS;
  }

  private double getMinZoomFactor() {
    return Math.min(1.0, (double) getParent().getWidth() / (double) myImage.getWidth());
  }

  public void zoomIn() {
    if (myZoomToFit) {
      myZoomToFit = false;
      setZoomFactor(computeCurrentZoomFactor() * ZOOM_STEP);
      return;
    }
    setZoomFactor(myZoomFactor * ZOOM_STEP);
  }

  public boolean canZoomIn() {
    return getZoomFactor() * ZOOM_STEP < MAX_ZOOM_FACTOR - EPS;
  }

  public void zoomActual() {
    if (myZoomToFit && myImagePanel.getWidth() >= myImage.getWidth() && myImagePanel.getHeight() >= myImage.getHeight()) {
      return;
    }
    myZoomToFit = false;
    setZoomFactor(1.0);
  }

  public void setZoomToFit(boolean zoomToFit) {
    myZoomToFit = zoomToFit;
    doRevalidate();
  }

  public boolean isZoomToFit() {
    return myZoomToFit;
  }

  private class MyImagePanelWrapper extends JLayeredPane {
    public MyImagePanelWrapper() {
      add(myImagePanel);
    }

    private void centerComponents() {
      Rectangle bounds = getBounds();
      Point point = myImagePanel.getLocation();
      point.x = (bounds.width - myImagePanel.getWidth()) / 2;
      myImagePanel.setLocation(point);
    }

    public void invalidate() {
      centerComponents();
      super.invalidate();
    }

    public Dimension getPreferredSize() {
      return myImagePanel.getSize();
    }
  }
}
