package org.jetbrains.jps.api;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.incremental.messages.BuildMessage;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.BuilderMessage;
import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/17/12
 */
public class CmdlineProtoUtil {

  public static CmdlineRemoteProto.Message.ControllerMessage createMakeRequest(String project,
                                                                               List<TargetTypeBuildScope> scopes,
                                                                               final Map<String, String> userData,
                                                                               final CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings globals,
                                                                               final @Nullable CmdlineRemoteProto.Message.ControllerMessage.FSEvent event) {
    return createBuildParametersMessage(
      CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.Type.MAKE, project, scopes, userData, Collections.<String>emptyList(), globals, event
    );
  }

  public static CmdlineRemoteProto.Message.ControllerMessage createUpToDateCheckRequest(String project,
                                                                                        List<TargetTypeBuildScope> scopes,
                                                                                        Collection<String> paths,
                                                                                        final Map<String, String> userData,
                                                                                        final CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings globals,
                                                                                        final @Nullable CmdlineRemoteProto.Message.ControllerMessage.FSEvent event) {
    return createBuildParametersMessage(
      CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.Type.UP_TO_DATE_CHECK, project, scopes, userData, paths, globals, event
    );
  }

  public static CmdlineRemoteProto.Message.ControllerMessage createForceCompileRequest(String project,
                                                                                       List<TargetTypeBuildScope> scopes,
                                                                                       Collection<String> paths,
                                                                                       final Map<String, String> userData,
                                                                                       final CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings globals,
                                                                                       final @Nullable CmdlineRemoteProto.Message.ControllerMessage.FSEvent event) {
    return createBuildParametersMessage(CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.Type.FORCED_COMPILATION, project,
                                        scopes, userData, paths, globals, event);
  }

  public static CmdlineRemoteProto.Message.ControllerMessage createRebuildRequest(String project,
                                                                                  List<TargetTypeBuildScope> scopes,
                                                                                  final Map<String, String> userData,
                                                                                  final CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings globals) {
    return createBuildParametersMessage(CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.Type.REBUILD, project,
                                        scopes, userData, Collections.<String>emptyList(),
                                        globals, null);
  }

  public static List<TargetTypeBuildScope> createAllModulesScopes() {
    return Arrays.asList(
      createAllTargetsScope(JavaModuleBuildTargetType.PRODUCTION),
      createAllTargetsScope(JavaModuleBuildTargetType.TEST)
    );
  }

  public static TargetTypeBuildScope createAllTargetsScope(BuildTargetType<?> type) {
    return TargetTypeBuildScope.newBuilder()
      .setTypeId(type.getTypeId())
      .setAllTargets(true)
      .build();
  }

  private static CmdlineRemoteProto.Message.ControllerMessage createBuildParametersMessage(CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.Type buildType,
                                                                                          String project,
                                                                                          List<TargetTypeBuildScope> scopes,
                                                                                          Map<String, String> userData,
                                                                                          Collection<String> paths,
                                                                                          final CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings globals,
                                                                                          @Nullable CmdlineRemoteProto.Message.ControllerMessage.FSEvent initialEvent) {
    final CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.Builder
      builder = CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.newBuilder();
    builder.setGlobalSettings(globals);
    builder.setBuildType(buildType);
    builder.setProjectId(project);
    builder.addAllScope(scopes);
    if (!userData.isEmpty()) {
      for (Map.Entry<String, String> entry : userData.entrySet()) {
        final String key = entry.getKey();
        final String value = entry.getValue();
        if (key != null && value != null) {
          builder.addBuilderParameter(createPair(key, value));
        }
      }
    }
    if (!paths.isEmpty()) {
      builder.addAllFilePath(paths);
    }
    final CmdlineRemoteProto.Message.ControllerMessage.Builder controlMessageBuilder = CmdlineRemoteProto.Message.ControllerMessage.newBuilder();
    if (initialEvent != null) {
      controlMessageBuilder.setFsEvent(initialEvent);
    }
    return controlMessageBuilder.setType(CmdlineRemoteProto.Message.ControllerMessage.Type.BUILD_PARAMETERS).setParamsMessage(builder.build()).build();
  }

  public static CmdlineRemoteProto.Message.KeyValuePair createPair(String key, String value) {
    return CmdlineRemoteProto.Message.KeyValuePair.newBuilder().setKey(key).setValue(value).build();
  }


  public static CmdlineRemoteProto.Message.Failure createFailure(String description, @Nullable Throwable cause) {
    final CmdlineRemoteProto.Message.Failure.Builder builder = CmdlineRemoteProto.Message.Failure.newBuilder();
    builder.setDescription(description);
    if (cause != null) {
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      final PrintStream stream = new PrintStream(baos);
      try {
        cause.printStackTrace(stream);
      }
      finally {
        stream.close();
      }
      builder.setStacktrace(new String(baos.toByteArray()));
    }
    return builder.build();
  }

  public static CmdlineRemoteProto.Message.ControllerMessage createCancelCommand() {
    return CmdlineRemoteProto.Message.ControllerMessage.newBuilder()
      .setType(CmdlineRemoteProto.Message.ControllerMessage.Type.CANCEL_BUILD_COMMAND).build();
  }

  public static BuilderMessage createCompileProgressMessageResponse(String text, float done) {
    return createCompileMessage(BuildMessage.Kind.PROGRESS, text, null, -1L, -1L, -1L, -1, -1, done);
  }

