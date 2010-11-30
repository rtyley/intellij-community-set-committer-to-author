package com.intellij.execution.process;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

/**
 * This runner manages ctrl+break(ctrl+c) termination of process.
 *
 * @author traff
 */
public class RunnerMediator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.RunnerMediator");

  private static final int SIGINT = 2;
  private static final int SIGKILL = 9;
  private static final String UID_KEY_NAME = "PROCESSUUID";

  private RunnerMediator() {
  }

  public static String getRunnerPath() {
    if (File.separatorChar == '\\') {
      return RunnerMediatorManager.getInstance().getRunnerPath();
    }
    else {
      throw new IllegalStateException("There is no need of runner under unix based OS");
    }
  }

  public static void injectRunnerCommand(@NotNull GeneralCommandLine commandLine) {
    commandLine.getParametersList().addAt(0, commandLine.getExePath());
    commandLine.setExePath(getRunnerPath());
  }

  public static String injectUid(@NotNull GeneralCommandLine commandLine) {
    String uid = commandLine.getExePath() + ":" + UUID.randomUUID().toString();
    commandLine.getEnvParams().put(UID_KEY_NAME, uid);
    return uid;
  }

  public static ColoredProcessHandler createProcessWithStopInjections(@NotNull GeneralCommandLine commandLine)
    throws ExecutionException {
    if (isWindows()) {
      injectRunnerCommand(commandLine);
    }

    Process p = commandLine.createProcess();

    return new CustomDestroyProcessHandler(p, commandLine);
  }

  public static boolean canSendSignals() {
    return SystemInfo.isLinux || SystemInfo.isMac;
  }

  public static boolean isWindows() {
    if (File.separatorChar == '\\') {
      return true;
    }
    return false;
  }


  public static void sendSigInt(Process process) {
    sendSignal(process, SIGINT);
  }

  public static void sendSigKill(Process process) {
    sendSignal(process, SIGKILL);
  }

  public static void sendSignal(Process process, int signal) {
    if (C_LIB == null) {
      throw new IllegalStateException("no CLIB");
    }
    int our_pid = C_LIB.getpid();
    int process_pid = getProcessPid(process);

    try {
      String[] psCmd = getCmd();
      Process p = Runtime.getRuntime().exec(psCmd);

      @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
      BufferedReader stdInput = new BufferedReader(new
                                                     InputStreamReader(p.getInputStream()));

      BufferedReader stdError = new BufferedReader(new
                                                     InputStreamReader(p.getErrorStream()));
      try {
        String s;
        stdInput.readLine(); //ps output header
        int foundPid = 0;
        while ((s = stdInput.readLine()) != null) {
          StringTokenizer st = new StringTokenizer(s, " ");

          int parent_pid = Integer.parseInt(st.nextToken());
          int pid = Integer.parseInt(st.nextToken());

          ProcessInfo.register(pid, parent_pid);

          if (pid == process_pid) {
            if (parent_pid == our_pid) {
              foundPid = pid;
            }
            else {
              throw new IllegalStateException("process is not our child");
            }
          }
        }

        if (foundPid != 0) {
          ProcessInfo.killProcTree(foundPid, signal);
        }
        else {
          throw new IllegalStateException("process not found: " + process_pid + ", idea pid =" + our_pid);
        }

        StringBuffer errorStr = new StringBuffer();
        while ((s = stdError.readLine()) != null) {
          errorStr.append(s).append("\n");
        }
        if (errorStr.length() > 0) {
          throw new IllegalStateException("error:" + errorStr.toString());
        }
      }
      finally {
        stdInput.close();
        stdError.close();
      }
    }
    catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static int getProcessPid(Process proc) {
    try {
      Field f = proc.getClass().getDeclaredField("pid");
      f.setAccessible(true);
      int pid = ((Number)f.get(proc)).intValue();
      return pid;
    }
    catch (NoSuchFieldException e) {
      throw new IllegalStateException("system is not linux", e);
    }
    catch (IllegalAccessException e) {
      throw new IllegalStateException("system is not linux", e);
    }
  }

  private static String[] getCmd() {
    if (SystemInfo.isLinux) {
      return new String[]{"ps", "e", "--format", "%P%p%a"};
    }
    else if (SystemInfo.isMac) {
      return new String[]{"ps", "-ax", "-E", "-o", "ppid,pid,command"};
    }
    else {
      throw new IllegalStateException(System.getProperty("os.name") + " is not supported.");
    }
  }

  private static boolean containsMarker(@NotNull String environ, @NotNull String uid) {
    return environ.contains(uid);
  }

  @NotNull
  public static String readProcEnviron(int child_pid) throws FileNotFoundException {
    StringBuffer res = new StringBuffer();
    Scanner s = new Scanner(new File("/proc/" + child_pid + "/environ"));
    while (s.hasNextLine()) {
      res.append(s).append("\n");
    }
    return res.toString();
  }

  private static void sendSignal(int pid, int signal) {
    C_LIB.kill(pid, signal);
  }

  interface CLib extends Library {
    int getpid();

    int kill(int pid, int signal);
  }

  static CLib C_LIB;

  static {
    try {
      if (!Platform.isWindows()) {

        C_LIB = ((CLib)Native.loadLibrary("c", CLib.class));
      }
    }
    catch (Exception e) {
      C_LIB = null;
    }
  }

  public static class CustomDestroyProcessHandler extends ColoredProcessHandler {
    private static final char IAC = (char)5;
    private static final char BRK = (char)3;

    private final String myCommand;

    public CustomDestroyProcessHandler(@NotNull Process process,
                                       @NotNull GeneralCommandLine commandLine) {
      super(process, commandLine.getCommandLineString());
      myCommand = commandLine.getExePath();
    }

    @Override
    protected void destroyProcessImpl() {
      if (!doCustomDestroy()) {
        super.destroyProcessImpl();
      }
    }

    private boolean doCustomDestroy() {
      try {
        if (isWindows()) {
          sendCtrlBreakThroughStream();
          return true;
        }
        else if (canSendSignals()) {
          sendSigKill(getProcess());
          return true;
        }
        else {
          return false;
        }
      }
      catch (Exception e) {
        LOG.error("Couldn't terminate the process", e);
        return false;
      }
    }

    private void sendCtrlBreakThroughStream() {
      OutputStream os = getProcessInput();
      PrintWriter pw = new PrintWriter(os);
      try {
        pw.print(IAC);
        pw.print(BRK);
        pw.flush();
      }
      finally {
        pw.close();
      }
    }
  }

  private static class ProcessInfo {

    private ProcessInfo() {
    }

    private static Map<Integer, List<Integer>> BY_PARENT = Maps.newTreeMap(); // pid -> list of children pids

    public static void register(Integer pid, Integer parentPid) {
      List<Integer> children = BY_PARENT.get(parentPid);
      if (children == null) children = Lists.newLinkedList();
      children.add(pid);
      BY_PARENT.put(parentPid, children);
    }

    static void killProcTree(int pid, int signal) {
      List<Integer> children = BY_PARENT.get(pid);
      if (children != null) {
        for (int child : children) killProcTree(child, signal);
      }
      sendSignal(pid, signal);
    }
  }
}
