package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.io.IOUtil;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * @author nik
 */
public class BuildTargetTypeState {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.storage.BuildTargetTypeState");
  private final Map<BuildTarget, Integer> myTargetIds;
  private final ConcurrentMap<BuildTarget, BuildTargetConfiguration> myConfigurations = new ConcurrentHashMap<BuildTarget, BuildTargetConfiguration>();
  private final BuildTargetType myTargetType;
  private final BuildTargetsState myTargetsState;
  private final File myTargetsFile;

  public BuildTargetTypeState(BuildTargetType targetType, BuildTargetsState state) {
    myTargetType = targetType;
    myTargetsState = state;
    myTargetsFile = new File(state.getTargetTypeDataRoot(targetType), "targets.dat");
    myTargetIds = new HashMap<BuildTarget, Integer>();
    load();
  }

  private boolean load() {
    try {
      DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(myTargetsFile)));
      try {
        input.readInt();//reserved for version
        int size = input.readInt();
        while (size-- > 0) {
          String stringId = IOUtil.readString(input);
          int intId = input.readInt();
          myTargetsState.markUsedId(intId);
          BuildTarget target = myTargetType.createTarget(stringId, myTargetsState.getRootsIndex(), myTargetsState.getArtifactRootsIndex());
          if (target != null) {
            myTargetIds.put(target, intId);
          }
          else {
            LOG.info("Unknown " + myTargetType.getTypeId() + " target: " + stringId);
          }
        }
        return true;
      }
      finally {
        input.close();
      }
    }
    catch (IOException e) {
      LOG.info("Cannot load " + myTargetType.getTypeId() + " targets data: " + e.getMessage(), e);
      return false;
    }
  }

  public synchronized void save() {
    try {
      FileUtil.createParentDirs(myTargetsFile);
      DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(myTargetsFile)));
      try {
        output.writeInt(0);
        output.writeInt(myTargetIds.size());
        for (Map.Entry<BuildTarget, Integer> entry : myTargetIds.entrySet()) {
          IOUtil.writeString(entry.getKey().getId(), output);
          output.writeInt(entry.getValue());
        }
      }
      finally {
        output.close();
      }
    }
    catch (IOException e) {
      LOG.info("Cannot save " + myTargetType.getTypeId() + " targets data: " + e.getMessage(), e);
    }
  }

  public synchronized int getTargetId(BuildTarget target) {
    if (!myTargetIds.containsKey(target)) {
      myTargetIds.put(target, myTargetsState.getFreeId());
    }
    return myTargetIds.get(target);
  }

  public BuildTargetConfiguration getConfiguration(BuildTarget target) {
    BuildTargetConfiguration configuration = myConfigurations.get(target);
    if (configuration == null) {
      configuration = new BuildTargetConfiguration(target, myTargetsState);
      myConfigurations.putIfAbsent(target, configuration);
      configuration = myConfigurations.get(target);
    }
    return configuration;
  }
}
