/*
 * Copyright (C) 2011 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.ros.time;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import org.ros.exception.RosRuntimeException;
import org.ros.message.Duration;
import org.ros.message.Time;

import java.io.IOException;
import java.net.InetAddress;

/**
 * @author damonkohler@google.com (Damon Kohler)
 */
public class NtpTimeProvider implements TimeProvider {

  private final InetAddress host;
  private final NTPUDPClient ntpClient;
  private final WallTimeProvider wallTimeProvider;

  private TimeInfo time;

  public NtpTimeProvider(InetAddress host) {
    this.host = host;
    this.wallTimeProvider = new WallTimeProvider();
    ntpClient = new NTPUDPClient();
  }

  public void updateTime() {
    try {
      time = ntpClient.getTime(host);
    } catch (IOException e) {
      throw new RosRuntimeException("Failed to read time from NTP server " + host.getHostName(), e);
    }
    time.computeDetails();
  }

  @Override
  public Time getCurrentTime() {
    Time currentTime = wallTimeProvider.getCurrentTime();
    long offset = time.getOffset();
    return currentTime.add(Duration.fromMillis(offset));
  }

}