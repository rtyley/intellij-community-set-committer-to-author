/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.errorreport.bean;

import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NonNls;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: May 5, 2003
 * Time: 9:34:26 PM
 * To change this template use Options | File Templates.
 */
public class ErrorBean {
  private Date date;
  private String os;
  private String lastAction;
  private String description;

  private String message;
  private String stackTrace;

  private String exceptionClass = "";

  public ErrorBean(Throwable throwable, String lastAction) {
    if (throwable != null) {
      exceptionClass = throwable.getClass().getName();

      message = throwable.getMessage();

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      throwable.printStackTrace(new PrintStream(baos, true));
      stackTrace = baos.toString();
    }
    os = SystemProperties.getOsName();
    date = new Date();
    this.lastAction = lastAction;
  }

  public Date getDate() {
    return date;
  }

  public String getOs() {
    return os;
  }

  public String getLastAction() {
    return lastAction;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(@NonNls String description) {
    this.description = description;
  }

  public String getExceptionClass() {
    return exceptionClass;
  }

  public String getStackTrace() {
    return stackTrace;
  }

  public String getMessage() {
    return message;
  }
}
