package org.jetbrains.plugins.cucumber.java.run;

import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.List;
import java.util.Queue;

/**
 * User: Andrey.Vokin
 * Date: 8/10/12
 */
public class CucumberJvmSMFormatter implements Formatter, Reporter {
  private int scenarioCount;
  private int passedScenarioCount;
  private boolean scenarioPassed = true;

  private int stepCount;
  private int passedStepCount;
  private int skippedStepCount;
  private int pendingStepCount;
  private int failedStepCount;
  private int undefinedStepCount;

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSSZ");

  private static final String TEMPLATE_TEST_STARTED =
    "##teamcity[testStarted timestamp = '%s' locationHint = 'file:///%s' captureStandardOutput = 'true' name = '%s']";
  private static final String TEMPLATE_TEST_FAILED =
    "##teamcity[testFailed timestamp = '%s' details = '%s' message = '%s' name = '%s' %s]";
  private static final String TEMPLATE_SCENARIO_FAILED = "##teamcity[customProgressStatus timestamp='%s' type='testFailed']";
  private static final String TEMPLATE_TEST_PENDING =
    "##teamcity[testIgnored name = '%s' message = 'Skipped step' timestamp = '%s']";

  private static final String TEMPLATE_TEST_FINISHED =
    "##teamcity[testFinished timestamp = '%s' diagnosticInfo = 'cucumber  f/s=(1344855950447, 1344855950447), duration=0, time.now=%s' duration = '0' name = '%s']";

  private static final String TEMPLATE_ENTER_THE_MATRIX = "##teamcity[enteredTheMatrix timestamp = '%s']";

  private static final String TEMPLATE_TEST_SUITE_STARTED =
    "##teamcity[testSuiteStarted timestamp = '%s' locationHint = 'file://%s' name = '%s']";
  private static final String TEMPLATE_TEST_SUITE_FINISHED = "##teamcity[testSuiteFinished timestamp = '%s' name = '%s']";

  private static final String TEMPLATE_SCENARIO_COUNTING_STARTED =
    "##teamcity[customProgressStatus testsCategory = 'Scenarios' count = '0' timestamp = '%s']";
  private static final String TEMPLATE_SCENARIO_COUNTING_FINISHED =
    "##teamcity[customProgressStatus testsCategory = '' count = '0' timestamp = '%s']";
  private static final String TEMPLATE_SCENARIO_STARTED = "##teamcity[customProgressStatus type = 'testStarted' timestamp = '%s']";

  public static final String RESULT_STATUS_PENDING = "pending";

  private Appendable appendable;

  private Queue<String> queue;

  private String uri;
  private String currentFeatureName;

  private boolean beforeExampleSection;

  private ScenarioOutline currentScenarioOutline;

  private Scenario currentScenario;

  private Queue<Step> currentSteps;

  @SuppressWarnings("UnusedDeclaration")
  public CucumberJvmSMFormatter(Appendable appendable) {
    this.appendable = System.err;
    queue = new ArrayDeque<String>();
    currentSteps = new ArrayDeque<Step>();
    outCommand(String.format(TEMPLATE_ENTER_THE_MATRIX, getCurrentTime()));
    outCommand(String.format(TEMPLATE_SCENARIO_COUNTING_STARTED, getCurrentTime()));
  }

