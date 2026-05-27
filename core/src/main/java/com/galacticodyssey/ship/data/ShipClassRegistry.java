package com.galacticodyssey.ship.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads and vends {@link ShipClassData} and {@link AtmosphereProfile} records from JSON data files.
 *
 * <p>Two overloads are provided for each load operation:
 * <ul>
 *   <li>{@code loadX(String path)} – production path that reads from {@link Gdx#files}.</li>
 *   <li>{@code loadX(JsonValue root)} – test-friendly overload that accepts a pre-parsed root.</li>
 * </ul>
 */
public class ShipClassRegistry {

    private final Map<String, ShipClassData> shipClasses = new HashMap<>();
    private final Map<String, AtmosphereProfile> atmosphereProfiles = new HashMap<>();

    // -------------------------------------------------------------------------
    // Ship classes
    // -------------------------------------------------------------------------

    /** Loads ship class definitions from an internal asset path. */
    public void loadShipClasses(String path) {
        loadShipClasses(new JsonReader().parse(Gdx.files.internal(path)));
    }

    /** Parses ship class definitions from a pre-parsed {@link JsonValue} array root. */
    public void loadShipClasses(JsonValue root) {
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            ShipClassData data = new ShipClassData();
            data.id = entry.getString("id");
            data.name = entry.getString("name");
            data.sizeClass = entry.getString("sizeClass");
            data.mass = entry.getFloat("mass");
            data.linearThrust = entry.getFloat("linearThrust");
            data.strafeThrustFraction = entry.getFloat("strafeThrustFraction");
            data.verticalThrustFraction = entry.getFloat("verticalThrustFraction");
            data.pitchYawTorque = entry.getFloat("pitchYawTorque");
            data.rollTorque = entry.getFloat("rollTorque");
            data.linearDrag = entry.getFloat("linearDrag");
            data.angularDrag = entry.getFloat("angularDrag");
            data.maxIsp = entry.getFloat("maxIsp");
            data.maxThrust = entry.getFloat("maxThrust");
            data.throttleResponseRate = entry.getFloat("throttleResponseRate");
            data.fuelCapacity = entry.getFloat("fuelCapacity");
            data.wingArea = entry.getFloat("wingArea");
            data.dragCoefficient = entry.getFloat("dragCoefficient");
            data.crossSectionArea = entry.getFloat("crossSectionArea");
            data.stallAngle = entry.getFloat("stallAngle");
            data.maxLiftCoefficient = entry.getFloat("maxLiftCoefficient");
            data.controlSurfaceAuthority = entry.getFloat("controlSurfaceAuthority");
            data.vtolThrustFraction = entry.getFloat("vtolThrustFraction");

            JsonValue lc = entry.get("liftCurve");
            if (lc != null) {
                data.liftCurve = new float[lc.size];
                for (int i = 0; i < lc.size; i++) {
                    data.liftCurve[i] = lc.getFloat(i);
                }
            }

            JsonValue wg = entry.get("defaultWeaponGroups");
            if (wg != null) {
                data.defaultWeaponGroups = new String[wg.size];
                for (int i = 0; i < wg.size; i++) {
                    data.defaultWeaponGroups[i] = wg.getString(i);
                }
            }

            shipClasses.put(data.id, data);
        }
    }

    // -------------------------------------------------------------------------
    // Atmosphere profiles
    // -------------------------------------------------------------------------

    /** Loads atmosphere profiles from an internal asset path. */
    public void loadAtmosphereProfiles(String path) {
        loadAtmosphereProfiles(new JsonReader().parse(Gdx.files.internal(path)));
    }

    /** Parses atmosphere profiles from a pre-parsed {@link JsonValue} array root. */
    public void loadAtmosphereProfiles(JsonValue root) {
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            AtmosphereProfile profile = new AtmosphereProfile();
            profile.id = entry.getString("id");
            profile.name = entry.getString("name");
            profile.surfaceDensity = entry.getFloat("surfaceDensity");
            profile.scaleHeight = entry.getFloat("scaleHeight");
            profile.speedOfSound = entry.getFloat("speedOfSound");
            profile.machThreshold = entry.getFloat("machThreshold");
            profile.composition = entry.getString("composition", "");
            atmosphereProfiles.put(profile.id, profile);
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the ship class data for {@code id}, or {@code null} if not found. */
    public ShipClassData getShipClass(String id) {
        return shipClasses.get(id);
    }

    /** Returns the atmosphere profile for {@code id}, or {@code null} if not found. */
    public AtmosphereProfile getAtmosphereProfile(String id) {
        return atmosphereProfiles.get(id);
    }
}
