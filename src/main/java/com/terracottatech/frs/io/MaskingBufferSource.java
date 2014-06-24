/*
 * All content copyright (c) 2012 Terracotta, Inc., except as may otherwise
 * be noted in a separate copyright notice. All rights reserved.
 */

package com.terracottatech.frs.io;

import java.io.StringWriter;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author mscott
 */
public class MaskingBufferSource implements BufferSource {
  
  private final BufferSource parent;
  private final Map<BufferEquals, ByteBuffer> out = new ConcurrentHashMap<BufferEquals, ByteBuffer>();
  private static final Logger LOGGER = LoggerFactory.getLogger(MaskingBufferSource.class);
  private final ReferenceQueue<ByteBuffer> queue;
  private final boolean DEBUG_LEAKS = Boolean.getBoolean("frs.leak.debug");
  private AtomicLong allocations = new AtomicLong();
  private AtomicLong allocTime = new AtomicLong();

  public MaskingBufferSource(BufferSource parent) {
    this(parent,false);
  }
  
  public MaskingBufferSource(BufferSource parent, boolean garbageCollector) {
    this.parent = parent;
    if ( DEBUG_LEAKS || garbageCollector ) {
      queue = new ReferenceQueue<ByteBuffer>();
    } else {
      queue = null;
    }
  }

  @Override
  public ByteBuffer getBuffer(int size) {
    long ntime = System.nanoTime();
    try {
      ByteBuffer src = parent.getBuffer(size);
      if ( src == null ) {
        LOGGER.warn("using heap for recovery, add more recovery memory " + size);
        return ByteBuffer.allocate(size);
      }
      return add(src);
    } finally {
      long ttime = allocTime.addAndGet(System.nanoTime()-ntime);
      long tcount = allocations.incrementAndGet();
      if ( LOGGER.isDebugEnabled() ) {
        if ( tcount % 1000 == 0 ) {
          LOGGER.debug("average allocation time:" + (ttime/tcount) + " for " + tcount + " allocations");
          allocTime.set(0);
          allocations.set(0);
        }
      }
    }
  }

  @Override
  public void returnBuffer(ByteBuffer buffer) {
    ByteBuffer src = remove(buffer);
    if ( src != null ) {
      parent.returnBuffer(src);
    } else {
      LOGGER.debug("possible double free of memory resource" + buffer);
    }
  }
  
  private ByteBuffer remove(ByteBuffer src) {
    ByteBuffer be = out.remove(new BufferEquals(src));
    if ( be != null ) {
      return be;
    }
    return null;
  }
  
  private ByteBuffer add(ByteBuffer src) {
    this.clean();
    ByteBuffer pass = src.slice();
    BufferEquals key = ( queue == null ) ? new BufferEquals(pass) : new BufferEquals(new BufferHolder(pass, queue));
    if ( out.put(key, src) != null  ) {
      throw new AssertionError();
    }
    return pass;
  }
  
  private void clean() {
    if ( queue == null ) {
      return;
    }
    BufferHolder poll = (BufferHolder)queue.poll();
    while ( poll != null ) {
      ByteBuffer leak = out.remove(new BufferEquals(poll));
      if ( leak != null ) {
        LOGGER.debug("LEAK " + poll);
        parent.returnBuffer(leak);
      }
      poll = (BufferHolder)queue.poll();
    }
  }

  @Override
  public void reclaim() {
    for ( Map.Entry<BufferEquals,ByteBuffer> b : new ArrayList<Map.Entry<BufferEquals,ByteBuffer>>(out.entrySet()) ) {
      parent.returnBuffer(b.getValue());
    }
    out.clear();
    parent.reclaim();
  }
  
  private static class BufferHolder extends WeakReference<ByteBuffer> {
    
    private final StackTraceElement[] stack;
    private final int hashcode;

    public BufferHolder(ByteBuffer referent, ReferenceQueue<? super ByteBuffer> queue) {
      super(referent, queue);
      hashcode = System.identityHashCode(referent);
       stack = Thread.currentThread().getStackTrace();
    }

    @Override
    public int hashCode() {
      return hashcode;
    }

    @Override
    public String toString() {
      StringWriter w = new StringWriter();
      for ( StackTraceElement v : stack ) {
        w.append(v.toString());
        w.append('\n');
      }
      return w.toString();
    }
  }
  
  private class BufferEquals  {
    
    private final Object base;
    private final int hashcode;
        
    public BufferEquals(ByteBuffer src) {
      this.base = src;
      hashcode = System.identityHashCode(src);
    }
    
    public BufferEquals(BufferHolder src) {
      this.base = src;
      hashcode = src.hashCode();
    }
    
    @Override
    public int hashCode() {
      return hashcode;
    }
    
    public ByteBuffer getBuffer() {
      if ( base instanceof BufferHolder ) {
        return ((BufferHolder)this.base).get();
      }
      return (ByteBuffer)this.base;
    }

    @Override
    public boolean equals(Object obj) {
      if ( obj == null ) {
        return false;
      }

      if ( obj instanceof BufferEquals ) {
        if ( this.base instanceof BufferHolder ) {
          if ( this.base.equals(((BufferEquals)obj).base) ) {
            return true;
          }
        }
        return this.getBuffer() == ((BufferEquals)obj).getBuffer();
      }
      
      return false;
    }
    
  }

  @Override
  public String toString() {
    return "MaskingBufferSource{" + "parent=" + parent + ", out=" + out.size() + '}';
  }
  
  
}
