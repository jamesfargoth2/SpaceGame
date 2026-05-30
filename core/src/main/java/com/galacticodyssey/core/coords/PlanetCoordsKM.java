package com.galacticodyssey.core.coords;

/** Planet-space position in kilometres (double), relative to the planet centre. */
public record PlanetCoordsKM(double x, double y, double z) {
    public static final PlanetCoordsKM ORIGIN = new PlanetCoordsKM(0, 0, 0);

    public PlanetCoordsKM add(PlanetCoordsKM o) { return new PlanetCoordsKM(x + o.x, y + o.y, z + o.z); }
    public PlanetCoordsKM sub(PlanetCoordsKM o) { return new PlanetCoordsKM(x - o.x, y - o.y, z - o.z); }
    public PlanetCoordsKM scl(double s)         { return new PlanetCoordsKM(x * s, y * s, z * s); }
    public double len()  { return Math.sqrt(x * x + y * y + z * z); }
    public double dst(PlanetCoordsKM o) { double dx = x - o.x, dy = y - o.y, dz = z - o.z; return Math.sqrt(dx*dx + dy*dy + dz*dz); }
    /** @return unit vector, or the zero vector if {@code len() == 0}. */
    public PlanetCoordsKM nor() { double l = len(); return l == 0 ? this : new PlanetCoordsKM(x / l, y / l, z / l); }
}
