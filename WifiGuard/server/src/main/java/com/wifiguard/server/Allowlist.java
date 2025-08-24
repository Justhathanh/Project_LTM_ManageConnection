package com.wifiguard.server;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

public class Allowlist {
  private final File file;
  private final Set<String> set = new ConcurrentSkipListSet<>();

  public Allowlist(File file) throws IOException { this.file = file; load(); }
  public synchronized void load() throws IOException {
    set.clear();
    if (!file.exists()) return;
    try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
      String line; while ((line = br.readLine()) != null) {
        line = line.trim(); if (line.isEmpty() || line.startsWith("#")) continue;
        set.add(line.toLowerCase());
      }
    }
  }
  public synchronized void add(String mac) throws IOException { set.add(mac.toLowerCase()); save(); }
  public synchronized void remove(String mac) throws IOException { set.remove(mac.toLowerCase()); save(); }
  public boolean contains(String mac) { return set.contains(mac.toLowerCase()); }
  public Collection<String> all() { return Collections.unmodifiableCollection(set); }
  private synchronized void save() throws IOException {
    try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
      bw.write("# allowlist\n");
      for (String m : set) { bw.write(m); bw.write('\n'); }
    }
  }
}
