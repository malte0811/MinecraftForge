/*
 * Minecraft Forge
 * Copyright (c) 2016-2019.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.common.loot;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.world.storage.loot.LootContext;

/**
 * Implementation that defines what a global loot modifier must implement in order to be functional.
 * {@link LootModifier} Supplies base functionality; most modders should only need to extend that.<br/>
 * Requires an {@link GlobalLootModifierSerializer} to be registered via json (see forge:loot_modifiers/global_loot_modifiers).
 */
public interface IGlobalLootModifier {
    /**
     * Applies the modifier to the list of generated loot. This function needs to be responsible for
     * checking ILootConditions as well.
     * @param generatedLoot the list of ItemStacks that will be dropped, generated by loot tables
     * @param context the LootContext, identical to what is passed to loot tables
     * @return modified loot drops
     */
    @Nonnull
    List<ItemStack> apply(List<ItemStack> generatedLoot, LootContext context);
}