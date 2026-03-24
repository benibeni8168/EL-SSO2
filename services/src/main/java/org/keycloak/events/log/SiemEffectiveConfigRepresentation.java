package org.keycloak.events.log;

public class SiemEffectiveConfigRepresentation extends SiemConfigRepresentation {

    private SiemConfigRepresentation globalDefaults;
    private SiemConfigRepresentation realmOverride;

    public SiemConfigRepresentation getGlobalDefaults() {
        return globalDefaults;
    }

    public void setGlobalDefaults(SiemConfigRepresentation globalDefaults) {
        this.globalDefaults = globalDefaults;
    }

    public SiemConfigRepresentation getRealmOverride() {
        return realmOverride;
    }

    public void setRealmOverride(SiemConfigRepresentation realmOverride) {
        this.realmOverride = realmOverride;
    }
}
