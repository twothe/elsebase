package dev.backroomindustry;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Minimal entry point shared by client and dedicated server.
 * Gameplay registration is deferred until the design is approved.
 */
@Mod(BackroomIndustry.MOD_ID)
public final class BackroomIndustry {
    public static final String MOD_ID = "backroom_industry";
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Initializes the bootstrap mod without changing gameplay. */
    public BackroomIndustry() {
        LOGGER.info("Backroom Industry bootstrap loaded; gameplay is not implemented yet.");
    }
}
