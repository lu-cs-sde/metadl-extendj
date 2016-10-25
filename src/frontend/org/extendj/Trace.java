/* Copyright (c) 2016, Jesper Öqvist <jesper.oqvist@cs.lth.se>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.extendj;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.zip.DeflaterOutputStream;

/**
 * General purpose event tracing framework.
 *
 * <p>Events are logged by pushEvent() and popEvent(). The resulting trace
 * can be sent via a socket to a profiling tool.
 */
public class Trace {
  private long start; // Start of time scale in nanoseconds.
  private static final int TRACE_FORMAT_VERSION = 2;


  // Version 2: switched child count from int to short.

  public Stack<Event> events = new Stack<Event>();

  public static class Event {
    public String name;
    public final long start;
    public final String metadata;
    public long end;
    public List<Event> children = new ArrayList<Event>();

    public Event(String name, long start) {
      this(name, start, "");
    }

    public Event(String name, long start, String metadata) {
      this.name = name;
      this.start = start;
      this.metadata = metadata;
    }
  }

  public Trace(String name) {
    start = System.nanoTime();
    Date timestamp = new Date();
    events.push(new Event(String.format("%s %tF %tT", name, timestamp, timestamp),
        System.nanoTime()));
  }

  /**
   * Sets the start time, in nanoseconds.
   */
  public void setStart(long start) {
    this.start = start;
  }

  /**
   * Adds a new event to the trace.
   * The new event should be popped by a later call to popEvent().
   */
  public void pushEvent(String name) {
    pushEvent(name, "");
  }

  /**
   * Adds a new event to the trace.
   * The new event should be popped by a later call to popEvent().
   */
  public void pushEvent(String name, String metadata) {
    Event event = new Event(name, System.nanoTime(), metadata);
    events.push(event);
  }

  /**
   * Pop the current event from the trace stack and update the end time.
   */
  public void popEvent() {
    long end = System.nanoTime();
    Event event = events.pop();
    event.end = end;
    events.peek().children.add(event);
  }

  /**
   * Write trace to file.
   * Log all collected trace information.
   * @param prefix the prefix to give the trace files
   */
  public void dumpTrace(String prefix) {
    if (events.size() != 1) {
      System.err.format("Warning: tracing event stack has unexpected size %d. "
          + "Only the top event will be reported.%n", events.size());
    }
    int suffix = 0;
    File dest;
    Date timestamp = new Date();
    String filename = String.format("%s.%tF.trace", prefix, timestamp);
    do {
      dest = new File(filename);
      suffix += 1;
      filename = String.format("%s.%tF.%d.trace", prefix, new java.util.Date(), suffix);
    } while (dest.exists());
    try {
      System.err.format("Writing trace to %s%n", dest.getAbsolutePath());
      writeTrace(new DataOutputStream(new DeflaterOutputStream(new FileOutputStream(dest))), start);
    } catch (IOException ignored) {
    }
  }

  private void writeTrace(OutputStream output, long t0) throws IOException {
    DataOutputStream out = new DataOutputStream(output);
    out.writeInt(-TRACE_FORMAT_VERSION);
    Event root = events.peek();
    root.end = System.nanoTime();
    Set<String> eventNames = getEventNames(root);
    Map<String, Integer> nameMap = new HashMap<String, Integer>();
    out.writeInt(eventNames.size());
    int id = 0;
    for (String name : eventNames) {
      nameMap.put(name, id++);
      out.writeUTF(name);
    }
    writeEvent(out, root, nameMap, t0);
    out.close();
  }

  private static void writeEvent(DataOutputStream out, Event event, Map<String, Integer> nameMap,
      long t0) throws IOException {
    out.writeInt(nameMap.get(event.name));
    out.writeUTF(event.metadata);
    out.writeLong(event.start - t0);
    out.writeLong(event.end - t0);
    if (event.children.size() > Short.MAX_VALUE) {
      throw new Error(String.format("Event has too many children to serialize (%d)!",
            event.children.size()));
    }
    out.writeShort(event.children.size());
    for (Event child : event.children) {
      writeEvent(out, child, nameMap, t0);
    }
  }

  private static Set<String> getEventNames(Event root) {
    Set<String> names = new HashSet<String>();
    Queue<Event> queue = new LinkedList<Event>();
    queue.add(root);
    while (!queue.isEmpty()) {
      Event event = queue.poll();
      names.add(event.name);
      queue.addAll(event.children);
    }
    return names;
  }

  public void sendTo(String host, int port) throws IOException {
    System.err.format("Sending trace to: %s:%d%n", host, port);
    Socket socket = new Socket(host, port);
    writeTrace(socket.getOutputStream(), start);
    //socket.close();
  }

}
