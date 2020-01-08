package com.example.smartdog.app;

class UartDataChunk {

    private byte[] data;
    private boolean isProcessed = false; // set true when app updated

    UartDataChunk(byte[] bytes) {
        this.data = bytes;
    }

    boolean getProcessed() { return isProcessed;}

    public void setProcessed(boolean processed) {
        this.isProcessed = processed;
    }

    public byte[] getData() {
        return data;
    }
}
