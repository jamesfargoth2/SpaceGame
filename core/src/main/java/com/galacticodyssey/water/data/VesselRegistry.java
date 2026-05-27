package com.galacticodyssey.water.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads and vends {@link VesselData} records from JSON data files.
 *
 * <p>Two overloads are provided for each load operation:
 * <ul>
 *   <li>{@code loadVessels(String path)} — production path via {@link Gdx#files}.</li>
 *   <li>{@code loadVessels(JsonValue root)} — test-friendly overload.</li>
 * </ul>
 */
public class VesselRegistry {

    private final Map<String, VesselData> vessels = new HashMap<>();

    /** Loads vessel definitions from an internal asset path. */
    public void loadVessels(String path) {
        loadVessels(new JsonReader().parse(Gdx.files.internal(path)));
    }

    /** Parses vessel definitions from a pre-parsed {@link JsonValue} array root. */
    public void loadVessels(JsonValue root) {
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            VesselData data = new VesselData();
            data.id = entry.getString("id");
            data.name = entry.getString("name");
            data.sizeClass = entry.getString("sizeClass", "small");

            data.length = entry.getFloat("length");
            data.beam = entry.getFloat("beam");
            data.draft = entry.getFloat("draft");
            data.freeboard = entry.getFloat("freeboard");

            data.dryMass = entry.getFloat("dryMass");
            data.blockCoefficient = entry.getFloat("blockCoefficient", 0.6f);

            data.skinFrictionCd = entry.getFloat("skinFrictionCd", 0.05f);
            data.formDragCd = entry.getFloat("formDragCd", 0.8f);

            data.maxThrust = entry.getFloat("maxThrust");
            data.maxReverseThrust = entry.getFloat("maxReverseThrust");
            data.rudderTorque = entry.getFloat("rudderTorque");
            data.throttleResponseRate = entry.getFloat("throttleResponseRate", 2.0f);
            data.rudderResponseRate = entry.getFloat("rudderResponseRate", 1.5f);
            data.minSpeedForFullRudder = entry.getFloat("minSpeedForFullRudder", 3.0f);

            data.samplePointCount = entry.getInt("samplePointCount", 16);

            data.linearDamping = entry.getFloat("linearDamping", 0.02f);
            data.angularDamping = entry.getFloat("angularDamping", 0.05f);

            vessels.put(data.id, data);
        }
    }

    /** Returns the vessel data for {@code id}, or {@code null} if not found. */
    public VesselData getVessel(String id) {
        return vessels.get(id);
    }

    /** Returns all loaded vessel IDs. */
    public Iterable<String> getVesselIds() {
        return vessels.keySet();
    }
}
