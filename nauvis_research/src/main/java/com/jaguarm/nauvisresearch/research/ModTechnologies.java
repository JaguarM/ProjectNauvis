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

/** The technology registry: a datapack registry, so a technology is a JSON file. */
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
