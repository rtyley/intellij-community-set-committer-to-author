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
package com.intellij.util.text;

import com.intellij.openapi.util.Clock;
import junit.framework.TestCase;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateFormatUtilTest extends TestCase {
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy hh.mm.ss");

  public void testBasics() throws ParseException {
    Clock.setTime(2004, 11, 10, 17, 10);

    doTestPrettyDate("Today", "10.12.2004 17.00.00");
    doTestPrettyDate("Today", "10.12.2004 00.00.00");
    doTestPrettyDate("Yesterday", "09.12.2004 23.59.59");
    doTestPrettyDate(DateFormatUtil.formatDate(DATE_FORMAT.parse("08.12.2004 23.59.59")), "08.12.2004 23.59.59");
    doTestPrettyDate(DateFormatUtil.formatDate(DATE_FORMAT.parse("10.12.2003 17.00.00")), "10.12.2003 17.00.00");
  }

  public void testPrettyDate() throws ParseException {
    Clock.setTime(2004, 11, 10, 17, 10, 15);

    assertEquals("17:10", DateFormatUtil.formatTime(Clock.getTime()));
    assertEquals("17:10:15", DateFormatUtil.formatTimeWithSeconds(Clock.getTime()));
  }

  public void testPrettyDateTime() throws ParseException {
    Clock.setTime(2004, 11, 10, 17, 0);

    doTestDateTime("Moments ago", "10.12.2004 16.59.31");
    doTestDateTime("A minute ago", "10.12.2004 16.59.29");
    doTestDateTime("5 minutes ago", "10.12.2004 16.55.00");
    doTestDateTime("1 hour ago", "10.12.2004 16.00.00");
    doTestDateTime("Today " + DateFormatUtil.formatTime(DATE_FORMAT.parse("10.12.2004 15.55.00")), "10.12.2004 15.55.00");
    doTestDateTime("Yesterday " + DateFormatUtil.formatTime(DATE_FORMAT.parse("09.12.2004 15.00.00")), "09.12.2004 15.00.00");

    doTestDateTime(DateFormatUtil.formatDateTime(DATE_FORMAT.parse("08.12.2004 15.00.00")), "08.12.2004 15.00.00");
    doTestDateTime(DateFormatUtil.formatDateTime(DATE_FORMAT.parse("07.12.2004 15.00.00")), "07.12.2004 15.00.00");

    Clock.setTime(2004, 0, 1, 15, 53);
    doTestDateTime(DateFormatUtil.formatDateTime(DATE_FORMAT.parse("01.01.2003 15.53.00")), "01.01.2003 15.53.00");
    doTestDateTime("Yesterday " + DateFormatUtil.formatTime(DATE_FORMAT.parse("31.12.2003 15.00.00")), "31.12.2003 15.00.00");
  }

  private void doTestPrettyDate(String expected, String date) throws ParseException {
    assertEquals(expected, DateFormatUtil.formatPrettyDate(DATE_FORMAT.parse(date)));
  }

  private void doTestDateTime(String expected, String date) throws ParseException {
    assertEquals(expected, DateFormatUtil.formatPrettyDateTime(DATE_FORMAT.parse(date)));
  }

  public void testConvertingMacToJavaPattern() throws Throwable {
    Clock.setTime(new Date(2004 - 1900, 1, 5, 16, 6, 7).getTime() + 8);

    String mon = new SimpleDateFormat("MMM").format(Clock.getTime());
    String month = new SimpleDateFormat("MMMMM").format(Clock.getTime());
    String weekd = new SimpleDateFormat("EEE").format(Clock.getTime());
    String weekday = new SimpleDateFormat("EEEEE").format(Clock.getTime());

    assertConvertedFormat("%y %Y", "04 2004");
    assertConvertedFormat("%b %B %m", mon + " " + month + " 02");
    assertConvertedFormat("%d %e %j", "05 5 036");
    assertConvertedFormat("%a %A %w", weekd + " " + weekday + " " + weekd);

    assertConvertedFormat("%H %I", "16 04");
    assertConvertedFormat("%M %S %F %p", "06 07 008 PM");

    assertConvertedFormat("%z %Z", "+0300 MSK");

    assertConvertedFormat(" foo bar ", " foo bar ");
    assertConvertedFormat(" 'foo''a'a'' '' ' ", " 'foo''a'a'' '' ' ");
    assertConvertedFormat(" '%a''%a'%a'' '' ' '%a ", " '" + weekd + "''" + weekd + "'" + weekd + "'' '' ' '" + weekd + " ");
    assertConvertedFormat("'a'", "'a'");
    assertConvertedFormat("'", "'");
    assertConvertedFormat("''", "''");
    assertConvertedFormat("a", "a");
    assertConvertedFormat(" ", " ");
    assertConvertedFormat("%1", "?%1?");
    assertConvertedFormat("", "");

    assertConvertedFormat("%", "");
  }

  private void assertConvertedFormat(String pattern, String expected) throws Throwable {
    String converted = DateFormatUtil.convertMacPattern(pattern);
    try {
      assertEquals(expected, new SimpleDateFormat(converted).format(Clock.getTime()));
    }
    catch (Throwable e) {
      System.out.println("cannot format with [" + converted + "]");
      throw e;
    }
  }
}