  public static BuilderMessage createCompileMessage(final BuildMessage.Kind kind,
                                                                               String text,
                                                                               String path,
                                                                               long beginOffset, long endOffset, long offset, long line,
                                                                               long column, float done) {

    final BuilderMessage.CompileMessage.Builder builder = BuilderMessage.CompileMessage.newBuilder();
    switch (kind) {
      case ERROR:
        builder.setKind(BuilderMessage.CompileMessage.Kind.ERROR);
        break;
      case WARNING:
        builder.setKind(BuilderMessage.CompileMessage.Kind.WARNING);
        break;
      case INFO:
        builder.setKind(BuilderMessage.CompileMessage.Kind.INFO);
        break;
      default:
        builder.setKind(BuilderMessage.CompileMessage.Kind.PROGRESS);
    }
    if (text != null) {
      builder.setText(text);
    }
    if (path != null) {
      builder.setSourceFilePath(path);
    }
    if (beginOffset >= 0L) {
      builder.setProblemBeginOffset(beginOffset);
    }
    if (endOffset >= 0L) {
      builder.setProblemEndOffset(endOffset);
    }
    if (offset >= 0L) {
      builder.setProblemLocationOffset(offset);
    }
    if (line >= 0L) {
      builder.setLine(line);
    }
    if (column >= 0L) {
      builder.setColumn(column);
    }
    if (done >= 0.0f) {
      builder.setDone(done);
    }
    return BuilderMessage.newBuilder().setType(BuilderMessage.Type.COMPILE_MESSAGE).setCompileMessage(builder.build()).build();
  }

  public static BuilderMessage createCustomBuilderMessage(String builderId, String messageType, String messageText) {
    BuilderMessage.BuildEvent.CustomBuilderMessage builderMessage =
      BuilderMessage.BuildEvent.CustomBuilderMessage.newBuilder()
        .setBuilderId(builderId)
        .setMessageType(messageType)
        .setMessageText(messageText)
        .build();
    return createBuildEvent(BuilderMessage.BuildEvent.Type.CUSTOM_BUILDER_MESSAGE, null, null, null, builderMessage);
  }

  public static BuilderMessage createBuildCompletedEvent(@Nullable String description, final BuilderMessage.BuildEvent.Status status) {
    return createBuildEvent(BuilderMessage.BuildEvent.Type.BUILD_COMPLETED, description, status, null, null);
  }

  public static BuilderMessage createFileGeneratedEvent(final Collection<Pair<String, String>> paths) {
    return createBuildEvent(BuilderMessage.BuildEvent.Type.FILES_GENERATED, null, null, paths, null);
  }

  private static BuilderMessage createBuildEvent(final BuilderMessage.BuildEvent.Type type,
                                                 @Nullable String description,
                                                 @Nullable final BuilderMessage.BuildEvent.Status status,
                                                 @Nullable Collection<Pair<String, String>> generatedPaths,
                                                 @Nullable final BuilderMessage.BuildEvent.CustomBuilderMessage builderMessage) {
    final BuilderMessage.BuildEvent.Builder builder = BuilderMessage.BuildEvent.newBuilder().setEventType(type);
    if (description != null) {
      builder.setDescription(description);
    }
    if (status != null) {
      builder.setCompletionStatus(status);
    }
    if (generatedPaths != null) {
      for (Pair<String, String> pair : generatedPaths) {
        final BuilderMessage.BuildEvent.GeneratedFile.Builder fileBuilder = BuilderMessage.BuildEvent.GeneratedFile.newBuilder();
        final BuilderMessage.BuildEvent.GeneratedFile generatedFile = fileBuilder.setOutputRoot(pair.first).setRelativePath(pair.second).build();
        builder.addGeneratedFiles(generatedFile);
      }
    }
    if (builderMessage != null) {
      builder.setCustomBuilderMessage(builderMessage);
    }
    return BuilderMessage.newBuilder().setType(BuilderMessage.Type.BUILD_EVENT).setBuildEvent(builder.build()).build();
  }

  public static BuilderMessage createParamRequest() {
    return BuilderMessage.newBuilder().setType(BuilderMessage.Type.PARAM_REQUEST).build();
  }

  public static CmdlineRemoteProto.Message toMessage(UUID sessionId, BuilderMessage builderMessage) {
    return CmdlineRemoteProto.Message.newBuilder().setSessionId(toProtoUUID(sessionId)).setType(CmdlineRemoteProto.Message.Type.BUILDER_MESSAGE).setBuilderMessage(builderMessage).build();
  }

  public static CmdlineRemoteProto.Message toMessage(UUID sessionId, CmdlineRemoteProto.Message.ControllerMessage builderMessage) {
    return CmdlineRemoteProto.Message.newBuilder().setSessionId(toProtoUUID(sessionId)).setType(CmdlineRemoteProto.Message.Type.CONTROLLER_MESSAGE).setControllerMessage(
      builderMessage).build();
  }

  public static CmdlineRemoteProto.Message toMessage(UUID sessionId, CmdlineRemoteProto.Message.Failure failure) {
    return CmdlineRemoteProto.Message.newBuilder().setSessionId(toProtoUUID(sessionId)).setType(CmdlineRemoteProto.Message.Type.FAILURE).setFailure(failure).build();
  }

  private static CmdlineRemoteProto.Message.UUID toProtoUUID(UUID sessionId) {
    final CmdlineRemoteProto.Message.UUID.Builder uuidBuilder = CmdlineRemoteProto.Message.UUID.newBuilder();
    return uuidBuilder.setMostSigBits(sessionId.getMostSignificantBits()).setLeastSigBits(sessionId.getLeastSignificantBits()).build();
  }

}
