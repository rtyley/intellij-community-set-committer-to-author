package git4idea;

import com.intellij.dvcs.test.MockVcsHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import cucumber.annotation.After;
import cucumber.annotation.Before;
import cucumber.annotation.Order;
import git4idea.commands.Git;
import git4idea.commands.GitHttpAuthService;
import git4idea.config.GitVcsSettings;
import git4idea.remote.GitHttpAuthTestService;
import git4idea.repo.GitRepository;
import git4idea.test.GitTestInitUtil;
import git4idea.test.TestNotificator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.WebServerManager;
import org.jetbrains.ide.WebServerManagerImpl;
import org.junit.Assert;
import org.picocontainer.MutablePicoContainer;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.dvcs.test.Executor.cd;
import static junit.framework.Assert.assertNotNull;

/**
 * <p>The container of test environment variables which should be visible from any step definition script.</p>
 * <p>Most of the fields are populated in the Before hook of the {@link GeneralStepdefs}.</p>
 *
 * @author Kirill Likhodedov
 */
public class GitCucumberWorld {

  public static String myTestRoot;
  public static String myProjectRoot;
  public static Project myProject;

  public static GitPlatformFacade myPlatformFacade;
  public static Git myGit;
  public static GitRepository myRepository;
  public static GitVcsSettings mySettings;
  public static ChangeListManager myChangeListManager;

  public static MockVcsHelper myVcsHelper;
  public static TestNotificator myNotificator;

  public static GitHttpAuthTestService myHttpAuthService; // only with @remote tag

  public static GitTestVirtualCommitsHolder virtualCommits;

  private static Collection<Future> myAsyncTasks;

  private IdeaProjectTestFixture myProjectFixture;

  @Before
  @Order(0)
  public void setUp() throws Throwable {
    System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, "PlatformLangXml");

    String tempFileName = getClass().getName() + "-" + new Random().nextInt();
    myProjectFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(tempFileName).getFixture();

    edt(new ThrowableRunnable<Exception>() {
      @Override
      public void run() throws Exception {
        myProjectFixture.setUp();
      }
    });

    myProject = myProjectFixture.getProject();

    ((ProjectComponent)ChangeListManager.getInstance(myProject)).projectOpened();
    ((ProjectComponent)VcsDirtyScopeManager.getInstance(myProject)).projectOpened();

    myProjectRoot = myProject.getBasePath();
    myTestRoot = myProjectRoot;

    myPlatformFacade = ServiceManager.getService(myProject, GitPlatformFacade.class);
    myGit = ServiceManager.getService(myProject, Git.class);
    mySettings = myPlatformFacade.getSettings(myProject);
    // dynamic overriding is used instead of making it in plugin.xml,
    // because MockVcsHelper is not ready to be a full featured implementation for all tests.
    myVcsHelper = overrideService(myProject, AbstractVcsHelper.class, MockVcsHelper.class);
    myChangeListManager = myPlatformFacade.getChangeListManager(myProject);
    myNotificator = (TestNotificator)ServiceManager.getService(myProject, Notificator.class);

    virtualCommits = new GitTestVirtualCommitsHolder();
    myAsyncTasks = new ArrayList<Future>();

    cd(myProjectRoot);
    myRepository = createRepo(myProjectRoot);

    ProjectLevelVcsManagerImpl vcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(myProject);
    AbstractVcs vcs = vcsManager.findVcsByName("Git");
    Assert.assertEquals(1, vcsManager.getRootsUnderVcs(vcs).length);
  }

  @NotNull
  private static GitRepository createRepo(String root) {
    GitTestInitUtil.initRepo(root);
    ProjectLevelVcsManagerImpl vcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(myProject);
    vcsManager.setDirectoryMapping(root, GitVcs.NAME);
    VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(new File(root));
    GitRepository repository = myPlatformFacade.getRepositoryManager(myProject).getRepositoryForRoot(file);
    assertNotNull("Couldn't find repository for root " + root, repository);
    return repository;
  }

  @Before("@remote")
  @Order(1)
  public void setUpRemoteOperations() {
    ((WebServerManagerImpl)WebServerManager.getInstance()).setEnabledInUnitTestMode(true);
    // default port will be occupied by main idea instance => define the custom default to avoid searching of free port
    System.setProperty(WebServerManagerImpl.PROPERTY_RPC_PORT, "64463");
    myHttpAuthService = (GitHttpAuthTestService)ServiceManager.getService(GitHttpAuthService.class);
  }

  @After
  public void tearDown() throws Throwable {
    waitForPendingTasks();
    nullifyStaticFields();
    edt(new ThrowableRunnable<Exception>() {
      @Override
      public void run() throws Exception {
        myProjectFixture.tearDown();
      }
    });
  }

  public static void executeOnPooledThread(Runnable runnable) {
    myAsyncTasks.add(ApplicationManager.getApplication().executeOnPooledThread(runnable));
  }

  private static void waitForPendingTasks() throws InterruptedException, ExecutionException, TimeoutException {
    for (Future future : myAsyncTasks) {
      future.get(30, TimeUnit.SECONDS);
    }
  }

  private static void nullifyStaticFields() throws IllegalAccessException {
    for (Field field : GitCucumberWorld.class.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        field.set(null, null);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T overrideService(@NotNull Project project, Class<? super T> serviceInterface, Class<T> serviceImplementation) {
    String key = serviceInterface.getName();
    MutablePicoContainer picoContainer = (MutablePicoContainer) project.getPicoContainer();
    picoContainer.unregisterComponent(key);
    picoContainer.registerComponentImplementation(key, serviceImplementation);
    return (T) ServiceManager.getService(project, serviceInterface);
  }

  private static void edt(@NotNull final ThrowableRunnable<Exception> runnable) throws Exception {
    final AtomicReference<Exception> exception = new AtomicReference<Exception>();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          runnable.run();
        }
        catch (Exception throwable) {
          exception.set(throwable);
        }
      }
    });
    //noinspection ThrowableResultOfMethodCallIgnored
    if (exception.get() != null) {
      throw exception.get();
    }
  }

}
