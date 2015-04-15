package com.github.kubernetes.java.client.unit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kubernetes.java.client.model.HostDir;
import com.github.kubernetes.java.client.model.Volume;
import com.github.kubernetes.java.client.model.VolumeSource;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.StringWriter;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Category(com.github.kubernetes.java.client.UnitTests.class)
public class VolumeTest {

    @Test
    public void testSerializeVolumeMount() throws Exception {
        Volume volume = new Volume();
        VolumeSource volumeSource = new VolumeSource();
        HostDir hostDir = new HostDir();
        hostDir.setPath("/mnt/mountpoint");
        volumeSource.setHostDir(hostDir);
        volume.setName("volname");
        volume.setSource(volumeSource);

        ObjectMapper mapper = new ObjectMapper();
        final StringWriter w = new StringWriter();
        mapper.writeValue(w, volume);

        final JsonNode tree = mapper.readTree(w.toString());
        final JsonNode mountpoint = tree.get("source").get("hostDir").get("path");
        assertThat(mountpoint.asText(), is("/mnt/mountpoint"));
    }
}