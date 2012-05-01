/*
 * All content copyright (c) 2012 Terracotta, Inc., except as may otherwise
 * be noted in a separate copyright notice. All rights reserved.
 */
package com.terracottatech.frs.log;

import com.terracottatech.frs.io.BufferSource;
import com.terracottatech.frs.io.Chunk;
import com.terracottatech.frs.io.WrappingChunk;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;

/**
 *
 * @author mscott
 */
public class CopyingPacker extends LogRegionPacker {
    
//    private ByteBuffer headers = ByteBuffer.allocate(1024 * 128);
    private final BufferSource pool;
    
    public CopyingPacker(Signature sig, BufferSource copyInto) {
        super(sig);
        pool = copyInto;
    }   
    
    private int sizeRegion(Iterable<LogRecord> records) {    
        int size = 0;
        for ( LogRecord r : records ) {
            ByteBuffer[] s = r.getPayload();
            for ( ByteBuffer b : s ) {
                size += b.remaining();
            }
            size += LOG_RECORD_HEADER_SIZE;
        }
        size += LOG_REGION_HEADER_SIZE;
        size *= 1.10;
        return size;
    }

    @Override
    protected Chunk writeRecords(Iterable<LogRecord> records) {        
        long lowestLsn = 0;
        int count = 0;

        ByteBuffer header = pool.getBuffer(sizeRegion(records));
 //  no more direct memory, do it the slow way
        if ( header == null ) {
            return super.writeRecords(records);
        }
        
        header.position(LOG_REGION_HEADER_SIZE);

        for (LogRecord record : records) {
            ByteBuffer copy = header.slice();
            copy.position(LOG_RECORD_HEADER_SIZE);
            
            ByteBuffer[] payload = record.getPayload();
            long len = 0;
            for ( ByteBuffer bb : payload ) {
                len += bb.remaining();
                copy.put(bb);
                count++;
            }

            if (lowestLsn == 0 || lowestLsn > record.getLowestLsn()) {
                lowestLsn = record.getLowestLsn();
            }

            header.position(header.position() + copy.position());
            
            copy.flip();            
            formRecordHeader(len,record.getLsn(),record.getLowestLsn(),copy);
        }
        
        header.flip();
        formRegionHeader(doChecksum() ? checksum(Collections.singletonList((ByteBuffer)header.duplicate().position(LOG_REGION_HEADER_SIZE))) : 0,(ByteBuffer)header.duplicate());
        return new WrappingChunk(header);
    }
}