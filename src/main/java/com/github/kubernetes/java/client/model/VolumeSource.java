package com.github.kubernetes.java.client.model;

public class VolumeSource {
    private HostDir hostDir;

    public HostDir getHostDir() {
        return hostDir;
    }

    public void setHostDir(HostDir hostDir) {
        this.hostDir = hostDir;
    }
}
