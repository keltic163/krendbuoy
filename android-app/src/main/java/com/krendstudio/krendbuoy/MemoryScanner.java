package com.krendstudio.krendbuoy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * A Cheat-Engine style memory scanner for finding and filtering addresses.
 */
final class MemoryScanner {
    private List<Integer> lastResults = new ArrayList<>();
    private boolean isXorMode = false;
    private int xorKey = 0;

    public boolean isXorMode() {
        return isXorMode;
    }

    void setXorMode(boolean enabled, int key) {
        this.isXorMode = enabled;
        this.xorKey = key;
    }

    List<Integer> getResults() {
        return lastResults;
    }

    int firstScan(int value) {
        lastResults.clear();
        
        // Scan BOTH EWRAM (0x02000000) and IWRAM (0x03000000)
        int[][] regions = {
            {0x02000000, 0x02040000}, // EWRAM 256KB
            {0x03000000, 0x03008000}  // IWRAM 32KB
        };

        for (int[] region : regions) {
            int start = region[0];
            int end = region[1];
            int size = end - start;
            int step = 8192;
            
            for (int addr = start; addr < end; addr += step) {
                byte[] chunk = NativeBridge.readMemory(addr, step + 4);
                if (chunk == null) continue;
                ByteBuffer bb = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN);
                
                // CRITICAL: Scan EVERY byte offset, not just 4-byte boundaries
                for (int i = 0; i < chunk.length - 4; i++) {
                    int current = bb.getInt(i);
                    if (current == value || (isXorMode && (current ^ xorKey) == value)) {
                        lastResults.add(addr + i);
                    }
                    if (lastResults.size() > 10000) return lastResults.size(); 
                }
            }
        }
        return lastResults.size();
    }

    int nextScan(int value) {
        List<Integer> newResults = new ArrayList<>();
        for (int addr : lastResults) {
            byte[] data = NativeBridge.readMemory(addr, 4);
            if (data == null || data.length < 4) continue;
            int current = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (current == value || (isXorMode && (current ^ xorKey) == value)) {
                newResults.add(addr);
            }
        }
        lastResults = newResults;
        return lastResults.size();
    }
}
