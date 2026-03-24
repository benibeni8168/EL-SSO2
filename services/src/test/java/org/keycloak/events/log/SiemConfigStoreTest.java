package org.keycloak.events.log;

import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

public class SiemConfigStoreTest {

    @Test
    public void realmOverrideWinsOverGlobalDefaults() {
        SiemConfigRepresentation global = new SiemConfigRepresentation();
        global.setEnabled(true);
        global.setExportUrl("https://global.example/siem");
        global.setFormat(LogExportConfig.FORMAT_CEF);

        SiemConfigRepresentation realm = new SiemConfigRepresentation();
        realm.setEnabled(true);
        realm.setInheritGlobal(false);
        realm.setExportUrl("https://realm.example/siem");
        realm.setFormat(LogExportConfig.FORMAT_SYSLOG);

        SiemConfigRepresentation merged = SiemConfigStore.mergeConfig(realm, global, null);

        Assert.assertNotNull(merged);
        Assert.assertEquals("https://realm.example/siem", merged.getExportUrl());
        Assert.assertEquals(LogExportConfig.FORMAT_SYSLOG, merged.getFormat());
    }

    @Test
    public void inheritedGlobalIsUsedWhenRealmDefersToGlobal() {
        SiemConfigRepresentation global = new SiemConfigRepresentation();
        global.setEnabled(true);
        global.setExportUrl("https://global.example/siem");
        global.setIncludeAdminEvents(false);
        global.setCustomHeaders(Map.of("X-SIEM", "true"));

        SiemConfigRepresentation realm = new SiemConfigRepresentation();
        realm.setInheritGlobal(true);

        SiemConfigRepresentation merged = SiemConfigStore.mergeConfig(realm, global, null);

        Assert.assertNotNull(merged);
        Assert.assertEquals("https://global.example/siem", merged.getExportUrl());
        Assert.assertFalse(merged.isIncludeAdminEvents());
        Assert.assertEquals(Set.of("X-SIEM"), merged.getCustomHeaders().keySet());
    }

    @Test
    public void disabledConfigDoesNotSyncListener() {
        SiemConfigRepresentation config = new SiemConfigRepresentation();
        config.setEnabled(false);
        config.setExportUrl("https://collector.example/siem");

        Assert.assertFalse(SiemConfigStore.shouldSyncListener(config));
    }

    @Test
    public void enabledConfigWithUrlSyncsListener() {
        SiemConfigRepresentation config = new SiemConfigRepresentation();
        config.setEnabled(true);
        config.setExportUrl("https://collector.example/siem");

        Assert.assertTrue(SiemConfigStore.shouldSyncListener(config));
    }

    @Test
    public void legacyStaticConfigIsUsedWhenNoRealmOverridesExist() {
        LogExportConfig legacy = new LogExportConfig(
                "https://legacy.example/siem",
                "",
                LogExportConfig.FORMAT_CEF,
                Set.of(),
                Set.of(),
                true,
                Map.of());

        SiemConfigRepresentation merged = SiemConfigStore.mergeConfig(
                null,
                null,
                SiemConfigStore.toLegacyRepresentation(legacy, true));
        Assert.assertNotNull(merged);
        Assert.assertEquals("https://legacy.example/siem", merged.getExportUrl());
        Assert.assertEquals(LogExportConfig.FORMAT_CEF, merged.getFormat());
    }

    @Test
    public void explicitRealmDisableOverridesGlobalAndLegacy() {
        SiemConfigRepresentation global = new SiemConfigRepresentation();
        global.setEnabled(true);
        global.setExportUrl("https://global.example/siem");

        SiemConfigRepresentation realm = new SiemConfigRepresentation();
        realm.setEnabled(false);
        realm.setInheritGlobal(false);
        realm.setExportUrl("https://realm.example/siem");

        LogExportConfig legacy = new LogExportConfig(
                "https://legacy.example/siem",
                "",
                LogExportConfig.FORMAT_JSON,
                Set.of(),
                Set.of(),
                true,
                Map.of());

        SiemConfigRepresentation merged = SiemConfigStore.mergeConfig(
                realm,
                global,
                SiemConfigStore.toLegacyRepresentation(legacy, true));

        Assert.assertNotNull(merged);
        Assert.assertFalse(merged.isEnabled());
        Assert.assertEquals("https://realm.example/siem", merged.getExportUrl());
    }
}
