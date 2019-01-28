package com.expedia.adaptivealerting.anomdetect.util.metrics_java;

/**
 * @author shsethi
 */
import com.expedia.metrics.IdFactory;
import com.expedia.metrics.MetricDefinition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MetricTankIdFactory implements IdFactory {

    @Override
    public String getId(MetricDefinition metric) {
        return getKey(metric).toString();
    }

    public MetricKey getKey(MetricDefinition metric) {
        final int orgId = MessagePackSerializer.getOrgId(metric);
        final String name = metric.getKey();
        if (name == null) {
            throw new IllegalArgumentException("Property 'key' is required by metrictank");
        }
        final int interval = MessagePackSerializer.getInterval(metric);
        final String unit = MessagePackSerializer.getUnit(metric);
        final String mtype = MessagePackSerializer.getMtype(metric);
        List<String> formattedTags = formatTags(metric.getTags().getKv());
        return getKey(orgId, name, unit, mtype, interval, formattedTags);
    }

    public String getId(int orgId, String name, String unit, String mtype, int interval, List<String> tags) {
        return getKey(orgId, name, unit, mtype, interval, tags).toString();
    }

    public MetricKey getKey(int orgId, String name, String unit, String mtype, int interval, List<String> tags) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("The JRE is missing required digest algorithm MD5", e);
        }
        md.update(name.getBytes(StandardCharsets.UTF_8));
        md.update((byte)0);
        md.update(unit.getBytes(StandardCharsets.UTF_8));
        md.update((byte)0);
        md.update(mtype.getBytes(StandardCharsets.UTF_8));
        md.update((byte)0);
        md.update(Integer.toString(interval).getBytes(StandardCharsets.UTF_8));
        for (final String tag : tags) {
            md.update((byte)0);
            md.update(tag.getBytes(StandardCharsets.UTF_8));
        }
        return new MetricKey(orgId, md.digest());
    }

    public List<String> formatTags(Map<String, String> tags) {
        List<String> result = new ArrayList<>(tags.size());

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();

            if (key == null || key.isEmpty() || key.contains("=") || key.contains(";") || key.contains("!")) {
                throw new IllegalArgumentException("Metric tank does not support key: " + key);
            }
            if (value == null || value.isEmpty() || value.contains(";")) {
                throw new IllegalArgumentException("Metric tank does not support value [" + value + "] for key " + key);
            }
            result.add(key + "=" + value);
        }

        Collections.sort(result);
        return result;
    }
}