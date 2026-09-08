/**
 * The one place the pack reaches into the engine: Minecraft's ninety-nine-item stack, lifted
 * to {@link com.jaguarm.nauvislib.item.Stacks#CEILING}.
 *
 * <p>Ninety-nine is written in four places and copied into a fifth, and each is its own mixin:
 *
 * <ul>
 *   <li>{@link com.jaguarm.nauvislib.mixin.ExtraCodecsMixin}: the count codecs. {@code ItemStack},
 *       {@code ItemStackTemplate} and the {@code max_stack_size} component all build their range
 *       as {@code ExtraCodecs.intRange(1, 99)}, inside lambdas, so the range is caught where it is
 *       made rather than where it is used. Nothing else in Minecraft or NeoForge asks for that
 *       exact range.</li>
 *   <li>{@link com.jaguarm.nauvislib.mixin.InventoryMixin},
 *       {@link com.jaguarm.nauvislib.mixin.SimpleContainerMixin} and
 *       {@link com.jaguarm.nauvislib.mixin.BaseContainerBlockEntityMixin}: {@code Container}'s
 *       default {@code getMaxStackSize()} is ninety-nine, and Mixin cannot inject into an
 *       interface, so the player's inventory, the plain container and every chest, barrel,
 *       hopper and furnace answer the ceiling instead. Minecart chests and any mod's own
 *       container still answer ninety-nine.</li>
 *   <li>{@link com.jaguarm.nauvislib.mixin.ItemStacksResourceHandlerMixin} and
 *       {@link com.jaguarm.nauvislib.mixin.ItemStackResourceHandlerMixin}: NeoForge's handlers cap
 *       a slot at {@code Item.ABSOLUTE_MAX_STACK_SIZE}, which is a compile-time constant and so
 *       inlined - changing the field would change nothing - so the method is replaced.</li>
 *   <li>{@link com.jaguarm.nauvislib.mixin.ItemEntityMixin}: two piles on the ground merge up to
 *       sixty-four, a literal; now up to the item's stack.</li>
 *   <li>{@link com.jaguarm.nauvislib.mixin.client.GuiGraphicsExtractorMixin}: the count in a slot
 *       is drawn for two digits; three or four are scaled to fit.</li>
 * </ul>
 *
 * <p>Read from Bigger Stacks for the list of places, and written fresh against 26.2's sources:
 * that mod is LGPL and this one is MIT, and its targets are 1.21's.
 */
package com.jaguarm.nauvislib.mixin;
