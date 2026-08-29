package com.jaguarm.nauvisresearch.research;

import java.util.Comparator;
import java.util.List;

import com.jaguarm.nauvisresearch.NauvisResearch;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * The technology registry: a datapack registry, so a technology is a JSON file.
 *
 * <p>Same argument as recipes. The tree is data, and data is edited, reviewed and generated
 * without a compile - {@code tools/gen_technologies.py} writes all 216 files and
 * {@code checkTechnologies} holds the ones on disk to it. A Java constant per technology would
 * put a generated table in a source file and make the build the thing that ships the tree.
 *
 * <p>Entries live in {@code data/nauvis_research/nauvis_research/technology/}. The doubled
 * namespace is NeoForge's layout and not a mistake: the first is the datapack supplying the
 * entry, the second is the registry key's own namespace.
 *
 * <p><b>It is synced.</b> The network codec is not optional here - the research screen and the
 * recipe panel both run on the client and both have to know what a technology costs and unlocks.
 * Passing one makes the mod required on clients, which it is anyway: this is a pack mod, and a
 * client without it could not draw the lab.
 */
public final class ModTechnologies {

    private ModTechnologies() {}

    public static final ResourceKey<Registry<Technology>> REGISTRY = ResourceKey.createRegistryKey(
            Identifier.fromNamespaceAndPath(NauvisResearch.MODID, "technology"));

    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(REGISTRY, Technology.CODEC, Technology.CODEC);
    }

    /** `automation` -> the key for this mod's technology of that name. */
    public static ResourceKey<Technology> key(String path) {
        return ResourceKey.create(REGISTRY, Identifier.fromNamespaceAndPath(NauvisResearch.MODID, path));
    }

    public static Registry<Technology> registry(RegistryAccess access) {
        return access.lookupOrThrow(REGISTRY);
    }

    /**
     * Every technology, in Factorio's own order.
     *
     * <p>{@code order} is the string Factorio's own technology screen sorts on, transcribed
     * straight out of the prototypes, so a list drawn in this order is the list the game draws.
     * The key is the tiebreak rather than the primary sort, which matters: sorting on the key
     * alone would put "Automation 2" beside "Automation 3" and both a long way from "Automation",
     * because ids sort alphabetically and Factorio's tree does not.
     */
    public static List<Holder.Reference<Technology>> all(RegistryAccess access) {
        return registry(access).listElements()
                .sorted(Comparator
                        .<Holder.Reference<Technology>, String>comparing(holder -> holder.value().order())
                        .thenComparing(holder -> holder.key().identifier().toString()))
                .toList();
    }
}