  @Override
  public void feature(Feature feature) {
    if (currentFeatureName != null) {
      done();
    }
    currentFeatureName = "Feature: " + getName(feature);
    outCommand(String.format(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), uri + ":" + feature.getLine(), currentFeatureName));
  }

  private boolean isRealScenario(final Scenario scenario) {
    return scenario.getKeyword().equals("Scenario");
  }

  @Override
  public void scenario(Scenario scenario) {
    closeScenario();
    if (isRealScenario(scenario)) {
      scenarioCount++;
      outCommand(String.format(TEMPLATE_SCENARIO_STARTED, getCurrentTime()));
      closeScenarioOutline();
      currentSteps.clear();
    }
    currentScenario = scenario;
    beforeExampleSection = false;
    outCommand(String.format(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), uri + ":" + scenario.getLine(), getName(currentScenario)));

    while (queue.size() > 0) {
      String smMessage = queue.poll();
      outCommand(smMessage);
    }
  }

  @Override
  public void scenarioOutline(ScenarioOutline outline) {
    scenarioCount++;
    outCommand(String.format(TEMPLATE_SCENARIO_STARTED, getCurrentTime()));
    queue.clear();
    currentSteps.clear();

    closePreviousScenarios();
    currentScenarioOutline = outline;
    currentScenario = null;
    beforeExampleSection = true;
    outCommand(
      String.format(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), uri + ":" + outline.getLine(), getName(currentScenarioOutline)));
  }

  @Override
  public void examples(Examples examples) {
    beforeExampleSection = false;
    outCommand(String.format(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), uri + ":" + examples.getLine(), "Examples:"));
  }

  @Override
  public void step(Step step) {
    if (beforeExampleSection) {
      return;
    }
    currentSteps.add(step);
  }

  @Override
  public void result(Result result) {
    stepCount++;
    Step currentStep = currentSteps.poll();
    outCommand(String.format(TEMPLATE_TEST_STARTED, getCurrentTime(), uri + ":" + currentStep.getLine(), getName(currentStep)), true);
    String stepFullName = getName(currentStep);
    if (result.getStatus().equals(Result.FAILED)) {
      failedStepCount++;
      scenarioPassed = false;
      String fullMessage = result.getErrorMessage().replace("\r", "").replace("\t", "  ");
      String[] messageInfo = fullMessage.split("\n", 2);
      final String message;
      final String details;
      if (messageInfo.length == 2) {
        message = messageInfo[0].trim();
        details = messageInfo[1].trim();
      }
      else {
        message = fullMessage;
        details = "";
      }

      outCommand(String.format(TEMPLATE_TEST_FAILED, getCurrentTime(), escape(details), escape(message), stepFullName, ""), true);
      outCommand(String.format(TEMPLATE_SCENARIO_FAILED, getCurrentTime()), true);
    }
    else if (result.getStatus().equals(RESULT_STATUS_PENDING)) {
      pendingStepCount++;
      scenarioPassed = false;
      outCommand(String.format(TEMPLATE_TEST_PENDING, stepFullName, getCurrentTime()), true);
    }
    else if (result.equals(Result.UNDEFINED)) {
      undefinedStepCount++;
      scenarioPassed = false;
      String message = "Undefined step: " + getName(currentStep);
      String details = "";
      outCommand(String.format(TEMPLATE_TEST_FAILED, getCurrentTime(), escape(details), escape(message), stepFullName, "error = 'true'"), true);
      outCommand(String.format(TEMPLATE_SCENARIO_FAILED, getCurrentTime()), true);
    }
    else if (result.equals(Result.SKIPPED)) {
      skippedStepCount++;
      scenarioPassed = false;
      outCommand(String.format(TEMPLATE_TEST_PENDING, stepFullName, getCurrentTime()), true);
    }
    else {
      passedStepCount++;
    }

    String currentTime = getCurrentTime();
    outCommand(String.format(TEMPLATE_TEST_FINISHED, currentTime, currentTime, stepFullName), true);
  }

  private void closeScenario() {
    if (currentScenario != null) {
      if (isRealScenario(currentScenario)) {
        if (scenarioPassed) {
          passedScenarioCount++;
        }
      }
      outCommand(String.format(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), getName(currentScenario)));
    }
    currentScenario = null;
  }

  private void closeScenarioOutline() {
    if (currentScenarioOutline != null) {
      if (scenarioPassed) {
        passedScenarioCount++;
      }
      if (!beforeExampleSection) {
        outCommand(String.format(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), "Examples:"));
      }
      outCommand(String.format(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), getName(currentScenarioOutline)));
    }
    currentScenarioOutline = null;
  }

  private void closePreviousScenarios() {
    closeScenario();
    closeScenarioOutline();
  }

  @Override
  public void background(Background background) {
    closeScenario();
    currentScenario = null;
  }

  @Override
  public void done() {
    closePreviousScenarios();
    outCommand(String.format(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), currentFeatureName));
  }

  @Override
  public void uri(String s) {
    String currentDir = System.getenv().get("current_dir");
    if (currentDir != null) {
      uri = currentDir + File.separator + s;
    }
    else {
      uri = s;
    }
  }

  @Override
  public void eof() {
  }

  @Override
  public void syntaxError(String s, String s1, List<String> strings, String s2, Integer integer) {
    outCommand("Syntax error\n");
  }

  @Override
  public void after(Match match, Result result) {
    outCommand("after\n");
  }

  @Override
  public void match(Match match) {
  }

  @SuppressWarnings("UnusedDeclaration")
  public void embedding(String mimeType, byte[] data) {
    outCommand("embedding\n");
  }

  @SuppressWarnings("UnusedDeclaration")
  public void embedding(String s, InputStream inputStream) {
    outCommand("embedding\n");
  }

  @Override
  public void write(String s) {
    out(s);
  }

  @Override
  public void close() {
    outCommand(String.format(TEMPLATE_SCENARIO_COUNTING_FINISHED, getCurrentTime()));

    outCommand(scenarioCount + " scenario (" + passedScenarioCount + " passed)\n");
    outCommand(stepCount + " steps (" + passedStepCount + " passed)\n");
  }

  @Override
  public void before(Match match, Result result) {
    outCommand("before\n");
  }

  private String getCurrentTime() {
    return DATE_FORMAT.format(new Date());
  }

  private String escape(String source) {
    return source.replace("|", "||").replace("\n", "|n").replace("\r", "|r").replace("'", "|'").replace("[", "|[").replace("]", "|]");
  }

  private void outCommand(String s) {
    outCommand(s, false);
  }

  private void outCommand(String s, boolean waitForScenario) {
    if (currentScenario == null && waitForScenario) {
      queue.add(s);
    }
    else {
      try {
        appendable.append("\n");
        appendable.append(s);
        appendable.append("\n");
      }
      catch (IOException ignored) {
      }
    }
  }

  private void out(String s) {
    try {
      appendable.append(s);
    }
    catch (IOException ignored) {
    }
  }

  private String getName(Scenario scenario) {
    if (scenario.getKeyword().equals("Scenario Outline")) {
      return escape("Scenario: Line: " + scenario.getLine());
    }
    else {
      return escape("Scenario: " + scenario.getName());
    }
  }

  private String getName(ScenarioOutline outline) {
    return escape("Scenario Outline: " + outline.getName());
  }

  private String getName(Step step) {
    return escape(step.getKeyword() + " " + step.getName());
  }

  private String getName(Feature feature) {
    return escape(feature.getName());
  }
}
