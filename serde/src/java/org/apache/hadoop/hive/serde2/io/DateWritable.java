/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hive.serde2.io;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.sql.Date;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.apache.hadoop.hive.serde2.ByteStream.RandomAccessOutput;
import org.apache.hadoop.hive.serde2.lazybinary.LazyBinaryUtils;
import org.apache.hadoop.hive.serde2.lazybinary.LazyBinaryUtils.VInt;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableUtils;


/**
 * DateWritable
 * Writable equivalent of java.sql.Date.
 *
 * Dates are of the format
 *    YYYY-MM-DD
 *
 */
public class DateWritable implements WritableComparable<DateWritable> {
  private static final long MILLIS_PER_DAY = TimeUnit.DAYS.toMillis(1);

  // Local time zone.
  // Java TimeZone has no mention of thread safety. Use thread local instance to be safe.
  private static final ThreadLocal<TimeZone> LOCAL_TIMEZONE = new ThreadLocal<TimeZone>() {
    @Override
    protected TimeZone initialValue() {
      return Calendar.getInstance().getTimeZone();
    }
  };
  private static final ThreadLocal<Calendar> UTC_CALENDAR = new ThreadLocal() {
    @Override
    protected Calendar initialValue() {
      return new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    }
  };
  private static final ThreadLocal<Calendar> LOCAL_CALENDAR = new ThreadLocal() {
    @Override
    protected Calendar initialValue() {
      return Calendar.getInstance();
    }
  };

  // Internal representation is an integer representing day offset from our epoch value 1970-01-01
  private int daysSinceEpoch = 0;

  /* Constructors */
  public DateWritable() {
  }

  public DateWritable(DateWritable d) {
    set(d);
  }

  public DateWritable(Date d) {
    set(d);
  }

  public DateWritable(int d) {
    set(d);
  }

  /**
   * Set the DateWritable based on the days since epoch date.
   * @param d integer value representing days since epoch date
   */
  public void set(int d) {
    daysSinceEpoch = d;
  }

  /**
   * Set the DateWritable based on the year/month/day of the date in the local timezone.
   * @param d Date value
   */
  public void set(Date d) {
    if (d == null) {
      daysSinceEpoch = 0;
      return;
    }

    set(dateToDays(d));
  }

  public void set(DateWritable d) {
    set(d.daysSinceEpoch);
  }

  /**
   *
   * @return Date value corresponding to the date in the local time zone
   */
  public Date get() {
    return get(true);
  }
  
  public Date get(boolean doesTimeMatter) {
    return new Date(daysToMillis(this.daysSinceEpoch, doesTimeMatter));
  }

  public int getDays() {
    return daysSinceEpoch;
  }

  /**
   *
   * @return time in seconds corresponding to this DateWritable
   */
  public long getTimeInSeconds() {
    return get().getTime() / 1000;
  }

  public static Date timeToDate(long l) {
    return new Date(l * 1000);
  }

  public static long daysToMillis(int d) {
    return daysToMillis(d, true);
  }
  
  public static long daysToMillis(int d, boolean doesTimeMatter) {
    // Convert from day offset to ms in UTC, then apply local timezone offset.
    long utcMidnight = d * MILLIS_PER_DAY;
    long utcMidnightOffset = ((TimeZone)LOCAL_TIMEZONE.get()).getOffset(utcMidnight);
    long hopefullyMidnight = utcMidnight - utcMidnightOffset;
    long offsetAtHM = ((TimeZone)LOCAL_TIMEZONE.get()).getOffset(hopefullyMidnight);
    if (utcMidnightOffset == offsetAtHM) {
      return hopefullyMidnight;
     }
    if (!doesTimeMatter) {
      return daysToMillis(d + 1) - (MILLIS_PER_DAY >> 1);
     }
    Calendar utc = (Calendar)UTC_CALENDAR.get();
    Calendar local = (Calendar)LOCAL_CALENDAR.get();
    utc.setTimeInMillis(utcMidnight);
    local.set(utc.get(1), utc.get(2), utc.get(5));
    return local.getTimeInMillis();
  }

  public static int millisToDays(long millisLocal) {
    long millisUtc = millisLocal + LOCAL_TIMEZONE.get().getOffset(millisLocal);
    int days;
    if (millisUtc >= 0L) {
      days = (int) (millisUtc / MILLIS_PER_DAY);
    } else {
      days = (int) ((millisUtc - 86399999) / MILLIS_PER_DAY);
    }
    return days;
  }

  public static int dateToDays(Date d) {
    // convert to equivalent time in UTC, then get day offset
    long millisLocal = d.getTime();
    return millisToDays(millisLocal);
  }

  public void setFromBytes(byte[] bytes, int offset, int length, VInt vInt) {
    LazyBinaryUtils.readVInt(bytes, offset, vInt);
    assert (length == vInt.length);
    set(vInt.value);
  }

  public void writeToByteStream(RandomAccessOutput byteStream) {
    LazyBinaryUtils.writeVInt(byteStream, getDays());
  }


  @Override
  public void readFields(DataInput in) throws IOException {
    daysSinceEpoch = WritableUtils.readVInt(in);
  }

  @Override
  public void write(DataOutput out) throws IOException {
    WritableUtils.writeVInt(out, daysSinceEpoch);
  }

  @Override
  public int compareTo(DateWritable d) {
    return daysSinceEpoch - d.daysSinceEpoch;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DateWritable)) {
      return false;
    }
    return compareTo((DateWritable) o) == 0;
  }

  @Override
  public String toString() {
    return get(false).toString();
  }

  @Override
  public int hashCode() {
    return daysSinceEpoch;
  }
}
