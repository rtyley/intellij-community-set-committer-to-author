package com.intellij.execution.configuration;

import com.intellij.execution.Location;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author traff
 */
public abstract class RunConfigurationExtension<T extends AbstractRunConfiguration> {
  @NotNull
  protected abstract String getSerializationId();

  protected abstract void readExternal(@NotNull final T runConfiguration,
                                       @NotNull final Element element) throws InvalidDataException;

  protected abstract void writeExternal(@NotNull final T runConfiguration,
                                        @NotNull final Element element) throws WriteExternalException;

  @Nullable
  protected abstract Icon getIcon(@NotNull final T configuration);

  @Nullable
  protected abstract <P extends T> SettingsEditor<P> createEditor(@NotNull final P configuration);
  
  @Nullable
  protected abstract String getEditorTitle();

  /**
   * @param configuration Run configuration
   * @return True if extension in general applicable to given run configuration - just to attach settings tab, etc. But extension may be
   * turned off in it's settings. E.g. RCov in general available for given run configuration, but may be turned off.
   */
  protected abstract boolean isApplicableFor(@NotNull final T configuration);

  /**
   * @param applicableConfiguration Applicable run configuration
   * @return True if extension is tuned on in configuration extension settings.
   * E.g. RCov is turned on for given run configuration.
   */
  protected abstract boolean isEnabledFor(@NotNull final T applicableConfiguration);

  protected abstract void patchCommandLine(@NotNull final T configuration,
                                             @NotNull final GeneralCommandLine cmdLine,
                                             @NotNull final AbstractRunConfiguration.RunnerType type);

  /**
   * Validate extensions after general configuration validation passed
   * @param configuration
   * @param isExecution
   * @param <T>
   * @throws com.intellij.execution.ExecutionException
   */
  protected abstract void validateConfiguration(@NotNull final T configuration,
                                                                                        final boolean isExecution) throws Exception;

  /**
   * Setup extension settings for created run configuration
   * @param configuration Configuration
   * @param location
   */
  protected abstract void extendCreatedConfiguration(@NotNull final T configuration,
                                                                                              @NotNull final Location location);

  protected abstract void extendTemplateConfiguration(@NotNull final T configuration);

  protected abstract void attachToProcess(@NotNull final T configuration,
                                          @NotNull final ProcessHandler handler,
                                          @NotNull final AbstractRunConfiguration.RunnerType type);
}
