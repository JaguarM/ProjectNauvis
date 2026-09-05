package com.jaguarm.nauvisfluids.compat.facrafting;

import com.jaguarm.facrafting.progress.MiningListeners;
import com.jaguarm.nauvisfluids.oil.OilProgress;

/**
 * Tells Facrafting what the pumpjacks are taking out of the ground.
 *
 * <h2>Why this is a package of its own</h2>
 *
 * <p>{@code neoforge.mods.toml} declares Facrafting <b>optional</b>, and it stays optional: the
 * pipe has a standalone bench recipe precisely so this mod can be played without it. But this class
 * names a Facrafting type, so loading it without Facrafting present would be a
 * {@code NoClassDefFoundError} at mod construction. So it lives behind a branch:
 * {@code NauvisFluids}'s constructor calls {@link #install()} only when {@code ModList} says
 * Facrafting is loaded, and the JVM resolves the reference the first time that call executes. The
 * Jade plugins and {@code nauvis_research}'s crafting gate use exactly the same trick.
 *
 * <h2>Why Facrafting, and not research</h2>
 *
 * <p>Oil processing is finished by pumping oil once, and research is another subsystem mod, which
 * this one may not depend on - non-negotiable #3. Facrafting is the one mod every subsystem may
 * compile against, and its {@code MiningListeners} is a meeting point that knows nothing about
 * oil or research: the pumpjack reports, research listens, and neither names the other.
 */
public final class FacraftingProgress {

    private FacraftingProgress() {}

    public static void install() {
        // The well's position is this mod's business; what crosses the seam is the resource and
        // how many times it was taken.
        OilProgress.add((level, well, resource, cycles) -> MiningListeners.fire(level, resource, cycles));
    }
}
