/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.terracottatech.fastrestartablestore.mock;

import com.terracottatech.fastrestartablestore.ReplayFilter;
import java.io.Serializable;

import com.terracottatech.fastrestartablestore.messages.Action;
import com.terracottatech.fastrestartablestore.spi.ObjectManager;

/**
 * 
 * @author cdennis
 */
class MockTransactionalAction implements Action, Serializable {
  private static final long serialVersionUID = 1L;
  private final long id;
  private final Action embedded;

  public MockTransactionalAction(long id, Action action) {
    this.id = id;
    this.embedded = action;
  }
  
  @Override
  public long record(ObjectManager<?, ?, ?> objManager, long lsn) {
    return embedded.record(objManager, lsn);
  }

  @Override
  public boolean replay(ReplayFilter filter, ObjectManager<?, ?, ?> objManager, long lsn) {
    if (filter.allows(this)) {
      return embedded.replay(filter, objManager, lsn);
    } else {
      return false;
    }
  }

  public String toString() {
    return "Transactional[id=" + id + "] " + embedded;
  }

  long getId() {
    return id;
  }
}
