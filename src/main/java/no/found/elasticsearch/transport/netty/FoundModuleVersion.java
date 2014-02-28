/*
 * Copyright (c) 2013, Found AS.
 * See LICENSE for details.
 */

package no.found.elasticsearch.transport.netty;

public class FoundModuleVersion {

    // The logic for ID is: XXYYZZAAAA, where XX is major version, YY is minor version, ZZ is revision, and AAAA is an ES
    // version identifier

    // the (internal) format of the id is there so we can easily do after/before checks on the id

    public static final int V_0_8_5_1000_ID = /*000*/8051000;
    public static final FoundModuleVersion V_0_8_5_1000 = new FoundModuleVersion(V_0_8_5_1000_ID);

    public static final int V_0_8_5_0907_ID = /*000*/8050907;
    public static final FoundModuleVersion V_0_8_5_0907 = new FoundModuleVersion(V_0_8_5_0907_ID);

    public static final int V_0_8_5_0902_ID = /*000*/8050902;
    public static final FoundModuleVersion V_0_8_5_0902 = new FoundModuleVersion(V_0_8_5_0902_ID);

    public static final int V_0_8_6_1000_ID = /*000*/8061000;
    public static final FoundModuleVersion V_0_8_6_1000 = new FoundModuleVersion(V_0_8_6_1000_ID);

    public static final int V_0_8_6_0907_ID = /*000*/8060907;
    public static final FoundModuleVersion V_0_8_6_0907 = new FoundModuleVersion(V_0_8_6_0907_ID);

    public static final int V_0_8_6_0902_ID = /*000*/8060902;
    public static final FoundModuleVersion V_0_8_6_0902 = new FoundModuleVersion(V_0_8_6_0902_ID);

    public static final FoundModuleVersion CURRENT = V_0_8_5_1000;

    public final int id;
    public final byte major;
    public final byte minor;
    public final byte revision;
    public final byte build;

    FoundModuleVersion(int id) {
        this.id = id;
        this.major = (byte) ((id / 1000000) % 100);
        this.minor = (byte) ((id / 10000) % 100);
        this.revision = (byte) ((id / 100) % 100);
        this.build = (byte) (id % 100);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FoundModuleVersion version = (FoundModuleVersion) o;

        return id == version.id;
    }

    @Override
    public int hashCode() {
        return id;
    }
}
