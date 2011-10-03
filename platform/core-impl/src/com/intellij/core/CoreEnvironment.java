/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.core;

import com.intellij.lang.*;
import com.intellij.lang.impl.PsiBuilderFactoryImpl;
import com.intellij.mock.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationComponentLocator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.impl.file.PsiDirectoryFactoryImpl;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.search.ProjectScopeBuilder;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.CachedValuesManagerImpl;
import com.intellij.util.Function;
import org.picocontainer.MutablePicoContainer;

import java.lang.reflect.Modifier;

/**
 * @author yole
 */
public class CoreEnvironment {
  private CoreFileTypeRegistry myFileTypeRegistry;
  private CoreEncodingRegistry myEncodingRegistry;
  private MockApplication myApplication;
  protected MockProject myProject;
  private CoreLocalFileSystem myLocalFileSystem;
  private MockFileIndexFacade myFileIndexFacade;
  protected final PsiManagerImpl myPsiManager;

  public CoreEnvironment(Disposable parentDisposable) {
    myFileTypeRegistry = new CoreFileTypeRegistry();
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    FileTypeRegistry.ourInstanceGetter = new Getter<FileTypeRegistry>() {
      @Override
      public FileTypeRegistry get() {
        return myFileTypeRegistry;
      }
    };

    myEncodingRegistry = new CoreEncodingRegistry();
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    EncodingRegistry.ourInstanceGetter = new Getter<EncodingRegistry>() {
      @Override
      public EncodingRegistry get() {
        return myEncodingRegistry;
      }
    };

    myApplication = new MockApplication(parentDisposable);
    new ApplicationManager() {{
      ourApplication = myApplication;
    }};
    ApplicationComponentLocator.setInstance(myApplication);
    myLocalFileSystem = new CoreLocalFileSystem();

    Extensions.registerAreaClass("IDEA_PROJECT", null);
    myProject = new MockProject(myApplication.getPicoContainer(), parentDisposable);

    final MutablePicoContainer appContainer = myApplication.getPicoContainer();
    registerComponentInstance(appContainer, FileDocumentManager.class, new MockFileDocumentManagerImpl(new Function<CharSequence, Document>() {
      @Override
      public Document fun(CharSequence charSequence) {
        return new DocumentImpl(charSequence);
      }
    }, null));

    myApplication.registerService(DefaultASTFactory.class, new CoreASTFactory());
    myApplication.registerService(PsiBuilderFactory.class, new PsiBuilderFactoryImpl());
    myApplication.registerService(ReferenceProvidersRegistry.class, new MockReferenceProvidersRegistry());

    myFileIndexFacade = new MockFileIndexFacade(myProject);
    final MutablePicoContainer projectContainer = myProject.getPicoContainer();
    
    myProject.registerService(PsiModificationTracker.class, new PsiModificationTrackerImpl(myProject));
    
    registerProjectExtensionPoint(PsiTreeChangePreprocessor.EP_NAME, PsiTreeChangePreprocessor.class);
    myPsiManager = new PsiManagerImpl(myProject, null, null, myFileIndexFacade, null);
    ((FileManagerImpl) myPsiManager.getFileManager()).markInitialized();
    registerComponentInstance(projectContainer, PsiManager.class, myPsiManager);

    myProject.registerService(PsiFileFactory.class, new PsiFileFactoryImpl(myPsiManager));
    myProject.registerService(CachedValuesManager.class, new CachedValuesManagerImpl(myProject, new PsiCachedValuesFactory(myPsiManager)));
    myProject.registerService(PsiDirectoryFactory.class, new PsiDirectoryFactoryImpl(myPsiManager));
    myProject.registerService(ProjectScopeBuilder.class, new CoreProjectScopeBuilder(myProject, myFileIndexFacade));
    myProject.registerService(DumbService.class, new MockDumbService(myProject));
  }

  public Project getProject() {
    return myProject;
  }

  public void registerFileType(FileType fileType, String extension) {
    myFileTypeRegistry.registerFileType(fileType, extension);
  }

  public void registerParserDefinition(ParserDefinition definition) {
    addExplicitExtension(LanguageParserDefinitions.INSTANCE, definition.getFileNodeType().getLanguage(), definition);
  }

  protected <T> void registerComponentInstance(final MutablePicoContainer container, final Class<T> key, final T implementation) {
    container.unregisterComponent(key);
    container.registerComponentInstance(key, implementation);
  }

  protected <T> void addExplicitExtension(final LanguageExtension<T> instance, final Language language, final T object) {
    instance.addExplicitExtension(language, object);
    Disposer.register(myProject, new Disposable() {
      @Override
      public void dispose() {
        instance.removeExplicitExtension(language, object);
      }
    });
  }

  protected <T> void registerExtensionPoint(final ExtensionsArea area, final ExtensionPointName<T> extensionPointName,
                                            final Class<? extends T> aClass) {
    final String name = extensionPointName.getName();
    if (!area.hasExtensionPoint(name)) {
      ExtensionPoint.Kind kind = aClass.isInterface() || (aClass.getModifiers() & Modifier.ABSTRACT) != 0 ? ExtensionPoint.Kind.INTERFACE : ExtensionPoint.Kind.BEAN_CLASS;
      area.registerExtensionPoint(name, aClass.getName(), kind);
    }
  }

  protected <T> void registerProjectExtensionPoint(final ExtensionPointName<T> extensionPointName,
                                            final Class<? extends T> aClass) {
    registerExtensionPoint(Extensions.getArea(myProject), extensionPointName, aClass);
  }

  public CoreLocalFileSystem getLocalFileSystem() {
    return myLocalFileSystem;
  }
}
