package de.nanoimaging.deconv;

public interface ModelManagerListener {

    void onStartDone(int taskId);

    void onRunDone(int taskId, String[] output);

    void onStopDone(int taskId);

    void onTimeout(int taskId);

    void onError(int taskId, int errCode);

    void onServiceDied();
}
