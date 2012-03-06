/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.designSurface;

import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.resources.configuration.*;
import com.intellij.android.designer.actions.ProfileAction;
import com.intellij.android.designer.componentTree.AndroidTreeDecorator;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.ViewsMetaManager;
import com.intellij.android.designer.profile.ProfileManager;
import com.intellij.designer.DesignerToolWindowManager;
import com.intellij.designer.componentTree.TreeComponentDecorator;
import com.intellij.designer.designSurface.ComponentDecorator;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.selection.DirectionResizePoint;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
import com.intellij.designer.designSurface.tools.ComponentCreationFactory;
import com.intellij.designer.designSurface.tools.ComponentPasteFactory;
import com.intellij.designer.model.MetaManager;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.palette.Item;
import com.intellij.designer.utils.Position;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.uipreview.LayoutDeviceConfiguration;
import org.jetbrains.android.uipreview.LocaleData;
import org.jetbrains.android.uipreview.RenderUtil;
import org.jetbrains.android.uipreview.RenderingException;
import org.jetbrains.android.util.AndroidSdkNotConfiguredException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class AndroidDesignerEditorPanel extends DesignerEditorPanel {
  private final TreeComponentDecorator myTreeDecorator = new AndroidTreeDecorator();
  private final XmlFile myXmlFile;
  private final ExternalPSIChangeListener myPSIChangeListener;
  private final ProfileAction myProfileAction;
  private volatile RenderSession mySession;

  public AndroidDesignerEditorPanel(@NotNull Module module, @NotNull VirtualFile file) {
    super(module, file);

    showProgress("Load configuration");
    myProfileAction = new ProfileAction(this, new Runnable() {
      @Override
      public void run() {
        myActionPanel.update();
        if (myRootComponent == null) {
          myPSIChangeListener.addRequest();
        }
        else {
          myPSIChangeListener.addRequest(new Runnable() {
            @Override
            public void run() {
              updateRenderer();
            }
          });
        }
      }
    });

    myXmlFile = (XmlFile)ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
      @Override
      public PsiFile compute() {
        return PsiManager.getInstance(getProject()).findFile(myFile);
      }
    });

    myPSIChangeListener = new ExternalPSIChangeListener(this, myXmlFile, 100, new Runnable() {
      @Override
      public void run() {
        reparseFile();
      }
    });
    // TODO: work over activate() / deactivate()
    myPSIChangeListener.start();
  }

  private void reparseFile() {
    try {
      myToolProvider.loadDefaultTool();
      mySurfaceArea.deselectAll();

      parseFile(new Runnable() {
        @Override
        public void run() {
          showDesignerCard();
          myLayeredPane.repaint();

          DesignerToolWindowManager.getInstance(getProject()).refresh();
        }
      });
    }
    catch (Throwable e) {
      showError("Parse error: ", e);
    }
  }

  private void parseFile(final Runnable runnable) {
    final RadViewComponent[] rootComponents = new RadViewComponent[1];
    final MetaManager metaManager = ViewsMetaManager.getInstance(getProject());
    final String layoutXmlText = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      RadViewComponent myComponent;

      @Override
      public String compute() {
        XmlTag root = myXmlFile.getRootTag();
        if (root != null) {
          root.accept(new XmlRecursiveElementVisitor() {
            @Override
            public void visitXmlTag(XmlTag tag) {
              myComponent = new RadViewComponent(myComponent);
              myComponent.setTag(tag);
              myComponent.setMetaModel(metaManager.getModelByTag(tag.getName()));

              if (rootComponents[0] == null) {
                rootComponents[0] = myComponent;
              }

              super.visitXmlTag(tag);

              myComponent = (RadViewComponent)myComponent.getParent();
            }
          });
        }

        return myXmlFile.getText();
      }
    });

    createRenderer(layoutXmlText, new ThrowableRunnable<Throwable>() {
      @Override
      public void run() throws Throwable {
        checkRenderer();

        RootView rootView = new RootView(mySession.getImage(), 30, 20);
        updateRootComponent(rootComponents, mySession.getRootViews(), rootView);

        JPanel rootPanel = new JPanel(null);
        rootPanel.setBackground(Color.WHITE);
        rootPanel.add(rootView);

        removeNativeRoot();
        myRootComponent = rootComponents[0];
        myLayeredPane.add(rootPanel, LAYER_COMPONENT);

        runnable.run();
      }
    });
  }

  private void updateRenderer() {
    final String layoutXmlText = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return myXmlFile.getText();
      }
    });
    createRenderer(layoutXmlText, new ThrowableRunnable<Throwable>() {
      @Override
      public void run() throws Throwable {
        checkRenderer();

        RadViewComponent rootComponent = (RadViewComponent)myRootComponent;
        RootView rootView = (RootView)rootComponent.getNativeComponent();
        rootView.setImage(mySession.getImage());
        updateRootComponent(rootComponent, mySession.getRootViews(), rootView);

        myLayeredPane.repaint();
      }
    });
  }

  private void checkRenderer() throws Throwable {
    Result result = mySession.getResult();
    if (!result.isSuccess()) {
      Throwable exception = result.getException();
      if (exception != null) {
        throw exception;
      }
      else {
        throw new Exception("No session result");
      }
    }
  }

  private void removeNativeRoot() {
    if (myRootComponent != null) {
      myLayeredPane.remove(((RadViewComponent)myRootComponent).getNativeComponent().getParent());
    }
  }

  private void updateRootComponent(RadViewComponent[] rootComponents, List<ViewInfo> views, JComponent nativeComponent) {
    RadViewComponent rootComponent = rootComponents[0];

    if (views.size() == 1) {
      RadViewComponent newRootComponent = new RadViewComponent(null);
      newRootComponent.setMetaModel(ViewsMetaManager.getInstance(getProject()).getModelByTag("<root>"));
      newRootComponent.getChildren().add(rootComponent);
      rootComponent.setParent(newRootComponent);

      updateComponent(rootComponent, views.get(0), nativeComponent, 0, 0);

      rootComponents[0] = newRootComponent;

      newRootComponent.setNativeComponent(nativeComponent);
      newRootComponent.setBounds(0, 0, nativeComponent.getWidth(), nativeComponent.getHeight());
    }
    else {
      updateRootComponent(rootComponent, views, nativeComponent);
    }
  }

  private static void updateRootComponent(RadViewComponent rootComponent, List<ViewInfo> views, JComponent nativeComponent) {
    int size = views.size();
    List<RadComponent> children = rootComponent.getChildren();
    for (int i = 0; i < size; i++) {
      updateComponent((RadViewComponent)children.get(i), views.get(i), nativeComponent, 0, 0);
    }

    rootComponent.setNativeComponent(nativeComponent);
    rootComponent.setBounds(0, 0, nativeComponent.getWidth(), nativeComponent.getHeight());
  }

  private static void updateComponent(RadViewComponent component, ViewInfo view, JComponent nativeComponent, int parentX, int parentY) {
    component.setNativeComponent(nativeComponent);

    int left = parentX + view.getLeft();
    int top = parentY + view.getTop();
    component.setBounds(left, top, view.getRight() - view.getLeft(), view.getBottom() - view.getTop());

    List<ViewInfo> views = view.getChildren();
    List<RadComponent> children = component.getChildren();
    int size = views.size();

    for (int i = 0; i < size; i++) {
      updateComponent((RadViewComponent)children.get(i), views.get(i), nativeComponent, left, top);
    }
  }


  private void createRenderer(final String layoutXmlText, final ThrowableRunnable<Throwable> runnable) {
    if (mySession == null) {
      ApplicationManager.getApplication().invokeLater(
        new Runnable() {
          @Override
          public void run() {
            if (mySession == null) {
              showProgress("Create RenderLib");
            }
          }
        }, new Condition() {
          @Override
          public boolean value(Object o) {
            return mySession != null;
          }
        }
      );
    }
    else {
      disposeSession();
    }

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          AndroidPlatform platform = AndroidPlatform.getInstance(myModule);
          if (platform == null) {
            throw new AndroidSdkNotConfiguredException();
          }

          AndroidFacet facet = AndroidFacet.getInstance(myModule);
          ProfileManager manager = myProfileAction.getProfileManager();

          LayoutDeviceConfiguration deviceConfiguration = manager.getSelectedDeviceConfiguration();
          if (deviceConfiguration == null) {
            throw new RenderingException("Device is not specified");
          }

          FolderConfiguration config = new FolderConfiguration();
          config.set(deviceConfiguration.getConfiguration());
          config.setUiModeQualifier(new UiModeQualifier(manager.getSelectedDockMode()));
          config.setNightModeQualifier(new NightModeQualifier(manager.getSelectedNightMode()));

          LocaleData locale = manager.getSelectedLocale();
          if (locale == null) {
            throw new RenderingException("Locale is not specified");
          }
          config.setLanguageQualifier(new LanguageQualifier(locale.getLanguage()));
          config.setRegionQualifier(new RegionQualifier(locale.getRegion()));

          float xdpi = deviceConfiguration.getDevice().getXDpi();
          float ydpi = deviceConfiguration.getDevice().getYDpi();

          mySession = RenderUtil
            .createRenderSession(getProject(), layoutXmlText, myFile, manager.getSelectedTarget(), facet, config, xdpi, ydpi,
                                 manager.getSelectedTheme());

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              try {
                hideProgress();
                runnable.run();
              }
              catch (Throwable e) {
                showError("Parse error: ", e);
              }
            }
          });
        }
        catch (RenderingException e) {
          // TODO
          e.printStackTrace();
        }
        catch (AndroidSdkNotConfiguredException e) {
          // TODO
          e.printStackTrace();
        }
        catch (final Throwable e) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              showError("Render session error: ", e);
            }
          });
        }
      }
    });
  }

  private void disposeSession() {
    if (mySession != null) {
      mySession.dispose();
      mySession = null;
    }
  }

  @Override
  public void showError(@NonNls String message, Throwable e) {
    removeNativeRoot();
    super.showError(message, e);
  }

  public ProfileAction getProfileAction() {
    return myProfileAction;
  }

  @Override
  public void activate() {
    myProfileAction.externalUpdate();
  }

  @Override
  public void dispose() {
    myPSIChangeListener.stop();
    super.dispose();
    disposeSession();
  }

  @Override
  public String getPlatformTarget() {
    return "android";
  }

  @Override
  public TreeComponentDecorator getTreeDecorator() {
    return myTreeDecorator;
  }

  @Override
  protected ComponentDecorator getRootSelectionDecorator() {
    return new ResizeSelectionDecorator(Color.RED, 1, new DirectionResizePoint(Position.EAST, "top_resize_"),
                                        new DirectionResizePoint(Position.SOUTH_EAST, "top_resize"),
                                        new DirectionResizePoint(Position.SOUTH, "top_resize"));
  }

  @Override
  protected EditOperation processRootOperation(OperationContext context) {
    return null;
  }

  @Override
  @NotNull
  protected ComponentCreationFactory createCreationFactory(Item paletteItem) {
    return new ComponentCreationFactory() {
      @Override
      @NotNull
      public RadComponent create() throws Exception {
        return new RadViewComponent(null);
      }
    };
    //return null;  // TODO: Auto-generated method stub
  }

  @Override
  public ComponentPasteFactory createPasteFactory(String xmlComponents) {
    return new ComponentPasteFactory() {
      @NotNull
      @Override
      public List<RadComponent> create() throws Exception {
        return Collections.<RadComponent>singletonList(new RadViewComponent(null));
      }
    };
    //return null; // TODO: Auto-generated method stub
  }

  @Override
  protected boolean execute(ThrowableRunnable<Exception> operation) {
    try {
      operation.run();
      return true;
    }
    catch (Throwable e) {
      showError("Execute command", e);
      return false;
    }
  }

  @Override
  protected void execute(List<EditOperation> operations) {
    try {
      for (EditOperation operation : operations) {
        operation.execute();
      }
    }
    catch (Throwable e) {
      showError("Execute command", e);
    }
  }

  private static class RootView extends JComponent {
    private int myX;
    private int myY;
    private BufferedImage myImage;

    public RootView(BufferedImage image, int x, int y) {
      myX = x;
      myY = y;
      setImage(image);
    }

    public void setImage(BufferedImage image) {
      myImage = image;
      setBounds(myX, myY, image.getWidth(), image.getHeight());
    }

    @Override
    public void paintComponent(Graphics g) {
      super.paintComponent(g);
      g.drawImage(myImage, 0, 0, null);
    }
  }
}