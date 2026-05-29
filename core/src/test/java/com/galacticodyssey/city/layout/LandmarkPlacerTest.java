package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.city.layout.model.Landmark;
import com.galacticodyssey.city.layout.model.LandmarkType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LandmarkPlacerTest {

    @Test
    void placesCivicCentreNearOrigin() {
        List<Landmark> lm = LandmarkPlacer.place(700f, true, new ArrayList<>(), 1L);
        Landmark civic = find(lm, LandmarkType.CIVIC_CENTRE);
        assertNotNull(civic);
        assertTrue(civic.position.len() < 0.1f * 700f);
    }

    @Test
    void spaceportSitsInOuterBand() {
        List<Landmark> lm = LandmarkPlacer.place(700f, true, new ArrayList<>(), 2L);
        Landmark sp = find(lm, LandmarkType.SPACEPORT);
        assertNotNull(sp);
        float d = sp.position.len();
        assertTrue(d >= 0.65f * 700f && d <= 0.85f * 700f, "spaceport at 65-85% radius, was " + d);
    }

    @Test
    void marketIsNotAtCentre() {
        List<Landmark> lm = LandmarkPlacer.place(700f, true, new ArrayList<>(), 3L);
        Landmark mk = find(lm, LandmarkType.MARKET_PLAZA);
        assertNotNull(mk);
        assertTrue(mk.position.len() > 0.15f * 700f);
    }

    @Test
    void authoredLandmarksArePreservedAndFlagged() {
        List<AuthoredLandmark> authored = new ArrayList<>();
        authored.add(new AuthoredLandmark(LandmarkType.FACTION_LANDMARK, new Vector2(123f, -45f)));
        List<Landmark> lm = LandmarkPlacer.place(700f, true, authored, 4L);
        Landmark a = lm.stream().filter(l -> l.authored).findFirst().orElse(null);
        assertNotNull(a);
        assertEquals(123f, a.position.x, 0.0001f);
        assertEquals(-45f, a.position.y, 0.0001f);
    }

    @Test
    void deterministic() {
        List<Landmark> a = LandmarkPlacer.place(700f, true, new ArrayList<>(), 9L);
        List<Landmark> b = LandmarkPlacer.place(700f, true, new ArrayList<>(), 9L);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).type, b.get(i).type);
            assertEquals(a.get(i).position.x, b.get(i).position.x, 0.0f);
            assertEquals(a.get(i).position.y, b.get(i).position.y, 0.0f);
        }
    }

    private Landmark find(List<Landmark> lm, LandmarkType t) {
        return lm.stream().filter(l -> l.type == t).findFirst().orElse(null);
    }
}
