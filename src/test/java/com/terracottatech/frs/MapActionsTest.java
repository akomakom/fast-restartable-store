/*
 * All content copyright (c) 2012 Terracotta, Inc., except as may otherwise
 * be noted in a separate copyright notice. All rights reserved.
 */
package com.terracottatech.frs;

import com.terracottatech.frs.action.Action;
import com.terracottatech.frs.action.ActionCodec;
import com.terracottatech.frs.action.ActionCodecImpl;
import com.terracottatech.frs.object.ObjectManager;
import com.terracottatech.frs.transaction.TransactionActions;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;

/**
 * @author tim
 */
public class MapActionsTest {
  private ObjectManager<Long, ByteBuffer, ByteBuffer> objectManager;
  private ActionCodec                                 actionCodec;

  @Before
  public void setUp() throws Exception {
    objectManager = mock(ObjectManager.class);
    actionCodec = new ActionCodecImpl(objectManager);

    TransactionActions.registerActions(0, actionCodec);
    MapActions.registerActions(1, actionCodec);
  }

  private void checkEncodeDecode(Action action) throws Exception {
    assertThat(actionCodec.decode(actionCodec.encode(action)), is(action));
  }

  @Test
  public void testPutAction() throws Exception {
    Action put =
            new PutAction(objectManager, 0L, makeByteBuffer(1), makeByteBuffer(2));
    checkEncodeDecode(put);
  }

  @Test
  public void testDeleteAction() throws Exception {
    Action delete = new DeleteAction(objectManager, 1L);
    checkEncodeDecode(delete);
  }

  @Test
  public void testRemove() throws Exception {
    Action remove = new RemoveAction(objectManager, 2L, makeByteBuffer(10));
    checkEncodeDecode(remove);
  }

  private ByteBuffer makeByteBuffer(int i) {
    ByteBuffer buffer = ByteBuffer.allocate(Integer.SIZE);
    buffer.putInt(i).flip();
    return buffer;
  }
}
